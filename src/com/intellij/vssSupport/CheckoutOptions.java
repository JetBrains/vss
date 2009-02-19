/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
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
public class CheckoutOptions implements JDOMExternalizable, Cloneable
{
  @NonNls public static final String TAG="CheckoutOptions";

  private final VssConfiguration myConfig;
  /**
   * -C
   */
  public String COMMENT="";

  /**
   * -G-
   * Do not perform "Get" operation. We must use this option very carefully since
   * it may be confusing for checking out files with "Do not get local copy" flag.
   * Currently it is by default st to "false" and can be overriden only in multiple
   * checkout operations when the files to be checked out are writable.
   */
  public boolean DO_NOT_GET_LATEST_VERSION;

  /**
   * -GWR
   * This parameter has sense only if <code>doNotGetLatestVersion</code> isn't specified.
   */
  public boolean REPLACE_WRITABLE;

  /**
   * This options presents only in UI and is not included by <code>getOptions</code>
   * method into the options set. The CheckoutDirCommand uses this options to operate.
   */
  public boolean RECURSIVE;
  
  @NonNls private static final String CHECKOUT_COMMAND = "Checkout";
  @NonNls private static final String _C_OPTION = "-C";
  @NonNls private static final String _C__OPTION = "-C-";
  @NonNls private static final String _G__OPTION = "-G-";
  @NonNls private static final String _GWR_OPTION = "-GWR";
  @NonNls private static final String _I_Y_OPTION = "-I-Y";
  @NonNls private static final String _R_OPTION = "-R";
  @NonNls private static final String _R_NOT_OPTION = "-R-";

  public CheckoutOptions(VssConfiguration config){
    myConfig=config;
  }

  public CheckoutOptions getCopy(){
    CheckoutOptions options = null;
    try{ options = (CheckoutOptions) clone(); }
    catch( CloneNotSupportedException ignored ) {}
    return options;
  }

  /**
   * Create command line options for specified file.
   */
  public List<String> getOptions( VirtualFile file )
  {
    ArrayList<String> options=new ArrayList<String>();
    options.add(CHECKOUT_COMMAND);
    options.add(VssUtil.getVssPath(file, myConfig.getProject()));
    // Comments
    if(COMMENT.length()!=0){
      options.add(StringUtil.escapeQuotes(_C_OPTION +COMMENT+""));
    }else{
      options.add(_C__OPTION);
    }

    // "Don't get local copy"
    if( DO_NOT_GET_LATEST_VERSION )
      options.add( _G__OPTION );
    else
    if( REPLACE_WRITABLE )
      options.add( _GWR_OPTION );

    if( file.isDirectory() )
    {
      //  If "Recursive" option is not set this does not mean that we should
      //  simply omit it. If the "Act on projects recursively" option is set
      //  in the SS Explorer, then all commands to the [sub]projects are
      //  applied in the recursive manner by default.
      if( RECURSIVE )
        options.add( _R_OPTION );
      else
        options.add( _R_NOT_OPTION );
    }

    //  Answer "Yes" on all appeared questions.
    //  Questions may arrise if:
    //  1. "File is already checked out" message - it is issued when the file
    //     is already on the HD.
    //  1. "file is already checked out by another user" - this is possible
    //     when the option "Enable Multiple Checkouts" is turned on.
    options.add( _I_Y_OPTION );
    
    // User name/password (if specified)
    if(myConfig.USER_NAME.length()>0){
      options.add(myConfig.getYOption());
    }
    return options;
  }

  public VssConfiguration getVssConfiguration(){
    return myConfig;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public VcsConfiguration getConfiguration() {
    return VcsConfiguration.getInstance(myConfig.getProject());
  }
}
