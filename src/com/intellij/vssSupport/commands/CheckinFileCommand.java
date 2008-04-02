package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.CheckinOptions;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * @author: lloix
 */
public class CheckinFileCommand extends VssCommandAbstract
{
  private final VirtualFile myFile;
  private CheckinOptions myOptions;
  private boolean suppressWarnOnOtherFolder;

  public CheckinFileCommand( Project project, VirtualFile file, List<VcsException> errors )
  {
    this( project, file, errors, false );
  }

  public CheckinFileCommand( Project project, VirtualFile file,
                             List<VcsException> errors, boolean suppressWarns )
  {
    super( project, errors );
    myFile = file;
    myOptions = myConfig.getCheckinOptions();
    suppressWarnOnOtherFolder = suppressWarns;
  }

  public void execute()
  {
    String workingPath = myFile.getParent().getPath();

    List<String> options = appendIOption( myOptions.getOptions( myFile ));
    runProcess( options, workingPath, new CheckinListener( myErrors ) );
  }

  private class CheckinListener extends VssOutputCollector
  {
    @NonNls private static final String NO_ANSWER = "N\n";
    @NonNls private static final String YES_ANSWER = "Y\n";
    @NonNls private static final String NOT_FROM_CURRENT_MESSAGE = "not from the current folder";
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";
    @NonNls private static final String ALREADY_CHECKED_OUT_MESSAGE = "is already checked out, continue?";
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String CHECKED_OUT_MESSAGE = "currently checked out";

    @NonNls private static final String PROPERLY_MERGED_QUESTION = "properly merged?";
    @NonNls private static final String NO_CONFLICTS_MESSAGE = "with no conflicts";
    @NonNls private static final String CONFLICTS_MESSAGE = "there are conflicts";

    public CheckinListener( List<VcsException> errors ) {  super(errors);   }

    public void everythingFinishedImpl( final String output )
    {
      int  exitCode = getExitCode();

      if( isNotExistingMessage(output) || isConflictsMessage(output) ||
          isNoConflictMessage(output) || isHasBeenDeletedMessage(output) ||
          ( isNotFromCurrentFolderMessage(output) && !suppressWarnOnOtherFolder ) ||
          isNotCurrentlyCheckedOutMessage(output) || isAlreadyCheckedOutMessage(output) ||
          VssUtil.EXIT_CODE_WARNING == exitCode )
      {
        addWarning(output);
      }
      else
      if( isProperlyMergedRequest(output) ) {
        onProperlyMerged();
      }
      else
      if( processQuestion(output) ){
      }
    }

    private void addWarning(final String message)
    {
      VcsException e = new VcsException( message ).setIsWarning( true );
      e.setVirtualFile( myFile );
      myErrors.add( e );
    }

    private boolean isNotFromCurrentFolderMessage(String errorOutput){
      return errorOutput.indexOf(NOT_FROM_CURRENT_MESSAGE) != -1;
    }

    private boolean isHasBeenDeletedMessage(String errorOutput) {
      return errorOutput.indexOf(DELETED_MESSAGE) != -1;
    }

    private boolean isAlreadyCheckedOutMessage(String errorOutput) {
      return errorOutput.indexOf(ALREADY_CHECKED_OUT_MESSAGE) != -1 && myOptions.KEEP_CHECKED_OUT;
    }

    private boolean isNotExistingMessage(String errorOutput) {
      return errorOutput.indexOf(NOT_EXISTING_MESSAGE) != -1;
    }

    private boolean isNotCurrentlyCheckedOutMessage(String errorOutput) {
      return errorOutput.indexOf(CHECKED_OUT_MESSAGE) != -1;
    }

    private boolean isProperlyMergedRequest(String errorOutput) {
      return errorOutput.indexOf(PROPERLY_MERGED_QUESTION) != -1;
    }

    private boolean isNoConflictMessage(String errorOutput) {
      return errorOutput.indexOf(NO_CONFLICTS_MESSAGE) != -1;
    }

    private boolean isConflictsMessage(String errorOutput) {
      return errorOutput.indexOf(CONFLICTS_MESSAGE) != -1;
    }

    private void onProperlyMerged()
    {
      boolean fileHasBeenProperlyMerged = fileHasBeenProperlyMerged();

      if (fileHasBeenProperlyMerged)
      {
        myOptions.defaultAnswer = Boolean.TRUE;
//        runProcess( myOptions.getVssConfiguration(), myOptions.getOptions( myFile ), myFile, simpleListener( myErrors ));
        execute();
      }
      else
      {
        myOptions.defaultAnswer = null;
        final boolean redoAutomaticMerge = redoAutomaticMerge();
        execute();
      }
    }

    private class MergeUserInput implements VSSExecUtil.UserInput
    {
      private boolean redoMerge;
      public MergeUserInput( boolean redoAutoMerge )
      {
        redoMerge = redoAutoMerge; 
      }
      public void doInput( Writer writer ) {
        try {
          answerNo(writer);
          if( redoMerge )
            answerYes(writer);
          else
            answerNo(writer);
          writer.flush();
        }
        catch (IOException e) {
          myErrors.add( new VcsException( e ));
        }
      }
    }

    private void answerYes(Writer writer) throws IOException {  writer.write( YES_ANSWER );  }
    private void answerNo (Writer writer) throws IOException {  writer.write( NO_ANSWER );   }

    /*
    private VssOutputCollector createSimpleCollection() {
      return new VssOutputCollector(myErrors) {
        public void everythingFinishedImpl() {  setExecutionState(true);  }
      };
    }
    */

    private boolean redoAutomaticMerge()
    {
      return showDialog( myProject, VssBundle.message("confirmation.text.redo.the.automatic.merge"),
                                VssBundle.message("confirmation.title.checkin"), Messages.getQuestionIcon() );
    }

    private boolean fileHasBeenProperlyMerged()
    {
      return showDialog( myProject, VssBundle.message("request.text.properly.merged", myFile.getPresentableUrl()),
                                VssBundle.message("confirmation.title.checkin"), Messages.getQuestionIcon() );
    }
    
    private boolean showDialog( final Project project, final String message, final String title, final Icon icon )
    {
      final int[] status = new int[ 1 ];
      Runnable runnable = new Runnable() { public void run() { status[ 0 ] = Messages.showYesNoDialog( project, message, title, icon ); } };
      ApplicationManager.getApplication().invokeAndWait( runnable, ModalityState.defaultModalityState() );
      return status[ 0 ] == 0;
    }
  }
}
