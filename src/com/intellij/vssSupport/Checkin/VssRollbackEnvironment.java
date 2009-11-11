package com.intellij.vssSupport.Checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jun 28, 2007
 */
public class VssRollbackEnvironment implements RollbackEnvironment
{
  public static final Key<Boolean> RENAME_ROLLBACK = new Key<Boolean>( "RENAME_ROLLBACK" );

  private final Project project;
  private final VssVcs host;

  public VssRollbackEnvironment( Project project, VssVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public String getRollbackOperationName() {  return VcsBundle.message("changes.action.rollback.text");  }

  public void rollbackChanges(List<Change> changes, final List<VcsException> errors, @NotNull final RollbackProgressListener listener)
  {
    List<String> renamedFolders = new ArrayList<String>();
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();

    listener.determinate();
    
    rollbackRenamedFolders( changes, processedFiles, renamedFolders, listener);
    rollbackNew( changes, processedFiles, listener);
    rollbackDeleted( changes, processedFiles, errors, listener);
    rollbackChanged( changes, processedFiles, errors, listener);

    for( String path : renamedFolders )
      host.renamedFolders.remove( VcsUtil.getCanonicalLocalPath( path ) );

    VcsUtil.refreshFiles( project, processedFiles );
  }

  private void rollbackRenamedFolders( List<Change> changes, HashSet<FilePath> processedFiles,
                                              List<String> renamedFolders, @NotNull final RollbackProgressListener listener)
  {
    for( Change change : changes )
    {
      if( VcsUtil.isRenameChange( change ) && VcsUtil.isChangeForFolder( change ) )
      {
        listener.accept(change);
        //  The only thing which we can perform on this step is physical
        //  rename of the folder back to its former name, since we can't
        //  keep track of what consequent changes were done (due to Java
        //  semantics of the package rename).
        FilePath folder = change.getAfterRevision().getFile();
        File folderNew = folder.getIOFile();
        File folderOld = change.getBeforeRevision().getFile().getIOFile();
        folderNew.renameTo( folderOld );
        VcsUtil.waitForTheFile( folderOld.getPath() );

        //  Remember these renamed folders so that we still can use them for
        //  proper resolving of changed files under these folders, but afterwards
        //  we need to remove them from the list of renamed folders.
        renamedFolders.add( folderNew.getPath() );
        
        processedFiles.add( folder );
      }
    }
  }

  private void rollbackNew( List<Change> changes, HashSet<FilePath> processedFiles, @NotNull final RollbackProgressListener listener)
  {
    HashSet<FilePath> newFileAndFolders = new HashSet<FilePath>();
    collectNewChangesBack( changes, newFileAndFolders, processedFiles );

    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    for( FilePath folder : newFileAndFolders )
    {
      listener.accept(folder);
      host.deleteNewFile( folder.getVirtualFile() );
      mgr.fileDirty( folder );
    }
  }

  /**
   * For each accumulated (to be rolledback) folder - collect ALL files
   * in the change lists with the status NEW (ADDED) which are UNDER this folder.
   * This ensures that no file will be left in any change list with status NEW.
   */
  private void collectNewChangesBack( List<Change> changes, HashSet<FilePath> newFilesAndFolders,
                                      HashSet<FilePath> processedFiles )
  {
    HashSet<FilePath> foldersNew = new HashSet<FilePath>();
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForNew( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();

        newFilesAndFolders.add( filePath );
        if( filePath.isDirectory() )
          foldersNew.add( filePath );

        processedFiles.add( filePath );
      }
    }

    ChangeListManager clMgr = ChangeListManager.getInstance(project);
    FileStatusManager fsMgr = FileStatusManager.getInstance(project);
    List<VirtualFile> allAffectedFiles = clMgr.getAffectedFiles();

    for( VirtualFile file : allAffectedFiles )
    {
      if( fsMgr.getStatus( file ) == FileStatus.ADDED )
      {
        for( FilePath folder : foldersNew )
        {
          if( file.getPath().toLowerCase().startsWith( folder.getPath().toLowerCase() ))
          {
            FilePath path = clMgr.getChange( file ).getAfterRevision().getFile();
            newFilesAndFolders.add( path );
          }
        }
      }
    }
  }

  private void rollbackDeleted( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors, @NotNull final RollbackProgressListener listener)
  {
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForDeleted( change ))
      {
        listener.accept(change);
        FilePath filePath = change.getBeforeRevision().getFile();
        rollbackMissingFileDeletion( filePath, errors );
        processedFiles.add( filePath );
      }
    }
  }

  private void rollbackChanged( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors, @NotNull final RollbackProgressListener listener)
  {
    ArrayList<String> rollbacked = new ArrayList<String>();
    for( Change change : changes )
    {
      if( !VcsUtil.isChangeForNew( change ) &&
          !VcsUtil.isChangeForDeleted( change ) &&
          !VcsUtil.isChangeForFolder( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        String path = filePath.getPath();
        listener.accept(change);

        if( VcsUtil.isRenameChange( change ) )
        {
          //  Track two different cases:
          //  - we delete the file which is already in the repository.
          //    Here we need to "Get" the latest version of the original
          //    file from the repository, make "UndoCheckout" for it (since
          //    it was obviously checked out for a rename change), and
          //    delete the new file.
          //  - we delete the renamed file which is new and does not exist
          //    in the repository. We need to ignore the error message from
          //    the SourceSafe ("file not existing") and just delete the
          //    new file.

          List<VcsException> localErrs = new ArrayList<VcsException>();
          final FilePath oldFile = change.getBeforeRevision().getFile();

          boolean fileAbsent = host.getLatestVersion( oldFile.getPath(), false, localErrs );
          if( fileAbsent )
            errors.addAll( localErrs );
          else
          {
            //-----------------------------------------------------------------
            //  1. add marker telling that we do not want to track this file's
            //     deletion event.
            //     NB: VirtualFile for this file can be null if e.g. this renamed
            //         file occured within renamed folder. Folders are rollbacked
            //         first, so there could be no placeholder for the old file
            //         location.
            //  2. WAIT for this file. Without this we aint be able to issue
            //     "Undo CheckOut" command since it work only on VirtualFiles.
            // ToDO: refactor UndoCheckout so that it does not require VirtualFiles.
            //-----------------------------------------------------------------
            VcsUtil.waitForTheFile( oldFile.getPath() );

            VirtualFile vfile = filePath.getVirtualFile();
            if( vfile != null )
              vfile.putUserData( RENAME_ROLLBACK, true );

            rollbacked.add( oldFile.getPath() );
          }

          host.renamedFiles.remove( path );
          FileUtil.delete( new File( path ) );
        }
        else
        {
          path = discoverOldName( path ); 
          //  Collect all files to be uncheckouted into one set so that
          //  if dialog popups we could say "Yes to all"
          rollbacked.add( path );
        }
        processedFiles.add( filePath );
      }
    }

    if( rollbacked.size() > 0 )
    {
      String[] files = ArrayUtil.toStringArray(rollbacked);
      host.rollbackChanges( files, errors );
    }
  }

  public List<VcsException> scheduleMissingFileForDeletion( List<FilePath> paths )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    for( FilePath file : paths )
    {
      String path = file.getPath();
      host.removeFile( path, errors );

      host.removedFiles.remove( VcsUtil.getCanonicalLocalPath( path ) );
      host.removedFolders.remove( VcsUtil.getCanonicalLocalPath( path ) );
    }
    return errors;
  }

  public void rollbackMissingFileDeletion(List<FilePath> paths, final List<VcsException> errors,
                                                        final RollbackProgressListener listener)
  {
    for( FilePath path : paths )
    {
      listener.accept(path);
      rollbackMissingFileDeletion( path, errors );
    }
  }

  private void rollbackMissingFileDeletion( FilePath filePath, List<VcsException> errors )
  {
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    File file = filePath.getIOFile();

    //  VSS can not get the content of the directory (subproject) if:
    //  - there is no corresponding local working folder and
    //  - there is no files in that folder.
    //  Thus we need to create this subfolder anyway, set it as the working folder
    //  and only after that issue "Get latest version".
    String path = VcsUtil.getCanonicalLocalPath( file.getPath() );
    if( host.isDeletedFolder( path ) ) 
    {
      //  In order for the "GetFileCommand" to retrieve the folder, it must
      //  already exist so that we correctly determine the working folder
      //  (it is a parent for the file and a folder itself for a particular
      //  folder).
      file.mkdir();
    }

    host.rollbackDeleted( file.getPath(), errors );
    mgr.fileDirty( filePath );
  }

  public void rollbackModifiedWithoutCheckout(final List<VirtualFile> files, final List<VcsException> errors,
                                                            final RollbackProgressListener listener)
  {
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    for( VirtualFile file : files )
    {
      listener.accept(file);
      host.getLatestVersion( file.getPath(), false, errors );
      file.refresh( true, file.isDirectory() );
      mgr.fileDirty( file );
    }
  }

  public void rollbackIfUnchanged(VirtualFile file) {
  }

  private String discoverOldName( String file )
  {
    String oldName = host.renamedFiles.get( VssUtil.getCanonicalLocalPath( file ) );
    if( oldName == null )
    {
      oldName = host.renamedFolders.get( VssUtil.getCanonicalLocalPath( file ) );
      if( oldName == null )
      {
        oldName = findInRenamedParentFolder( file );
        if( oldName == null )
          oldName = file;
      }
    }

    return oldName;
  }

  private String findInRenamedParentFolder( String name )
  {
    String fileInOldFolder = name;
    for( String folder : host.renamedFolders.keySet() )
    {
      String oldFolderName = host.renamedFolders.get( folder );
      if( name.startsWith( folder ) )
      {
        fileInOldFolder = oldFolderName + name.substring( folder.length() );
        break;
      }
    }
    return fileInOldFolder;
  }
}
