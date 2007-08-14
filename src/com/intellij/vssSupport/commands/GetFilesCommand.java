package com.intellij.vssSupport.commands;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.GetOptions;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.ui.ConfirmMultipleDialog;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 * @author LloiX
 */
public class GetFilesCommand extends VssCommandAbstract
{
  private VirtualFile[] myFiles;
  private GetOptions myBaseOptions;

  /**
   * List of <code>java.io.File</code> objects. It doesn't contains directories.
   */
  private ArrayList<File> myFilesToBeGot;
  private boolean myReplaceAllWritable;
  private boolean myDoNotReplaceAllWritable;

  /**
   * Creates new <code>GetFilesCommand</code> instance.
   * @param project project.
   * @param files files to be gotten. Note, that the passed
   * files must be under VSS control, i.e. <code>VssUtil.isUnderVss</code>
   * method must return <code>true</code> for each of them.
   */
  public GetFilesCommand( Project project, VirtualFile[] files, List<VcsException> errors )
  {
    super( project, errors );
    myFiles = files;
    myFilesToBeGot = new ArrayList<File>(myFiles.length);
  }

  /**
   * Gets the files specified in the constructor.
   */
  public void execute()
  {
    FileDocumentManager.getInstance().saveAllDocuments();
    myBaseOptions = myConfig.getGetOptions();

    collectFiles( 0 );
  }

  /**
   * Collect all files and projecs from myFiles[idx].
   * Stores collected files in the myFilesToBeGot list. When all files are collected then
   * it invokes get(0) function
   */
  private void collectFiles(int idx)
  {
    if( idx >= myFiles.length ){
      get(0);
      return;
    }

    VirtualFile file = myFiles[ idx ];
    myFilesToBeGot.add( new File( file.getPath().replace('/',File.separatorChar ) ));
    collectFiles( idx + 1 );
  }

  /**
   * Checkes out the file with the specified index.
   * If index is out of range then does nothing.
   */
  private void get( int idx )
  {
    if( idx >= myFilesToBeGot.size() )
    {
      for( VirtualFile file : myFiles )
        file.refresh( true, true );
      
      return;
    }

    File file = getFileToBeGot( idx );

    // Test whether file is writable.
    // User is free to replace or skip writable files. It means
    // that command's options can be different for each file.

    GetOptions options = myBaseOptions;
    if( file.exists() && file.canWrite() && GetOptions.OPTION_ASK == options.REPLACE_WRITABLE )
    {
      options = myBaseOptions.getCopy();
      if( myReplaceAllWritable ){
        options.REPLACE_WRITABLE = GetOptions.OPTION_REPLACE;
      }else if( myDoNotReplaceAllWritable ){
        options.REPLACE_WRITABLE = GetOptions.OPTION_SKIP;
      }else{
        ConfirmMultipleDialog dialog = new ConfirmMultipleDialog(
          VssBundle.message("dialog.title.confirm.replace"),
          VssBundle.message("dialog.label.file.is.writable.confirm.replace", file.getAbsolutePath()),
          myProject);
        dialog.show();
        int exitCode = dialog.getExitCode();
        if(ConfirmMultipleDialog.YES_EXIT_CODE == exitCode){
          options.REPLACE_WRITABLE = GetOptions.OPTION_REPLACE;
        }else if(ConfirmMultipleDialog.YES_ALL_EXIT_CODE == exitCode){
          myReplaceAllWritable = true;
          options.REPLACE_WRITABLE = GetOptions.OPTION_REPLACE;
        }else if(ConfirmMultipleDialog.NO_EXIT_CODE==exitCode){
          options.REPLACE_WRITABLE = GetOptions.OPTION_SKIP;
        }else if(ConfirmMultipleDialog.NO_ALL_EXIT_CODE==exitCode){
          myDoNotReplaceAllWritable = true;
          options.REPLACE_WRITABLE = GetOptions.OPTION_SKIP;
        }else if(ConfirmMultipleDialog.CANCEL_OPTION==exitCode){
          return;
        }
      }
    }

    GetListener checkoutListener = new GetListener( idx, myErrors );
    runProcess( options.getOptions( file ), file.getParentFile().getAbsolutePath(), checkoutListener );
  }

  private File getFileToBeGot(int idx){
    return myFilesToBeGot.get(idx);
  }

  private class GetListener extends VssOutputCollector
  {
    private int myIdx;
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";

    public GetListener(int idx, List<VcsException> errors)
    {
      super(errors);
      myIdx = idx;
    }

    public void everythingFinishedImpl( final String output )
    {
      int exitCode = getExitCode();

      if( output.indexOf(NOT_EXISTING_MESSAGE) != -1  || output.indexOf(DELETED_MESSAGE) != -1 ||
          exitCode != VssUtil.EXIT_CODE_SUCCESS )
      {
        myErrors.add( new VcsException( output ));
      }
      else
        VssUtil.showStatusMessage( myProject, VssBundle.message("message.text.file.successfully.received", getFileToBeGot( myIdx )));

      // Get the next file.
      get( myIdx + 1 );
    }
  }
}
