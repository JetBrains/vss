package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import com.intellij.vssSupport.VssVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 19, 2007
*/
public class GetProjectListener extends VssOutputCollector
{
  @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";
  @NonNls private static final String NO_ITEMS_FOUND = "No items found under";
  @NonNls private static final String WRITABLE_COPY_PREFIX = "A writable copy of ";
  @NonNls private static final String WRITABLE_COPY_SUFFIX = " already exists";
  @NonNls private static final String GETTING_PREFIX = "Getting ";
  @NonNls private static final String REPLACING_PREFIX = "Replacing local copy of ";
  @NonNls private static final String NOT_FOUND_FOLDER_CREATE = "not found, create?";
  @NonNls private static final String SET_AS_DEFAULT_PROJECT = "as the default folder";
  @NonNls private static final String FILE_DESTROYED_PROJECT = "has been destroyed, ";
  @NonNls private static final String CONTINUE_ANYWAY_SIG = "Continue anyway?";

  private final Project project;
  private final VirtualFile path;

  public ArrayList<String> filesChanged;
  public ArrayList<String> filesAdded;
  public ArrayList<String> filesSkipped;

  public GetProjectListener(Project project, VirtualFile pathToProcess, List<VcsException> errors)
  {
    super( errors );
    this.project = project;
    path = pathToProcess;

    filesChanged = new ArrayList<>();
    filesAdded = new ArrayList<>();
    filesSkipped = new ArrayList<>();
  }

  public void everythingFinishedImpl( final String output )
  {
    if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
      myErrors.add( new VcsException(VssBundle.message( "message.text.path.is.not.existing.filename.or.project", path )));
    else
    if( output.indexOf( NO_ITEMS_FOUND ) == -1 ) //  just skip the rest.
      parseOutput( output );
  }

  /**
   * Example of output of ss.exe v6.0d:
      $/vsstest/src/DIR2:
      Getting ClassInNonFolderIn.java
      Getting ClassUnderDir5Again.java
      i0.pdf
      wantNewClassForComment.java
   * Example of output of ss.exe v8.0:
      $/vsstest/src/DIR2/i0.pdf
      $/vsstest/src/DIR2/wantNewClassForComment.java
      Getting $/vsstest/src/DIR2/ClassUnderDir5Again.java
      Getting $/vsstest/src/DIR2/ClassInNonFolderIn.java
   */
  private void parseOutput( String errorOutput )
  {
    String[] lines = LineTokenizer.tokenize( errorOutput, false );

    int offset = 0;
    String localPath = "";
    while( offset < lines.length )
    {
      LineType lineType = whatSubProjectLine( lines, offset );
      if( lineType != LineType.NO_PROJECT )
      {
        localPath = constructLocalFromSubproject( lines, offset );
        localPath = VssUtil.getLocalPath( localPath, project );

        offset += (lineType == LineType.SIMPLE_FORMAT) ? 1 : 2;
      }
      else
      {
        //  Files which are 2005-formatted are simply skipped.
        if( !isStringVss2005Formatted( lines[ offset ] ) )
        {
          if( lines[ offset ].trim().length() > 0 )
              analyzeLine( lines[ offset ], localPath );
        }
        offset++;
      }
    }
  }

  private void analyzeLine( String line, String localProjectPath )
  {
    //  Several patterns serve as the default notifications on actions done
    //  (since we always start the command with the -I-Y or -I-N switches).
    //  Skip these lines.
    if( line.indexOf( NOT_FOUND_FOLDER_CREATE ) != -1 ||
        line.indexOf( SET_AS_DEFAULT_PROJECT ) != -1 ||
        line.indexOf( FILE_DESTROYED_PROJECT ) != -1 ||
        line.indexOf( CONTINUE_ANYWAY_SIG ) != -1 )
      return;

    //  Skip writable files which are either checked-out files and must not be
    //  changed or accidentally writable files which should be resolved later.
    if( line.indexOf( WRITABLE_COPY_PREFIX ) != -1 )
    {
      line = line.substring( WRITABLE_COPY_PREFIX.length() );
      line = line.replaceFirst( WRITABLE_COPY_SUFFIX, "" );
      filesSkipped.add( line );
    }
    else
    {
      //  File is new:
      //  - if the file or its directory is not under the project;
      //  - prefix "Getting" is present;
      //  or modified:
      //  - prefix "Replacing local copy of " is present;
      //
      //  NB: When processing "Getting" or "Replacing..." instruction, take
      //      into account different formats of ss.exe output for 6.0d and 8.0 versions:
      //   6.0d: Getting XXX.YY, where XXX.YY is a filename that must be
      //         concatenated with the local path listed above;
      //   8.0: Getting $/XXX/YYY/zzz.ttt, where $/XXX/YYY/zzz.ttt is the
      //        full path of the file in the vss repository.

      String  fullPath;
      if( line.startsWith( REPLACING_PREFIX ) )
      {
        line = line.substring( REPLACING_PREFIX.length() );
        fullPath = constructLocalPath( line, localProjectPath );
        filesChanged.add( fullPath );
      }
      else
      if( line.startsWith( GETTING_PREFIX ) )
      {
        line = line.substring( GETTING_PREFIX.length() );
        fullPath = constructLocalPath( line, localProjectPath );
        filesAdded.add( fullPath );
      }
      else
      {
        fullPath = constructLocalPath( line, localProjectPath );
        if( !VcsUtil.isFileForVcs( fullPath, project, VssVcs.getInstance(project)))
          filesAdded.add( fullPath );
      }
    }
  }

  @Nullable
  private String constructLocalPath( String line, String localProjectPath )
  {
    if( !line.startsWith( "$/" ) )
      return new File( localProjectPath, line ).getPath();
    else
      return VssUtil.getLocalPath( line, project );
  }

  /**
   * Return true if the line is formatted conforming the VSS 2005 format:
   * it starts with the "$/" prefix, does not end up with the ':' char and
   * thus refers to the valid local file on the file system.
   * NB: We process here only the cases when ss.exe skips the corresponding
   *     file (since its copy already exists), since "File.exists()" may fail
   *     for files which are being got (or replaced?) during the "Get" operation.
   *     Files which are "Getting" or "Replaced" are processed above.
   */
  private boolean isStringVss2005Formatted( final String line )
  {
    boolean fileExists = false;
    if( line.startsWith( "$/" ) )
    {
      String file = VssUtil.getLocalPath( line, project );
      if( file != null )
      {
        fileExists = new File( file ).exists();
      }
    }
    return fileExists;
  }
}
