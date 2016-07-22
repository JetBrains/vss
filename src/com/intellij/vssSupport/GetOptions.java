/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Describes set of operation's options. Consider this class just as the
 * structure.
 */
public class GetOptions implements JDOMExternalizable, Cloneable
{
  /**
   * XML tag.
   */
  @NonNls public static final String TAG = "GetOptions";
  /**
   * Ask confirmation when get writable files.
   */
  public static final int OPTION_ASK     = 0;
  /**
   * Perlace writable files.
   */
  public static final int OPTION_REPLACE = 1;
  /**
   * Do not get writable files.
   */
  public static final int OPTION_SKIP    = 2;

  private final VssConfiguration myConfig;
  /**
   * -GWR
   */
  public int REPLACE_WRITABLE = OPTION_ASK;
  /**
   * -W
   */
  public boolean MAKE_WRITABLE;
  /**
   * -I-N
   */
  public boolean ANSWER_NEGATIVELY;
  /**
   * -I-Y
   */
  public boolean ANSWER_POSITIVELY;
  /**
   * -R
   */
  public boolean RECURSIVE;
  /**
   * -V
   */
  public String VERSION;

  @NonNls private static final String GET_COMMAND = "Get";
  @NonNls private static final String _GWS_OPTION = "-GWS";
  @NonNls private static final String _GWR_OPTION = "-GWR";
  @NonNls private static final String _W_OPTION = "-W";
  @NonNls private static final String _I_Y_OPTION = "-I-Y";
  @NonNls private static final String _I_N_OPTION = "-I-N";
  @NonNls private static final String _R_OPTION = "-R";
  @NonNls private static final String _R_NOT_OPTION = "-R-";
  @NonNls private static final String _V_OPTION = "-V";

  public GetOptions(VssConfiguration config){
    myConfig=config;
  }

  public GetOptions getCopy(){
    GetOptions options=null;
    try{
      options=(GetOptions)clone();
    }catch(CloneNotSupportedException ignored){}
    return options;
  }

  public VssConfiguration getVssConfiguration(){  return myConfig;  }

  /**
   * Create command line options for specified file.
   */
  public List<String> getOptions( File file )
  {
    Project project = myConfig.getProject();
    ArrayList<String> options = new ArrayList<>();
    options.add( GET_COMMAND );

    if( file.isDirectory() )
    {
      //  If "Recursive" option is not set this does not mean that we should
      //  simply omit it. If the "Act on projects recursively" option is set
      //  in the SS Explorer, then all commands to the [sub]projects are
      //  applied in the recursive manner by default.
      options.add( RECURSIVE ? _R_OPTION : _R_NOT_OPTION );
    }

    options.add( VssUtil.getVssPath( file, project ));
    
    if( VERSION != null )
      options.add( _V_OPTION + VERSION.trim() );

    if( REPLACE_WRITABLE == GetOptions.OPTION_REPLACE )
      options.add( _GWR_OPTION );
    else
    if( REPLACE_WRITABLE == GetOptions.OPTION_SKIP )
      options.add(_GWS_OPTION);

    if( MAKE_WRITABLE )
      options.add( _W_OPTION );

    if( ANSWER_POSITIVELY )
      options.add( _I_Y_OPTION );
    if( ANSWER_NEGATIVELY )
      options.add( _I_N_OPTION );

    if( myConfig.USER_NAME.length() > 0 )
      options.add( myConfig.getYOption() );

    return options;
  }

  public List<String> getOptions( VirtualFile file )
  {
    return getOptions( new File( file.getPath().replace('/',File.separatorChar )) );
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
