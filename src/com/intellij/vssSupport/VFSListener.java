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

package com.intellij.vssSupport;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vssSupport.Checkin.VssCheckinEnvironment.RENAME_ROLLBACK;

public class VFSListener implements CommandListener, VirtualFileListener {
  private final Project project;
  private final VssVcs  host;

  private int     commandLevel;
  private final List<VirtualFile> filesAdded = new ArrayList<>();
  private final List<FilePath> filesDeleted = new ArrayList<>();

  public VFSListener( Project project, VssVcs host ) {  this.project = project; this.host = host; }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event )
  {
    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))  return;

    //  In the case when the project content is synchronized over the
    //  occasionally removed files.
    //  NB: These structures must be updated even in the case of refresh events
    //      (lines below).
    String path = file.getPath();
    host.removedFiles.remove( path );
    host.removedFolders.remove( path );
    host.deletedFiles.remove( path );
    host.deletedFolders.remove( path );

    if( event.isFromRefresh() )  return;

    if( isFileProcessable( file ) )
    {
      //  Add file into the list for further confirmation only if the folder
      //  is not marked as UNKNOWN. In this case the file under that folder
      //  will be marked as unknown automatically. 
      VirtualFile parent = file.getParent();
      if( parent != null )
      {
        FileStatus status = ChangeListManager.getInstance( project ).getStatus( parent );
        if( status != FileStatus.UNKNOWN )
          filesAdded.add( file );
      }
    }
  }

  @Override
  public void beforeFileDeletion(@NotNull VirtualFileEvent event )
  {
    if( !isIgnoredEvent( event ) )
    {
      //  When user performs undo for "Extract Interface or Superclass" refactoring
      //  with the option "Rename original class" set, the newly created file is
      //  deleted. Since it is always an unversioned file, we should remove it
      //  silently without confirmation. Otherwise its status (deleted) will conflict
      //  with the status of the original file which is renamed back to the current name.
      if( !host.isWasRenamed( event.getFile().getPath() ) )
        performDeleteFile( event.getFile() );
    }
  }

  private void performDeleteFile( VirtualFile file )
  {
    FileStatus status = FileStatusManager.getInstance( project ).getStatus( file );
    Boolean isRenameDest = file.getUserData( RENAME_ROLLBACK );

    //  NB: <isRenameDest> is set in the CheckinEnvironment during
    //      the rollback of rename change. We should not put it into the list
    //      of the removed files for further control.
    if(( status != FileStatus.UNKNOWN ) && ( status != FileStatus.IGNORED ) &&
       ( isRenameDest == null ))
    {
      if( status == FileStatus.ADDED )
        host.deleteNewFile( file );
      else
      if( isFileProcessable( file ) )
      {
        FilePath path = VcsContextFactory.SERVICE.getInstance().createFilePathOn( file );
        filesDeleted.add( path );
      }
    }
  }

  @Override
  public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event )
  {
    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return;

    if( event.getPropertyName().equals( VirtualFile.PROP_WRITABLE ))
    {
      //  On every change of the "Writable" property clear the cache of the
      //  content revisions. This will make possible to reread the correct
      //  version content after series of checkins/checkouts.
      ContentRevisionFactory.clearCacheForFile( file.getPath() );
    }
    else
    if( event.getPropertyName().equals( VirtualFile.PROP_NAME ))
    {
      FileStatus status = FileStatusManager.getInstance( project ).getStatus( file );
      if( status != FileStatus.ADDED && status != FileStatus.UNKNOWN && status != FileStatus.IGNORED )
      {
        String parentDir = file.getParent().getPath() + "/";
        String oldName = parentDir + event.getOldValue();
        String newName = parentDir + event.getNewValue();

        performRename( file.isDirectory() ? host.renamedFolders : host.renamedFiles, oldName, newName );
      }
    }
  }

  private static void performRename( HashMap<String, String> store, String oldName, String newName )
  {
    //  Newer name must refer to the oldest name in the chain of renamings
    String prevName = store.get( oldName );
    if( prevName == null )
      prevName = oldName;

    //  Check whether we are trying to rename the file back - if so,
    //  just delete the old key-value pair
    if( !prevName.equals( newName ) )
      store.put( newName, prevName );

    store.remove( oldName );
  }

  @Override
  public void beforeFileMovement(@NotNull VirtualFileMoveEvent event )
  {
    if( isIgnoredEvent( event ) )
        return;

    VirtualFile file = event.getFile();
    if( !file.isDirectory() )
    {
      String oldName = file.getPath();
      String newName = event.getNewParent().getPath() + "/" + file.getName();

      //  If the file is moved into Vss-versioned module, then it is a simple
      //  movement. Otherwise (move into non-versioned module), mark it
      //  "for removal" in the current, versioned module.
      if( VcsUtil.isFileForVcs( newName, project, host ) )
      {
        //  Newer name must refer to the oldest one in the chain of movements
        String prevName = host.renamedFiles.get( oldName );
        if( prevName == null )
          prevName = oldName;

        //  Check whether we are trying to rename the file back -
        //  if so, just delete the old key-value pair
        if( !prevName.equals( newName ) )
          host.renamedFiles.put( newName, prevName );

        host.renamedFiles.remove( oldName );

        //  Clear the cache of the content revisions for this file.
        //  This will make possible to reread the correct version content
        //  after the referred FilePath/VirtualFile is changed
        ContentRevisionFactory.clearCacheForFile( file.getPath() );
      }
      else
      {
        performDeleteFile( file );
      }
    }
  }

  /**
   * File is not processable if e.g. it was created during "GetLatestVersion",
   * if it outside the vcs scope or it is in the list of excluded project files.
   */
  private boolean isFileProcessable( VirtualFile file )
  {
    return !host.isFileIgnored( file ) && !FileTypeManager.getInstance().isFileIgnored( file );
  }

  private boolean isIgnoredEvent( VirtualFileEvent e )
  {
    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( e.getFile(), project, host ))
      return true;

    //  Do not ask user if the file operation is caused by the vcs operation
    //  like UPDATE.
    return e.isFromRefresh();

  }

  @Override
  public void commandStarted(final CommandEvent event)
  {
    if( project == event.getProject() )
      commandLevel++;
  }

  @Override
  public void commandFinished(final CommandEvent event)
  {
    if (project != event.getProject()) return;

    commandLevel--;
    if (commandLevel == 0)
    {
      if (!filesAdded.isEmpty() || !filesDeleted.isEmpty() )
      {
        // avoid reentering commandFinished handler - saving the documents may cause a "before file deletion" event firing,
        // which will cause closing the text editor, which will itself run a command that will be caught by this listener
        commandLevel++;
        try {  FileDocumentManager.getInstance().saveAllDocuments();  }
        finally {  commandLevel--;  }

        if (!filesAdded.isEmpty())
          executeAdd();

        if (!filesDeleted.isEmpty() )
          executeDelete();

        filesAdded.clear();
        filesDeleted.clear();
      }
    }
  }

  private void executeAdd()
  {
    ArrayList<VirtualFile> files = new ArrayList<>(filesAdded);
    VcsShowConfirmationOption confirmOption = host.getAddConfirmation();

    if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;
    if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY)
      performAdding( files );
    else
    {
      //  Choose appropriate dialog title (discriminate file or folder).
      String title = VssBundle.message( "action.Vss.Add.file.description" );
      if( files.size() == 1 && files.get( 0 ).isDirectory() )
        title = VssBundle.message( "action.Vss.Add.folder.description" );

      final AbstractVcsHelper helper = AbstractVcsHelper.getInstance( project );
      Collection<VirtualFile> filesToProcess = helper.selectFilesToProcess( files, VssBundle.message( "title.select.files.add" ), null,
                                                                            title, VssBundle.message( "action.Vss.Add.Question" ),
                                                                            confirmOption );
      if( filesToProcess != null ) 
        performAdding( filesToProcess );
    }
  }

  private void performAdding( Collection<VirtualFile> files )
  {
    for( VirtualFile file : files )
    {
      String path = file.getPath();

      //  In the case when the project content is synchronized over the
      //  occasionally removed files.
      host.removedFiles.remove( path );
      host.removedFolders.remove( path );
      host.deletedFiles.remove( path );
      host.deletedFolders.remove( path );

      host.add2NewFile( file );
      VcsUtil.markFileAsDirty( project, file );
    }
  }

  private void executeDelete()
  {
    VcsShowConfirmationOption confirmOption = host.getRemoveConfirmation();

    //  In the case when we need to perform "Delete" vcs action right upon
    //  the file's deletion, put the file into the host's cache until it
    //  will be analyzed by the ChangeProvider.
    if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY )
    {
      markFileRemoval( filesDeleted, host.deletedFolders, host.deletedFiles );
    }
    else
    if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY )
    {
      markFileRemoval( filesDeleted, host.removedFolders, host.removedFiles );
    }
    else
    {
      final List<FilePath> deletedFiles = new ArrayList<>(filesDeleted);
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance( project );
      Collection<FilePath> filesToProcess = helper.selectFilePathsToProcess( deletedFiles, VssBundle.message("title.select.files.delete"),
                                                                             null, VssBundle.message("action.Vss.Delete.description") + "?",
                                                                             VssBundle.message("action.Vss.Delete.Question"), confirmOption );
      if( filesToProcess != null )
        markFileRemoval( filesToProcess, host.deletedFolders, host.deletedFiles );
      else
        markFileRemoval( deletedFiles, host.removedFolders, host.removedFiles );
    }
  }

  private void markFileRemoval( final Collection<FilePath> paths, HashSet<String> folders, HashSet<String> files )
  {
    final ArrayList<FilePath> allpaths = new ArrayList<>(paths);
    for( FilePath fpath : allpaths )
    {
      String path = fpath.getPath();
      path = VcsUtil.getCanonicalLocalPath( path );
      if( fpath.isDirectory() )
      {
        markSubfolderStructure( path );
        folders.add( path );
      }
      else
      if( !isUnderDeletedFolder( host.removedFolders, path ) &&
          !isUnderDeletedFolder( host.deletedFolders, path ) )
      {
        files.add( path );
      }

      VcsUtil.markFileAsDirty( project, fpath );
    }
  }

  /**
   * When adding new path into the list of the removed folders, remove from
   * that list all files/folders which were removed previously locating under
   * the given one (including it).
   */
  private void  markSubfolderStructure( String path )
  {
    removeRecordFrom( host.removedFiles, path );
    removeRecordFrom( host.removedFolders, path );
    removeRecordFrom( host.deletedFiles, path );
    removeRecordFrom( host.deletedFolders, path );
  }

  private static void removeRecordFrom( HashSet<String> set, String path )
  {
    for( Iterator<String> it = set.iterator(); it.hasNext(); )
    {
      String strFile = it.next();
      if( strFile.startsWith( path ) )
       it.remove();
    }
  }

  private static boolean isUnderDeletedFolder( HashSet<String> folders, String path )
  {
    for( String folder : folders )
    {
      if( path.toLowerCase().startsWith( folder.toLowerCase() )) return true;
    }
    return false;
  }
}
