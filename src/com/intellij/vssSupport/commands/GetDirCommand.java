/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.commands;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.GetOptions;
import com.intellij.vssSupport.VssBundle;

import java.io.File;
import java.util.List;

public class GetDirCommand extends VssCommandAbstract
{
  private final VirtualFile myDir;

  public GetDirCommand( Project project, VirtualFile dir, List<VcsException> errors )
  {
    super( project, errors );
    myDir = dir;
  }

  public void execute()
  {
    final GetOptions baseOptions = myConfig.getGetOptions();

    FileDocumentManager.getInstance().saveAllDocuments();
    List<String> options = baseOptions.getOptions( myDir );
    options.add( _I_Y_OPTION );
    String workingPath = myDir.getPath().replace('/', File.separatorChar);

    GetProjectListener listener = new GetProjectListener( myProject, myDir, myErrors );
    runProcess( options, workingPath, listener );

    //  Make files in the folder refresh immediately after the "Get" operation
    //  is finished. Otherwise synch can be made synchronously far later.
    myDir.refresh( true, true );

    //  Show conglomerated information on files that were somehow affected -
    //  added, changed, or we did not manage to operate over.
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    for( String file : listener.filesAdded )
      updatedFiles.getGroupById( FileGroup.LOCALLY_ADDED_ID ).add( file );

    for( String file : listener.filesChanged )
      updatedFiles.getGroupById( FileGroup.UPDATED_ID ).add( file );

    for( String file : listener.filesSkipped )
      updatedFiles.getGroupById( FileGroup.SKIPPED_ID ).add( file );

    ProjectLevelVcsManager.getInstance( myProject ).showProjectOperationInfo(
      updatedFiles, VssBundle.message("dialog.title.get.file", myDir.getName()) );
  }
}
