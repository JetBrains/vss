package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.DiffDirParser;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Mar 13, 2006
 */
public class DiffDirCommand extends VssCommandAbstract
{
  @NonNls private static final String DIFF_DIR_COMMAND = "Diff";

  public HashSet<String> filesNew = new HashSet<String>();
  public HashSet<String> filesDeleted = new HashSet<String>();
  public HashSet<String> filesChanged = new HashSet<String>();
  public boolean         folderNotFound = false;

  private final String tmpPath;

  public DiffDirCommand( Project project, String subprojectPath )
  {
    super( project, null );
    tmpPath = subprojectPath;
  }

  public void execute()
  {
    //  Avoid running the command with NULL Vss path. This is possible when
    //  input path lies outside the Vss project store.
    final String vssPath = VssUtil.getVssPath( tmpPath, true, myProject );
    if( vssPath != null )
    {
      //  Issue "diff" command to vss command line tool, retrieve its responce
      //  and parse it.

      List<String> options = formOptions( DIFF_DIR_COMMAND, vssPath );
      runProcess( options, tmpPath, new DiffDirListener( myErrors ) );
    }
  }

  /**
   * Catch messages from "Diff" VSS command. If "Diff" command completes
   * successfully then it parses command output and fills internal structures.
   */
  private class DiffDirListener extends VssOutputCollector
  {
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String PROJECT_NOT_IN_VSS = "File or project not found";

    public DiffDirListener(List<VcsException> errors)
    {
      super( errors );
    }

    public void everythingFinishedImpl( final String output )
    {
      if( output.indexOf( PROJECT_NOT_IN_VSS ) != -1 )
        folderNotFound = true;
      else
      if( output.indexOf( DELETED_MESSAGE ) != -1 )
        filesDeleted.add( tmpPath );
      else
      if (output.indexOf( NOT_EXISTING_MESSAGE ) != -1)
        filesNew.add( tmpPath );
      else
      {
        if( VssUtil.EXIT_CODE_FAILURE == getExitCode() )
          myErrors.add( new VcsException( output ) );
        else
          parseContent( output );
      }
    }

    private void parseContent( String out )
    {
      if( out.indexOf( PROJECT_NOT_IN_VSS ) == -1 )
      {
        DiffDirParser.parse( out );
        for( String item : DiffDirParser.filesNew )
        {
          if( VcsUtil.isFileForVcs(item, myProject, VssVcs.getInstance(myProject)))
            filesNew.add( VssUtil.getCanonicalLocalPath( item ) );
        }
        for( String item : DiffDirParser.filesDeleted )
          filesDeleted.add( VssUtil.getCanonicalLocalPath( item ) );

        for( String item : DiffDirParser.filesChanged )
          filesChanged.add( VssUtil.getCanonicalLocalPath( item ) );
      }
    }
  }
}
