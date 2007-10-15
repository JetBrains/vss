package com.intellij.vssSupport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.commands.GetFileCommand;
import com.intellij.vssSupport.commands.SynchronizeCommand;
import com.intellij.vssSupport.ui.GetDirDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Feb 20, 2007
 */
public class VssUpdateEnvironment implements UpdateEnvironment
{
  @NonNls
  private final static String TAB_NAME = "Update Project";
  private Project project;

  public VssUpdateEnvironment( Project project )
  {
    this.project = project;
  }

  public void fillGroups( UpdatedFiles groups ){}

  public UpdateSession updateDirectories( final FilePath[] roots, UpdatedFiles updatedFiles,
                                          ProgressIndicator progress ) throws ProcessCanceledException
  {
    final ArrayList<VcsException> errors = new ArrayList<VcsException>();

    progress.setText( VssBundle.message("message.synch.with.repository") );
    FileDocumentManager.getInstance().saveAllDocuments();

    final boolean cancelled = showOptions( roots );
    if( !cancelled )
    {
      //  We allow update/synch operations only for Folders, not for ordinary
      //  files. Since in "Version Control" window it is allowable (so far) to
      //  issue "Update File" command on the individual file, we need to issue
      //  "Get" command instead.
      for( FilePath root : roots )
      {
        processPath( root, errors, updatedFiles );
      }
    }

    return new UpdateSession(){
      public List<VcsException> getExceptions() { return errors; }
      public void onRefreshFilesCompleted()     {}
      public boolean isCanceled()               { return cancelled;  }
    };
  }

  private void processPath( final FilePath root, List<VcsException> errors, UpdatedFiles updatedFiles )
  {
    if( root.isDirectory() )
    {
      try
      {
        final VirtualFile rootFile = root.getVirtualFile();
        SynchronizeCommand cmd = new SynchronizeCommand( project, rootFile, errors );
        cmd.execute();

        fillGroup( updatedFiles, FileGroup.CREATED_ID, cmd.getFilesAdded() );
        fillGroup( updatedFiles, FileGroup.UPDATED_ID, cmd.getFilesChanged() );
        fillGroup( updatedFiles, FileGroup.SKIPPED_ID, cmd.getFilesSkipped() );

        //  Make files in the folder refresh immediately after the "Get" operation
        //  is finished. Otherwise synch can be made synchronously far later.
        rootFile.refresh( true, rootFile.isDirectory() );
      }
      catch( Throwable e )
      {
        VcsException exc = new VcsException( "Can not process content root " + root.getPath() );
        AbstractVcsHelper.getInstance(project).showError( exc, TAB_NAME );
      }
    }
    else
    {
      GetFileCommand cmd = new GetFileCommand( project, root.getPath(), errors );
      cmd.execute();
    }
  }

  private boolean showOptions( final FilePath[] roots )
  {
    final boolean[] cancelled = new boolean[ 1 ]; cancelled[ 0 ] = false;
    if( VssVcs.getInstance( project ).getGetOptions().getValue() )
    {
      Runnable runnable = new Runnable() { public void run() {
          GetDirDialog editor = new GetDirDialog( project );
          if( roots.length == 1 )
            editor.setTitle( VssBundle.message( "dialog.title.get.directory", roots[ 0 ].getPath() ) );
          else
            editor.setTitle( VssBundle.message( "dialog.title.get.multiple" ) );
          editor.makeConsistentForUpdateProject();
          editor.show();

          cancelled[ 0 ] = !editor.isOK();
        }
      };
      ApplicationManager.getApplication().invokeAndWait( runnable, ModalityState.defaultModalityState() );
    }
    return cancelled[ 0 ];
  }

  private static void fillGroup( UpdatedFiles updatedFiles, String id, Collection<String> list )
  {
    for( String file : list )
      updatedFiles.getGroupById( id ).add( file );
  }

  @Nullable
  public Configurable createConfigurable(Collection<FilePath> files)
  {
    return null;
  }
}
