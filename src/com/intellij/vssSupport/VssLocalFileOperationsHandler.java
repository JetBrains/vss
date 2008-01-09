package com.intellij.vssSupport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 3, 2007
 */
public class VssLocalFileOperationsHandler implements LocalFileOperationsHandler
{
  private final Project project;
  private final VssVcs host;

  public VssLocalFileOperationsHandler( Project project, VssVcs host )
  {
    this.project = project;
    this.host = host;
  }

  /**
   * If the folder to be deleted is versioned, and contains unversioned files,
   * issue a warning with the possibility to cancel the deletion operation.
   */
  public boolean delete( VirtualFile file ) throws IOException
  {
    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return false;

    if( file.isDirectory() )
    {
      boolean needToAsk = false;
      ChangeListManager mgr = ChangeListManager.getInstance( project );
      for( Change change : mgr.getChangesIn( file ) )
      {
        if( change.getAfterRevision() != null )
        {
          //  Exclude folder itself from the iteration.
          if( !isFolderItself( change, file ) && isStatusSuitable( change ) )
          {
            needToAsk = true;
            break;
          }
        }
      }

      if( needToAsk )
      {
        int result = Messages.showOkCancelDialog( project, VssBundle.message("dialog.text.folder.contains.unversioned"),
                                                  VssBundle.message("dialog.title.folder.contains.unversioned"),
                                                  Messages.getQuestionIcon() );
        return ( result != 0 );
      }
    }
    return false;
  }
  public boolean move(VirtualFile file, VirtualFile toDir) throws IOException { return false; }
  public File copy(VirtualFile file, VirtualFile toDir, final String copyName) throws IOException { return null; }
  public boolean rename(VirtualFile file, String newName) throws IOException  { return false; }

  public boolean createFile(VirtualFile dir, String name) throws IOException  { return false; }
  public boolean createDirectory(VirtualFile dir, String name) throws IOException { return false; }

  private static boolean isFolderItself( Change change, VirtualFile folder )
  {
    final String filePath = change.getAfterRevision().getFile().getVirtualFile().getPath();
    return filePath.equalsIgnoreCase( folder.getPath() );
  }

  private static boolean isStatusSuitable( Change change )
  {
    return (change.getFileStatus() == FileStatus.ADDED) || (change.getFileStatus() == FileStatus.UNKNOWN);
  }
}
