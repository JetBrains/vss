package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class HistoryCommand extends VssCommandAbstract
{
  @NonNls private static final String HISTORY_COMMAND = "History";

  public ArrayList<HistoryParser.SubmissionData> changes;
  private final String path;

  public HistoryCommand( Project project, String path, List<VcsException> errors )
  {
    super( project, errors );
    this.path = path;
  }

  public void execute()
  {
    //  Protect ourselves from calling History on the deleted files (since
    //  FilePath object can refer to already illegal file system objects).
    String pathNorm = VssUtil.getCanonicalLocalPath( path );
    VirtualFile vFile = VcsUtil.getVirtualFile( pathNorm );
    if( vFile != null )
    {
      String vssPath = VssUtil.getVssPath( pathNorm, false, myProject );

      List<String> options = formOptions( HISTORY_COMMAND, vssPath, _I_Y_OPTION );
      runProcess( options, null, new VssHistoryListener( myErrors ) );
    }
  }

  public class VssHistoryListener extends VssOutputCollector
  {
    public VssHistoryListener( List<VcsException> errors ) {  super( errors );  }

    public void everythingFinishedImpl( final String output )
    {
      if( VssUtil.EXIT_CODE_SUCCESS == getExitCode() )
        changes = HistoryParser.parse( output );
      else
        myErrors.add( new VcsException( output ) );
    }
  }
}
