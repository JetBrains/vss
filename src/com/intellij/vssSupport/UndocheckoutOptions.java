/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class UndocheckoutOptions implements JDOMExternalizable, Cloneable
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.vssSupport.UndocheckoutOptions");

  /**
   * XML tag.
   */
  @NonNls public static final String TAG = "UndocheckoutOptions";
  /**
   * Ask for replace when local copy isn't compared with repository version.
   */
  public static final int OPTION_ASK = 0;
  /**
   * Do not replace local copy.
   */
  public static final int OPTION_LEAVE = 1;
  /**
   * Local copy should be replaced with repository version.
   */
  public static final int OPTION_REPLACE = 2;

  /**
   * -W
   */
  public boolean MAKE_WRITABLE;

  /**
   * @see com.intellij.vssSupport.UndocheckoutOptions#OPTION_ASK
   * @see com.intellij.vssSupport.UndocheckoutOptions#OPTION_LEAVE
   * @see com.intellij.vssSupport.UndocheckoutOptions#OPTION_REPLACE
   */
  public int REPLACE_LOCAL_COPY=OPTION_ASK;

  /**
   * -R
   * This options presents only in UI and is not included by <code>getOptions</code>
   * method into the options set. The UndocheckoutDirCommand uses this options to operate.
   */
  public boolean RECURSIVE;

  private VssConfiguration myConfig;

  @NonNls private static final String UNDOCHECKOUT_COMMAND = "Undocheckout";
  @NonNls private static final String _I_N_OPTION = "-I-N";
  @NonNls private static final String _G_OPTION = "-G-";
  @NonNls private static final String _I_Y_OPTION = "-I-Y";
  @NonNls private static final String _W_OPTION = "-W";
  @NonNls private static final String _R_OPTION = "-R";
  @NonNls private static final String _R_NOT_OPTION = "-R-";

  public UndocheckoutOptions(VssConfiguration config){
    myConfig = config;
  }

  public UndocheckoutOptions copy(){
    UndocheckoutOptions options = null;
    try {  options=(UndocheckoutOptions)clone();  }
    catch( CloneNotSupportedException ignored ){}
    return options;
  }

  public List<String> getOptions( VirtualFile file )
  {
    ArrayList<String> options = new ArrayList<String>();
    options.add( UNDOCHECKOUT_COMMAND );
    options.add( VssUtil.getVssPath( file, myConfig.getProject() ));

    if( OPTION_ASK == REPLACE_LOCAL_COPY ){
      options.add(_I_N_OPTION);
    }else if( OPTION_LEAVE == REPLACE_LOCAL_COPY ){
      options.add(_G_OPTION);
      options.add(_I_Y_OPTION);
    }else if( OPTION_REPLACE == REPLACE_LOCAL_COPY ){
      options.add(_I_Y_OPTION);
    }else{
      LOG.error("Unknown REPLACE_LOCAL_COPY value: "+REPLACE_LOCAL_COPY);
    }

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

    if( MAKE_WRITABLE )
      options.add( _W_OPTION );

    if(myConfig.USER_NAME.length()>0){
      options.add(myConfig.getYOption());
    }
    return options;
  }

  public VssConfiguration getVssConfiguration() {  return myConfig;  }


  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
