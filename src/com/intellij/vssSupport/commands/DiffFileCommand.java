package com.intellij.vssSupport.commands;

import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 * @author lloix
 */
public class DiffFileCommand extends VssCommandAbstract
{
  @NonNls private static final String GET_COMMAND = "Get";
  @NonNls private static final String _GWR_OPTION = "-GWR";
  @NonNls private static final String _GL_OPTION = "-GL";
  @NonNls private static final String TMP_FILE_NAME = "idea_vss";

  private VirtualFile myFile;
  private File myTmpFile;

  public DiffFileCommand( Project project, VirtualFile vFile, List<VcsException> errors )
  {
    super(project, errors);
    myFile = vFile;
  }

  public void execute()
  {
    FileDocumentManager.getInstance().saveAllDocuments();

    // The implementation of Diff command consists from two parts:
    // 1. Get the latest repository version into the temporary folder;
    // 2. Performs difference of two local versions.

    try
    {
      // The name of temporary copy is the name of temporary directory concatenated
      // with the name of file.
      File tmpFile = File.createTempFile(TMP_FILE_NAME, "." + myFile.getExtension());
      tmpFile.deleteOnExit();
      File tmpDir = tmpFile.getParentFile();
      myTmpFile = new File(tmpDir, myFile.getName());
      myTmpFile.deleteOnExit();

      String workingPath = myFile.getParent().getPath().replace('/', File.separatorChar);
      List<String> options = formOptions( GET_COMMAND, VssUtil.getVssPath( myFile, myProject ),
                                          _GL_OPTION + tmpDir.getCanonicalPath(), _GWR_OPTION );
      runProcess( options, workingPath, new FileDiffListener( myErrors ) );
    }
    catch( Exception exc ) {
      myErrors.add( new VcsException( exc ) );
    }
  }

  /**
   * Use this listener to catch messages from "Get" VSS command.
   * If "Get" command completed successfully then it launch "Diff"
   * VSS command or external diff (if specified).
   */
  private class FileDiffListener extends VssOutputCollector
  {
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";

    public FileDiffListener( List<VcsException> errors ) {  super(errors);   }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public void everythingFinishedImpl( final String output )
    {
      if( output.indexOf( DELETED_MESSAGE ) != -1 ||
          output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
      {
        VcsException e = new VcsException( output );
        e.setVirtualFile( myFile );
        myErrors.add( e );
      }
      else
      {
        if (VssUtil.EXIT_CODE_FAILURE == getExitCode()) {
          VssUtil.showErrorOutput(output, myProject);
          return;
        }
        try
        {
          SimpleDiffRequest diffData = new SimpleDiffRequest(myProject, VssBundle.message("dialog.title.diff.for.file", myFile.getPresentableUrl()));
          diffData.setContentTitles(VssBundle.message("diff.content.title.repository"), VssBundle.message("diff.content.title.local"));
          DiffContent currentContent = FileContent.fromFile(myProject, myFile);
          DiffContent vssContent =
            SimpleContent.fromIoFile(myTmpFile, myFile.getCharset().name(), currentContent.getContentType());
          diffData.setContents( vssContent, currentContent );
          DiffManager.getInstance().getDiffTool().show( diffData );
        }
        catch (IOException e) {
          myErrors.add( new VcsException(e.getLocalizedMessage()) );
        }
      }
    }
  }
}
