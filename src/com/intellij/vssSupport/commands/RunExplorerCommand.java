/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssConfiguration;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class RunExplorerCommand
{
  @NonNls private static final String ROOT_PATH = "$/";
  @NonNls private static final String _s_OPTION = "-s";
  @NonNls private static final String _p_OPTION = "-p";
  
  private Project myProject;
  private String myVssPath;

  /**
   * @param file can represent file or directory.
   */
  public RunExplorerCommand( Project project, VirtualFile file )
  {
    myProject = project;
    if( file == null){
      myVssPath = ROOT_PATH;
    }else if( !file.isDirectory() ){
      myVssPath = VssUtil.getVssPath( file.getParent(), project );
    }else if( file.isDirectory() ){
      myVssPath = VssUtil.getVssPath( file, project );
    }
  }

  public void execute()
  {
    VssConfiguration config = VssConfiguration.getInstance(myProject);
    List<String> options = new ArrayList<String>();
    options.add(_s_OPTION + config.getSSDIR());
    options.add(_p_OPTION + myVssPath);
    if( config.USER_NAME.length() > 0 )
      options.add( config.getYOption() );

    try
    {
      VSSExecUtil.runProcessDoNotWaitForTermination( config.getExplorerPath(), options.toArray(new String[ options.size() ]),
                                                     config.getSSDIREnv() );
    }
    catch( ExecutionException exc )
    {
      String msg = config.checkCmdPath();
      Messages.showErrorDialog( (msg != null) ? msg : exc.getLocalizedMessage(),
                                VssBundle.message("message.title.could.not.start.process"));
    }
  }
}
