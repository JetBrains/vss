/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

public class UndocheckoutDirCommand extends VssCommandAbstract
{
  private final VirtualFile myDir;

  /**
   * Creates new <code>UndocheckoutDirCommand</code> instance.
   * @param dir directory to be unchecked out. Note, that the passed
   * directory must be under VSS control, i.e. <code>VssUtil.isUnderVss</code>
   * method must return <code>true</code>.
   */
  public UndocheckoutDirCommand( Project project, VirtualFile dir, List<VcsException> errors )
  {
    super(project, errors);
    myDir = dir;
  }

  public void execute()
  {
    FileDocumentManager.getInstance().saveAllDocuments();
    List<String> options = myConfig.getUndocheckoutOptions().getOptions( myDir );
    String workingPath = myDir.getPath().replace('/', File.separatorChar);

    runProcess( options, workingPath, new UndoCheckoutListener( myErrors ) );

    //  Make "RO" attributes refresh immediately after the undo operation
    //  is finished. Otherwise synch can be made synchronously far later.
    myDir.refresh( true, true );
  }

  private class UndoCheckoutListener extends VssOutputCollector
  {
    @NonNls private static final String NOT_CHECKED_OUT_GROUP = "NOT_CHECKED_OUT";
    @NonNls private static final String NOT_EXISTING_GROUP = "NOT_EXISTING";

    @NonNls private static final String CHECKED_OUT_MESSAGE = "currently checked out";
    @NonNls private static final String DELETED_MESSAGE = "has been deleted";
    @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
    @NonNls private static final String LOSE_CHANGES_NO_MESSAGE  = "has changed. Undo check out and lose changes?(Y/N)N";
    @NonNls private static final String LOSE_CHANGES_YES_MESSAGE = "has changed. Undo check out and lose changes?(Y/N)Y";
    @NonNls private static final String REPLACING_LOCAL_COPY_MESSAGE = "Replacing local copy of ";
    @NonNls private static final String CONTINUE_QUESTION_SIG = "continue anyway?";

    UndoCheckoutListener( List<VcsException> errors ) {  super(errors);   }

    public void everythingFinishedImpl( final String output )
    {
      final UpdatedFiles updatedFiles = UpdatedFiles.create();
      updatedFiles.registerGroup( new FileGroup(VssBundle.message("update.group.name.not.checkedout"),
                                                VssBundle.message("update.group.name.not.checkedout"),
                                                false, NOT_CHECKED_OUT_GROUP, true ));
      updatedFiles.registerGroup( new FileGroup(VssBundle.message("update.group.name.notexisting"),
                                                VssBundle.message("update.group.name.notexisting"),
                                                false, NOT_EXISTING_GROUP, true ));

      int index;
      int logRecordsCount = 0;
      String fileName;
      String lastFolderName = myDir.getPath();

      String[] lines = LineTokenizer.tokenize( output, false );
      final VcsKey vcsKey = VssVcs.getKey();
      for( String line : lines )
      {
        if( line.length() == 0 )
          continue;

        //  If the file is modified and local copy will be replace, this
        //  line is ignored since the next line ("Replacing local copy of...")
        //  will indicate that file is replaced.
        if( line.indexOf( LOSE_CHANGES_YES_MESSAGE ) != -1 )
          continue;

        //  These questions are ignored since all the necessary status information
        //  is shown afterwards in the "Version Control" toolwindow.
        if( line.indexOf( CONTINUE_QUESTION_SIG ) != -1 )
          continue;

        if( line.charAt( line.length() - 1 ) == ':' )
        {
          lastFolderName = line.substring( 0, line.length() - 1 );
          lastFolderName = VssUtil.getLocalPath( lastFolderName, myProject );
        }
        else
        if( (index = line.indexOf( LOSE_CHANGES_NO_MESSAGE )) != -1 )
        {
          fileName = line.substring( 0, index - 1 );

          logRecordsCount++;
          updatedFiles.getGroupById( FileGroup.SKIPPED_ID ).add(fileName, vcsKey, null);
        }
        else
        if( (index = line.indexOf( NOT_EXISTING_MESSAGE )) != -1 )
        {
          fileName = line.substring( 0, index );
          fileName = VssUtil.getLocalPath( fileName, myProject );

          logRecordsCount++;
          updatedFiles.getGroupById( NOT_EXISTING_GROUP ).add(fileName, vcsKey, null);
        }
        else
        if( (index = line.indexOf( CHECKED_OUT_MESSAGE )) != -1 )
        {
          fileName = line.substring( 0, index );
          logRecordsCount++;
          updatedFiles.getGroupById( NOT_CHECKED_OUT_GROUP ).add(fileName, vcsKey, null);
        }
        else
        if( (index = line.indexOf( DELETED_MESSAGE )) != -1 )
        {
          fileName = line.substring( 0, index );
          logRecordsCount++;
          updatedFiles.getGroupById( FileGroup.REMOVED_FROM_REPOSITORY_ID ).add(fileName, vcsKey, null);
        }
        else
        if( line.indexOf( REPLACING_LOCAL_COPY_MESSAGE ) != -1 )
        {
          /*
             Do nothing with these files. Since they are successfully replaced,
             just do not mess them with other output information.
          */
        }
      }
      if( logRecordsCount > 0 )
      {
        ApplicationManager.getApplication().invokeLater( new Runnable() {
          public void run() {
            if (myProject.isDisposed()) return;
            ProjectLevelVcsManager.getInstance(myProject).showProjectOperationInfo(
              updatedFiles, VssBundle.message("dialog.title.undo.check.out", myDir.getName()) );
          }
        });
      }
      else
      {
        VcsUtil.showStatusMessage( myProject,
                                   VssBundle.message("message.text.undo.successfully", myDir.getName() ) );
      }
    }
  }
}
