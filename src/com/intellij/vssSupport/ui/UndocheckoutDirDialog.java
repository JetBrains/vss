/**
 * @author Vladimir Kondratyev
 * @author Michael Gerasimov
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

public class UndocheckoutDirDialog extends OptionsDialog
{
  private UndocheckoutOptions options;

  // UI controls.
  private JComboBox comboReplaceLocalCopy;
  private JCheckBox chboxRecursive;
  private JCheckBox chboxMakeWritable;

  public UndocheckoutDirDialog( Project project )
  {
    super(project);
    options = VssConfiguration.getInstance(project).getUndocheckoutOptions();
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
    options.RECURSIVE = chboxRecursive.isSelected();
    options.MAKE_WRITABLE = chboxMakeWritable.isSelected();

    boolean isReplace = (comboReplaceLocalCopy.getSelectedIndex() == 0);
    options.REPLACE_LOCAL_COPY = isReplace ? UndocheckoutOptions.OPTION_REPLACE : UndocheckoutOptions.OPTION_LEAVE;
    
    super.doOKAction();
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel( new GridBagLayout() );

    // "Local copy"

    JLabel label=new JLabel(VssBundle.message("label.undo.options.replace.local.copy"));
    label.setLabelFor(comboReplaceLocalCopy.getEditor().getEditorComponent());
    panel.add( label,
      new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0)
    );
    panel.add(comboReplaceLocalCopy,
      new GridBagConstraints(1,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,10,5,0),0,0)
    );

    // "Recursive"

    panel.add(chboxRecursive,
      new GridBagConstraints(0,1,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0)
    );

    // "Make writable"

    chboxMakeWritable.setPreferredSize(new Dimension(280,20));
    panel.add(chboxMakeWritable,
      new GridBagConstraints(0,2,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0)
    );

    return panel;
  }

  protected void init()
  {
    chboxRecursive = new JCheckBox(VssBundle.message("checkbox.undo.options.recursive"));
    chboxMakeWritable = new JCheckBox(VssBundle.message("checkbox.undo.optiond.make.writable"));
    comboReplaceLocalCopy = new JComboBox(
      new DefaultComboBoxModel( new String[]{ VssBundle.message("combo.options.replace.policy.replace"),
                                              VssBundle.message("combo.undo.options.replace.policy.leave") }
      ) );
    chboxRecursive.setSelected( options.RECURSIVE );
    chboxMakeWritable.setSelected( options.MAKE_WRITABLE );

    boolean option = (options.REPLACE_LOCAL_COPY == UndocheckoutOptions.OPTION_LEAVE);
    comboReplaceLocalCopy.setSelectedIndex( option ? 1 : 0 );

    super.init();
  }
}