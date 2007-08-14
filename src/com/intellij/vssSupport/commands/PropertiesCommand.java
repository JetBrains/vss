package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 13, 2006
 */
public class PropertiesCommand extends VssCommandAbstract
{
  @NonNls private static final String PROPS_COMMAND = "Properties";
  @NonNls private static final String NO_RECURSIVE_SWITCH = "-R-";

  private String vssPath;
  private String tmpPath;
  private boolean isValidRepositoryObject;

  public PropertiesCommand( Project project, String path, boolean isFolder )
  {
    super( project, new ArrayList<VcsException>() );

    tmpPath = new File( path ).getParent();
    vssPath = VssUtil.getVssPath( path, isFolder, project );
    isValidRepositoryObject = true;
  }

  public void execute()
  {
    //  Avoid running the command with NULL Vss path. This is possible when
    //  input path lies outside the Vss project store.
    if( vssPath != null )
    {
      List<String> options = formOptions( PROPS_COMMAND, NO_RECURSIVE_SWITCH, vssPath );
      runProcess( options, tmpPath, new VssPropsListener( myErrors ) );
    }
  }

  public boolean isValidRepositoryObject() {  return isValidRepositoryObject;  }

  /**
   * Use this listener to catch messages from "Properties" VSS command.
   */
  private class VssPropsListener extends VssOutputCollector
  {
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String VSS_PROJECT_DELETED = "has been deleted";

    public VssPropsListener( List<VcsException> errors ) {  super( errors );  }

    public void everythingFinishedImpl( final String output )
    {
      if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 ||
          output.indexOf( VSS_PROJECT_DELETED ) != -1 )
        isValidRepositoryObject = false;
    }
  }
}
