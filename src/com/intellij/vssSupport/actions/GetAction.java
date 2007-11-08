package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.commands.GetDirCommand;
import com.intellij.vssSupport.commands.GetFilesCommand;
import com.intellij.vssSupport.ui.GetDirDialog;
import com.intellij.vssSupport.ui.GetFilesDialog;

import java.util.ArrayList;

/**
 * @author Vladimir Kondratyev
 */
public class GetAction extends VssAction
{
  public void update( AnActionEvent e )
  {
    super.update( e );

    if( e.getPresentation().isVisible() && e.getPresentation().isEnabled() )
    {
      Project project = e.getData( PlatformDataKeys.PROJECT );
      ChangeListManager mgr = ChangeListManager.getInstance( project );
      VirtualFile[] files = VcsUtil.getVirtualFiles( e );

      if( !allFilesAreFolders( files ) )
      {
        boolean isEnabled = true;
        for( VirtualFile file : files )
        {
          FileStatus status = mgr.getStatus( file );
          isEnabled &= !file.isDirectory() && (status != FileStatus.UNKNOWN) &&
                       (status != FileStatus.ADDED) && (status != FileStatus.IGNORED);
        }
        e.getPresentation().setEnabled( isEnabled );
      }
    }
  }

  public void actionPerformed( AnActionEvent e )
  {
    Project project = e.getData( PlatformDataKeys.PROJECT );
    VirtualFile[] files = VssUtil.getVirtualFiles( e );

    ArrayList<VcsException> errors = new ArrayList<VcsException>();
    try
    {
      boolean showOptions = VssVcs.getInstance( project ).getGetOptions().getValue();
      if( showOptions || isShiftPressed( e ) )
      {
        OptionsDialog editor = allFilesAreFolders( files ) ? new GetDirDialog( project ) :
                                                             new GetFilesDialog( project );
        editor.setTitle( (files.length == 1) ? VssBundle.message( "dialog.title.get.file", files[ 0 ].getName() ) :
                                               VssBundle.message( "dialog.title.get.multiple" ) );
        editor.show();
        if( !editor.isOK())
          return;
      }
      if( allFilesAreFolders( files ) )
      {
        for( VirtualFile file : files )
        {
          (new GetDirCommand( project, file, errors )).execute();
          if( !errors.isEmpty() )
            break;
        }
      }
      else
      {
        (new GetFilesCommand( project, files, errors )).execute();
      }
    }
    finally
    {
      if( !errors.isEmpty() )
        Messages.showErrorDialog( errors.get( 0 ).getLocalizedMessage(), VssBundle.message("message.title.could.not.start.process"));
    }
  }
}
