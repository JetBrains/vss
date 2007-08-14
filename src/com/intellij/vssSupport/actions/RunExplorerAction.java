package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.commands.RunExplorerCommand;

/**
 * @author Vladimir Kondratyev
 */
public class RunExplorerAction extends VssAction
{
  public void actionPerformed(AnActionEvent e)
  {
    final Project project = e.getData( DataKeys.PROJECT );
    VirtualFile virtualFile = VcsUtil.getOneVirtualFile( e );
    (new RunExplorerCommand(project, virtualFile)).execute();
  }

  /**
   * Action is enabled if single selection exists.
   */
  public void update( AnActionEvent e )
  {
    super.update( e );
    
    if( e.getPresentation().isEnabled() )
      e.getPresentation().setEnabled( VssUtil.getVirtualFiles( e ).length == 1 );
  }
}
