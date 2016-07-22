package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.commands.LabelCommand;
import com.intellij.vssSupport.ui.SetLabelDialog;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 */
public class LabelAction extends VssAction
{
  public void actionPerformed( AnActionEvent e ) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    SetLabelDialog dlg = new SetLabelDialog(project);
    if (dlg.showAndGet() && StringUtil.isNotEmpty(dlg.getLabel())) {
      VirtualFile[] files = VssUtil.getVirtualFiles(e);
      ArrayList<VcsException> errors = new ArrayList<>();
      (new LabelCommand(project, dlg.getLabel(), dlg.getComment(), files, errors)).execute();

      if (!errors.isEmpty())
        Messages.showErrorDialog(errors.get(0).getLocalizedMessage(), VssBundle.message("message.title.could.not.start.process"));
    }
  }

  /**
   * Action is enabled if anything is selected and all files/folders
   * are already in the repository.
   */
  public void update( AnActionEvent e )
  {
    super.update( e );
    if( e.getPresentation().isEnabled() )
    {
      Project project = e.getData( CommonDataKeys.PROJECT );
      FileStatusManager mgr = FileStatusManager.getInstance( project );

      boolean isEnabled = false;
      VirtualFile[] files = VssUtil.getVirtualFiles( e );
      if( files.length > 0 )
      {
        isEnabled = true;
        for( VirtualFile file : files )
        {
          FileStatus status = mgr.getStatus( file );
          isEnabled &= (status != FileStatus.ADDED) && (status != FileStatus.UNKNOWN) &&
                       (status != FileStatus.IGNORED);
        }
      }
      e.getPresentation().setEnabled( isEnabled );
    }
  }
}
