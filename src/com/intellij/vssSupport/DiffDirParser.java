package com.intellij.vssSupport;

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;

public class DiffDirParser
{
  @NonNls private static final String DIFF_GROUP_PREFIX = "Diffing: ";
  @NonNls private static final String DIFF_LOCAL_PATH_PREFIX = "Against: ";
  @NonNls private static final String LOCALFILES_SIG = "Local files not in the current project";
  @NonNls private static final String VSSFILES_SIG = "SourceSafe files not in the current folder";
  @NonNls private static final String FILE_DIFFERENT_SIG = "SourceSafe files different from";

  public static HashSet<String> filesNew = new HashSet<>();
  public static HashSet<String> filesDeleted = new HashSet<>();
  public static HashSet<String> filesChanged = new HashSet<>();

  public static void parse( String log )
  {
    filesNew.clear();
    filesDeleted.clear();
    filesChanged.clear();

    String[] parts = log.split( DIFF_GROUP_PREFIX );
    for( String part : parts )
    {
      if( part.trim().length() > 0 )
        processLogPart( part );
    }
  }

  private static void processLogPart( String out )
  {
    //-------------------------------------------------------------------------
    //  First two lines of the subproject difference part are:
    //  |Diffing: $/VssSubprojectPath
    //  |Against: LocalPath
    //
    //  We need to extract local path in order to merge it with parsed file
    //  names to produce fully readable complete local path. After trimming
    //  "Diffing" part, first line is always an "Against" tag.
    //-------------------------------------------------------------------------

    int index = out.lastIndexOf( DIFF_LOCAL_PATH_PREFIX );
    if( index == -1 )
      return;
    
    out = out.substring( index );
    String   basePath = extractBasePath( out );

    //-------------------------------------------------------------------------
    //  We process sections in reversed order : so find the beginning of the
    //  section describing files that differ from that in the VssProject, then
    //  section for files which are in the repository and not in local storage
    //  and then files which are presented locall but not in the repository.
    //-------------------------------------------------------------------------
    index = out.lastIndexOf( FILE_DIFFERENT_SIG );
    if( index > 0 )
    {
      String diffPart = out.substring( index );
      processDiffingPart( diffPart, basePath );
      out = out.substring( 0, index - 1 );
    }
    index = out.lastIndexOf( VSSFILES_SIG );
    if( index > 0 )
    {
      String vssPart = out.substring( index );
      processRepositoryPart( vssPart, basePath );
      out = out.substring( 0, index - 1 );
    }

    index = out.lastIndexOf( LOCALFILES_SIG );
    if( index > 0 )
    {
      String localPart = out.substring( index );
      processLocals( localPart, basePath );
    }
  }

  private static void processLocals( String part, String basePath )
  {
    String[] lines = LineTokenizer.tokenize( part.toCharArray(), false );
    addToList( lines, basePath, filesNew );
  }

  private static void processRepositoryPart( String part, String basePath )
  {
    String[] lines = part.split( "\r\n");
    addToList( lines, basePath, filesDeleted );
  }

  private static void addToList( String[] lines, String basePath, HashSet<String> list )
  {
    for( int i = 1; i < lines.length; i++ )
    {
      //  Avoid separator lines which delimit different sections or
      //  mark the end of all lists.
      if( lines[ i ].trim().length() == 0 )
        break;

      addList( lines[ i ], basePath, list );
    }
  }

  private static void processDiffingPart( String diffPart, String basePath )
  {
    String[] lines = diffPart.split( "\r\n" );
    for( int i = 1; i < lines.length; i++ )
    {
      //  Avoid separator lines which delimit different sections or
      //  mark the end of all lists.
      if( lines[ i ].trim().length() == 0 )
        break;

      String file = basePath.concat( "\\" ).concat( lines[ i ].trim() );
      filesChanged.add( file );
    }
  }

  private static String  extractBasePath( String line )
  {
    int index = line.indexOf( "\r\n" );
    if( index == -1 ) index = line.indexOf( "\n" );
    line = line.substring( DIFF_LOCAL_PATH_PREFIX.length(), index );
    return line;
  }

  private static void addList( String line, String basePath, HashSet<String> dest )
  {
    String[] files = line.split( "  " );
    for( String str : files )
    {
      str = str.trim();
      if( str.length() > 0 )
      {
        dest.add( basePath.concat( "\\" ).concat( str ) );
      }
    }
  }
}
