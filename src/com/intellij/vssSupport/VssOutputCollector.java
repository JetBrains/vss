/**
 * @author Michael Gerasimov
 */
package com.intellij.vssSupport;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;

import java.util.ArrayList;
import java.util.List;

public abstract class VssOutputCollector
{
  protected enum LineType { NO_PROJECT, SIMPLE_FORMAT, WRAPPED_FORMAT }

  private int myExitCode;
  protected final List<VcsException> myErrors;

  public VssOutputCollector( List<VcsException> errors )
  {
    myErrors = (errors == null) ? new ArrayList<VcsException>() : errors;
  }

  public void  setExitCode( int code ) {  myExitCode = code;  }
  public int   getExitCode()           {  return myExitCode;  }

  public final void onCommandCriticalFail( final String message )
  {
    myErrors.add( new VcsException( message ) );
  }

  /**
   * This method is invoked when process succesfully finished.
   * It means the VSS database was found, user name/password pair is correct
   * and user's access rights is also correct. Note, that the method is invoked
   * in event dispath thread.
   * @param output
   */
  public abstract void everythingFinishedImpl(final String output);

  /**
   Two examples of VSS folder sig:

   $/06_Development/mdd/awf/metasrc_gen/de/bertelsmann/awf/container:
   $/06_Development/mdd/awf/metasrc_gen/de/bertelsmann/awf/container/entity:
   AbstractEntityBase.java
   AlterTableGeneralization.java

   and

   $/06_Development/src/de/bertelsmann/container/addrcheck/internal/infoscore/chec
   k:
   IdentityCheckValidator.java
   InfoScoreAddressCheckXMLMarshaller.java

   NB: An assumption (simplistic!) is made that [sub]project line is not spanned
       more than over two lines.
  */
  protected static LineType whatSubProjectLine( final String[] lines, int offset )
  {
    if( StringUtil.startsWithChar( lines[offset], '$') )
    {
      if( StringUtil.endsWithChar( lines[offset], ':' ) )
        return LineType.SIMPLE_FORMAT;

      if( offset + 1 < lines.length &&
          !StringUtil.startsWithChar( lines[offset + 1], '$') &&
          StringUtil.endsWithChar( lines[offset + 1], ':' ))
        return LineType.WRAPPED_FORMAT;
    }
    return LineType.NO_PROJECT;
  }

  /**
   * Attention: in this method we do not perform safety checks - we rely that
   * it is called in conjunction with correct return value of
   * <code>whatSubProjectLine<code> - WRAPPED_FORMAT or SIMPLE_FORMAT.
   */
  protected static String constructLocalFromSubproject( final String[] lines, int offset )
  {
    String localPath = lines[ offset ];
    if( !StringUtil.endsWithChar( lines[offset], ':' ) )
      localPath = lines[ offset ].concat( lines[ offset + 1 ] );

    localPath = localPath.substring( 0, localPath.length() - 1 );
    return localPath;
  }
}