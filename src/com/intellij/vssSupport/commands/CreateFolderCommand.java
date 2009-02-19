package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author: lloix
 */
public class CreateFolderCommand extends VssCommandAbstract
{
  @NonNls private static final String CREATE_COMMAND = "Create";

  private final VirtualFile folder;

  public CreateFolderCommand( Project project, VirtualFile file, List<VcsException> errors )
  {
    super( project, errors );
    folder = file;
  }

  public void execute()
  {
    String workingPath = folder.getParent().getPath();
    List<String> options = formOptions( CREATE_COMMAND, VssUtil.getVssPath( folder, myProject ), _I__OPTION );

    runCP( folder.getParent(), workingPath );
    runProcess( options, workingPath );
  }
}
