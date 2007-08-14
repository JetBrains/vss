package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Michael (LloiX) Gerasimov
 * Date: Mar 12, 2007
 */
public class LabelCommand extends VssCommandAbstract
{
  private VirtualFile[] myFiles;
  private String myLabel;
  private String myComment;
  @NonNls private static final String LABEL_COMMAND = "Label";
  @NonNls private static final String INLINE_LABEL_OPTION = "-L";
//  @NonNls private static final String DONOT_ASK_OPTION = "-I-";
  @NonNls private static final String COMMENT_OPTION = "-C";
  @NonNls private static final String NO_COMMENT_OPTION = "-C-";

  /**
   * @param project project.
   * @parem label label to be assigned to the file(s) or folder(s)
   * @param files  files for which a label will be set. Note, that the passed
   *               files must be under VSS control, i.e. it must have
   *               not <code>null</code> nearest mapping item.
   */
  public LabelCommand( Project project, String label, String comment,
                       VirtualFile[] files, List<VcsException> errors )
  {
    super( project, errors );

    if( StringUtil.isEmptyOrSpaces( label ))
      throw new IllegalArgumentException( "Label parameter must be a valid string" );
    
    myLabel = label;
    myComment = comment;
    if( myComment != null )
      myComment = myComment.replace( '\n', ' ' );
    myFiles = files;
  }

  public void execute()
  {
    List<String> options = formOptions( LABEL_COMMAND, _I__OPTION, INLINE_LABEL_OPTION + myLabel );

    for( VirtualFile file : myFiles )
    {
      String vssPath = VssUtil.getVssPath( file, myProject );
      if( vssPath != null )
        options.add( vssPath );
    }
    options.add( StringUtil.isNotEmpty( myComment ) ? COMMENT_OPTION + myComment : NO_COMMENT_OPTION );
    
    runProcess( options, null );
  }
}

