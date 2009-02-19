package com.intellij.vssSupport.commands;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;

import java.io.File;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 * @author LloiX
 */
public class CheckoutFileCommand extends VssCheckoutAbstractCommand
{
  private final VirtualFile myFile;

  /**
   * Creates new <code>CheckoutFileCommand</code> instance.
   * @param project project.
   * @param file file to be checked out. Note, that the passed
   * file must be under VSS control, i.e. <code>VssUtil.isUnderVss</code>
   * method must return <code>true</code> for it.
   */
  public CheckoutFileCommand( Project project, VirtualFile file, List<VcsException> errors )
  {
    super(project, errors);
    myFile = file;
  }

  /**
   * Checks out the files specified in the constructor.
   */
  public void execute()
  {
    FileDocumentManager.getInstance().saveAllDocuments();
    List<String> options = myConfig.getCheckoutOptions().getOptions( myFile );
    String workingPath = myFile.getParent().getPath().replace('/', File.separatorChar);

    runProcess( options, workingPath, new CheckoutListener( myErrors ) );
  }

  /**
   * Use this listener to catch messages from "Checkout" VSS command.
   */
  private class CheckoutListener extends VssOutputCollector
  {
    CheckoutListener( List<VcsException> errors ) {  super(errors);  }

    /**
     * Parses ss.exe's output and shows corresponded messages in case of error.
     * If no fatal error occurred then checkes out the next file.
     */
    public void everythingFinishedImpl( final String output )
    {
      int exitCode = getExitCode();

      if( output.indexOf( HAVE_FILE_MESSAGE ) != -1 )
      {
        checkedAlready.add( myFile.getPath() );
      }
      else if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
      {
        notexistingFiles.add( myFile.getPath() );
      }
      else if( output.indexOf( DELETED_MESSAGE ) != -1 )
      {
        deletedFiles.add( myFile.getPath() );
      }
      else if( output.indexOf( CHECKED_OUT_BY_ANOTHER_USER_MESSAGE ) != -1 )
      {
        checkedByOther.add( myFile.getPath() );
      }
      else if( VssUtil.EXIT_CODE_SUCCESS == exitCode || VssUtil.EXIT_CODE_WARNING == exitCode )
      {
        successFiles.add( myFile.getPath() );
      }
      else
      {
        VssUtil.showErrorOutput( output, myProject );
      }

      if( VssUtil.EXIT_CODE_SUCCESS != exitCode && VssUtil.EXIT_CODE_WARNING != exitCode )
      {
        //  IDEADEV-11892. While impossible to keep track of the files which were
        //  checked (via native ss client) into another working folder, we need
        //  to give the user a hint.
        String out = output;
        if( output.indexOf( HAVE_FILE_MESSAGE ) != -1 )
          out += "\n" + VssBundle.message( "message.text.hint" );
        
        VcsException e = new VcsException( out );
        e.setVirtualFile( myFile );
        myErrors.add( e );
      }
    }
  }

  public static String getUserNameFrom(String errorOutput)
  {
    int beginOfUserName = errorOutput.indexOf(CHECKED_OUT_BY_ANOTHER_USER_MESSAGE);
    if (beginOfUserName == -1) return "";
    beginOfUserName += CHECKED_OUT_BY_ANOTHER_USER_MESSAGE.length();
    int endOfUserName = errorOutput.indexOf(";", beginOfUserName);
    if (endOfUserName == -1) return "";
    return errorOutput.substring( beginOfUserName, endOfUserName ).trim();
  }
}
