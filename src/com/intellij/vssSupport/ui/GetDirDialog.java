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

public class GetDirDialog extends OptionsDialog
{
  private final static int SKIP_ORDER = 1;

  private GetOptions options;

  // UI controls.
  private JComboBox myComboBoxReplaceWritable;
  private JCheckBox myCheckBoxRecursive;
  private JCheckBox myCheckBoxMakeWritable;

  public GetDirDialog( Project project )
  {
    super( project );
    options = VssConfiguration.getInstance( project ).getGetOptions();
    init();
  }

  /**
   * GetDirDialog is used for "Update Project" operation as well. For this
   * particular operation several options must be prepared consistently to
   * the operation logics:
   * - It is always done recursively
   * - It uses "Skip" variant for interacting with user when dealing with
   *   writable files.
   * - Writable files are not ignored (and are not replaced).
   */
  public void makeConsistentForUpdateProject()
  {
    if( myCheckBoxRecursive != null )
    {
      myCheckBoxRecursive.setSelected( true );
      myCheckBoxRecursive.setEnabled( false );
    }
    
    if( myComboBoxReplaceWritable != null )
    {
      myComboBoxReplaceWritable.setSelectedIndex( SKIP_ORDER );
      myComboBoxReplaceWritable.setEnabled( false );
    }

    if( myCheckBoxMakeWritable != null )
    {
      myCheckBoxMakeWritable.setSelected( false );
      myCheckBoxMakeWritable.setEnabled( false );
    }
  }

  protected boolean isToBeShown() {  return VssVcs.getInstance( myProject ).getGetOptions().getValue();  }
  protected void setToBeShown( boolean value, boolean onOk ) {  VssVcs.getInstance( myProject ).getGetOptions().setValue( value );  }
  protected boolean shouldSaveOptionsOnCancel()  {   return false;   }

  /**
   * Stores edited data into the passed data holder.
   */
  protected void doOKAction()
  {
    int idx = myComboBoxReplaceWritable.getSelectedIndex();
    options.REPLACE_WRITABLE = (idx == 0) ? GetOptions.OPTION_REPLACE : GetOptions.OPTION_SKIP;

    options.RECURSIVE = myCheckBoxRecursive.isSelected();
    options.MAKE_WRITABLE = myCheckBoxMakeWritable.isSelected();
    
    super.doOKAction();
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel( new GridBagLayout() );

    // "Replace writable"

    JLabel label = new JLabel( VssBundle.message("label.get.options.replace.writable") );
    label.setLabelFor( myComboBoxReplaceWritable.getEditor().getEditorComponent() );
    panel.add( label, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0) );
    panel.add( myComboBoxReplaceWritable, new GridBagConstraints(1,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,10,5,0),0,0) );

    // "Recursive"

    panel.add( myCheckBoxRecursive, new GridBagConstraints(0,1,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0) );

    // "Make writable"

    myCheckBoxMakeWritable.setPreferredSize(new Dimension(280,20));
    panel.add( myCheckBoxMakeWritable, new GridBagConstraints(0,2,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0) );

    return panel;
  }

  protected void init()
  {
    //  Do not use option "Ask" for batch operations like getting folder content.
    myComboBoxReplaceWritable = new JComboBox(
      new DefaultComboBoxModel( new String[]{ VssBundle.message("combo.options.replace.policy.replace"),
                                              VssBundle.message("combo.options.replace.policy.skip")  } )
    );
    myCheckBoxRecursive = new JCheckBox(VssBundle.message("checkbox.checkout.dir.options.recursive"));
    myCheckBoxMakeWritable = new JCheckBox(VssBundle.message("checkbox.get.optiond.make.writable"));

    myComboBoxReplaceWritable.setSelectedIndex( ( GetOptions.OPTION_REPLACE == options.REPLACE_WRITABLE ) ? 0 : 1 );
    myCheckBoxRecursive.setSelected( options.RECURSIVE );
    myCheckBoxMakeWritable.setSelected( options.MAKE_WRITABLE );

    super.init();
  }
}