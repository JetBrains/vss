package com.intellij.vssSupport.commands;

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 31, 2006
 */
public class HistoryParser
{
  @NonNls private static final String FILE_SECTION_SIG = "*****";
  @NonNls private static final String VERSION_SECTION_PREFIX_SIG = "*****************  Version ";
  @NonNls private static final String VERSION_SECTION_SUFFIX_SIG = "*****************";
  @NonNls private static final String FOLDER_LABEL_SECTION_PREFIX_SIG = "**********************";
  @NonNls private static final String CHECKED_IN_SIG = "Checked in ";
  @NonNls private static final String FILE_ADDED_SIG = " added";
  @NonNls private static final String FILE_CREATED_SIG = "Created";
  @NonNls private static final String FILE_LABELLED_SIG = "Labeled";
  @NonNls private static final String FILE_DELETED_SIG = " deleted";
  @NonNls private static final String FILE_PURGED_SIG = " purged";
  @NonNls private static final String FILE_RECOVERED_SIG = " recovered";
  @NonNls private static final String FILE_DESTROYED_SIG = " destroyed";

  @NonNls private static final String VERSION_SIG = "Version ";
  @NonNls private static final String USER_SIG = "User: ";
  @NonNls private static final String DATE_SIG = "Date: ";
  @NonNls private static final String TIME_SIG = "Time: ";
  @NonNls private static final String COMMENT_SIG = "Comment: ";
  @NonNls private static final String LABEL_COMMENT_SIG = "Label comment: ";
  @NonNls private static final String LABEL_SIG = "Label: ";

  private HistoryParser() {}

  public static class SubmissionData
  {
    public String action;
    public String version;
    public String submitter;
    public String changeDate;
    public String comment;
    public String label;
    public int    order;
  }

  public static ArrayList<SubmissionData> parse( final String content )
  {
    ArrayList<SubmissionData> changes = new ArrayList<SubmissionData>();
    String[] lines = LineTokenizer.tokenize( content, false );
    int      order = 0;

    for( int i = 0; i < lines.length; i++ )
    {
      //  For folder-wide operations support only labelling
      
      if( lines[ i ].indexOf( FOLDER_LABEL_SECTION_PREFIX_SIG ) != -1 )
      {
        //  !**********************
        //  !Label: "Label with..."
        //  !User: Lloix        Date:  2-05-07   Time:  5:01p
        //  !Labeled
        //  !Label comment: ...Some comment
        if( i + 4 < lines.length && lines[ i + 3 ].indexOf( FILE_LABELLED_SIG ) != -1 )
        {
          SubmissionData change = new SubmissionData();
          change.order = order++;
          change.submitter = parseActor( lines[ i + 2 ] );
          change.changeDate = parseDateTime( lines[ i + 2 ] );
          change.action = FILE_LABELLED_SIG;
          change.label = parseLabel( lines[ i + 1 ] );
          change.comment = parseComment( lines, i + 4 );

          changes.add( change );
        }
      }
      else
      if( lines[ i ].indexOf( VERSION_SECTION_PREFIX_SIG ) != -1 )
      {
        // !*****************  Version 6   *****************
        // !User: Lloix        Date: 13-11-06   Time:  5:52p
        // !Checked in $/VssTest/SRC/Dir1
        // !Comment: New comment
        //    or
        // !*****************  Version 6   *****************
        // !Label: <label>
        // !User: Lloix        Date: 13-11-06   Time:  5:52p
        // !Labelled
        // !Label Comment: New comment
        if( i + 3 < lines.length )
        {
          SubmissionData change = new SubmissionData();
          change.version = parseVersion( lines[ i ] );
          change.order = order++;

          if( lines[ i + 1 ].indexOf( LABEL_SIG ) != -1 )
          {
            change.label = parseLabel( lines[ i + 1 ] );
            i++;
          }
          change.submitter = parseActor( lines[ i + 1 ] );
          change.changeDate = parseDateTime( lines[ i + 1 ] );
          change.action = parseAction( lines[ i + 2 ] );
          change.comment = parseComment( lines, i + 3 );

          changes.add( change );
        }
      }
      else
      if( lines[ i ].indexOf( FILE_SECTION_SIG ) != - 1 )
      {
        // !*****  File1.java  *****
        // !Version 6
        // !User: Lloix        Date: 13-11-06   Time:  5:52p
        // !Checked in $/VssTest/SRC/Dir1
        // !Comment: New comment
        if( i + 3 < lines.length )
        {
          SubmissionData change = new SubmissionData(); 
          change.order = order++;
          change.submitter = parseActor( lines[ i + 2 ] );
          change.version = lines[ i + 1 ].substring( VERSION_SIG.length() );
          change.changeDate = parseDateTime( lines[ i + 2 ] );
          change.action = parseAction( lines[ i + 3 ] );
          change.comment = parseComment( lines, i + 4 );

          changes.add( change );
        }
      }
    }

    //  Changes for "Labeled" operation are given without version id (since
    //  they do not the version given on the previous checkin operation).
    //  Assign version ids for these operations in the backward order.
    updateEarlierChanges( changes );
    return changes;
  }

  private static String parseActor( String line )
  {
    int indexOfDate = line.indexOf( DATE_SIG );
    return line.substring( 0, indexOfDate - 1 ).replace( USER_SIG, "" ).trim();
  }

  @Nullable
  private static String parseDateTime( String line )
  {
    String date = null;
    int indexOfDate = line.indexOf( DATE_SIG );
    int indexOfTime = line.indexOf( TIME_SIG );
    if( indexOfDate != -1 && indexOfTime != -1 )
    {
      date = line.substring( indexOfDate + DATE_SIG.length(), indexOfTime ).trim();
      date = date + " " + line.substring( indexOfTime + TIME_SIG.length() ).trim();
    }
    return date;
  }

  private static String parseVersion( String line )
  {
    line = line.substring( VERSION_SECTION_PREFIX_SIG.length() );
    line = line.substring( 0, line.length() - VERSION_SECTION_SUFFIX_SIG.length() ).trim();
    return line;
  }

  private static String parseLabel( final String line )
  {
    String label = null;
    int index = line.indexOf( LABEL_SIG );
    if( index >= 0 )
    {
      label = line.substring( index + LABEL_SIG.length() );
    }
    return label;
  }

  private static String parseComment( final String[] lines, int startIndex )
  {
    StringBuffer comment = new StringBuffer();
    int i = startIndex;
    if( i < lines.length &&
        ( lines[ i ].startsWith( COMMENT_SIG ) || lines[ i ].startsWith( LABEL_COMMENT_SIG ) ))
    {
      if( lines[ i ].startsWith( COMMENT_SIG ) )
        lines[ i ] = lines[ i ].substring( COMMENT_SIG.length() );
      else
        lines[ i ] = lines[ i ].substring( LABEL_COMMENT_SIG.length() );
      
      while( i < lines.length && lines[ i ].length() > 0 )
      {
        if( comment.length() > 0 )
          comment.append( "\n" );
        comment.append( lines[ i ] );
        i++;
      }
    }
    return comment.toString();
  }
  
  private static String parseAction( String line )
  {
    String action = "Unknown";

    if( line.indexOf( FILE_CREATED_SIG ) != -1 )
    {
      action = FILE_CREATED_SIG;
    }
    else
    if( line.indexOf( CHECKED_IN_SIG ) != -1 )
    {
      action = CHECKED_IN_SIG;
    }
    else
    if( line.endsWith( FILE_ADDED_SIG ) )
    {
      action = "Add";
    }
    else
    if( line.endsWith( FILE_LABELLED_SIG ) )
    {
      action = "Label";
    }
    else
    if( line.endsWith( FILE_DELETED_SIG ) || line.endsWith( FILE_PURGED_SIG ) ||
        line.endsWith( FILE_DESTROYED_SIG ))
    {
      action = "Deleted";
    }
    else
    if( line.endsWith( FILE_RECOVERED_SIG ) )
    {
      action = "Recovered";
    }
    return action;
  }

  private static void updateEarlierChanges( ArrayList<SubmissionData> changes )
  {
    String lastVersion = "0";
    for( int i = changes.size() - 1; i >= 0; i-- )
    {
      SubmissionData ch = changes.get( i );
      if( ch.version == null )
        ch.version = lastVersion;
      else
        lastVersion = ch.version;
    }
  }
}
