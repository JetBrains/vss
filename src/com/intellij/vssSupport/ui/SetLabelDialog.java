package com.intellij.vssSupport.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.vssSupport.VssBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class SetLabelDialog extends DialogWrapper
{
  // UI controls.
  private JTextField myLabel;
  private JTextArea myTextAreaComment;

  public SetLabelDialog( Project project )
  {
    super( project, false );
    setTitle( VssBundle.message( "dialog.title.set.label" ) );
    init();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.vssSupport.ui.SetLabelDialog";
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel( new GridBagLayout() );

    // Label

    JLabel lblLabel = new JLabel(VssBundle.message("label.options.label"));
    lblLabel.setLabelFor( myLabel );
    panel.add( lblLabel, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,3,5),0,0) );
    panel.add( myLabel, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,3,5),0,0) );
    myLabel.getDocument().addDocumentListener( new DocumentListener() {
      public void insertUpdate(final DocumentEvent e) {  checkValidString();   }
      public void removeUpdate(final DocumentEvent e) {  checkValidString();   }
      public void changedUpdate(final DocumentEvent e) {  checkValidString();   }
    });

    // Comment

    JLabel lblComment = new JLabel( VssBundle.message("label.options.comment") );
    lblComment.setLabelFor( myTextAreaComment );
    panel.add( lblComment,
               new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,3,5),0,0) );

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTextAreaComment);
    scrollPane.setPreferredSize( new Dimension(250,70) );
    panel.add( scrollPane,
               new GridBagConstraints(1,1,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,5,0),0,0) );

    checkValidString();
    return panel;
  }

  protected void init()
  {
    myLabel = new JTextField();
    myTextAreaComment = new JTextArea();
    super.init();
  }

  private void checkValidString()
  {
    String label = myLabel.getText();

    //  Do not allow blanks inside the label string
    boolean labelOK = StringUtil.isNotEmpty( label ) && (label.indexOf( ' ' ) == -1);
    setOKActionEnabled( labelOK );
  }

  public JComponent getPreferredFocusedComponent(){  return myLabel;  }

  public String getLabel()    {  return myLabel.getText();  }
  public String getComment()  {  return myTextAreaComment.getText();  }
}
