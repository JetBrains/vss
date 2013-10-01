package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.commands.UndocheckoutDirCommand;
import com.intellij.vssSupport.commands.UndocheckoutFilesCommand;
import com.intellij.vssSupport.ui.UndocheckoutDirDialog;
import com.intellij.vssSupport.ui.UndocheckoutFilesDialog;

import java.util.ArrayList;

/**
 * @author Vladimir Kondratyev
 */
public class UndocheckoutAction extends VssAction
{
  //  Command is enabled only if all files are under Vss control (this is checked)
  //  in the base class, AND they have been already checked out

  public void update( AnActionEvent e )
  {
    super.update( e );

    if( e.getPresentation().isEnabled() )
    {
      //  UndoCheckout works only for a set of folders or for a set of ordinary files
      Project project = e.getData( CommonDataKeys.PROJECT );
      ChangeListManager mgr = ChangeListManager.getInstance( project );
      VirtualFile[] files = VcsUtil.getVirtualFiles( e );

      boolean isEnabled = allFilesAreFolders( files );
      if( !isEnabled )
      {
        isEnabled = true;
        for ( VirtualFile file : files )
        {
          FileStatus status = mgr.getStatus( file );
          isEnabled &= !file.isDirectory() && file.isWritable() && (status == FileStatus.MODIFIED);
        }
      }
      e.getPresentation().setEnabled( isEnabled );
    }
  }

  public void actionPerformed( AnActionEvent e )
  {
    Project project = e.getData( CommonDataKeys.PROJECT );
    VirtualFile[] files = VssUtil.getVirtualFiles( e );
    ArrayList<VcsException> errors = new ArrayList<VcsException>();

    try
    {
      VssConfiguration config = VssConfiguration.getInstance(project);
      boolean showOptions = VssVcs.getInstance(config.getProject()).getUndoCheckoutOptions().getValue();
      if( showOptions || isShiftPressed( e ) )
      {
        OptionsDialog editor = allFilesAreFolders( files ) ? new UndocheckoutDirDialog( project ) :
                                                             new UndocheckoutFilesDialog( project );
        editor.setTitle( (files.length == 1) ? VssBundle.message( "dialog.title.undo.check.out", files[ 0 ].getName()) :
                                               VssBundle.message( "dialog.title.undo.check.out.multiple" ) );
        editor.show();
        if( !editor.isOK())
          return;
      }

      if( allFilesAreFolders( files ))
      {
        for( VirtualFile file : files )
        {
          (new UndocheckoutDirCommand( project, file, errors )).execute();
        }
      }
      else
      {
        (new UndocheckoutFilesCommand( project, files, errors ) ).execute();
      }
    }
    finally
    {
      if (!errors.isEmpty())
        Messages.showErrorDialog(errors.get( 0 ).getLocalizedMessage(), VssBundle.message("message.title.could.not.start.process"));
    }
  }
}
