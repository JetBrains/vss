/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.vssSupport.VssUtil;
import org.jdom.Element;

public class MapItem implements JDOMExternalizable{
  /**
   * Path to VSS project/item. This path doesn't contains any slashes at the end, excepting the
   * root project ({@code $/}). It means that </code>$/myProject1/</code> is illegal path
   * and {@code $/myProject1} is the legal one.
   */
  public String VSS_PATH="";
  /**
   * Path in local file system. It doesn't contains any slashes ant the end, excepting
   * local disk roots: {@code C:\}, etc. This path has UNIX separator chars.
   */
  public String LOCAL_PATH="";

  public MapItem(String vssPath,String localPath){
    VSS_PATH=vssPath;
    LOCAL_PATH=localPath;
  }

  /**
   * Tests the passed object on deep equality.
   */
  public boolean equals(Object obj){
    if(!(obj instanceof MapItem)){
      return false;
    }
    MapItem mapItem=(MapItem)obj;
      return VSS_PATH.equals(mapItem.VSS_PATH) && LOCAL_PATH.equals(mapItem.LOCAL_PATH);
  }

  public int hashCode(){
    return VSS_PATH.hashCode() + LOCAL_PATH.hashCode();
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    VSS_PATH= VssUtil.getCanonicalVssPath(VSS_PATH);
    LOCAL_PATH=VssUtil.getCanonicalVssPath(LOCAL_PATH);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
