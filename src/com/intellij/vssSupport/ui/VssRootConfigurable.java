package com.intellij.vssSupport.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.DocumentAdapter;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssRootSettings;
import com.intellij.vssSupport.commands.VssCommandAbstract;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class VssRootConfigurable implements UnnamedConfigurable
{
  private final static String VSS_PROJECT_PREFIX = "$/";

  private final Project project;
  private final VcsDirectoryMapping myMapping;
  private JPanel myPanel;
  private JComboBox myComboBox1;
  private JButton btnTest;

  public VssRootConfigurable( final VcsDirectoryMapping mapping, final Project project )
  {
    myMapping = mapping;
    this.project = project;

    //  Add custom editor so that we can control the shallow validness of the
    //  string describing the vss project path.
    BasicComboBoxEditor editor = new BasicComboBoxEditor(){
      {
        editor.getDocument().addDocumentListener( new DocumentAdapter() {
          protected void textChanged(final DocumentEvent e)
          {
            btnTest.setEnabled( validProject() );
          }
        });
      }
    };
    myComboBox1.setEditor( editor );

    btnTest.addActionListener(new ActionListener() {
      public void actionPerformed( ActionEvent e )
      {
        List<VcsException> errors = new ArrayList<>();
        VssCommandAbstract cmd = new CPCommand( project, errors );
        cmd.execute();
        if( errors.size() == 0 )
          Messages.showInfoMessage( project, VssBundle.message( "message.project.valid" ), VssBundle.message( "message.title.check.status" ) );
        else
          Messages.showErrorDialog( project, VssBundle.message( "message.project.not.valid" ), VssBundle.message( "message.title.check.status" ) );
      }
    });

    VssRootSettings rootSettings = (VssRootSettings) mapping.getRootSettings();
    if (rootSettings != null)
      myComboBox1.setSelectedItem( rootSettings.getVssProject() );

    btnTest.setEnabled( validProject() );
  }

  public JComponent createComponent() {  return myPanel;  }

  public boolean isModified() {  return false;  }

  public void apply() {
    myMapping.setRootSettings( new VssRootSettings( (String)myComboBox1.getSelectedItem() ) );
  }

  public void reset() {}

  private boolean validProject()
  {
    String text = (String)myComboBox1.getEditor().getItem();
    return (text != null) && text.startsWith( VSS_PROJECT_PREFIX );
  }

  private class CPCommand extends VssCommandAbstract
  {
    public CPCommand( Project project, List <VcsException> errors )
    {
      super( project, errors );
    }
    
    public void execute()
    {
      String vssPath = (String)myComboBox1.getSelectedItem();
      List<String> options = formOptions( "Properties", "-R-", vssPath, _I_Y_OPTION );
      runProcess( options, project.getBaseDir().getPath() );
    }
  }
}
