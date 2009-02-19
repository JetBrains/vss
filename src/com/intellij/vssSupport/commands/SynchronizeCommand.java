package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.GetOptions;
import com.intellij.vssSupport.VssVcs;

import java.util.List;

/**
 * @author LloiX
 */
public class SynchronizeCommand extends VssCommandAbstract
{
  private final VirtualFile projectRoot;
  private final GetProjectListener listener;

  /**
   * Creates new <code>SynchronizeCommand</code> instance.
   *
   * @param project        project.
   * @param path           paths to be gotten. Note, that the passed
   *                       paths must be under VSS control, i.e. <code>VssUtil.isUnderVss</code>
   *                       method must return <code>true</code> for each of them.
   */
  public SynchronizeCommand( Project project, VirtualFile path, List<VcsException> errors )
  {
    super( project, errors );

    VssVcs host = VssVcs.getInstance( project );
    if( !VcsUtil.isFileForVcs( path, myProject, host ) )
      throw new IllegalArgumentException( "SYNCHRONIZE command can be started only for a valid content root" );

    projectRoot = path;
    listener = new GetProjectListener( project, projectRoot, myErrors );
  }

  public List<String> getFilesChanged()  {  return listener.filesChanged;  }
  public List<String> getFilesAdded()    {  return listener.filesAdded;    }
  public List<String> getFilesSkipped()  {  return listener.filesSkipped;  }

  /**
   * Gets the files specified in the constructor.
   */
  public void execute()
  {
    final GetOptions options = myConfig.getGetOptions().getCopy();
    options.ANSWER_POSITIVELY = true;
    options.MAKE_WRITABLE = false;
    options.RECURSIVE = true;
    options.REPLACE_WRITABLE = GetOptions.OPTION_SKIP;

    List<String> params = options.getOptions( projectRoot );

    runProcess( params, projectRoot.getPath(), listener);
  }
}
