/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.ui;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.GetOptions;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;

import javax.swing.*;
import java.awt.*;

public class GetFilesDialog extends OptionsDialog
{
  private final GetOptions options;

  // UI controls.
  private JCheckBox myCheckBoxMakeWritable;
  private JComboBox myComboBoxReplaceWritable;

  public GetFilesDialog(Project project)
  {
    super( project );
    options = VssConfiguration.getInstance( project ).getGetOptions();
    init();
  }

  protected boolean isToBeShown() {  return VssVcs.getInstance( myProject ).getGetOptions().getValue();  }
  protected void setToBeShown( boolean value, boolean onOk ) {  VssVcs.getInstance( myProject ).getGetOptions().setValue( value );  }
  protected boolean shouldSaveOptionsOnCancel()  {   return false;   }

  /**
   * Stores edited data into the passed data holder.
   */
  protected void doOKAction()
  {
    options.MAKE_WRITABLE = myCheckBoxMakeWritable.isSelected();
    int idx = myComboBoxReplaceWritable.getSelectedIndex();
    if( idx == 0 )
      options.REPLACE_WRITABLE=GetOptions.OPTION_ASK;
    else if( idx == 1 )
      options.REPLACE_WRITABLE=GetOptions.OPTION_REPLACE;
    else if( idx == 2 )
      options.REPLACE_WRITABLE=GetOptions.OPTION_SKIP;

    super.doOKAction();
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel( new GridBagLayout() );

    // "Replace writable"

    JLabel label=new JLabel(VssBundle.message("label.get.options.replace.writable"));
    label.setLabelFor(myComboBoxReplaceWritable.getEditor().getEditorComponent());
    panel.add( label, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0) );
    panel.add( myComboBoxReplaceWritable,
      new GridBagConstraints(1,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,10,5,0),0,0)
    );

    // "Make writable"

    myCheckBoxMakeWritable.setPreferredSize(new Dimension(280,20));
    panel.add( myCheckBoxMakeWritable,
      new GridBagConstraints(0,1,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0)
    );

    return panel;
  }

  protected void init()
  {
    myCheckBoxMakeWritable = new JCheckBox(VssBundle.message("checkbox.get.optiond.make.writable"));
    myComboBoxReplaceWritable = new JComboBox(
      new DefaultComboBoxModel( new String[]{ VssBundle.message("combo.replace.policy.ask"),
                                              VssBundle.message("combo.options.replace.policy.replace"),
                                              VssBundle.message("combo.options.replace.policy.skip")}
      )
    );

    myCheckBoxMakeWritable.setSelected( options.MAKE_WRITABLE );
    if( GetOptions.OPTION_ASK == options.REPLACE_WRITABLE )
     myComboBoxReplaceWritable.setSelectedIndex( 0 );
    else if( GetOptions.OPTION_REPLACE == options.REPLACE_WRITABLE )
      myComboBoxReplaceWritable.setSelectedIndex( 1 );
    else if( GetOptions.OPTION_SKIP == options.REPLACE_WRITABLE )
      myComboBoxReplaceWritable.setSelectedIndex( 2 );

    super.init();
  }
}
