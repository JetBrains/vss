package com.intellij.vssSupport.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import com.intellij.vssSupport.commands.CheckoutDirCommand;
import com.intellij.vssSupport.commands.CheckoutFileCommand;
import com.intellij.vssSupport.commands.VssCheckoutAbstractCommand;
import com.intellij.vssSupport.ui.CheckoutDirDialog;
import com.intellij.vssSupport.ui.CheckoutFilesDialog;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author LloiX
 */
public class CheckoutAction extends VssAction
{
  @NonNls public static final String CHECKED_BY_ANOTHER_GROUP_ID = "CHECKED_BY_ANOTHER";
  @NonNls public static final String ALREADY_CHECKED_OUT_GROUP_ID = "ALREADY_CHECKED_OUT";
  @NonNls public static final String WRITABLE_GROUP_ID = "WRITABLE";
  @NonNls public static final String SUCCESS_GROUP_ID = "SUCCESSFULLY_CHECKED_OUT";
  @NonNls public final static String FILE_DELETED_GROUP_ID = "FILE_DELETED_GROUP_ID";
  @NonNls public final static String NO_EXISING_FILE_GROUP_ID = "NO_EXISING_FILE_GROUP_ID";

  /** Command is enabled only if all files are under Vss control (this is checked
   *  in the base class), AND:
   *  they are either all folders OR they are all non-modified files.
   */
  public void update( AnActionEvent e )
  {
    super.update( e );

    if( e.getPresentation().isEnabled() )
    {
      Project project = e.getData( CommonDataKeys.PROJECT );
      ChangeListManager mgr = ChangeListManager.getInstance( project );
      VirtualFile[] files = VssUtil.getVirtualFiles( e );

      boolean isEnabled = allFilesAreFolders( files );
      if( !isEnabled )
      {
        isEnabled = true;
        for ( VirtualFile file : files )
        {
          FileStatus status = mgr.getStatus( file );
          isEnabled &= !file.isDirectory() && (status == FileStatus.NOT_CHANGED);
        }
      }
      e.getPresentation().setEnabled( isEnabled );
    }
  }

  public void actionPerformed( AnActionEvent e )
  {
    boolean isActionProduces = false;
    Project project = e.getData( CommonDataKeys.PROJECT );
    VirtualFile[] files = VssUtil.getVirtualFiles( e );
    ArrayList<VcsException> errors = new ArrayList<>();

    UpdatedFiles updatedFiles = UpdatedFiles.create();
    initializeGroups( updatedFiles );
    try
    {
      boolean showOptions = VssVcs.getInstance( project ).getCheckoutOptions().getValue();
      if( showOptions || isShiftPressed( e ) ) {
        OptionsDialog editor;
        editor = allFilesAreFolders(files) ? new CheckoutDirDialog(project) :
                 new CheckoutFilesDialog(project);
        editor.setTitle((files.length == 1) ? VssBundle.message("dialog.title.check.out.file", files[0].getName()) :
                        VssBundle.message("dialog.title.check.out.multiple"));
        if (!editor.showAndGet()) {
          return;
        }
      }

      isActionProduces = true;
      performActionOnFiles( files, project, updatedFiles, errors );
    }
    finally
    {
      if( isActionProduces )
      {
        /**
         * Show errors only once - collect information on checkout failures and
         * show the batch of messages in the toolwindow. This overall scheme
         * allows to successfully checkout all possible files without interruption.
         */
        if( !errors.isEmpty() && files.length == 1 )
        {
          String errMessage = VssBundle.message( "message.text.file.checked.out.failed" );
          Messages.showErrorDialog( errors.get( 0 ).getMessage(), errMessage );
        }
        else
        {
          ProjectLevelVcsManager.getInstance( project ).showProjectOperationInfo(
            updatedFiles, VssBundle.message("dialog.title.check.out.results" ) );
        }

        updateStatuses( updatedFiles.getGroupById( SUCCESS_GROUP_ID ).getFiles() );
        refreshRO( files );
      }
    }
  }

  private static void performActionOnFiles( VirtualFile[] files, Project project,
                                            UpdatedFiles updatedFiles, List<VcsException> errors )
  {
    for( VirtualFile file : files )
    {
      VssCheckoutAbstractCommand cmd;
      if( file.isDirectory() )
        cmd = new CheckoutDirCommand( project, file, errors );
      else
        cmd = new CheckoutFileCommand( project, file, errors );
      
      cmd.execute();
      storeResult( cmd, updatedFiles );
    }
  }

  private static void storeResult( VssCheckoutAbstractCommand cmd, UpdatedFiles updatedFiles )
  {
    final VcsKey vcsKey = VssVcs.getKey();
    for( String fileName : cmd.successFiles )
      updatedFiles.getGroupById( SUCCESS_GROUP_ID ).add(fileName, vcsKey, null);

    for( String fileName : cmd.writableFiles )
      updatedFiles.getGroupById( WRITABLE_GROUP_ID ).add(fileName, vcsKey, null);

    for( String fileName : cmd.checkedAlready )
      updatedFiles.getGroupById( ALREADY_CHECKED_OUT_GROUP_ID ).add(fileName, vcsKey, null);

    for( String fileName : cmd.checkedByOther )
      updatedFiles.getGroupById( CHECKED_BY_ANOTHER_GROUP_ID ).add(fileName, vcsKey, null);

    for( String fileName : cmd.deletedFiles )
      updatedFiles.getGroupById( FILE_DELETED_GROUP_ID ).add(fileName, vcsKey, null);

    for( String fileName : cmd.notexistingFiles )
      updatedFiles.getGroupById( NO_EXISING_FILE_GROUP_ID ).add(fileName, vcsKey, null);
  }

  public static void initializeGroups( UpdatedFiles groups )
  {
    String title = VssBundle.message("update.group.name.checked.by.other");
    groups.registerGroup( new FileGroup( title, title, false, CHECKED_BY_ANOTHER_GROUP_ID, true ));

    title = VssBundle.message("update.group.name.already.checkedout");
    groups.registerGroup( new FileGroup( title, title, false, ALREADY_CHECKED_OUT_GROUP_ID, true ));

    title = VssBundle.message("update.group.name.writable");
    groups.registerGroup( new FileGroup( title, title, false, WRITABLE_GROUP_ID, true ));

    title = VssBundle.message("update.group.name.checked.out");
    groups.registerGroup( new FileGroup( title, title, false, SUCCESS_GROUP_ID, true ));

    title = VssBundle.message("update.group.name.file.deleted");
    groups.registerGroup( new FileGroup( title, title, false, FILE_DELETED_GROUP_ID, true ));

    title = VssBundle.message("update.group.name.notexisting");
    groups.registerGroup( new FileGroup( title, title, false, NO_EXISING_FILE_GROUP_ID, true ));
  }

  private static void updateStatuses( Collection<String> files )
  {
    for( String file : files )
    {
      VirtualFile vFile = VcsUtil.getVirtualFile( file );
      if( vFile != null )
        vFile.putUserData( VssCheckoutAbstractCommand.SUCCESSFUL_CHECKOUT, true );
    }
  }

  private static void refreshRO( VirtualFile[] files )
  {
    for( VirtualFile file : files )
    {
      file.refresh( true, file.isDirectory() );
    }
  }
}
