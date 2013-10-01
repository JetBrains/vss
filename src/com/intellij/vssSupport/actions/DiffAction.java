package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.commands.DiffFileCommand;

import java.util.ArrayList;

/**
 * @author Vladimir Kondratyev
 */
public class DiffAction extends VssAction
{
  public void actionPerformed(AnActionEvent e)
  {
    Project project = e.getData( CommonDataKeys.PROJECT );
    VirtualFile[] files = VssUtil.getVirtualFiles( e );
    VirtualFile   vFile = files[ 0 ];
    ArrayList<VcsException> errors = new ArrayList<VcsException>();
    (new DiffFileCommand(project, vFile, errors)).execute();
    
    if( !errors.isEmpty() )
      Messages.showErrorDialog(errors.get(0).getLocalizedMessage(), VssBundle.message("message.title.could.not.start.process"));
  }

  /**
   * Action is anable if and only if project and single FILE selection is available
   * and VSS isn't busy.
   */
  public void update(AnActionEvent e)
  {
    super.update( e );
    if( e.getPresentation().isEnabled() )
    {
      VirtualFile[] files = VssUtil.getVirtualFiles( e );
      e.getPresentation().setEnabled( (files.length == 1) && !files[ 0 ].isDirectory() );
    }
  }
}
