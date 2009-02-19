package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

/**
 * @author: lloix
 */
public class DeleteFileOrDirectoryCommand extends VssCommandAbstract
{
  @NonNls private static final String DELETE_COMMAND = "Delete";

  private final String path;

  public DeleteFileOrDirectoryCommand( Project project, String path, List<VcsException> errors )
  {
    super( project, errors );
    this.path = path;
  }

  public void execute()
  {
    String workingPath = new File( path ).getParentFile().getPath();
    List<String> options = formOptions( DELETE_COMMAND, VssUtil.getVssPath( new File( path ), myProject), _I_Y_OPTION );

    runProcess( options, workingPath );
  }
}
