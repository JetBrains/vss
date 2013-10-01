package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Checkin.VssCheckinEnvironment;
import com.intellij.vssSupport.VssVcs;

/**
 * @author Michael Gerasimov
 */
public class AddAction extends VssAction
{
  public void actionPerformed(AnActionEvent e)
  {
    Project project = e.getData( CommonDataKeys.PROJECT );
    VirtualFile[] files = VcsUtil.getVirtualFiles( e );
    VssVcs host = VssVcs.getInstance( project );

    VssCheckinEnvironment env = (VssCheckinEnvironment)host.getCheckinEnvironment();
    if( env != null )
      env.scheduleUnversionedFilesForAddition( files );
  }

  /**
   * Action is enabled if all files are not folders, all are under this
   * version control and have status "UNVERSIONED".
   */
  public void update( AnActionEvent e )
  {
    super.update( e );
    
    Presentation  presentation = e.getPresentation();
    if( presentation.isEnabled() )
    {
      Project project = e.getData( CommonDataKeys.PROJECT );
      VssVcs  host = VssVcs.getInstance( project );
      VirtualFile[] files = VcsUtil.getVirtualFiles( e );
      
      ProjectLevelVcsManager pm = ProjectLevelVcsManager.getInstance( project );
      ChangeListManager mgr = ChangeListManager.getInstance( project );

      boolean status = pm.checkAllFilesAreUnder( host, files );
      for( VirtualFile file : files )
      {
        status &= !file.isDirectory() && ( mgr.getStatus( file ) == FileStatus.UNKNOWN );
      }
      presentation.setEnabled( status );
    }
  }
}
