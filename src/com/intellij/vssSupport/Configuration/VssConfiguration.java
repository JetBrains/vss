/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.vssSupport.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class VssConfiguration implements ProjectComponent, JDOMExternalizable
{
  @NonNls private static final String SSEXP_FILE_NAME = "ssexp.exe";
  @NonNls private static final String SSDIR_PARAM_NAME = "SSDIR";
  @NonNls private static final String _Y_OPTION = "-Y";
  @NonNls private static final String MAP_ITEM_ELEMENT_NAME = "MapItem";

  //  All fields are public for JDOM saving mechanism to
  //  work properly. Damn.
  public String CLIENT_PATH = "";
  public String SRCSAFEINI_PATH = "";
  public String USER_NAME = "";
  public String PWD = "";
  public ArrayList myMapItems;

  /*
   * These options are reused by dialogs to "remember" previous option set.
   */
  private final CheckoutOptions myCheckoutOptions;
  private final CheckinOptions myCheckinOptions;
  private final AddOptions myAddOptions;
  private final UndocheckoutOptions myUndocheckoutOptions;
  private final GetOptions myGetOptions;
  private final Project myProject;

  public VssConfiguration( Project project )
  {
    myProject = project;
    myMapItems = new ArrayList();
    myCheckoutOptions = new CheckoutOptions( this );
    myCheckinOptions = new CheckinOptions( this );
    myAddOptions = new AddOptions( this );
    myUndocheckoutOptions = new UndocheckoutOptions( this );
    myGetOptions = new GetOptions( this );
  }

  public void disposeComponent() {}
  public void initComponent() {}
  public void projectOpened() {}
  public void projectClosed() {}

  public Project         getProject()         {  return myProject;          }
  public CheckoutOptions getCheckoutOptions() {  return myCheckoutOptions;  }
  public CheckinOptions  getCheckinOptions()  {  return myCheckinOptions;   }
  public AddOptions      getAddOptions()      {  return myAddOptions;       }
  public GetOptions      getGetOptions()      {  return myGetOptions;       }
  public UndocheckoutOptions getUndocheckoutOptions(){ return myUndocheckoutOptions;  }

  @NotNull
  public String  getComponentName() {  return "VssConfiguration";  }

  /**
   * @return Error message text if path to VSS client is not properly configured.
   * Otherwise the method returns <code>NULL</code>.
   */
  @Nullable
  public String checkCmdPath()
  {
    String message = null;
    File client = new File( CLIENT_PATH );
    if( CLIENT_PATH.length() == 0 )
      message = VssBundle.message( "message.text.specify.path.to.client");
    else if( !client.exists() )
      message = VssBundle.message( "message.text.path.does.not.exist", CLIENT_PATH );
    else if( client.isDirectory() )
      message = VssBundle.message("message.text.path.is.directory", CLIENT_PATH);

    return message;
  }

  /**
   * @return full path to SourceSafe explorer executable. The path is constructed base on
   * config.CLIENT_PATH
   */
  public String getExplorerPath(){
    File client=new File(CLIENT_PATH);
    File clientDir=client.getParentFile();
    String pathToExplorer="";
    if(clientDir!=null){
      try{
        pathToExplorer=clientDir.getCanonicalPath()+File.separatorChar+SSEXP_FILE_NAME;
      }catch(IOException ignored){}
    }
    return pathToExplorer;
  }

  public String getSSDIR(){
    File srcsafeini=new File(SRCSAFEINI_PATH);
    File ssDir=srcsafeini.getParentFile();
    String pathToSSDIR="";
    if(ssDir!=null){
      try{
        pathToSSDIR=ssDir.getCanonicalPath();
      }catch(IOException ignored){}
    }
    return pathToSSDIR;
  }

  public HashMap<String, String> getSSDIREnv(){
    HashMap<String, String> result = new HashMap<String, String>(1);
    result.put(SSDIR_PARAM_NAME, getSSDIR());
    return result;
  }

  /**
   * Create "-Y" option for ss.exe command line tool.
   */
  public String getYOption(){
    return _Y_OPTION + USER_NAME + (getPassword().length() > 0 ? "," + getPassword() : "");
  }

  public static VssConfiguration getInstance(Project project){
    return project.getComponent(VssConfiguration.class);
  }

  /**
   * @return <code>MapItem</code> with the specified index.
   */
  public MapItem getMapItem( int idx ){  return (MapItem) myMapItems.get( idx );  }

  /**
   * @return the number of all available items.
   */
  public int getMapItemCount(){  return myMapItems.size();  }

  public String getPassword() {
    try {  return PasswordUtil.decodePassword(PWD);  }
    catch( Exception e ) {  return "";  }
  }

  public void setPassword(final String PWD) {  this.PWD = PasswordUtil.encodePassword( PWD );  }

  public void readExternal(Element parentNode) throws InvalidDataException
  {
    DefaultJDOMExternalizer.readExternal(this,  parentNode);
    myMapItems.clear();
    for (Iterator iterator = parentNode.getChildren().iterator(); iterator.hasNext();) {
      Element element = (Element)iterator.next();
      String name=element.getName();
      if(MAP_ITEM_ELEMENT_NAME.equals( name )){
        MapItem mapItem=new MapItem("","");
        mapItem.readExternal( element );
        myMapItems.add( mapItem );
      }else if( AddOptions.TAG.equals( name )){
        DefaultJDOMExternalizer.readExternal( myAddOptions, element );
      }else if( CheckoutOptions.TAG.equals( name )){
        DefaultJDOMExternalizer.readExternal( myCheckoutOptions, element );
      }else if( CheckinOptions.TAG.equals( name )){
        DefaultJDOMExternalizer.readExternal( myCheckinOptions, element );
      }else if( UndocheckoutOptions.TAG.equals( name )){
        DefaultJDOMExternalizer.readExternal( myUndocheckoutOptions, element );
      }else if( GetOptions.TAG.equals( name )){
        DefaultJDOMExternalizer.readExternal( myGetOptions, element );
      }
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException
  {
    DefaultJDOMExternalizer.writeExternal(this,  parentNode);

    // Write options.
     Element elem;

    elem = new Element( CheckoutOptions.TAG );
    parentNode.addContent( elem );
    DefaultJDOMExternalizer.writeExternal( myCheckoutOptions, elem );

    elem = new Element( CheckinOptions.TAG );
    parentNode.addContent( elem );
    DefaultJDOMExternalizer.writeExternal( myCheckinOptions, elem );

    elem = new Element( AddOptions.TAG );
    parentNode.addContent( elem );
    DefaultJDOMExternalizer.writeExternal( myAddOptions, elem );

    elem = new Element( UndocheckoutOptions.TAG );
    parentNode.addContent( elem );
    DefaultJDOMExternalizer.writeExternal( myUndocheckoutOptions, elem );

    elem = new Element( GetOptions.TAG );
    parentNode.addContent( elem );
    DefaultJDOMExternalizer.writeExternal( myGetOptions, elem );
  }
}
