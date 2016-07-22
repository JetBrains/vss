/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.vssSupport.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class VssCommandAbstract
{
  @NonNls private static final String QUESTION_SUBSTRING = "(Y/N)N";
  
  @NonNls protected static final String _I_OPTION = "-I";
  @NonNls protected static final String _I_Y_OPTION = "-I-Y";
  @NonNls protected static final String _I__OPTION = "-I-";

  @NotNull protected final Project myProject;
  protected final VssConfiguration myConfig;
  protected final List<VcsException> myErrors;

  protected VssCommandAbstract( @NotNull Project project )
  {
    myProject = project;
    myErrors = new ArrayList<>();
    myConfig = VssConfiguration.getInstance( project );
  }

  protected VssCommandAbstract( @NotNull Project project, List<VcsException> errors )
  {
    myProject = project;
    myErrors = errors;
    myConfig = VssConfiguration.getInstance( project );
  }

  public List<VcsException> getErrors()
  {
    return myErrors;
  }

  protected void runProcess( List<String> list, String wrkPath )
  {
    runProcess( list, wrkPath, new MinimalOutputListener( myErrors ) );
  }

  protected void runProcess( List<String> list, String wrkPath, VssOutputCollector listener )
  {
    try
    {
      VSSExecUtil.runProcess( myProject, myConfig.CLIENT_PATH, list, myConfig.getSSDIREnv(), wrkPath, listener );
    }
    catch( ExecutionException exc )
    {
      String msg = myConfig.checkCmdPath();
      VcsException e = new VcsException( (msg != null) ? msg : exc.getLocalizedMessage() );
      myErrors.add( e );
    }
  }

  public abstract void execute();

  /**
   * Standard behavior for a ss.exe command which require some input from the user:
   * if we find a question signature (with the default answer) - ask the user exactly
   * the same question, and in the case of positive answer restart the command with
   * the flag to set "-I-Y" option into the command line (via <myContinue> field).
   *
   * NB: Current problem - ss.exe commands are divided into two classes - those
   *     which require the reissuing the command after the answer and those which
   *     finish the desired action after the answer. In the latter case reissuing
   *     command leads to an error message.
   *     At least one command known - "Add" (file) - belongs to the second
   *     class.
   */
  protected boolean processQuestion( String errorOutput )
  {
    int questionIndex = errorOutput.indexOf(QUESTION_SUBSTRING);
    if( questionIndex != -1 )
    {
      if( !ApplicationManager.getApplication().isDispatchThread() )
        throw new AssertionError( "Commands can be issued only in the dispatch thread" );

      String request = errorOutput.substring(0, questionIndex);
      int answer = Messages.showYesNoDialog(request, VssBundle.message("message.title.source.safe"), Messages.getQuestionIcon());
      if( answer != Messages.YES )
      {
//        setExecutionState( false );
      }
      else
      {
//        myContinue = true;
//        if( continueUponPositiveAnswer )
          execute();
      }
      return true;
    }
    return false;
  }

  protected static List<String> appendIOption(List<String> options){
    for (final String option : options) {
      if( option.startsWith( _I_OPTION ) ) return options;
    }
//    if (myContinue)
      options.add(_I_Y_OPTION);
//    else
//      options.add(_I__OPTION);

    return options;
  }

  protected void runCP( VirtualFile folder, String workingFolder )
  {
    List<String> cpParams = formOptions( "cp", VssUtil.getVssPath( folder, myProject ) );
    runProcess( cpParams, workingFolder );
  }

  protected void runCP( String folder, String workingFolder )
  {
    folder = VcsUtil.getCanonicalLocalPath( folder );
    folder = VssUtil.getVssPath( folder, true, myProject );
    List<String> cpParams = formOptions( "cp", folder );
    
    runProcess( cpParams, workingFolder );
  }

  protected List<String> formOptions( @NonNls String... subcmd ) {
    List<String> params = new ArrayList<>();
    ContainerUtil.addAll(params, subcmd);

    if (myConfig.USER_NAME.length() > 0)
      params.add(myConfig.getYOption());

    return params;
  }
  
  private static class MinimalOutputListener extends VssOutputCollector
  {
    public MinimalOutputListener( List<VcsException> errors ) {  super( errors );  }

    public void everythingFinishedImpl( final String output )
    {
      int exitCode = getExitCode();
      if( exitCode != VssUtil.EXIT_CODE_SUCCESS )
      {
        VcsException exc = new VcsException( output );
        exc.setIsWarning( exitCode == VssUtil.EXIT_CODE_WARNING );
        myErrors.add( exc );
      }
    }
  }
}
