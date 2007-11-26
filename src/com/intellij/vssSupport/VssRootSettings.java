package com.intellij.vssSupport;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsRootSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 16, 2007
 */
public class VssRootSettings implements VcsRootSettings
{
  @NonNls private final static String PROJECT_TAG = "VssProject";

  private String vssProject;

  public VssRootSettings() {}

  public VssRootSettings(final String project) {  vssProject = project;  }

  public String getVssProject() {  return vssProject;  }

  public void setVssProject(final String vssProject) {  this.vssProject = vssProject;  }

  public void readExternal(Element element) throws InvalidDataException
  {
    vssProject = element.getAttributeValue( PROJECT_TAG );
    if( vssProject.length() == 0 )
      vssProject = null;
  }

  public void writeExternal(Element element) throws WriteExternalException
  {
    element.setAttribute( PROJECT_TAG, (vssProject != null) ? vssProject : "" );
  }

  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    final VssRootSettings that = (VssRootSettings)o;
    return (vssProject == null && that.vssProject == null ) ||
           (vssProject != null && vssProject.equals( that.vssProject ) );
  }

  public int hashCode() {  return vssProject.hashCode();  }
}
