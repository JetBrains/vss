package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.GetOptions;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;


public class GetFileCommand extends VssCommandAbstract
{
  //  Get file into the given location.
  @NonNls private final static String OUTPUT_KEY = "-GL";

  private String  path;
  private String  outputPath;
  private String  version;
  private boolean makeWritable = false;
  private boolean isNonExistingFile = false;

  /**
   * Creates new {@code GetFilesCommand} instance.
   * @param project project.
   * @param filePath file path to be got
   */
  public GetFileCommand( Project project, String filePath, List<VcsException> errors )
  {
    this( project, filePath, false, errors );
  }

  public GetFileCommand( Project project, String filePath, String version, List<VcsException> errors )
  {
    this( project, filePath, false, errors );
    this.version = version;
  }

  public GetFileCommand( Project project, String filePath, boolean writable, List<VcsException> errors )
  {
    super( project, errors );
    path = filePath;
    version = null;
    makeWritable = writable;
  }

  public boolean isFileNonExistent() { return isNonExistingFile; }

  public void setOutputPath(final String outputPath) {
    this.outputPath = outputPath;
  }

  /**
   * Gets the files specified in the constructor.
   */
  public void execute()
  {
    File file = new File( path.replace('/',File.separatorChar ));
    String workingPath = file.getParentFile().getAbsolutePath();
    GetOptions options = createOptions();

    //  Difference is in the working folder: it is a parent for the file and
    //  a folder itself for a particular folder.
    if( file.isDirectory() )
    {
      workingPath = file.getAbsolutePath();
      options.RECURSIVE = true;
    }

    List<String> opts = options.getOptions( file );

    //  Modify output parameters if we need to download file into other location.
    if( outputPath != null )
      opts.add( OUTPUT_KEY + outputPath.trim() );

    runProcess( opts, workingPath, new GetListener( path, myErrors ) );
  }

  private GetOptions createOptions()
  {
    GetOptions options = myConfig.getGetOptions().getCopy();

    options.REPLACE_WRITABLE = GetOptions.OPTION_REPLACE;
    options.MAKE_WRITABLE = makeWritable;
    options.ANSWER_POSITIVELY = true;
    options.VERSION = version; //  ok if version==null

    return options;
  }

  private class GetListener extends VssOutputCollector
  {
    /**
     * Index of file to be checked out.
     */
    private final String path;
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";

    public GetListener(String filePath, List<VcsException> errors)
    {
      super( errors );
      path = filePath;
    }

    public void everythingFinishedImpl( final String output )
    {
      int exitCode = getExitCode();
      if( output.indexOf(NOT_EXISTING_MESSAGE) != -1 )
      {
        isNonExistingFile = true;
        myErrors.add(new VcsException( VssBundle.message("message.text.path.is.not.existing.filename.or.project", path ) ));
      }
      else if( output.indexOf(DELETED_MESSAGE) != -1 )
      {
        myErrors.add(new VcsException( VssBundle.message("message.text.cannot.get.deleted.file", path) ));
      }
      else
      {
        if( VssUtil.EXIT_CODE_SUCCESS == exitCode || VssUtil.EXIT_CODE_WARNING == exitCode )
          VssUtil.showStatusMessage( myProject, VssBundle.message("message.text.file.successfully.received", path ));
        else
          myErrors.add(new VcsException( output ) );
      }
    }
  }
}
