package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

/**
 * author: lloix
 */
public class ShareFileCommand extends VssCommandAbstract
{
  @NonNls private static final String SHARE_COMMAND = "Share";
  @NonNls private static final String OVERWRITE_SWITCH = "-GWR";

  private final File myOldFile;
  private final File myNewFile;

  public ShareFileCommand( Project project, File oldFile, File newFile, List<VcsException> errors )
  {
    super( project, errors );
    myOldFile = oldFile;
    myNewFile = newFile;
  }

  public void execute()
  {
    String workingPath = myNewFile.getParentFile().getPath();
    List<String> options = formOptions( SHARE_COMMAND, VssUtil.getVssPath( myOldFile, myProject ), _I_Y_OPTION, OVERWRITE_SWITCH );

    runCP( workingPath, workingPath );
    runProcess( options, workingPath );
  }
}
