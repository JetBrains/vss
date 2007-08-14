/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Nov 9, 2006
 */

public class DirectoryCommand extends VssCommandAbstract
{
  @NonNls private static final String DIR_COMMAND = "Dir";
  @NonNls private static final String RECURSIVE_OPTION = "-R";
  @NonNls private static final String EXTENDED_FORMAT_OPTION = "-E";

  private String localRootPath;
  private String vssProjectPath;
  private HashSet<String> filesInProject = new HashSet<String>();
  private HashSet<String> filesCheckedOut = new HashSet<String>();

  public DirectoryCommand( Project project, String path, List<VcsException> errors )
  {
    super( project, errors );
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( myProject );

    //  Given folder must be under the one of the repository roots - add it
    //  to the processing list as the working folder.

    VirtualFile aux = VcsUtil.getVirtualFile( path );
    VirtualFile contentRoot = mgr.getVcsRootFor( aux );
    if( contentRoot == null )
      throw new IllegalArgumentException( "DIRECTORY command can be started only for a valid content root" );

    localRootPath = contentRoot.getPath();
    vssProjectPath = VssUtil.getVssPath( aux, myProject );
  }

  public boolean isInProject( String path ) {  return filesInProject.contains( path );  }
  public boolean isCheckedOut( String path ){  return filesCheckedOut.contains( path ); }

  public void execute()
  {
    DirectoryCommandListener listener = new DirectoryCommandListener( myProject, localRootPath,
                                                                      filesInProject, filesCheckedOut, myErrors );
    List<String> options = formOptions( DIR_COMMAND, RECURSIVE_OPTION, EXTENDED_FORMAT_OPTION, vssProjectPath );
    runProcess( options, localRootPath, listener );
  }
}