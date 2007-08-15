package com.intellij.vssSupport.Checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssVcs;

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

  private Project project;
  private VssVcs host;

  public VssRollbackEnvironment( Project project, VssVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public String getRollbackOperationName() {  return VcsBundle.message("changes.action.rollback.text");  }

  public List<VcsException> rollbackChanges( List<Change> changes )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();

    rollbackRenamedFolders( changes, processedFiles );
    rollbackNew( changes, processedFiles );
    rollbackDeleted( changes, processedFiles, errors );
    rollbackChanged( changes, processedFiles, errors );

    VcsUtil.refreshFiles( project, processedFiles );

    return errors;
  }

  private void rollbackRenamedFolders( List<Change> changes, HashSet<FilePath> processedFiles )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isRenameChange( change ) && VcsUtil.isChangeForFolder( change ) )
      {
        //  The only thing which we can perform on this step is physical
        //  rename of the folder back to its former name, since we can't
        //  keep track of what consequent changes were done (due to Java
        //  semantics of the package rename).
        FilePath folder = change.getAfterRevision().getFile();
        File folderNew = folder.getIOFile();
        File folderOld = change.getBeforeRevision().getFile().getIOFile();
        folderNew.renameTo( folderOld );
        VcsUtil.waitForTheFile( folderOld.getPath() );
        host.renamedFolders.remove( VcsUtil.getCanonicalLocalPath( folderNew.getPath() ) );
        processedFiles.add( folder );
      }
    }
  }

  private void rollbackNew( List<Change> changes, HashSet<FilePath> processedFiles )
  {
    HashSet<FilePath> newFileAndFolders = new HashSet<FilePath>();
    collectNewChangesBack( changes, newFileAndFolders, processedFiles );

    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    for( FilePath folder : newFileAndFolders )
    {
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

  private void rollbackDeleted( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForDeleted( change ))
      {
        FilePath filePath = change.getBeforeRevision().getFile();
        rollbackMissingFileDeletion( filePath, errors );
        processedFiles.add( filePath );
      }
    }
  }

  private void rollbackChanged( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors )
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
          //  Collect all files to be uncheckouted into one set so that
          //  if dialog popups we could say "Yes to all"
          rollbacked.add( path );
        }
        processedFiles.add( filePath );
      }
    }

    if( rollbacked.size() > 0 )
    {
      String[] files = rollbacked.toArray( new String[ rollbacked.size() ] );
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

  public List<VcsException> rollbackMissingFileDeletion( List<FilePath> paths )
  {
    List<VcsException> errors = new ArrayList<VcsException>();

    for( FilePath path : paths )
    {
      rollbackMissingFileDeletion( path, errors );
    }
    return errors;
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

  public List<VcsException> rollbackModifiedWithoutCheckout( final List<VirtualFile> files )
  {
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    List<VcsException> errors = new ArrayList<VcsException>();
    for( VirtualFile file : files )
    {
      host.getLatestVersion( file.getPath(), false, errors );
      file.refresh( true, file.isDirectory() );
      mgr.fileDirty( file );
    }
    return errors;
  }

  public void rollbackIfUnchanged(VirtualFile file) {
  }
}
