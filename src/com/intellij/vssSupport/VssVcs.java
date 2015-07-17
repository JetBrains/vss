/*
 * Created by IntelliJ IDEA.
 * User: LloiX
 */
package com.intellij.vssSupport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.ultimate.PluginVerifier;
import com.intellij.ultimate.UltimateVerifier;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Checkin.VssCheckinEnvironment;
import com.intellij.vssSupport.Checkin.VssRollbackEnvironment;
import com.intellij.vssSupport.Configuration.MapItem;
import com.intellij.vssSupport.Configuration.VssConfigurable;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.commands.*;
import com.intellij.vssSupport.ui.VssRootConfigurable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class VssVcs extends AbstractVcs implements ProjectComponent, JDOMExternalizable, RoamingTypeDisabled
{
  public static final Key<String> FILE_TYPE = new Key<String>("FILE_TYPE");

  @NonNls private static final String PERSISTENCY_REMOVED_FILE_TAG = "SourceSafePersistencyRemovedFile";
  @NonNls private static final String PERSISTENCY_REMOVED_FOLDER_TAG = "SourceSafePersistencyRemovedFolder";
  @NonNls private static final String PERSISTENCY_RENAMED_FILE_TAG = "SourceSafePersistencyRenamedFile";
  @NonNls private static final String PERSISTENCY_RENAMED_FOLDER_TAG = "SourceSafePersistencyRenamedFolder";
  @NonNls private static final String PERSISTENCY_NEW_FILE_TAG = "SourceSafePersistencyNewFile";
  @NonNls private static final String PERSISTENCY_DELETED_FILE_TAG = "SourceSafePersistencyDeletedFile";
  @NonNls private static final String PERSISTENCY_DELETED_FOLDER_TAG = "SourceSafePersistencyDeletedFolder";
  @NonNls private static final String OPTIONS_FOLDER = "VSS";
  @NonNls private static final String OPTIONS_FILE = "projects";
  @NonNls private static final String PATH_DELIMITER = "%%%";

  @NonNls private static final String VSSVER_FILE_SIG = "vssver.scc";
  @NonNls private static final String VSSVER2_FILE_SIG = "vssver2.scc";
  private static final String NAME = "SourceSafe";
  private static final VcsKey ourKey = createKey(NAME);

  private VssCheckinEnvironment checkinEnvironment;
  private final VssRollbackEnvironment rollbackEnvironment;
  private final VssUpdateEnvironment updateEnvironment;
  private final VssChangeProvider  changeProvider;
  private final VssFileHistoryProvider historyProvider;
  private final EditFileProvider  editFileProvider;
  private VirtualFileListener listener;
  private LocalFileOperationsHandler removalHandler;

  private VcsShowSettingOption myCheckoutOptions;
  private VcsShowSettingOption myUndoCheckoutOptions;
  private VcsShowSettingOption myGetOptions;
  private VcsShowConfirmationOption addConfirmation;
  private VcsShowConfirmationOption removeConfirmation;

  private final HashSet<String>      savedProjectPaths;

  public  HashSet<String> removedFiles;
  public  HashSet<String> removedFolders;
  public  HashSet<String> deletedFiles;
  public  HashSet<String> deletedFolders;
  public  HashMap<String, String> renamedFiles;
  public  HashMap<String, String> renamedFolders;
  private final HashSet<VirtualFile> newFiles;

  public VssVcs(@NotNull Project project, UltimateVerifier verifier) {
    super( project, NAME);
    PluginVerifier.verifyUltimatePlugin(verifier);

    checkinEnvironment = new VssCheckinEnvironment( project, this );
    rollbackEnvironment = new VssRollbackEnvironment( project, this );
    updateEnvironment = new VssUpdateEnvironment( project );
    changeProvider = new VssChangeProvider( project, this );
    historyProvider = new VssFileHistoryProvider( project );
    editFileProvider = new VssEditFileProvider( project );

    removedFiles = new HashSet<String>();
    removedFolders = new HashSet<String>();
    renamedFiles = new HashMap<String, String>();
    renamedFolders = new HashMap<String, String>();
    newFiles = new HashSet<VirtualFile>();
    deletedFiles = new HashSet<String>();
    deletedFolders = new HashSet<String>();
    savedProjectPaths = new HashSet<String>();
  }

  public VcsShowSettingOption getCheckoutOptions()        {  return myCheckoutOptions;   }
  public VcsShowSettingOption getUndoCheckoutOptions()    {  return myUndoCheckoutOptions;  }
  public VcsShowSettingOption getGetOptions()             {  return myGetOptions;        }
  public VcsShowConfirmationOption getAddConfirmation()   {  return addConfirmation;     }
  public VcsShowConfirmationOption getRemoveConfirmation(){  return removeConfirmation;  }

  @Override
  @NotNull
  public String getComponentName()  {  return "VssVcs";  }
  @Override
  public String getDisplayName()    {  return NAME;  }
  @Override
  public String getMenuItemText()   {  return VssBundle.message("menu.item.source.safe.group.name"); }

  public static VssVcs getInstance( Project project )   {  return project.getComponent(VssVcs.class);  }

  @Override
  public Configurable         getConfigurable()         {  return new VssConfigurable(myProject );  }
  @Override
  public CheckinEnvironment   createCheckinEnvironment()   {  return checkinEnvironment;  }
  @Override
  public RollbackEnvironment  createRollbackEnvironment()  {  return rollbackEnvironment; }

  @Override
  public ChangeProvider       getChangeProvider()       {  return changeProvider;    }
  @Override
  public VcsHistoryProvider   getVcsHistoryProvider()   {  return historyProvider;   }
  @Override
  public EditFileProvider     getEditFileProvider()     {  return editFileProvider;  }
  @Override
  public UpdateEnvironment    createUpdateEnvironment()    {  return updateEnvironment; }
  public HashSet<String>      getSavedProjectPaths()    {  return savedProjectPaths;  }
  public void                 addSavedProjectPath( String path ) {  savedProjectPaths.add( path );  }

  @Override
  public void   initComponent()     {}
  @Override
  public void   disposeComponent()  {checkinEnvironment = null;  }

  @Override
  public void projectOpened()
  {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(getProject());

    myCheckoutOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKOUT, this);
    myUndoCheckoutOptions = vcsManager.getOrCreateCustomOption(VssBundle.message("action.name.undo.check.out"), this);
    myGetOptions = vcsManager.getOrCreateCustomOption(VssBundle.message("action.name.get.latest.version"), this);

    addConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.ADD, this );
    removeConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.REMOVE, this );

    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        addIgnoredFiles();
      }
    });
  }

  @Override
  public void projectClosed() {}

  @Override
  public void activate()
  {
    //  Control the appearance of project items so that we can easily
    //  track down potential changes in the repository.
    listener = new VFSListener( getProject(), this );
    removalHandler = new VssLocalFileOperationsHandler( myProject, this );
    LocalFileSystem.getInstance().addVirtualFileListener( listener );
    CommandProcessor.getInstance().addCommandListener( (CommandListener)listener );
    LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler( removalHandler );

    VssConfiguration config = VssConfiguration.getInstance( myProject );
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( myProject );
    List<VcsDirectoryMapping> currentMappings = mgr.getDirectoryMappings();
    List<VcsDirectoryMapping> newMappings = new ArrayList<VcsDirectoryMapping>();

    //  Load old-formatted content root mappings, transform them into new ones.
    //  VssConfiguration reads them but never writes down again, so this procedure
    //  will be performed once per old-formatted project.
    if( config.getMapItemCount() > 0 )
    {
      for( int i = 0; i < config.getMapItemCount(); i++ )
      {
        MapItem item = config.getMapItem( i );
        if( !hasMappedFolder( item.LOCAL_PATH, currentMappings ) )
        {
          VcsDirectoryMapping mapping = new VcsDirectoryMapping( item.LOCAL_PATH, getName() );
          mapping.setRootSettings( new VssRootSettings( item.VSS_PATH ) );
          newMappings.add( mapping );
        }
      }
    }
    if( newMappings.size() > 0 )
    {
      mgr.setDirectoryMappings( newMappings );
    }

    //  Add information about VSS project roots from the local project.
    List<VcsDirectoryMapping> list = mgr.getDirectoryMappings();
    for( VcsDirectoryMapping pair : list )
    {
      savedProjectPaths.add( pair.getVcs() );
    }
  }

  @Override
  public void deactivate()
  {
    LocalFileSystem.getInstance().removeVirtualFileListener( listener );
    CommandProcessor.getInstance().removeCommandListener( (CommandListener)listener );
    LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler( removalHandler );

    ContentRevisionFactory.detachListeners();
  }

  private static boolean hasMappedFolder( String path, List<VcsDirectoryMapping> mappings )
  {
    for( VcsDirectoryMapping mapping : mappings )
    {
      //  remove possible backslash in settings.
      final String normalPath = VssUtil.normalizeDirPath( mapping.getDirectory() );
      if( normalPath.equalsIgnoreCase( path ) )
        return true;
    }
    return false;
  }

  /**
   * Automatically add "vssver.scc" pattern into the list of ignored file so that
   * they are not becoming the part of the project.
   */
  private static void addIgnoredFiles()
  {
    String patterns = FileTypeManager.getInstance().getIgnoredFilesList();

    //  This code corrects the obvious bug in the previous installations
    @NonNls String errorPattern = "vssver.sccvssver2.scc;";
    patterns = patterns.replaceAll( errorPattern, "" );

    String newPattern = patterns;
    if( patterns.indexOf( VSSVER_FILE_SIG ) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + VSSVER_FILE_SIG;

    if( patterns.indexOf( VSSVER2_FILE_SIG ) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + VSSVER2_FILE_SIG;

    if( !newPattern.equals( patterns ))
    {
      final String newPat = newPattern;
      ApplicationManager.getApplication().runWriteAction( new Runnable()
        { @Override
                                                            public void run() { FileTypeManager.getInstance().setIgnoredFilesList(newPat ); } }
      );
    }
  }

  public void checkinFile( VirtualFile file, List<VcsException> errors, boolean suppressWarns )
  {
    new CheckinFileCommand( myProject, file, errors, suppressWarns ).execute();
  }

  public void renameAndCheckInFile( String path, String newName, List<VcsException> errors )
  {
    File file = new File( path );
    File newFile = new File( file.getParentFile(), newName );
    VirtualFile newVFile = VcsUtil.getVirtualFile( newFile );

    new RenameFileCommand( myProject, newVFile, file.getName(), errors ).execute();
    new CheckinFileCommand( myProject, newVFile, errors ).execute();
  }

  public void renameDirectory( String path, String newName, List<VcsException> errors )
  {
    File file = new File(path);
    File newFile = new File( file.getParentFile(), newName );
    VirtualFile newVFile = VcsUtil.getVirtualFile( newFile );
    RenameFileCommand cmd = new RenameFileCommand( myProject, newVFile, file.getName(), errors );
    cmd.execute();
  }

  public void moveRenameAndCheckInFile( String path, String newParentPath, String newName,
                                        List<VcsException> errors )
  {
    File oldFile = new File( path );
    File newParent = new File( newParentPath );
    File newFile = new File( newParent, newName );

    boolean isRenamed = !oldFile.getName().equals( newName );

    try
    {
      FileUtil.copy( newFile, oldFile );
      VirtualFile newVFile = VcsUtil.getVirtualFile( newFile );
      VirtualFile oldVFile = VcsUtil.waitForTheFile( oldFile.getPath() );

      new CheckinFileCommand( myProject, oldVFile, errors ).execute();
      new ShareFileCommand( myProject, oldFile, newFile, errors ).execute();
      new DeleteFileOrDirectoryCommand( myProject, oldFile.getAbsolutePath(), errors ).execute();
      if( isRenamed )
      {
        new RenameFileCommand( myProject, newVFile, oldFile.getName(), errors ).execute();
      }
      oldFile.delete();
      newFile.setReadOnly();
    }
    catch( IOException e )
    {
      errors.add( new VcsException( e.getMessage() ));
    }
  }

  public void moveAndRenameDirectory(String path, String newParentPath, String newName, List<VcsException> errors )
  {
    File oldFile = new File( path );
    boolean isRenamed = (oldFile.getName() != newName);
    new MoveDirectoryCommand( myProject, oldFile, new File( newParentPath, oldFile.getName() ), errors ).execute();

    if( isRenamed )
    {
      File newFile = new File( new File( newParentPath ), newName );
      VirtualFile newVFile = VcsUtil.getVirtualFile( newFile );
      new RenameFileCommand( myProject, newVFile, oldFile.getName(), errors ).execute();
    }
  }

  public void addFile( VirtualFile file, List<VcsException> errors )
  {
    AddFileCommand cmd = new AddFileCommand( myProject, file, errors );
    cmd.execute();
  }

  public void addFolder( VirtualFile folder, List<VcsException> errors )
  {
    CreateFolderCommand cmd = new CreateFolderCommand( myProject, folder, errors );
    cmd.execute();
  }

  public void removeFile( String path, List<VcsException> errors )
  {
    DeleteFileOrDirectoryCommand cmd = new DeleteFileOrDirectoryCommand( myProject, path, errors );
    cmd.execute();
  }

  public boolean getLatestVersion( String path, boolean makeWritable, List<VcsException> errors )
  {
    GetFileCommand cmd = new GetFileCommand( getProject(), path, makeWritable, errors );
    cmd.execute();
    return cmd.isFileNonExistent();
  }

  public void rollbackDeleted( final String path, List<VcsException> errors )
  {
    getLatestVersion( path, false, errors );

    //  Do not forget to refresh the VFS file holder.
    VirtualFile file = VcsUtil.waitForTheFile( path );
    if( file != null )
    {
      //  During file deletion IDEA clears RO status from the file (if it is not
      //  cleared yet). If the file under the repository, this step causes checkout
      //  automatically (otherwise, RO status is not cleared and file can not be
      //  removed). By "getLatestVersion" we get the content, now silently
      //  remove "Checkouted" sign from it.
      //
      //  Errors list is fake since we do not want to propagate any errors on this
      //  step back.
      List<VcsException> errorsFake = new ArrayList<VcsException>();
      rollbackChanges( path, errorsFake );
    }
  }

  public void rollbackChanges( String path, List<VcsException> errors )
  {
    VirtualFile file = VcsUtil.getVirtualFile( path );
    if( file != null )
    {
      if( file.isDirectory() )
        (new UndocheckoutDirCommand( myProject, file, errors )).execute();
      else
        (new UndocheckoutFilesCommand( myProject, new VirtualFile[]{ file }, errors )).execute();
    }
  }

  public void rollbackChanges( String[] paths, List<VcsException> errors )
  {
    VirtualFile[] files = VcsUtil.paths2VFiles( paths );
    if( files.length == 1 && files[ 0 ].isDirectory() )
    {
      (new UndocheckoutDirCommand( myProject, files[ 0 ], errors )).execute();
    }
    else
    {
      (new UndocheckoutFilesCommand( myProject, files, errors )).execute();
    }
  }

  /**
   * Consults <code>ChangeListManager</code> whether the file belongs to the list of ignored
   * files or resides under the ignored folder.
   */
  public boolean isFileIgnored( VirtualFile file )
  {
    ChangeListManager mgr = ChangeListManager.getInstance( myProject );
    return mgr.isIgnoredFile( file );
  }

  @Override
  public boolean fileExistsInVcs(FilePath path ) {
    return fileIsUnderVcs( path ) && super.fileExistsInVcs( path );
  }

  @Override
  public boolean fileIsUnderVcs(FilePath path )
  {
    ProjectLevelVcsManager pm = ProjectLevelVcsManager.getInstance( getProject() );
    return pm.getVcsFor( path ) == this;
  }

  public boolean fileIsUnderVcs( VirtualFile file )
  {
    ProjectLevelVcsManager pm = ProjectLevelVcsManager.getInstance( getProject() );
    return file != null && (pm.getVcsFor( file ) == this);
  }

  public void add2NewFile( @NotNull VirtualFile file )    {  newFiles.add( file );             }
  public void deleteNewFile( @NotNull VirtualFile file )  {  newFiles.remove( file );          }
  public boolean containsNew( @NotNull VirtualFile file ) {  return newFiles.contains( file ); }
  public boolean containsNew( String path )
  {
    VirtualFile file = VcsUtil.getVirtualFile( path );
    return newFiles.contains( file );
  }

  public boolean isDeletedFile( String path )   {  return deletedFiles.contains( path ) || removedFiles.contains( path );  }
  public boolean isDeletedFolder( String path ) {  return deletedFolders.contains( path ) || removedFolders.contains( path );  }

  public boolean isWasRenamed( String path )    {  return renamedFiles.containsValue( path );  }
  public boolean isNewOverRenamed( String path ){  return containsNew( path ) && isWasRenamed( path );  }

  @Override
  public boolean isVersionedDirectory(VirtualFile dir )
  {
    final VirtualFile versionFile2003 = dir.findChild( VSSVER_FILE_SIG );
    final VirtualFile versionFile2005 = dir.findChild( VSSVER2_FILE_SIG );

    return ((versionFile2003 != null && !versionFile2003.isDirectory()) ||
           (versionFile2005 != null && !versionFile2005.isDirectory()));
  }

  @Override
  public UnnamedConfigurable getRootConfigurable( final VcsDirectoryMapping mapping )
  {
    return new VssRootConfigurable( mapping, myProject );
  }

  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(final String revisionNumberString)
  {
    int revision;
    try { revision = (int)Long.parseLong( revisionNumberString );  }
    catch( NumberFormatException ex ) {  return null;  }

    return new VcsRevisionNumber.Int( revision );
  }

  @Override
  public String getRevisionPattern() {
    return ourIntegerPattern;
  }

  //
  // JDOMExternalizable methods
  //

  @Override
  public void readExternal(final Element element ) throws InvalidDataException
  {
    readElements( element, removedFiles, PERSISTENCY_REMOVED_FILE_TAG, false );
    readElements( element, removedFolders, PERSISTENCY_REMOVED_FOLDER_TAG, false );
    readElements( element, deletedFiles, PERSISTENCY_DELETED_FILE_TAG, false );
    readElements( element, deletedFolders, PERSISTENCY_DELETED_FOLDER_TAG, false );

    HashSet<String> tmp = new HashSet<String>();
    readElements( element, tmp, PERSISTENCY_NEW_FILE_TAG, true );

    readRenamedElements( element, renamedFiles, PERSISTENCY_RENAMED_FILE_TAG, true );
    readRenamedElements( element, renamedFolders, PERSISTENCY_RENAMED_FOLDER_TAG, true );

    readUsedProjectPaths();

    for( String path : tmp )
    {
      VirtualFile file = VcsUtil.getVirtualFile( path );
      if( file != null )
        newFiles.add( file );
    }
  }

  private static void readElements( final Element element, HashSet<String> list, String tag, boolean isExist )
  {
    List files = element.getChildren( tag );
    for (Object cclObj : files)
    {
      if (cclObj instanceof Element)
      {
        final Element currentCLElement = ((Element)cclObj);
        final String path = currentCLElement.getValue();

        // Safety check - file can be added again between IDE sessions.
        if( new File( path ).exists() == isExist )
          list.add( path );
      }
    }
  }

  private static void readRenamedElements( final Element element, HashMap<String, String> list,
                                           String tag, boolean isExist )
  {
    List files = element.getChildren( tag );
    for (Object cclObj : files)
    {
      if (cclObj instanceof Element)
      {
        final Element currentCLElement = ((Element)cclObj);
        final String pathPair = currentCLElement.getValue();
        int delimIndex = pathPair.indexOf( PATH_DELIMITER );
        if( delimIndex != -1 )
        {
          final String newName = pathPair.substring( 0, delimIndex );
          final String oldName = pathPair.substring( delimIndex + PATH_DELIMITER.length() );

          // Safety check - file can be deleted or changed between IDE sessions.
          if( new File( newName ).exists() == isExist )
            list.put( newName, oldName );
        }
      }
    }
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException
  {
    writeElement( element, removedFiles, PERSISTENCY_REMOVED_FILE_TAG );
    writeElement( element, removedFolders, PERSISTENCY_REMOVED_FOLDER_TAG );
    writeElement( element, deletedFiles, PERSISTENCY_DELETED_FILE_TAG );
    writeElement( element, deletedFolders, PERSISTENCY_DELETED_FOLDER_TAG );

    HashSet<String> tmp = new HashSet<String>();
    for( VirtualFile file : newFiles )
    {
      FileStatus status = FileStatusManager.getInstance( myProject ).getStatus( file );
      if( status == FileStatus.ADDED )
        tmp.add( file.getPath() );
    }
    writeElement( element, tmp, PERSISTENCY_NEW_FILE_TAG );

    writeRenElement( element, renamedFiles, PERSISTENCY_RENAMED_FILE_TAG );
    writeRenElement( element, renamedFolders, PERSISTENCY_RENAMED_FOLDER_TAG );

    //  Do not write to the file which is shared between several projects since
    //  the default settings may be incomplete and they override the current
    //  project's settings.
    if( !myProject.isDefault() )
    {
      writeUsedProjectPaths();
    }
  }

  private static void writeElement( final Element element, HashSet<String> files, String tag )
  {
    //  Sort elements of the list so that there is no perturbation in .ipr/.iml
    //  files in the case when no data has changed.
    String[] sorted = ArrayUtil.toStringArray(files);
    Arrays.sort( sorted );

    for( String file : sorted )
    {
      final Element listElement = new Element( tag );
      listElement.addContent( file );
      element.addContent( listElement );
    }
  }

  private static void writeRenElement( final Element element, HashMap<String, String> files, String tag )
  {
    for( String file : files.keySet() )
    {
      final Element listElement = new Element( tag );
      final String pathPair = file.concat( PATH_DELIMITER ).concat( files.get( file ) );

      listElement.addContent( pathPair );
      element.addContent( listElement );
    }
  }

  private void readUsedProjectPaths()
  {
    savedProjectPaths.clear();
    try {
      String optionsFile = PathManager.getConfigPath() + File.separatorChar + OPTIONS_FOLDER + File.separatorChar + OPTIONS_FILE;
      BufferedReader reader = new BufferedReader(new FileReader(optionsFile));
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          savedProjectPaths.add(line);
        }
      }
      finally {
        reader.close();
      }
    }
    catch (Exception e) {
      //  Nothing to do, no special treatment is necessary, the file
      //  can be recovered next time.
    }
  }

  private void writeUsedProjectPaths()
  {
    try {
      //  Create folder "VSS" under the predefined config folder,
      //  and put paths into the special file under that folder.
      String configPath = PathManager.getConfigPath() + File.separatorChar + OPTIONS_FOLDER;
      new File(configPath).mkdir();

      String optionsFile = configPath + File.separatorChar + OPTIONS_FILE;

      //  Do not forget to clear the content of the file - we always overwrite
      //  the available information anew.
      File file = new File(optionsFile);
      file.delete();

      PrintWriter writer = new PrintWriter(optionsFile);
      try {
        for (String path : savedProjectPaths) {
          writer.write(path + "\n");
        }
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      //  Nothing to do, no special treatment is necessary, the file
      //  can be recovered next time.
    }
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public VcsRootSettings createEmptyVcsRootSettings() {
    return new VssRootSettings();
  }
}
