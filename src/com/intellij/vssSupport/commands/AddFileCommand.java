package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.List;

/**
 * @author lloix
 */
public class AddFileCommand extends VssCommandAbstract
{
  private final VirtualFile file;
  private final boolean continueUponPositiveAnswer;

  public AddFileCommand( Project project, VirtualFile vFile, List<VcsException> errors )
  {
    super( project, errors );
    file = vFile;

    //-------------------------------------------------------------------------
    //  Notify base class that this command should not be repeated if for a
    //  question issued to the user a positive answer was given.
    //  In particular, this modifier prohibits running "ADD" command twice if
    //  user tries to add a file which was deleted from the repository.
    //-------------------------------------------------------------------------
    continueUponPositiveAnswer = false;
  }

  public void execute()
  {
    String workingPath = file.getParent().getPath().replace('/', File.separatorChar);

    runCP( file.getParent(), workingPath );
    runProcess( myConfig.getAddOptions().getOptions( file ), workingPath );
  }
}
