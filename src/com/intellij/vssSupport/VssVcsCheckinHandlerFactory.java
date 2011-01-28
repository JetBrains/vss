package com.intellij.vssSupport;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.vssSupport.Checkin.VssCheckinHandler;
import org.jetbrains.annotations.NotNull;

/**
* @author irengrig
*         Date: 1/28/11
*         Time: 6:09 PM
*/
public class VssVcsCheckinHandlerFactory extends VcsCheckinHandlerFactory {
  public VssVcsCheckinHandlerFactory() {
    super(VssVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(CheckinProjectPanel panel) {
    return new VssCheckinHandler( VssVcs.getInstance(panel.getProject()), panel );
  }
}
