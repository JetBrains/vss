package com.intellij.vssSupport.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.UndocheckoutOptions;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.ui.ConfirmMultipleDialog;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 * @author LloiX
 */
public class UndocheckoutFilesCommand extends VssCommandAbstract
{
  private final VirtualFile[] myFiles;
  private boolean needToAsk = false;

  public UndocheckoutFilesCommand( Project project, VirtualFile[] files, List<VcsException> errors )
  {
    super( project, errors );
    myFiles = files;
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  public void execute()
  {
    final UndocheckoutOptions baseOptions = myConfig.getUndocheckoutOptions();
    int savedActionType = baseOptions.REPLACE_LOCAL_COPY;
    boolean replaceInstance = false;

    UndocheckoutListenerNew listener = new UndocheckoutListenerNew( myErrors );
    for( int i = 0; i < myFiles.length; i++ )
    {
      List<String> options = baseOptions.getOptions( myFiles[ i ] );
      String workingPath = myFiles[ i ].getParent().getPath().replace( '/', File.separatorChar );
      listener.setFileName( myFiles[ i ].getPath() );

      // If options specify "ask before replace", Undocheckout runs with -I-N option.
      // This causes replacement of unchanged file but the confirmation is requested
      // for changed files. Analyze output and repeat with proper "Yes" or "No" answer.
      needToAsk = false;

      runProcess( options, workingPath, listener );

      //  Return the option value back - we need to ask again for the next file 
      if( replaceInstance ){
        baseOptions.REPLACE_LOCAL_COPY = savedActionType;
        replaceInstance = false;
      }

      if( needToAsk )
      {
        int exitCode = askOption( myFiles[ i ].getPath() );
        if( exitCode == ConfirmMultipleDialog.YES_EXIT_CODE ){
          replaceInstance = true;
          baseOptions.REPLACE_LOCAL_COPY = UndocheckoutOptions.OPTION_REPLACE;
          //  repeat for the file with the new options set
          i--;
        }
        else
        if( exitCode == ConfirmMultipleDialog.YES_ALL_EXIT_CODE  ){
          baseOptions.REPLACE_LOCAL_COPY = UndocheckoutOptions.OPTION_REPLACE;
          //  repeat for this file and all others with the new options set
          i--;
        }
        else
        if( exitCode == ConfirmMultipleDialog.NO_ALL_EXIT_CODE ){
          baseOptions.REPLACE_LOCAL_COPY = UndocheckoutOptions.OPTION_LEAVE;
        }
        else
        if( exitCode == ConfirmMultipleDialog.CANCEL_OPTION ){
          break;
        }
        // and nothing to do in the case of ConfirmMultipleDialog.NO_EXIT_CODE
      }
    }
    baseOptions.REPLACE_LOCAL_COPY = savedActionType;

    for( VirtualFile file : myFiles )
      file.refresh( true, true );
  }

  private int askOption( final String fileName )
  {
    final int[] exitCode = new int[ 1 ];
    Runnable runnable = new Runnable() {  public void run() {
      ConfirmMultipleDialog dialog = new ConfirmMultipleDialog( VssBundle.message("confirm.text.undo.check.out"),
                                                                VssBundle.message("confirm.text.file.changed.undo", fileName ),
                                                                myProject);
      dialog.show();
      exitCode[ 0 ] = dialog.getExitCode();
    } };

      ApplicationManager.getApplication().invokeAndWait( runnable, ModalityState.defaultModalityState() );

    return exitCode[ 0 ];
  }

  private class UndocheckoutListenerNew extends VssOutputCollector
  {
    private String fileName;
    @NonNls private static final String CHECKED_OUT_MESSAGE = "currently checked out";
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";
    @NonNls private static final String NOT_FROM_CURRENT_MESSAGE = "not from the current folder";
    @NonNls private static final String UNDO_CHECKOUT_CONF_MESSAGE = "Undo check out and lose changes?(Y/N)N";

    public UndocheckoutListenerNew( List<VcsException> errors ){
      super( errors );
    }

    public void setFileName( String name ){
      fileName = name;
    }

    public void everythingFinishedImpl( final String output )
    {
      if( output.indexOf( CHECKED_OUT_MESSAGE ) != -1 )
          myErrors.add( new VcsException( VssBundle.message("message.text.file.not.checked.out", fileName ) ));
      else 
      if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
        myErrors.add( new VcsException( VssBundle.message("message.text.path.is.not.existing.filename.or.project", fileName ) ));
      else
      if( output.indexOf( DELETED_MESSAGE )!= -1 )
        myErrors.add( new VcsException( VssBundle.message("message.text.cannot.undo.file.deleted", fileName) ));
      else
      if( output.indexOf( NOT_FROM_CURRENT_MESSAGE )!= -1)
        myErrors.add( new VcsException( VssBundle.message("message.text.cannot.undo.checked.out.not.from.current", fileName) ));
      else
      if( output.indexOf( UNDO_CHECKOUT_CONF_MESSAGE )!= -1)
        needToAsk = true;
      else
      if( VssUtil.EXIT_CODE_SUCCESS == getExitCode() || VssUtil.EXIT_CODE_WARNING == getExitCode() )
        VssUtil.showStatusMessage( myProject, VssBundle.message("message.text.undo.successfully", fileName ));
      else
        myErrors.add( new VcsException( output ));
    }
  }
}
