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
public class MoveDirectoryCommand extends VssCommandAbstract
{
  @NonNls private static final String MOVE_COMMAND = "Move";

  private final File myOldPath;
  private final File myNewPath;

  public MoveDirectoryCommand( Project project, File oldPath, File newPath, List<VcsException> errors )
  {
    super( project, errors );
    myNewPath = newPath;
    myOldPath = oldPath;
  }

  public void execute()
  {
    List<String> options = formOptions( MOVE_COMMAND, VssUtil.getVssPath( myOldPath, myProject ),
                                        VssUtil.getVssPath( myNewPath.getParentFile(), myProject ), _I__OPTION );
    runProcess( options, null );
  }
}
