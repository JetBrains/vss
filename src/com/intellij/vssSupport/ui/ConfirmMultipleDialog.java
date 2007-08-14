/**
 * @author  Vladimir Kondratyev
 */

package com.intellij.vssSupport.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ConfirmMultipleDialog extends DialogWrapper
{
  public static final int YES_EXIT_CODE = 0;
  public static final int YES_ALL_EXIT_CODE = 1;
  public static final int NO_EXIT_CODE = 2;
  public static final int NO_ALL_EXIT_CODE = 3;
  public static final int CANCEL_OPTION = 4;

  private String myText;

  public ConfirmMultipleDialog(String title,String text, Project project){
    super(project, false);
    setTitle(title);
    myText=text;
    setButtonsAlignment(SwingUtilities.CENTER);
    init();
  }

  protected Action[] createActions(){
    return new Action[]{
      new MyAction(CommonBundle.getYesButtonText(),YES_EXIT_CODE),
      new MyAction(CommonBundle.getYesForAllButtonText(),YES_ALL_EXIT_CODE),
      new MyAction(CommonBundle.getNoButtonText(),NO_EXIT_CODE),
      new MyAction(CommonBundle.getNoForAllButtonText(),NO_ALL_EXIT_CODE),
      new MyAction(CommonBundle.getCancelButtonText(),CANCEL_OPTION),
    };
  }

  public void doCancelAction() {
    close(CANCEL_OPTION);
  }

  protected JComponent createCenterPanel(){
    JPanel panel=new JPanel(new GridBagLayout());
    JLabel label = new JLabel(myText);
    label.setUI(new MultiLineLabelUI());
    panel.add(
      label,
      new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(10,10,15,10),0,0)
    );
    Icon icon = UIUtil.getQuestionIcon();
    if (icon != null) {
      label.setIcon(icon);
      label.setIconTextGap(7);
    }
    return panel;
  }

  private class MyAction extends AbstractAction{
    private int myExitCode;

    public MyAction(String text,int exitCode){
      super(text);
      myExitCode=exitCode;
    }

    public void actionPerformed(ActionEvent e){
      close(myExitCode);
    }
  }

}
