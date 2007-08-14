/**
 * @author Vladimir Kondratyev
 */
package com.intellij.vssSupport.ui;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vssSupport.UndocheckoutOptions;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssConfiguration;
import com.intellij.vssSupport.VssVcs;

import javax.swing.*;
import java.awt.*;

public class UndocheckoutFilesDialog extends OptionsDialog
{
  private UndocheckoutOptions options;

  // UI controls.
  private JCheckBox myCheckBoxMakeWritable;
  private JComboBox myComboBoxReplaceLocalCopy;

  public UndocheckoutFilesDialog(Project project)
  {
    super( project );
    options = VssConfiguration.getInstance( project ).getUndocheckoutOptions();
    init();
  }

  protected boolean isToBeShown() {  return VssVcs.getInstance( myProject ).getUndoCheckoutOptions().getValue();  }
  protected void setToBeShown( boolean value, boolean onOk ) {  VssVcs.getInstance( myProject ).getUndoCheckoutOptions().setValue( value );  }
  protected boolean shouldSaveOptionsOnCancel()  {  return false;  }
  
  /**
   * Stores edited data into the passed data holder.
   */
  protected void doOKAction()
  {
    options.MAKE_WRITABLE = myCheckBoxMakeWritable.isSelected();
    int idx = myComboBoxReplaceLocalCopy.getSelectedIndex();
    switch( idx )
    {
      case 0: options.REPLACE_LOCAL_COPY=UndocheckoutOptions.OPTION_ASK; break;
      case 1: options.REPLACE_LOCAL_COPY=UndocheckoutOptions.OPTION_LEAVE; break;
      case 2: options.REPLACE_LOCAL_COPY=UndocheckoutOptions.OPTION_REPLACE; break;
    }

    super.doOKAction();
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel( new GridBagLayout() );

    // "Local copy"

    JLabel label = new JLabel(VssBundle.message("label.undo.options.replace.local.copy"));
    label.setLabelFor(myComboBoxReplaceLocalCopy.getEditor().getEditorComponent());
    panel.add( label,
               new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0) );
    panel.add( myComboBoxReplaceLocalCopy,
               new GridBagConstraints(1,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,10,5,0),0,0) );

    // "Make writable"

    myCheckBoxMakeWritable.setPreferredSize(new Dimension(280,20));
    panel.add( myCheckBoxMakeWritable,
               new GridBagConstraints(0,1,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0) );

    return panel;
  }

  protected void init()
  {
    myCheckBoxMakeWritable = new JCheckBox(VssBundle.message("checkbox.undo.optiond.make.writable"));
    myComboBoxReplaceLocalCopy = new JComboBox( new DefaultComboBoxModel(
                                                      new String[]{ VssBundle.message("combo.replace.policy.ask"),
                                                                    VssBundle.message("combo.undo.options.replace.policy.leave"),
                                                                    VssBundle.message("combo.options.replace.policy.replace") } )
                                              );
    myCheckBoxMakeWritable.setSelected( options.MAKE_WRITABLE );

    if( UndocheckoutOptions.OPTION_ASK == options.REPLACE_LOCAL_COPY )
      myComboBoxReplaceLocalCopy.setSelectedIndex( 0 );
    else if( UndocheckoutOptions.OPTION_LEAVE == options.REPLACE_LOCAL_COPY )
      myComboBoxReplaceLocalCopy.setSelectedIndex( 1 );
    else if( UndocheckoutOptions.OPTION_REPLACE == options.REPLACE_LOCAL_COPY)
      myComboBoxReplaceLocalCopy.setSelectedIndex( 2 );

    super.init();
  }
}