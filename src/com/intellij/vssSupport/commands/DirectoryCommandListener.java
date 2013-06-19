package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jul 18, 2007
 */

/**
 * Use this listener to catch messages from "Dir" VSS command.
 */
public class DirectoryCommandListener extends VssOutputCollector
{
  @NonNls private static final String TOTAL_SIG = " items(s)";
  @NonNls private static final String NO_ITEMS_FOUND_SIG = "No items found under";
  @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";

  private final Project project;
  private final String startFolder;
  private final HashSet<String> filesInProject;
  private final HashSet<String> filesCheckedOut;

  public DirectoryCommandListener( Project project, String startFolder,
                                   HashSet<String> projectFiles, HashSet<String> checkedOut,
                                   List<VcsException> errors )
  {
    super(errors);
    this.project = project;
    this.startFolder = startFolder;
    filesInProject = projectFiles;
    filesCheckedOut = checkedOut;
  }

  public void everythingFinishedImpl( final String output )
  {
    if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
    {
      myErrors.add( new VcsException( VssBundle.message( "message.text.path.is.not.existing.filename.or.project", startFolder )) );
      return;
    }

    String localPath = startFolder;

    String[] lines = LineTokenizer.tokenize( output, false );
    int offset = 0;
    while( offset < lines.length )
    {
      String line = lines[ offset ];
      if( line.length() > 0 )
      {
        //  First process lines that denote VSS project path like
        //  --- cut ---
        //  !$/vsstest/SRC/Dir5:
        //  --- end cut ---
        //  or (in the case of spanning to the next line:
        //  --- cut ---
        //  !$/vsstest/SRC/Dir5/SomeVeryLongPath/
        //  !withContinuation:
        //  --- end cut ---

        LineType lineType = whatSubProjectLine( lines, offset );
        if( lineType != LineType.NO_PROJECT )
        {
          localPath = constructLocalFromSubproject( lines, offset );
          localPath = VssUtil.getLocalPath( localPath, project );

          offset += (lineType == LineType.SIMPLE_FORMAT) ? 0 : 1;
        }
        else
        {
          //  If the line starts with '$' and does not end with ':' then it
          //  denotes subfolder under the given VSS [sub]project (see above).
          //  --- cut ---
          //    $/vsstest/SRC/Dir5:
          // ==>$foo
          //  --- cut ---
          if( !( line.charAt( 0 ) == '$' && (line.charAt(line.length() - 1) != ':') ) &&
              //  Skip lines with no useful information.
              line.indexOf( TOTAL_SIG ) == -1 &&
              line.indexOf( NO_ITEMS_FOUND_SIG ) == -1 )
          {
            extractFileAndCheckoutInfo( localPath, line );
          }
        }
      }
      offset++;
    }
  }

  /**
   * Parse output of the "Dir" command in the extended format which also
   * shows the checkout information independent of the current user information.
   * Example of the output is:
   * --- cut ---
   * $/vsstest/SRC/Dir5:
   * $foo
   * $PackageForTestRemoval2
   * $r
   * ConsoleOutput3.java
   * ConsoleOutputNotInVss.java
   * ConsoleOutputNotInV Lloix          2-18-07  6:39p  D:\PROJECTS\TEST
   * PROJECTS\VSSDEV\SRC
   * ClassinIt1.java
   * ClassInIt2.java     Lloix2         2-18-07  1:55p  D:\PROJECTS\VSSDEV2\SRC\Dir5
   * NewClassInIn1.java  Lloix          2-18-07 12:42p  D:\PROJECTS\TEST
   * PROJECTS\VSSDEV\SRC
   * NewClassInIn2.java  Lloix2         2-18-07  1:55p  D:\PROJECTS\VSSDEV2\SRC\Dir5
   * --- end cut ---
   *
   * 1. Non-checked out file is the only parseable element on the line
   * 2. For checked out file a user account, c/o date and working path is printed
   * 3. Long paths containing blanks are wrapped into several lines.
   * 4. User name (if present) always starts from the 20th symbol.
   * 5. File name must not contain backslashes.
   * TODO: Distinguish files whih have blank inside the filename exactly on the
   *       19th position.
   */
  private void extractFileAndCheckoutInfo( String lastFolderName, String line )
  {
    String fileName = null;
    String userName = VssConfiguration.getInstance( project ).USER_NAME.toLowerCase();

    //  If the user name is specified (in the configuration), try to find it in
    //  the input line. If present, then the file belongs to the checkout list
    //  of the current user. Otherwise it is a valid file which needs to be
    //  extracted from this mess.
    if( line.length() > 20 && line.charAt( 19 ) == ' ' )
    {
      fileName = line.substring( 0, 19 ).trim();
      boolean fileIsCut = (fileName.length() == 19);

      if( fileName.indexOf( '\\' ) == -1 ) //  avoid broken paths with blanks inside
      {
        fileName = lastFolderName + "\\" + fileName;

        //  File name might be truncated if its lenght is exactly 19 symbols.
        if( fileIsCut )
          fileName = completeFileName( fileName );

        if( StringUtil.isNotEmpty( userName ) && hasLoginName( line, userName ) )
        {
          fileName = VssUtil.getCanonicalLocalPath( fileName ).toLowerCase();
          filesCheckedOut.add( fileName );
        }
      }
    }
    else if( line.indexOf( '\\' ) == -1 )
    {
      fileName = lastFolderName + "\\" + line;
    }

    if( fileName != null )
    {
      fileName = VssUtil.getCanonicalLocalPath( fileName ).toLowerCase();
      filesInProject.add( fileName );
    }
  }

  private static boolean hasLoginName( String line, String login )
  {
    return StringUtil.startsWithConcatenation(line.substring(20).toLowerCase(), login, " ");
  }
  
  /**
   * Given a possibly truncated path of a checked out file, try to complete
   * the path by matching file names of writable files in the same directory.
   * NB: Does not work with renamed files or files under the renamed folders.
   * ToDo: fix that!
   */
  private static String completeFileName(String fileName)
  {
    File file = new File(fileName);
    final String parent = file.getParent();
    final String truncatedName = file.getName().toLowerCase();
    String fullName = truncatedName;
    String[] fullNames = file.getParentFile().list(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.toLowerCase().startsWith(truncatedName);
      }
    });

    //  if abstract pathname does not denote a directory, or if an I/O error
    // occurs, "File.list()" returns null.
    if( fullNames != null )
    {
      for (String name : fullNames) {
        if (new File(parent, name).canWrite()) {
          fullName = name.toLowerCase();
          break;
        }
      }
    }
    return parent + "/" + fullName;
  }
}
