package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vssSupport.VssOutputCollector;
import com.intellij.vssSupport.VssUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Feb 20, 2007
 */
public class StatusMultipleCommand extends VssCommandAbstract
{
  @NonNls private static final String STATUS_COMMAND = "Status";
  @NonNls private static final String CURRENT_USER_OPTION = "-U";

  @NonNls private static final String DELETED_MESSAGE = "has been deleted";
  @NonNls private static final String NOT_EXISTING_MESSAGE = "is not an existing";

  private static final int  CMDLINE_MAX_LENGTH = 500;

  private final List<String> files;

  private HashSet<String> deletedFiles;
  private HashSet<String> nonexistingFiles;
  private HashSet<String> checkoutFiles;

  public StatusMultipleCommand( Project project, List<String> paths )
  {
    super( project );
    files = paths;
  }

  public void execute()
  {
    VssOutputCollector listener = (files.size() == 1) ? new SingleStatusListener( myErrors ) :
                                                        new MultipleStatusListener( myErrors );
    deletedFiles = new HashSet<String>();
    nonexistingFiles = new HashSet<String>();
    checkoutFiles = new HashSet<String>();

    int currIndex = 0;
    int cmdLineLen;
    LinkedList<String> options = new LinkedList<String>();
    while( currIndex < files.size() )
    {
      cmdLineLen = STATUS_COMMAND.length() + CURRENT_USER_OPTION.length();

      options.clear();
      options.add( STATUS_COMMAND );
      if( myConfig.USER_NAME.length() > 0 )
      {
        options.add( myConfig.getYOption() );
        cmdLineLen += myConfig.getYOption().length();
      }
      options.add( CURRENT_USER_OPTION );
      
      while( currIndex < files.size() && cmdLineLen < CMDLINE_MAX_LENGTH )
      {
        String vssPath = VssUtil.getVssPath( files.get( currIndex++ ), false, myProject );
        options.add( vssPath );
        cmdLineLen += vssPath.length() + 1;
      }
      
      runProcess( options, null, listener );
    }
  }

  public boolean isDeleted( String file ) {
    return deletedFiles.contains( file.toLowerCase() );
  }

  public boolean isNonexist( String file ) {
    return nonexistingFiles.contains( file.toLowerCase() );
  }

  public boolean isCheckedout( String file ) {
    return checkoutFiles.contains( file.toLowerCase() );
  }

  private class MultipleStatusListener extends VssOutputCollector
  {
    public MultipleStatusListener( List<VcsException> errors )
    {
      super( errors );
    }

    public void everythingFinishedImpl( final String output )
    {
      String lastVssFolder = null;
      String[] lines = LineTokenizer.tokenize( output, false );
      VSSExecUtil.LOG.info( "--- MultipleStatus --- " );
      VSSExecUtil.LOG.info( "\n" + output );
      VSSExecUtil.LOG.info( "--- MultipleStatus end --- " );

      for (int i = 0; i < lines.length; i++)
      {
        String line = lines[ i ];

        // $/longpathupdateproblem/src/com/idea/mytest/ratherLongPackageName/firstSubPacka
        // ge/secondSubpackage/TestNameToTestVSS:
        // Rttt.java           Lloix         Exc   4-23-07  4:18p  D:\Projects\Test
        // Projects\p2_renamed_project\src\com\idea\mytest\ratherLongPackageName\firstSubP
        // ackage\secondSubpackage\TestNameToTestVSS

        // $/longpathupdateproblem/src/first/second/third/forth/fifth/sixth/seventh/eith/n
        // inth/tenth/ratherLongPackage/isntitlongenough/pp1:
        // T2.java             Lloix         Exc   4-23-07  4:18p  D:\Projects\Test
        // Projects\p2_renamed_project\src\first\second\third\forth\fifth\sixth\seventh\ei
        // th\ninth\tenth\ratherLongPackage\isntitlongenough\pp1
        // T3.java             Lloix         Exc   4-23-07  4:18p  D:\Projects\Test
        // Projects\p2_renamed_project\src\first\second\third\forth\fifth\sixth\seventh\ei
        // th\ninth\tenth\ratherLongPackage\isntitlongenough\pp1
         if( line.startsWith( "$" ) )
        {
          int index_1 = line.indexOf(DELETED_MESSAGE);
          int index_2 = line.indexOf(NOT_EXISTING_MESSAGE);

          if (index_1 != -1) {
            String file = line.substring(0, index_1).trim();
            file = VssUtil.getLocalPath(file, myProject);
            file = VssUtil.getCanonicalLocalPath(file);
            deletedFiles.add(file.toLowerCase());
          }
          else
          if (index_2 != -1)
          {
            String file = line.substring(0, index_2).trim();
            file = VssUtil.getLocalPath(file, myProject);
            file = VssUtil.getCanonicalLocalPath(file);
            nonexistingFiles.add(file.toLowerCase());
          }
          else
          if( StringUtil.endsWithChar(line, ':'))
          {
            lastVssFolder = line.substring(0, line.length() - 1);
          }
          else
          if( i < lines.length - 1 && !lines[ i + 1 ].startsWith( "$" ) &&
              StringUtil.endsWithChar( lines[ i + 1 ], ':' ) )
          {
            lastVssFolder = line + lines[ i + 1 ].substring( 0, lines[ i + 1 ].length() - 1 );
            i++;
          }
        }
        else
        {
          if( /*lastVssFolder != null &&*/ line.length() > 20 && line.charAt(19) == ' ')
          {
            String file = line.substring(0, 19).trim();
            boolean mayBeTruncated = line.charAt(18) != ' ';

            //  MegaShit!!!
            //  Sometimes (e.g. when the name of a user is large enough, like
            //  Alexander.Chernikov) the information on checked out file is given
            //  WITHOUT the preceeding vss subproject path. In such case, just
            //  guess on the file given the list of files that we gave to the Status
            //  command as input.
            if( lastVssFolder == null )
            {
              file = guessOnFilePath( file, mayBeTruncated );
            }
            else
            {
              if( mayBeTruncated )
                file = guessOnFileName( file );

              file = lastVssFolder + "/" + file;
              file = VssUtil.getLocalPath( file, myProject );
            }

            //  If the VSS->local path conversion failed, simply do noting
            if( file != null )
            {
              file = VssUtil.getCanonicalLocalPath( file );
              checkoutFiles.add( file.toLowerCase() );
            }
          }
        }
      }
    }

    private String guessOnFileName( final String file )
    {
      String normalizedName = file.toLowerCase();
      String newFile = file;
      for( String f : files )
      {
        File ioFile = new File( f );
        if( ioFile.getName().toLowerCase().startsWith( normalizedName ) )
        {
          newFile = ioFile.getName(); 
        }
      }
      return newFile;
    }
  }

  private String guessOnFilePath( final String file, boolean isTruncated )
  {
    String normalizedName = file.toLowerCase();
    String newFile = file;

    for( String f : files )
    {
      File ioFile = new File( f );
      String fileName = ioFile.getName().toLowerCase();
      if(  isTruncated && fileName.startsWith( normalizedName ) ||
          !isTruncated && fileName.equals( normalizedName ) )
      {
        newFile = ioFile.getPath();
      }
    }
    return newFile;
  }

  /**
   * Use this listener to catch messages from "Status" VSS command over the
   * single file.
   */
  private class SingleStatusListener extends VssOutputCollector
  {
    @NonNls private static final String NOFILES_SIG = "No checked out files found";
    @NonNls private static final String NOFILES_BY_USER_SIG = "No files found checked out by";

    public SingleStatusListener( List<VcsException> errors )
    {
      super( errors );
    }

    public void everythingFinishedImpl(final String output)
    {
      String file = files.get( 0 ).toLowerCase();
      if( output.indexOf( DELETED_MESSAGE ) != -1 )
      {
        deletedFiles.add( file );
      }
      else if( output.indexOf( NOT_EXISTING_MESSAGE ) != -1 )
      {
        nonexistingFiles.add( file );
      }
      else
      if( VssUtil.EXIT_CODE_FAILURE != getExitCode() )
      {
        String[] lines = LineTokenizer.tokenize( output, false );

        //  Fix IDEADEV-22275 - no output from Status command.
        //  Taking seriously - I do not know the reason for this behavior
        //  right now since proper files lead to proper "ss.exe Status" responce,
        //  and all major error messages are processed on the level before.
        if( lines.length > 0 )
        {
          if( (lines[ 0 ].indexOf( NOFILES_SIG ) == -1) &&
              (lines[ 0 ].indexOf( NOFILES_BY_USER_SIG ) == -1) )
          {
            checkoutFiles.add( file );
          }
        }
      }
    }
  }
}
