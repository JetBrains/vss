package com.intellij.vssSupport.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.*;
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
  private VirtualFile[] myFiles;
  private UndocheckoutOptions myBaseOptions;
  private boolean mySuppressOnNotCheckedOutMessage;

  private boolean myReplaceAllLocalCopies;
  private boolean myDoNotReplaceAllLocalCopies;

  /**
   * Creates new <code>UndocheckoutFilesCommand</code> instance.
   * @param files files to be unchecked out. Note, that the passed
   * files must be under VSS control, i.e. <code>VssUtil.isUnderVss</code>
   * method must return <code>true</code> for each of them.
   */
  public UndocheckoutFilesCommand( Project project, VirtualFile[] files,
                                   boolean suppressNotCheckedOutDiag, List<VcsException> errors )
  {
    super( project, errors );
    myFiles = files;
    mySuppressOnNotCheckedOutMessage = suppressNotCheckedOutDiag;
  }

  public void execute()
  {
    myBaseOptions = myConfig.getUndocheckoutOptions();
    FileDocumentManager.getInstance().saveAllDocuments();
    undoCheckOut( 0 );
  }

  private void undoCheckOut(int idx)
  {
    if( idx >= myFiles.length )
    {
      for( VirtualFile file : myFiles )
        file.refresh( true, true );

      return;
    }

    // If base options specify leave local copies or replace then everything is OK.
    //
    // If base options specify ask before replace local files then our life become a
    // litte bit difficult.
    // First of all I need to run Undocheckout with -I-N option. It cause replacement
    // of unchenged file but skip the operation if the file has been changed. So after
    // that I should analize output and repeat with rigth "Yes" or "No" answer.

    runVss( idx, myBaseOptions, new UndocheckoutListener( idx, myErrors ));
  }

  private void runVss( int idx, UndocheckoutOptions options, VssOutputCollector processListener )
  {
    VirtualFile file = myFiles[ idx ];
    VssConfiguration config = options.getVssConfiguration();
    try{
      VSSExecUtil.runProcess( myProject, config.CLIENT_PATH, options.getOptions( file ), config.getSSDIREnv(),
                              file.getParent().getPath().replace('/',File.separatorChar), processListener);
    }
    catch( ExecutionException exc )
    {
      String msg = config.checkCmdPath();
      myErrors.add( new VcsException( (msg != null) ? msg : exc.getLocalizedMessage() ));
    }
  }

  private void showStatusMessage(int idx,int exitCode,String errorOutput)
  {
    if( VssUtil.EXIT_CODE_SUCCESS == exitCode || VssUtil.EXIT_CODE_WARNING == exitCode )
      VssUtil.showStatusMessage(myProject, VssBundle.message("message.text.undo.successfully", myFiles[idx].getPresentableUrl()));
    else
      VssUtil.showErrorOutput(errorOutput, myProject);
  }

  private class UndocheckoutListener extends VssOutputCollector
  {
    private int myIdx;
    @NonNls private static final String CHECKED_OUT_MESSAGE = "currently checked out";
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";
    @NonNls private static final String NOT_FROM_CURRENT_MESSAGE = "not from the current folder";
    @NonNls private static final String UNDO_CHECKOUT_CONF_MESSAGE = "Undo check out and lose changes?(Y/N)N";

    public UndocheckoutListener(int idx, List<VcsException> errors)
    {
      super( errors );
      myIdx = idx;
    }

    public void everythingFinishedImpl( final String output )
    {
      String fileName = myFiles[myIdx].getPresentableUrl();
      if( output.indexOf( CHECKED_OUT_MESSAGE ) != -1 )
      {
        if( !mySuppressOnNotCheckedOutMessage )
          myErrors.add( new VcsException( VssBundle.message("message.text.file.not.checked.out", fileName ) ));
      }
      else if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
        myErrors.add( new VcsException( VssBundle.message("message.text.path.is.not.existing.filename.or.project", fileName ) ));
      else if( output.indexOf( DELETED_MESSAGE )!= -1 )
        myErrors.add( new VcsException( VssBundle.message("message.text.cannot.undo.file.deleted", fileName) ));
      else if( output.indexOf( NOT_FROM_CURRENT_MESSAGE )!= -1)
        myErrors.add( new VcsException( VssBundle.message("message.text.cannot.undo.checked.out.not.from.current", fileName) ));
      else if( output.indexOf( UNDO_CHECKOUT_CONF_MESSAGE )!= -1){
        onLooseChanges();
        return;
      }else
        showStatusMessage(myIdx,getExitCode(),output);

      undoCheckOut( myIdx + 1 );
    }

    private void onLooseChanges()
    {
      int exitCode = askOption();

      if(ConfirmMultipleDialog.YES_EXIT_CODE == exitCode)
      {
        UndocheckoutOptions options = myBaseOptions.copy();
        options.REPLACE_LOCAL_COPY = UndocheckoutOptions.OPTION_REPLACE;
        runVss( myIdx, options,  new VssOutputCollector(myErrors){
                                        public void everythingFinishedImpl(final String output){
                                          showStatusMessage( myIdx, getExitCode(), output );
                                          undoCheckOut( myIdx + 1 );
                                        }
                                 } );
        return;
      }
      else if(ConfirmMultipleDialog.YES_ALL_EXIT_CODE == exitCode)
      {
        UndocheckoutOptions options = myBaseOptions.copy();
        options.REPLACE_LOCAL_COPY = UndocheckoutOptions.OPTION_REPLACE;
        myReplaceAllLocalCopies = true;
        runVss( myIdx, options, new VssOutputCollector(myErrors){
                                      public void everythingFinishedImpl(final String output){
                                        showStatusMessage(myIdx,getExitCode(), output );
                                        undoCheckOut( myIdx + 1 );
                                      }
                                } );
        return;
      }else if(ConfirmMultipleDialog.NO_EXIT_CODE==exitCode){
        // Skip current file and continue
      }else if(ConfirmMultipleDialog.NO_ALL_EXIT_CODE==exitCode){
        myDoNotReplaceAllLocalCopies=true;
        // Skip current file and continue
      }else if(ConfirmMultipleDialog.CANCEL_OPTION==exitCode){
        // Break undo sequence
        return;
      }
      undoCheckOut( myIdx + 1 );
    }

    private int askOption()
    {
      final int[] exitCode = new int[ 1 ];
      if( myReplaceAllLocalCopies )
        exitCode[ 0 ] = ConfirmMultipleDialog.YES_ALL_EXIT_CODE;
      else
      if( myDoNotReplaceAllLocalCopies )
        exitCode[ 0 ] = ConfirmMultipleDialog.NO_ALL_EXIT_CODE;
      else
      {
        if( ApplicationManager.getApplication().isDispatchThread() )
          exitCode[ 0 ] = runDialogAskOption();
        else
        {
          Runnable runnable = new Runnable() {  public void run() {  exitCode[ 0 ] = runDialogAskOption(); } };
          ApplicationManager.getApplication().invokeAndWait( runnable, ModalityState.defaultModalityState() );
        }
      }
      return exitCode[ 0 ];
    }

    private int runDialogAskOption()
    {
      ConfirmMultipleDialog dialog = new ConfirmMultipleDialog(
        VssBundle.message("confirm.text.undo.check.out"),
        VssBundle.message("confirm.text.file.changed.undo", myFiles[myIdx].getPresentableUrl()), myProject);
      dialog.show();
      return dialog.getExitCode();
    }
  }
}