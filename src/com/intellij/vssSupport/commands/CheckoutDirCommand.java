/**
 * @author Vladimir Kondratyev
 * @author LloiX
 */
package com.intellij.vssSupport.commands;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.CheckoutOptions;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;

import java.io.File;
import java.util.List;

public class CheckoutDirCommand extends VssCheckoutAbstractCommand
{
  private final VirtualFile myDir;

  /**
   * @param project project.
   * @param dir directory to be checked out. Note, that the passed
   * directory must be a directory and this directory should be under VSS control,
   * i.e. method <code>VssUtil.isUnderVss</code> must return <code>true</code>.
   */
  public CheckoutDirCommand( Project project, VirtualFile dir, List<VcsException> errors )
  {
    super( project, errors );
    myDir = dir;
  }

  public void execute()
  {
    final CheckoutOptions baseOptions = myConfig.getCheckoutOptions();
    if( baseOptions == null )
      return;

    List<String> options = baseOptions.getOptions( myDir );
    String workingPath = myDir.getPath().replace('/', File.separatorChar);

    FileDocumentManager.getInstance().saveAllDocuments();
    runProcess( options, workingPath, new CheckoutListener( myErrors ) );

    //  Make "RO" attributes refresh immediately after the undo operation
    //  is finished. Otherwise synch can be made synchronously far later.
    myDir.refresh( true, true );
  }

  /**
   * Use this listener to catch messages from "Checkout" VSS command.
   */
  private class CheckoutListener extends VssOutputCollector
  {
    CheckoutListener( List<VcsException> errors ) {  super( errors );  }

    public void everythingFinishedImpl( final String output )
    {
      if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
      {
        notexistingFiles.add( myDir.getPath() );
        return;
      }

      int index;
      String fileName;
      String lastFolderName = myDir.getPath();

      String[] lines = LineTokenizer.tokenize( output, false );
      for( int i = 0; i < lines.length; i++ )
      {
        String line = lines[i];
        if (line.length() == 0) continue;

        LineType lineType = whatSubProjectLine( lines, i );
        if( lineType != LineType.NO_PROJECT )
        {
          lastFolderName = constructLocalFromSubproject( lines, i );
          lastFolderName = VssUtil.getLocalPath( lastFolderName, myProject );
          //noinspection AssignmentToForLoopParameter
          i += (lineType == LineType.SIMPLE_FORMAT) ? 0 : 1;
        }
        else if ((index = line.indexOf(ALREADY_CHECKED_MESSAGE)) != -1)
        {
          fileName = line.substring(index + ALREADY_CHECKED_MESSAGE.length());
          fileName = fileName.substring(0, fileName.length() - CHECKED_OUT_SUFFIX.length());
          fileName = VssUtil.getLocalPath(fileName, myProject);

          checkedAlready.add(fileName);
        }
        else if ((index = line.indexOf(CHECKED_OUT_BY_ANOTHER_USER_MESSAGE)) != -1)
        {
          fileName = line.substring(0, index - 1);
          fileName = fileName.substring(5);
          fileName = VssUtil.getLocalPath(fileName, myProject);

          checkedByOther.add(fileName);
        }
        else if ((index = line.indexOf(WRITABLE_COPY_MESSAGE)) != -1)
        {
          fileName = line.substring(index + WRITABLE_COPY_MESSAGE.length());
          fileName = fileName.substring(0, fileName.length() - 15);

          writableFiles.add(fileName);
        }
        else
        {
          if (line.charAt(line.length() - 1) != ':') {
            fileName = lastFolderName + "\\" + line;
            successFiles.add(fileName);
          }
        }
      }
    }
  }
}
