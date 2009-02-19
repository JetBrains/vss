/**
 * @author  Vladimir Kondratyev
 */
package com.intellij.vssSupport.Configuration;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.vssSupport.VssBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class VssConfigurable extends BaseConfigurable
{
  private static final Icon ourIcon = IconLoader.getIcon("/general/vss.png");

  private final VssConfiguration myConfig;
  private final Project myProject;

  // UI Components.
  // The components are created on demand by first call of getComponent() method.
  // If myPanel is null then all UI component are null either.
  JPanel myPanel;
  private TextFieldWithBrowseButton myClientPath;
  private TextFieldWithBrowseButton mySrcsafeIni;
  private JTextField myTextFieldUserName;
  private JPasswordField myPasswordField;

  @NonNls public static final String PATH_TO_SS_EXE = "ss.exe";
  @NonNls public static final String PATH_TO_SS_INI = "srcsafe.ini";

  @NonNls public String getDisplayName() {  return "SourceSafe";  }

  public VssConfigurable( Project project )
  {
    myProject = project;
    myConfig = VssConfiguration.getInstance( myProject );
  }

  public void apply() throws ConfigurationException
  {
    myConfig.CLIENT_PATH = myClientPath.getText().replace('/',File.separatorChar);
    myConfig.SRCSAFEINI_PATH = mySrcsafeIni.getText().replace('/',File.separatorChar);
    myConfig.USER_NAME = myTextFieldUserName.getText().trim();
    myConfig.setPassword( new String( myPasswordField.getPassword() ) );
  }

  public void disposeUIResources() {  myPanel = null;  }
  public String getHelpTopic() {  return "project.propVSS";  }
  public Icon getIcon() {  return ourIcon;  }

  public JComponent createComponent()
  {
    myClientPath.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent ignored){
          JFileChooser fileChooser=new JFileChooser();
          FileFilter[] filters=fileChooser.getChoosableFileFilters();
          for (FileFilter filter : filters) {
            fileChooser.removeChoosableFileFilter(filter);
          }
          fileChooser.addChoosableFileFilter(
            new FileFilter(){
              public boolean accept(File f){
                return f.isDirectory() || PATH_TO_SS_EXE.equalsIgnoreCase(f.getName());
              }

              public String getDescription(){
                return VssBundle.message("dialog.description.configuration.path.to.ss.exe");
              }
            }
          );
          if(
            JFileChooser.APPROVE_OPTION!=fileChooser.showOpenDialog(WindowManager.getInstance().suggestParentWindow(myProject))
          ){
            return;
          }
          File selection=fileChooser.getSelectedFile();
          myClientPath.setText(selection.getAbsolutePath());
        }
      }
    );

    // SSDIR (srcsafe.ini)

    mySrcsafeIni.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent ignored){
          JFileChooser fileChooser=new JFileChooser();
          FileFilter[] filters=fileChooser.getChoosableFileFilters();
          for (FileFilter filter : filters) {
            fileChooser.removeChoosableFileFilter(filter);
          }
          fileChooser.addChoosableFileFilter(
            new FileFilter(){
              public boolean accept(File f){
                return f.isDirectory() || PATH_TO_SS_INI.equalsIgnoreCase(f.getName());
              }

              public String getDescription(){
                return VssBundle.message("dialog.description.configuration.path.to.srcsafe.ini");
              }
            }
          );
          if(
            JFileChooser.APPROVE_OPTION!=fileChooser.showOpenDialog(WindowManager.getInstance().suggestParentWindow(myProject))
          ){
            return;
          }
          File selection=fileChooser.getSelectedFile();
          mySrcsafeIni.setText(selection.getAbsolutePath());
        }
      }
    );

    return myPanel;
  }

  public boolean isModified()
  {
    return !myClientPath.getText().replace('/',File.separatorChar).equals( myConfig.CLIENT_PATH ) ||
           !mySrcsafeIni.getText().replace('/',File.separatorChar).equals( myConfig.SRCSAFEINI_PATH ) ||
           !myTextFieldUserName.getText().trim().equals( myConfig.USER_NAME ) ||
           !(new String(myPasswordField.getPassword())).equals( myConfig.getPassword() );
  }

  public void reset()
  {
    myClientPath.setText(myConfig.CLIENT_PATH);
    mySrcsafeIni.setText(myConfig.SRCSAFEINI_PATH);
    myTextFieldUserName.setText(myConfig.USER_NAME);
    myPasswordField.setText(myConfig.getPassword());
  }
}
