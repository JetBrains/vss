package com.intellij.vssSupport.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;
import com.intellij.vssSupport.VssVcs;

public class VssGroup extends StandardVcsGroup {
  public AbstractVcs getVcs(Project project) {
    return VssVcs.getInstance(project);
  }
}
