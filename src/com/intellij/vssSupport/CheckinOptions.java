/**
 * @author Vladimir Kondratyev
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
public class CheckinOptions implements JDOMExternalizable
{
  @NonNls public static final String TAG="CheckinOptions";

  @NonNls private static final String CHECKIN_COMMAND = "Checkin";
  @NonNls private static final String _C_OPTION = "-C";
  @NonNls private static final String _C__OPTION = "-C-";
  @NonNls private static final String _K_OPTION = "-K";
  @NonNls private static final String _K__OPTION = "-K-";
  @NonNls private static final String _I_Y_OPTION = "-I-Y";
  @NonNls private static final String _I_N_OPTION = "-I-N";

  private final VssConfiguration myConfig;
  /**
   * -C
   */
  public String COMMENT="";
  /**
   * -K or -K-
   */
  public boolean KEEP_CHECKED_OUT;
  /**
   * -I-Y or -I-N
   *
   * This is default answer on all questions.
   * If it's <code>null</code> then it means that the answer is undefined
   * and it should not be included into the set of command's optioon.
   */
  public Boolean defaultAnswer;
  /**
   * This options presents only in UI and is not included by <code>getOptions</code>
   * method into the options set. The CheckinDirCommand uses this options to operate.
   */
  public boolean RECURSIVE;

  public CheckinOptions(VssConfiguration config){
    myConfig=config;
  }

  public List<String> getOptions(VirtualFile virtualFile){
    ArrayList<String> options= new ArrayList<>();
    options.add(CHECKIN_COMMAND);
    String vssPath = VssUtil.getVssPath(virtualFile, myConfig.getProject());
    if (vssPath != null)
      options.add(vssPath);
    // Comments
    if(COMMENT.length()!=0){
      options.add(StringUtil.escapeQuotes(_C_OPTION +COMMENT+""));
    }else{
      options.add(_C__OPTION);
    }
    // Keep checked out
    if(KEEP_CHECKED_OUT){
      options.add(_K_OPTION);
    }else{
      options.add(_K__OPTION);
    }
    // Default answer.
    if(defaultAnswer!=null){
      options.add(defaultAnswer.booleanValue()?_I_Y_OPTION :_I_N_OPTION);
    }
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
}
