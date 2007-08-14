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

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import static com.intellij.vssSupport.Checkin.VssCheckinEnvironment.RENAME_ROLLBACK;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 21, 2006
 */
public class VFSListener extends VirtualFileAdapter
{
  private Project project;
  private VssVcs  host;

  public VFSListener( Project project, VssVcs host ) {  this.project = project; this.host = host; }

  public void beforeFileMovement( VirtualFileMoveEvent event )
  {
    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return;

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

  public void beforePropertyChange( VirtualFilePropertyEvent event )
  {
    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return;

    if( event.getPropertyName() == VirtualFile.PROP_WRITABLE )
    {
      //  On every change of the "Writable" property clear the cache of the
      //  content revisions. This will make possible to reread the correct
      //  version content after series of checkins/checkouts.
      ContentRevisionFactory.clearCacheForFile( file.getPath() );
    }
    else
    if( event.getPropertyName() == VirtualFile.PROP_NAME )
    {
      String parentDir = file.getParent().getPath() + "/";
      String oldName = parentDir + event.getOldValue();
      String newName = parentDir + event.getNewValue();

      performRename( file.isDirectory() ? host.renamedFolders : host.renamedFiles, oldName, newName );
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

  public void fileCreated( VirtualFileEvent event )
  {
    VirtualFile file = event.getFile();
    String path = file.getPath();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return;

    //  In the case when the project content is synchronized over the
    //  occasionally removed files.
    host.removedFiles.remove( path );
    host.removedFolders.remove( path );
    host.deletedFiles.remove( path );
    host.deletedFolders.remove( path );

    //  Do not ask user if the files created came from the vcs per se
    //  (obviously they are not new).
    if( event.isFromRefresh() )
      return;

    //  Take into account only processable files.
    
    if( isFileProcessable( event.getFile() ) )
    {
      VcsShowConfirmationOption confirmOption = host.getAddConfirmation();

      //  In the case when we need to perform "Add" vcs action right upon
      //  the file's creation, put the file into the host's cache until it
      //  will be analyzed by the ChangeProvider.
      if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY )
        host.add2NewFile( path );
      else
      if( confirmOption.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION )
      {
        List<VirtualFile> files = new ArrayList<VirtualFile>();
        files.add( event.getFile() );
        Collection<VirtualFile> filesToProcess = AbstractVcsHelper.getInstance( project ).selectFilesToProcess( files, VssBundle.message("dialog.title.info"),
                                                                        null, VssBundle.message("action.Vss.Add.description") + "?",
                                                                       VssBundle.message("action.Vss.Add.Question"), confirmOption );
        if( filesToProcess != null )
          host.add2NewFile( path );
      }
    }
  }

  public void beforeFileDeletion( VirtualFileEvent event )
  {
    //  Do not ask user if the files deletion is caused by the vcs operation
    //  like UPDATE (obviously they are deleted without a necessity to recover
    //  or to keep track).
    if( event.isFromRefresh() )
      return;

    VirtualFile file = event.getFile();
    performDeleteFile( file );
  }

  private void performDeleteFile( VirtualFile file )
  {
    FileStatus status = FileStatusManager.getInstance( project ).getStatus( file );
    Boolean isRenameDest = file.getUserData( RENAME_ROLLBACK );

    //  NB: <isRenameDest> is set in the CheckinEnvironment during
    //      the rollback of rename change. We should not put it into the list
    //      of the removed files for further control.
    if( VcsUtil.isFileForVcs( file, project, host ) &&
       ( status != FileStatus.UNKNOWN ) && ( status != FileStatus.IGNORED ) &&
       ( isRenameDest == null ))
    {
      if( status == FileStatus.ADDED )
      {
        host.deleteNewFile( file );
      }
      else
      {
        VcsShowConfirmationOption confirmOption = host.getRemoveConfirmation();

        //  In the case when we need to perform "Delete" vcs action right upon
        //  the file's deletion, put the file into the host's cache until it
        //  will be analyzed by the ChangeProvider.
        if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY )
        {
          markFileRemoval( file, host.deletedFolders, host.deletedFiles );
        }
        else
        if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY )
        {
          markFileRemoval( file, host.removedFolders, host.removedFiles );
        }
        else
        {
          List<VirtualFile> files = new ArrayList<VirtualFile>();
          files.add( file );
          Collection<VirtualFile> filesToProcess = AbstractVcsHelper.getInstance( project ).selectFilesToProcess( files, VssBundle.message("dialog.title.info"),
                                                                          null, VssBundle.message("action.Vss.Delete.description") + "?",
                                                                         VssBundle.message("action.Vss.Delete.Question"), confirmOption );
          if( filesToProcess != null )
            markFileRemoval( file, host.deletedFolders, host.deletedFiles );
          else
            markFileRemoval( file, host.removedFolders, host.removedFiles );
        }
      }
    }
  }

  private void markFileRemoval( VirtualFile file, HashSet<String> folders, HashSet<String> files )
  {
    String path = file.getPath();
    if( file.isDirectory() )
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
      if( path.toLowerCase().startsWith( folder.toLowerCase() ))
        return true;
    }
    return false;
  }

  /**
   * File is not processable if e.g. it was created during "GetLatestVersion",
   * if it outside the vcs scope or it is in the list of excluded project files.
   */
  private boolean isFileProcessable( VirtualFile file )
  {
    return host.fileIsUnderVcs( file ) && !host.isFileIgnored( file ) &&
           !FileTypeManager.getInstance().isFileIgnored( file.getName() );
  }
}
