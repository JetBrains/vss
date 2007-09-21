/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.10.2006
 * Time: 19:49:23
 */
package com.intellij.vssSupport.Checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.*;

import javax.swing.*;
import java.util.*;

public class VssCheckinEnvironment implements CheckinEnvironment
{
  public static final Key<Boolean> RENAME_ROLLBACK = new Key<Boolean>( "RENAME_ROLLBACK" );

  private Project project;
  private VssVcs host;
  private double fraction;

  public VssCheckinEnvironment( Project project, VssVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public String getCheckinOperationName()  {  return VssBundle.message("action.name.checkin");  }

  public String prepareCheckinMessage( String text ) {  return text;  }

  public boolean showCheckinDialogInAnyCase()   {  return false;  }

  public String getHelpId() {  return null;   }

  public RefreshableOnComponent createAdditionalOptionsPanel( CheckinProjectPanel panel )
  {
    VssConfiguration config = VssConfiguration.getInstance( project );
    final CheckinOptions checkinOptions = config.getCheckinOptions();
    final AddOptions addOptions = config.getAddOptions();

    final JPanel additionalPanel = new JPanel();
    additionalPanel.setLayout( new BoxLayout( additionalPanel, BoxLayout.PAGE_AXIS ));
    final JCheckBox keepCheckedOut = new JCheckBox( VssBundle.message("checkbox.option.keep.checked.out") );
    additionalPanel.add( keepCheckedOut );

    //  Add "Store only latest version" is shown only in the case if there is
    //  at least one file with status "ADDED".
    final JCheckBox storeOnlyLatestVersion = new JCheckBox( VssBundle.message("checkbox.option.store.only.latest.version") );
    if( isAnyNewFile( panel.getVirtualFiles() ) )
    {
      additionalPanel.add( storeOnlyLatestVersion );
    }

    return new RefreshableOnComponent()
    {
      public JComponent getComponent() {   return additionalPanel;    }

      public void saveState() {
        addOptions.STORE_ONLY_LATEST_VERSION = storeOnlyLatestVersion.isSelected();
        checkinOptions.KEEP_CHECKED_OUT = addOptions.CHECK_OUT_IMMEDIATELY = keepCheckedOut.isSelected();
      }

      public void restoreState() {  refresh();   }

      public void refresh() {
        storeOnlyLatestVersion.setSelected(addOptions.STORE_ONLY_LATEST_VERSION);
        keepCheckedOut.setSelected(checkinOptions.KEEP_CHECKED_OUT);
      }
    };
  }

  private boolean isAnyNewFile( final Collection<VirtualFile> files )
  {
    FileStatusManager mgr = FileStatusManager.getInstance(project);
    for( VirtualFile file : files )
    {
      if( mgr.getStatus( file ) == FileStatus.ADDED )
        return true;
    }
    return false;
  }
  /**
   * Force to reuse the last checkout's comment for the checkin.
   */
  public String getDefaultMessageFor( FilePath[] filesToCheckin )
  {
    VssConfiguration config = VssConfiguration.getInstance(project);

    //  If Checkout comment is null, <caller> will inherit last commit's
    //  message for this commit.
    return config.getCheckoutOptions().COMMENT;

    //  TODO: reuse comment for a changlist (if it was set) as the checkin comment
  }

  public List<VcsException> commit( List<Change> changes, String comment )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();

    if( comment != null )
      VssConfiguration.getInstance(project).getCheckinOptions().COMMENT = comment;

    //  Keep track of the fact that we deal with renamed fodlers. This will
    //  help us to suppress undesirable warning messages of type
    //  "X was checkout from Y folder, continue?" which are invevitable when
    //  we will checkin changed files under the renamed fodlers.
    boolean isAnyAddedFolder = adjustChangesWithRenamedParentFolders( changes );

    try
    {
      initProgress( changes.size() );

      //  Committing of renamed folders must be performed first since they
      //  affect all other checkings under them (except those having status
      //  "ADDED") since:
      //  - if modified file is checked in before renamed folder checkin then
      //    we need to checkin from (yet) nonexisting file into (already) non-
      //    existing space. It is too tricky to recreate the old folders
      //    structure and commit from out of there.
      //  - if modified file is checked AFTER the renamed folder has been
      //    checked in, we just have to checkin in into the necessary place,
      //    just get the warning that we checking in file which was checked out
      //    from another location. Supress it.

      commitRenamedFolders( changes, errors );

      commitDeleted( changes, errors );

      //  IMPORTANT!
      //  Committment of the changed files must be performed first because of
      //  specially processed exceptions described in the ChangeProvider.
      commitChanged( changes, processedFiles, errors, isAnyAddedFolder );
      commitNew( changes, processedFiles, errors );
    }
    catch( ProcessCanceledException e )
    {
      //  Nothing to do, just refresh the files which are already committed.
    }

     VcsUtil.refreshFiles( project, processedFiles );

    return errors;
  }

  private boolean adjustChangesWithRenamedParentFolders( List<Change> changes )
  {
    Set<VirtualFile> renamedFolders = new HashSet<VirtualFile>();
    boolean isAnyAddedFolder = getNecessaryRenamedFoldersForList( changes, renamedFolders );
    if( isAnyAddedFolder )
    {
      for( VirtualFile folder : renamedFolders )
        changes.add( ChangeListManager.getInstance( project ).getChange( folder ) );
    }
    return isAnyAddedFolder;
  }

  private void commitRenamedFolders( List<Change> changes, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isRenameChange( change ) && VcsUtil.isChangeForFolder( change ) )
      {
        FilePath newFile = change.getAfterRevision().getFile();
        FilePath oldFile = change.getBeforeRevision().getFile();

        host.renameDirectory(oldFile.getPath(), newFile.getName(), errors);
        host.renamedFolders.remove(newFile.getPath());
        incrementProgress(newFile.getPath());
      }
    }
  }

  /**
   *  Add all folders first, then add all files into these folders.
   *  Difference between added and modified files is that added file
   *  has no "before" revision.
   */
  private void commitNew( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    HashSet<FilePath> folders = new HashSet<FilePath>();
    HashSet<FilePath> files = new HashSet<FilePath>();

    collectNewChanges( changes, folders, files, processedFiles );

    //  Sort folders in ascending order - from the most outer folder
    //  to the inner one.
    FilePath[] foldersSorted = folders.toArray( new FilePath[ folders.size() ] );
    foldersSorted = VcsUtil.sortPathsFromOutermost( foldersSorted );

    for( FilePath folder : foldersSorted )
      host.addFolder( folder.getVirtualFile(), errors );

    for( FilePath file : files )
    {
      host.addFile( file.getVirtualFile(), errors );
      incrementProgress( file.getPath() );
    }
  }

  private void collectNewChanges( List<Change> changes, HashSet<FilePath> folders,
                                  HashSet<FilePath> files, HashSet<FilePath> processedFiles )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForNew( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        if( filePath.isDirectory() )
          folders.add( filePath );
        else
        {
          files.add( filePath );
          analyzeParent( filePath, folders, processedFiles );
        }
        processedFiles.add( filePath );
      }
    }
  }

  /**
   * If the parent of the file has status New or Unversioned - add it
   * to the list of folders OBLIGATORY for addition into the repository -
   * no file can be added into VSS without all higher folders are already
   * presented there.
   * Process with the parent's parent recursively.
   */
  private void analyzeParent( FilePath file, HashSet<FilePath> folders,
                              HashSet<FilePath> processedFiles )
  {
    VirtualFile parent = file.getVirtualFileParent();
    FileStatus status = FileStatusManager.getInstance( project ).getStatus( parent );
    if( status == FileStatus.ADDED || status == FileStatus.UNKNOWN )
    {
      FilePath parentPath = file.getParentPath();

      folders.add( parentPath );
      processedFiles.add( parentPath );
      analyzeParent( parentPath, folders, processedFiles );
    }
  }

  private void commitDeleted( List<Change> changes, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForDeleted( change ) )
      {
        final FilePath fp = change.getBeforeRevision().getFile();
        String path = VcsUtil.getCanonicalLocalPath( fp.getPath() );
        host.removeFile( path, errors );

        host.deletedFiles.remove( path );
        host.deletedFolders.remove( path );
        ApplicationManager.getApplication().invokeLater( new Runnable() {
          public void run() { VcsDirtyScopeManager.getInstance( project ).fileDirty( fp );  }
        });
      }
    }
  }

  private void commitChanged( List<Change> changes, HashSet<FilePath> processedFiles,
                              List<VcsException> errors, boolean suppressWarns )
  {
    for( Change change : changes )
    {
      if( !VcsUtil.isChangeForNew( change ) &&
          !VcsUtil.isChangeForDeleted( change ) &&
          !VcsUtil.isChangeForFolder( change ) )
      {
        FilePath file = change.getAfterRevision().getFile();
        ContentRevision before = change.getBeforeRevision();
        String newPath = VcsUtil.getCanonicalLocalPath( file.getPath() );
        String oldPath = host.renamedFiles.get( newPath );
        if( oldPath != null )
        {
          FilePath oldFile = before.getFile();
          String prevPath = VcsUtil.getCanonicalLocalPath( oldFile.getPath() );

          //  If parent folders' names of the revisions coinside, then we
          //  deal with the simle rename, otherwise we process full-scaled
          //  file movement across folders (packages).

          if( oldFile.getVirtualFileParent().getPath().equals( file.getVirtualFileParent().getPath() ))
          {
            host.renameAndCheckInFile( prevPath, file.getName(), errors );
          }
          else
          {
            String newFolder = VcsUtil.getCanonicalLocalPath( file.getVirtualFileParent().getPath() );
            host.moveRenameAndCheckInFile( prevPath, newFolder, file.getName(), errors );
          }
          host.renamedFiles.remove( newPath );
        }
        else
        {
          host.checkinFile( file.getVirtualFile(), errors, suppressWarns );
        }

        incrementProgress( file.getPath() );
        processedFiles.add( file );
      }
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

  public List<VcsException> scheduleUnversionedFilesForAddition( List<VirtualFile> files )
  {
    return scheduleUnversionedFilesForAddition( files.toArray( new VirtualFile[ files.size() ] ) );
  }

  public List<VcsException> scheduleUnversionedFilesForAddition( VirtualFile[] files )
  {
    for( VirtualFile file : files )
    {
      host.add2NewFile( file );
      VcsUtil.markFileAsDirty( project, file );

      //  Extend status change to all parent folders if they are not
      //  included into the context of the menu action.
      extendStatus( file );
    }
    // Keep intentionally empty.
    return new ArrayList<VcsException>();
  }

  private void extendStatus( VirtualFile file )
  {
    FileStatusManager mgr = FileStatusManager.getInstance( project );
    VirtualFile parent = file.getParent();

    if( mgr.getStatus( parent ) == FileStatus.UNKNOWN )
    {
      host.add2NewFile( parent );
      VcsUtil.markFileAsDirty( project, parent );

      extendStatus( parent );
    }
  }

  private boolean getNecessaryRenamedFoldersForList( List<Change> changes, Set<VirtualFile> set )
  {
    boolean isAnyRenamedFolderForFiles;
    for( Change change : changes )
    {
      if( !VcsUtil.isChangeForDeleted( change ))
      {
        ContentRevision rev = change.getAfterRevision();
        for( String newFolderName : host.renamedFolders.keySet() )
        {
          if( rev.getFile().getPath().startsWith( newFolderName ) )
          {
            VirtualFile parent = VcsUtil.getVirtualFile( newFolderName );
            set.add( parent );
          }
        }
      }
    }
    isAnyRenamedFolderForFiles = set.size() > 0;

    for( Change change : changes )
    {
      if( !VcsUtil.isChangeForDeleted( change ))
      {
        ContentRevision rev = change.getAfterRevision();
        VirtualFile submittedParent = rev.getFile().getVirtualFile();
        if( submittedParent != null )
          set.remove( submittedParent );
      }
    }
    
    return isAnyRenamedFolderForFiles;
  }

  private void initProgress( int total )
  {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if( progress != null )
    {
      fraction = 1.0 / (double) total;
      progress.setIndeterminate( false );
      progress.setFraction( 0.0 );
    }
  }

  private void incrementProgress( String text ) throws ProcessCanceledException
  {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if( progress != null )
    {
      double newFraction = progress.getFraction();
      newFraction += fraction;
      progress.setFraction( newFraction );
      progress.setText( text );

      if( progress.isCanceled() )
        throw new ProcessCanceledException();
    }
  }
}