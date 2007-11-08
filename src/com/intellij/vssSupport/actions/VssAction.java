package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssConfiguration;
import com.intellij.vssSupport.VssVcs;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * @author Vladimir Kondratyev
 */
abstract class VssAction extends AnAction
{
  /**
   * Action is enabled if and only if project is accessible from context,
   * VSS isn't busy and all files in the context are under VSS control.
   */
  public void update( AnActionEvent e )
  {
    Presentation presentation = e.getPresentation();

    //  Do not show anything if no project is set.
    Project project = e.getData( PlatformDataKeys.PROJECT );
    if( project == null )
    {
      presentation.setVisible( false );
      presentation.setEnabled( false );
      return;
    }
    
    //  Add additional condition for VSS actions visibility like that
    //  presented in the VssGroup/StandardVcsGroup.
    ProjectLevelVcsManager pm = ProjectLevelVcsManager.getInstance( project );
    boolean state = pm.checkVcsIsActive( VssVcs.getInstance( project ) );
    presentation.setVisible( state );

    if( state )
    {
      state = false;
      VssConfiguration  config = VssConfiguration.getInstance( project );
      if( config != null )
      {
        VssVcs  host = VssVcs.getInstance( project );
        pm = ProjectLevelVcsManager.getInstance( project );
        state = pm.checkAllFilesAreUnder( host, VcsUtil.getVirtualFiles( e ) );
      }
      presentation.setEnabled( state );
    }
  }

  protected static boolean allFilesAreFolders( VirtualFile[] files )
  {
    return allFilesAreFolders( files, true );
  }

  protected static boolean allFilesAreFolders( VirtualFile[] files, boolean is )
  {
    boolean allFolders = true;
    for( VirtualFile file : files )
      allFolders &= ( file.isDirectory() == is );

    return allFolders;
  }

  protected static boolean isShiftPressed( AnActionEvent e )
  {
    InputEvent inputEvent = e.getInputEvent();
    return inputEvent != null && (inputEvent.getModifiers() & Event.SHIFT_MASK) != 0;
  }
}
