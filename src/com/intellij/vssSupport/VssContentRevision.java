package com.intellij.vssSupport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.commands.VSSExecUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Feb 21, 2007
 */
public class VssContentRevision implements ContentRevision
{
  @NonNls private static final String _GWR_OPTION = "-GWR";
  @NonNls private static final String _GL_OPTION = "-GL";
  @NonNls private static final String GET_COMMAND = "Get";
  @NonNls private static final String TMP_FILE_NAME = "idea_vss";

  private final FilePath  path;
  private final Project   project;
  private File      myTmpFile;
  private String    myServerContent;

  public VssContentRevision( FilePath path, Project proj )
  {
    this.path = path;
    project = proj;
  }

  public String getContent()
  {
    if( myServerContent == null )
      myServerContent = getServerContent();

    return myServerContent;
  }

  private String getServerContent()
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    GetContentListener listener = new GetContentListener( errors );

    //  1. For renamed or deleted files, VirtualFile for <path> is NULL.
    //  2. For files which are in the project but reside outside the repository
    //     root their base revision version content is not defined (NULL).

    //  I use rather non-very-standard way to check whether the file resides
    //  under Vss vcs, but a call to ProjectLevelVcsMgr requires dispatch thread
    //  or read action, so more straightforward way is used (since this has to
    //  be done anyway...)
    String vssPath = VssUtil.getVssPath( path, project );
    if( vssPath != null )
    {
      VssConfiguration config = VssConfiguration.getInstance( project );
      try
      {
        //  The name of temporary copy is the name of temporary directory concatenated
        //  with the name of file.
        File tmpFile = FileUtil.createTempFile(TMP_FILE_NAME, "." + path.getName());
        tmpFile.deleteOnExit();
        File tmpDir = tmpFile.getParentFile();
        myTmpFile = new File( tmpDir, path.getName() );
        myTmpFile.deleteOnExit();

        // Launch Get command to store temporary file.
        List<String> options = new LinkedList<String>();
        options.add( GET_COMMAND );
        options.add( vssPath );
        options.add( _GL_OPTION + tmpDir.getCanonicalPath() );
        options.add( _GWR_OPTION );
        if( config.USER_NAME.length() > 0 )
          options.add( config.getYOption() );

        String workDir;
        VirtualFile parentDir = path.getVirtualFileParent();
        if( parentDir != null )
        {
          workDir = path.getVirtualFileParent().getPath().replace('/', File.separatorChar);
        }
        else
        {
          final VirtualFile[] roots = new VirtualFile[ 1 ];
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {  roots[ 0 ] = ProjectLevelVcsManager.getInstance(project).getVcsRootFor( path );  }
          });
          workDir = roots[ 0 ].getPath().replace('/', File.separatorChar);
        }
        VSSExecUtil.runProcess( project, config.CLIENT_PATH, options, config.getSSDIREnv(), workDir, listener );
      }
      catch( Exception exc )
      {
        VssUtil.showErrorOutput( exc.getMessage(), project );
      }
    }

    return listener.content;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber()  {  return VcsRevisionNumber.NULL;   }
  @NotNull public FilePath getFile()            {  return path; }

  private class GetContentListener extends VssOutputCollector
  {
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";

    public String content = null;

    public GetContentListener( List<VcsException> errors ) { super( errors ); }

    public void everythingFinishedImpl( final String output )
    {
      //---------------------------------------------------------------------
      //  In the case when the file has been deleted in the repository, or
      //  the file in its current state is not present there (e.g. if the file
      //  is renamed), do not throw any message.
      //---------------------------------------------------------------------
      if(( output.indexOf( DELETED_MESSAGE ) == -1 ) && ( output.indexOf( NOT_EXISTING_MESSAGE ) == -1 ))
      {
        try
        {
          char[] charContent = FileUtil.loadFileText( myTmpFile );
          content = new String( charContent );  
        }
        catch( IOException e ) { content = null; }
      }
      else
      if( VssUtil.EXIT_CODE_FAILURE == getExitCode() )
      {
        ApplicationManager.getApplication().invokeLater( new Runnable() { public void run() { VssUtil.showErrorOutput( output, project ); } });
      }
    }
  }
}
