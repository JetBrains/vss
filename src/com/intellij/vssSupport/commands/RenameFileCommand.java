package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * author: lloix
 */
public class RenameFileCommand extends VssCommandAbstract
{
  @NonNls private static final String RENAME_COMMAND = "Rename";

  private VirtualFile file;
  private final String myOldName;

  public RenameFileCommand( Project project, VirtualFile vFile, String oldName, List<VcsException> errors )
  {
    super( project, errors );
    file = vFile;
    myOldName = oldName;
  }

  public void execute()
  {
    String workingPath = file.getParent().getPath();

    runCP( file.getParent(), workingPath );
    
    List<String> options = formOptions( RENAME_COMMAND, myOldName, file.getName(), _I__OPTION );
    runProcess( options, workingPath );
  }
}