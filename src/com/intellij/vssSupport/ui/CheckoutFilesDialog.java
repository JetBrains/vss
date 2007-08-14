/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.ui;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vssSupport.CheckoutOptions;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssConfiguration;
import com.intellij.vssSupport.VssVcs;

import javax.swing.*;
import java.awt.*;

public class CheckoutFilesDialog extends OptionsDialog
{
  private CheckoutOptions options;

  // UI controls.
  private JTextArea myTextAreaComment;

  public CheckoutFilesDialog(Project project)
  {
    super( project );
    options = VssConfiguration.getInstance( project ).getCheckoutOptions();
    init();
  }

  protected boolean isToBeShown() {  return VssVcs.getInstance(myProject).getCheckoutOptions().getValue();  }
  protected void setToBeShown( boolean value, boolean onOk ) {  VssVcs.getInstance(myProject).getCheckoutOptions().setValue( value );  }

  protected String getDimensionServiceKey() {  return "#com.intellij.vssSupport.ui.CheckoutFilesDialog";  }
  public JComponent getPreferredFocusedComponent(){  return myTextAreaComment;  }
  protected boolean shouldSaveOptionsOnCancel()  {   return false;   }

  protected void doOKAction()
  {
    options.COMMENT = myTextAreaComment.getText().trim();
    options.DO_NOT_GET_LATEST_VERSION = false;

    super.doOKAction();
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel( new GridBagLayout() );

    JLabel lblComment = new JLabel(VssBundle.message("label.options.comment"));
    lblComment.setLabelFor(myTextAreaComment);
    panel.add( lblComment, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,3,5),0,0) );

    JScrollPane scrollPane = new JScrollPane(myTextAreaComment);
    scrollPane.setPreferredSize(new Dimension(250,70));
    panel.add( scrollPane, new GridBagConstraints(0,1,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,5,0),0,0) );

    return panel;
  }

  protected void init()
  {
    myTextAreaComment = new JTextArea();
    myTextAreaComment.setText( options.COMMENT );
    super.init();
  }
}
