package com.intellij.vssSupport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.vssSupport.commands.CheckoutFileCommand;
import com.intellij.vssSupport.ui.CheckoutFilesDialog;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Feb 1, 2007
 */
public class VssEditFileProvider implements EditFileProvider
{
  private Project project;

  public VssEditFileProvider( Project project )
  {
    this.project = project;
  }

  public String getRequestText()    {  return VssBundle.message("edit.file.confirmation.text"); }

  public void editFiles( VirtualFile[] files )
  {
    ArrayList<VcsException> errors = new ArrayList<VcsException>();
    for( final VirtualFile file : files )
    {
      //  Calc options for each iteration since user can set "do not show"
      //  in the middle.
      boolean showOptions = VssVcs.getInstance( project ).getCheckoutOptions().getValue();
      if( showOptions )
      {
        CheckoutFilesDialog editor = new CheckoutFilesDialog( project );
        editor.setTitle( VssBundle.message( "dialog.title.check.out.file", file.getName()) );
        editor.show();
        if( !editor.isOK() )
          return;
      }
      ( new CheckoutFileCommand( project, file, errors )).execute();

      //  In the case of any errors, set RO status back to the file.
      if( !errors.isEmpty() )
      {
        Messages.showErrorDialog( errors.get( 0 ).getLocalizedMessage(), VssBundle.message("message.title.error"));

        ApplicationManager.getApplication().runWriteAction( new Runnable() { public void run(){
          try {   ReadOnlyAttributeUtil.setReadOnlyAttribute( file, false );  }
          catch( IOException e ) {
            Messages.showErrorDialog( VssBundle.message("message.text.ro.set.error", file.getPath()),
                                      VssBundle.message("message.title.error"));
          }
        } });
      }
      errors.clear();
    }

    for( VirtualFile file : files )
      file.refresh( true, false );
  }
}
