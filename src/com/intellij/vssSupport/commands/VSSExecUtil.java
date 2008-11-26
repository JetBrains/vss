package com.intellij.vssSupport.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.process.InterruptibleProcess;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author LloiX
 */
public class VSSExecUtil
{
  public static final Logger LOG = Logger.getInstance("#com.intellij.vssSupport.commands.VSSExecUtil");

  @NonNls private final static String SYSTEMROOT_VAR = "SYSTEMROOT";
  @NonNls private final static String TEMP_VAR = "TEMP";
  @NonNls private final static String USER_SIG_OPTION_PREFIX = " -Y";

  private static final int TIMEOUT_LIMIT = 40;
  private static final int TIMEOUT_EXIT_CODE = -1000;

  private VSSExecUtil() {}

  public static interface UserInput {  void doInput(Writer writer);  }

  public synchronized static void runProcess( final Project project,
                                              String exePath, List<String> paremeters,
                                              HashMap<String, String> envParams, String workingDir,
                                              VssOutputCollector listener) throws ExecutionException
  {
    String[] programParams = paremeters.toArray( new String[ paremeters.size() ] );
    addVSS2005Values( envParams );

    GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.addParameters( programParams );
    cmdLine.setWorkDirectory( workingDir );
    cmdLine.setEnvParams( envParams );
    cmdLine.setExePath( exePath );

    LOG.info( cmdLine.getCommandLineParams() );

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if( progress != null )
    {
      String descriptor = prepareTitleString( cmdLine.getCommandLineString() );
      progress.setText2( descriptor );
    }

    VssProcess worker = new VssProcess( cmdLine.createProcess(), project );

    final VssStreamReader errReader = new VssStreamReader( worker.getErrorStream() );
    final VssStreamReader outReader = new VssStreamReader( worker.getInputStream() );

    final Application app = ApplicationManager.getApplication();
    final Future<?> errorStreamReadingFuture = app.executeOnPooledThread( errReader );
    final Future<?> outputStreamReadingFuture = app.executeOnPooledThread( outReader );

    final int rc = worker.execute();

    try
    {
      errorStreamReadingFuture.get();
      outputStreamReadingFuture.get();

      worker.getInputStream().close();
      worker.getErrorStream().close();
    }
    catch( java.util.concurrent.ExecutionException e ){
      listener.onCommandCriticalFail( e.getMessage() );
    }
    catch( IOException e ) {
      listener.onCommandCriticalFail( e.getMessage() );
    }
    catch( InterruptedException e ) {
      listener.onCommandCriticalFail( e.getMessage() );
    }

    //-------------------------------------------------------------------------
    //  Process is either exits by itself with some exit code or it is aborted
    //  by the timeout.
    //  In the former case we PRE-analyze output and error streams, trying to
    //  find most general error messages which require for the process to be
    //  aborted abnormally. In the case of normal exit we notify the
    //  VssOutputCollector instance with the result output string.
    //-------------------------------------------------------------------------
    if( rc == TIMEOUT_EXIT_CODE )
    {
      listener.onCommandCriticalFail( VssBundle.message( "message.text.process.shutdown.on.timeout" ) );
      LOG.info( "++ Command Shutdown detected ++");
    }
    else
    if( outReader.getReason() != null || errReader.getReason() != null )
    {
      String reason = (outReader.getReason() != null) ? outReader.getReason() : errReader.getReason();

      LOG.info( "++ Critical error detected: " + reason );
      listener.setExitCode( worker.getExitCode() );
      listener.onCommandCriticalFail( reason );
      listener.everythingFinishedImpl( reason );

      //  Hack: there is no known (so far) way to give the necessary sequence of
      //  characters to the input of the SS.EXE process to emulate "Enter" on its
      //  request to reenter the password. Thus we simply notify the parent process
      //  that it should finish.
      worker.destroy();
    }
    else
    {
      String text = errReader.getReadString() + outReader.getReadString();

      listener.setExitCode( worker.getExitCode() );
      listener.everythingFinishedImpl( text );
    }

    if( progress != null )
      progress.setText( "" );
  }

  public static void runProcessDoNotWaitForTermination( String exePath, String[] programParms,
                                                        HashMap<String, String> envParams ) throws ExecutionException
  {
    addVSS2005Values( envParams );

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.addParameters(programParms);
    commandLine.setWorkDirectory( null );
    commandLine.setEnvParams( envParams );
    commandLine.setExePath( exePath );

    final OSProcessHandler result = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
    result.startNotify();
  }

  /**
   * Add two system environment variables to the environment of this process.
   * This is required for Visual SourceSafe 2005 support.
   */
  private static void addVSS2005Values( HashMap<String, String> envParams )
  {
    String sysRootVar = System.getenv( SYSTEMROOT_VAR );
    String tempVar = System.getenv( TEMP_VAR );
    if( sysRootVar != null )
      envParams.put( SYSTEMROOT_VAR, sysRootVar );
    if( tempVar != null )
      envParams.put( TEMP_VAR, tempVar );
  }

  private static String  prepareTitleString( String original )
  {
    String result = original;
    int index = result.indexOf( USER_SIG_OPTION_PREFIX );
    if( index != -1 )
    {
      int blankIndex = result.indexOf( ' ', index + 2 );
      if( blankIndex == -1 )
        result = result.substring( 0, index );
      else
        result = result.substring( 0, index ) + result.substring( blankIndex );
    }
    return result;
  }

  private static class VssProcess extends InterruptibleProcess
  {
    private final Project project;
    public VssProcess( Process process, Project project )
    {
      super( process, TIMEOUT_LIMIT, TimeUnit.SECONDS );
      this.project = project;
    }
    public void destroy()        {  super.interrupt();  }
    public int  processTimeout() {  return TIMEOUT_EXIT_CODE;  }

    @Override
    protected int processTimeoutInEDT()
    {
      return project.isDisposed() ? TIMEOUT_EXIT_CODE : super.processTimeoutInEDT();
    }
  }
}
