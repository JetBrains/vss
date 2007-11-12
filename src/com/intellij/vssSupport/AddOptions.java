/**
 * @author Vladimir Kondratyev
 * @author Michael Gerasimov
 */
package com.intellij.vssSupport;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes set of operation's options. Consider this class just as the
 * structure.
 */
public class AddOptions implements JDOMExternalizable
{
  @NonNls private static final String ADD_COMMAND = "Add";
  @NonNls private static final String _C_OPTION = "-C";
  @NonNls private static final String _C__OPTION = "-C-";
  @NonNls private static final String _D_OPTION = "-D";
  @NonNls private static final String _I__OPTION = "-I-";
  @NonNls private static final String _K_OPTION = "-K";

  @NonNls public static final String TAG = "AddOptions";

  public boolean STORE_ONLY_LATEST_VERSION;
  public boolean CHECK_OUT_IMMEDIATELY;

  private VssConfiguration myConfig;

  public AddOptions( VssConfiguration config )
  {
    myConfig = config;
  }

  /**
   * Creates option set for passed file.
   */
  public List<String> getOptions( VirtualFile virtualFile )
  {
    ArrayList<String> options = new ArrayList<String>();
    options.add( ADD_COMMAND );
    options.add( virtualFile.getName() );

    //-- Comments --
    //  We use unified Checkin dialog for all operations - add new, update
    //  changed etc. Thus for "ADD" operation we inherit the comment usually
    //  stored in the "CHECKIN" operation environment.
    String comment = myConfig.getCheckinOptions().COMMENT;

    if( comment.length() > 0 )
      options.add( StringUtil.escapeQuotes( _C_OPTION + comment ) );
    else
      options.add( _C__OPTION ); // no comments is used

    //-- Store only latest version --
    if( STORE_ONLY_LATEST_VERSION )
      options.add( _D_OPTION );

    //-- Do not ask for input under any circumstances --
    options.add( _I__OPTION );

    //-- Check out immediatly --
    if( CHECK_OUT_IMMEDIATELY )
      options.add( _K_OPTION );

    //-- User name/password (if specified) --
    if( myConfig.USER_NAME.length() > 0 )
      options.add( myConfig.getYOption() );

    return options;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
