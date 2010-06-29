/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vssSupport.CheckoutOptions;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;

import javax.swing.*;
import java.awt.*;

public class CheckoutDirDialog extends OptionsDialog
{
  private final CheckoutOptions options;

  // UI controls.
  private JTextArea myTextAreaComment;
  private JCheckBox myCheckBoxRecursive;

  public CheckoutDirDialog( Project project )
  {
    super(project);
    options = VssConfiguration.getInstance( project ).getCheckoutOptions();
    init();
  }

  protected boolean isToBeShown() {  return VssVcs.getInstance(myProject).getCheckoutOptions().getValue();  }
  protected void setToBeShown( boolean value, boolean onOk ) {  VssVcs.getInstance(myProject).getCheckoutOptions().setValue( value );  }

  protected String getDimensionServiceKey() { return "#com.intellij.vssSupport.ui.CheckoutDirDialog";  }
  public JComponent getPreferredFocusedComponent(){  return myTextAreaComment;  }
  protected boolean shouldSaveOptionsOnCancel()  {   return false;   }

  protected void doOKAction()
  {
    options.COMMENT = myTextAreaComment.getText().trim();
    options.RECURSIVE = myCheckBoxRecursive.isSelected();
    super.doOKAction();
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());

    // "Comment"
    JLabel lblComment = new JLabel(VssBundle.message("label.options.comment"));
    lblComment.setLabelFor( myTextAreaComment );
    panel.add( lblComment, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,3,5),0,0) );

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTextAreaComment);
    scrollPane.setPreferredSize( new Dimension(250,70) );
    panel.add( scrollPane, new GridBagConstraints(0,1,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,5,0),0,0) );

    // "Recursive"
    panel.add( myCheckBoxRecursive, new GridBagConstraints(0,2,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,5,0),0,0) );

    return panel;
  }

  protected void init()
  {
    myTextAreaComment = new JTextArea();
    myCheckBoxRecursive = new JCheckBox(VssBundle.message("checkbox.checkout.dir.options.recursive"));
    myTextAreaComment.setText( options.COMMENT );
    myCheckBoxRecursive.setSelected( options.RECURSIVE );

    super.init();
  }
}
