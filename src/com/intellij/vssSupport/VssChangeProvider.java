package com.intellij.vssSupport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.Configuration.VssConfiguration;
import com.intellij.vssSupport.Configuration.VssRootSettings;
import com.intellij.vssSupport.commands.DirectoryCommand;
import com.intellij.vssSupport.commands.PropertiesCommand;
import com.intellij.vssSupport.commands.StatusMultipleCommand;
import static com.intellij.vssSupport.commands.VssCheckoutAbstractCommand.SUCCESSFUL_CHECKOUT;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 6, 2006
 */
public class VssChangeProvider implements ChangeProvider
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.vssSupport.VssChangeProvider");

  private static final int PER_FILE_DIFF_MARGIN = 30;

  private Project project;
  private VssVcs  host;
  private boolean isBatchUpdate;
  private boolean showInvalidConfigMessage = true;
  private ProgressIndicator progress;

  private HashSet<String> filesNew = new HashSet<String>();
  private HashSet<String> filesHijacked = new HashSet<String>();
  private HashSet<String> filesChanged = new HashSet<String>();
  private HashSet<String> filesObsolete = new HashSet<String>();
  private HashSet<String> filesIgnored = new HashSet<String>();

  public VssChangeProvider( Project project, VssVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public boolean isModifiedDocumentTrackingRequired() { return false;  }

  public void getChanges( final VcsDirtyScope dirtyScope, final ChangelistBuilder builder,
                          final ProgressIndicator indicator )
  {
    //-------------------------------------------------------------------------
    //  Protect ourselves from the calls which come during the unsafe project
    //  phases like unload or reload.
    //-------------------------------------------------------------------------
    if( project.isDisposed() )
      return;

    validateChangesOverTheHost( dirtyScope );
    logChangesContent( dirtyScope );

    isBatchUpdate = isBatchUpdate( dirtyScope );
    progress = indicator;

    //  Do not perform any actions if we have no VSS-related
    //  content roots configured.
    if( !checkDirectoryMappings() )
      return;

    //  Safety check #1: if user did not manage to set the proper path to ss.exe
    //  we fail to proceed further.
    if( !checkCommandPath() )
      return;

    initInternals();

    iterateOverRecursiveFolders( dirtyScope );
    iterateOverDirtyDirectories( dirtyScope );
    iterateOverDirtyFiles( dirtyScope );
    processStatusExceptions();

    addAddedFiles( builder );
    addHijackedFiles( builder );
    addObsoleteFiles( builder );
    addChangedFiles( builder );
    addRemovedFiles( builder );
    addIgnoredFiles( builder );
    LOG.info( "-- ChangeProvider| New: " + filesNew.size() + ", modified: " + filesChanged.size() +
              ", hijacked:" + filesHijacked.size() + ", obsolete: " + filesObsolete.size() +
              ", ignored: " + filesIgnored.size() );
  }

  /**
   *  Iterate over the project structure, find all writable files in the project,
   *  and check their status against the VSS repository. If file exists in the repository
   *  it is assigned "changed" status, otherwise it has "new" status.
   */
  private void iterateOverRecursiveFolders( final VcsDirtyScope dirtyScope )
  {
    for( FilePath path : dirtyScope.getRecursivelyDirtyDirectories() )
    {
      iterateOverProjectPath( path );
    }
  }

  /**
   *  Deleted and New folders are marked as dirty too and we provide here
   *  special processing for them.
   */
  private void iterateOverDirtyDirectories( final VcsDirtyScope dirtyScope )
  {
    for( FilePath path : dirtyScope.getDirtyFiles() )
    {
      String fileName = path.getPath();
      VirtualFile file = path.getVirtualFile();

      //  make sure that:
      //  - a file is a folder which exists physically
      //  - it is under out vcs
      if( path.isDirectory() && (file != null) && VcsUtil.isFileForVcs( path, project, host ) )
      {
        if( host.isFileIgnored( file ))
          filesIgnored.add( fileName );
        else
        {
          String refName = discoverOldName( host, fileName );
          if( !isFolderExists( refName ) )
            filesNew.add( fileName );
          else
          //  NB: Do not put to the "Changed" list those folders which are under
          //      the renamed one since we will have troubles in checking such
          //      folders in (it is useless, BTW).
          //      Simultaneously, this prevents valid processing of renamed folders
          //      that are under another renamed folders.
          //  Todo Inner rename.
          if( !refName.equals( fileName ) && !isUnderRenamedFolder( fileName ) )
            filesChanged.add( fileName );
        }
      }
    }
  }

  private void iterateOverDirtyFiles( final VcsDirtyScope scope )
  {
    List<String> paths = new ArrayList<String>();
    for( FilePath path : scope.getDirtyFiles() )
    {
      VirtualFile file = path.getVirtualFile();
      String fileName = VssUtil.getCanonicalLocalPath( path.getPath() );

      if( isFileVssProcessable( file ) && isProperNotification( path ) )
      {
        if( host.isFileIgnored( file ))
          filesIgnored.add( fileName );
        else
        {
          //  Do not analyze the file if it is known to be under the paths for
          //  which we know that they are New to the repository.
          //  Uses heuristic - take the file status of the parent folder and
          //  if it is "ADDED" or "UNKNOWN (UNVERSIONED)" then the file is new
          //  as well
          if( isParentFolderNewOrUnversioned( file ) )
          {
            filesNew.add( fileName );
          }
          else
          {
            //  Do not analyze the file if we know that this file just has been
            //  successfully checked out from the repository, its RO status is
            //  writable and it is ready for editing.
            Boolean isCheckoutResult = file.getUserData( SUCCESSFUL_CHECKOUT );
            if( isCheckoutResult != null && isCheckoutResult.booleanValue() )
            {
              //  Do not forget to delete this property right after the change
              //  is classified, otherwise this file will always be determined
              //  as modified.
              file.putUserData( SUCCESSFUL_CHECKOUT, null );
              filesChanged.add( file.getPath() );
            }
            else
            {
              paths.add( path.getPath() );
            }
          }
        }
      }
    }
    analyzeWritableFilesByStatus( paths, filesNew, filesChanged, filesHijacked, filesObsolete );
  }

  private void iterateOverProjectPath( FilePath path )
  {
    LOG.info( "-- ChangeProvider - Iterating over project structure starting from scope root: " + path.getPath() );
    if( progress != null )
      progress.setText( VssBundle.message( "message.statusbar.collect.writables" ) );

    List<String> writableFiles = new ArrayList<String>();
    collectSuspiciousFiles( path, writableFiles );
    LOG.info( "-- ChangeProvider - Found: " + writableFiles.size() + " writable files." );

    if( progress != null )
      progress.setText( VssBundle.message( "message.statusbar.searching.new" ) );
    analyzeWritableFiles( path, writableFiles );
  }

  private void collectSuspiciousFiles( final FilePath filePath, final List<String> writableFiles )
  {
    VirtualFile vf = filePath.getVirtualFile();
    if( vf != null )
    {
      ProjectLevelVcsManager.getInstance(project).iterateVcsRoot( vf, new Processor<FilePath>()
        {
          public boolean process(final FilePath file) {
            String path = file.getPath();
            VirtualFile vFile = file.getVirtualFile();
            if(vFile != null) {
              if( host.isFileIgnored(vFile) )
                filesIgnored.add( path );
              else if (vFile.isWritable() && !vFile.isDirectory() )
                writableFiles.add( path );
            }

            return true;
          }

        }
      );
    }
  }

  private void analyzeWritableFiles( FilePath filePath, List<String> writableFiles )
  {
    final HashSet<String> newFiles = new HashSet<String>();

    if( writableFiles.size() == 0 )
      return;
    
    if( writableFiles.size() < PER_FILE_DIFF_MARGIN )
    {
      LOG.info( "-- ChangeProvider - Analyzing writable files on per-file basis" );
      analyzeWritableFilesByStatus( writableFiles, newFiles, filesChanged, filesHijacked, filesObsolete );
    }
    else
    {
      LOG.info( "-- ChangeProvider - Analyzing writable files on the base of \"Directory\" command" );
      analyzeWritableFilesByDirectory( filePath, writableFiles, newFiles, filesChanged, filesHijacked );
    }

    //  For each new file check whether some subfolders structure above it
    //  is also new.
    if( isBatchUpdate )
    {
      final List<String> newFolders = new ArrayList<String>();
      final HashSet<String> processedFolders = new HashSet<String>();
      for( String file : newFiles )
      {
        if( !isPathUnderProcessedFolders( processedFolders, file ))
          analyzeParentFolderStructureForPresence( file, newFolders, processedFolders );
      }
      filesNew.addAll( newFolders );
    }
    filesNew.addAll( newFiles );
  }

  private void analyzeWritableFilesByStatus( List<String> files,
                                             HashSet<String> newf, HashSet<String> changed,
                                             HashSet<String> hijacked, HashSet<String> obsolete )
  {
    List<String> oldNames = new ArrayList<String>();
    for( String file : files )
    {
      String legalName = discoverOldName( host, file );
      oldNames.add( legalName );
    }

    try
    {
      StatusMultipleCommand cmd = new StatusMultipleCommand( project, oldNames );
      cmd.execute();

      //  If any error occured, most probably it is the critical one - others are
      //  processed on the "by line" basis (and per file correspondingly).
      if( cmd.getErrors().size() > 0 )
      {
        VcsUtil.showErrorMessage( project, cmd.getErrors().get( 0 ).getMessage(),
                                  VssBundle.message("message.title.check.status"));
      }
      else
      {
        for( int i = 0; i < files.size(); i++ )
        {
          if( cmd.isDeleted( oldNames.get( i ) ) )
            obsolete.add( files.get( i ) );
          else
          if( cmd.isNonexist( oldNames.get( i ) ) )
            newf.add( files.get( i ) );
          else
          if( cmd.isCheckedout( oldNames.get( i ) ) )
            changed.add( files.get( i ) );
          else
            hijacked.add( files.get( i ) );
        }
      }
    }
    catch( NullPointerException e )
    {
      LOG.info( "\n*** Found non-convertible paths: ");
      for( String name : oldNames )
      {
        LOG.info( "\t" + name );
      }
      LOG.info( "*** for content root paths: ");
      ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
      VirtualFile[] roots = mgr.getRootsUnderVcs( host );
      for( VirtualFile root : roots )
      {
        LOG.info( "\t" + root.getPath() );
      }
      VssUtil.showErrorOutput( "Internal error occured. Please submit a bug with the IDEA log file attached.", project );
    }
  }

  private void analyzeWritableFilesByDirectory( FilePath filePath, List<String> writableFiles,
                                                HashSet<String> newFiles, HashSet<String> changed,
                                                HashSet<String> hijacked )
  {
    ArrayList<VcsException> errors = new ArrayList<VcsException>();
    DirectoryCommand cmd = new DirectoryCommand( project, filePath.getPath(), errors );
    cmd.execute();

    //  If any error occured, most probably it is the critical one - others are
    //  processed on the "by line" basis (and per file correspondingly).
    if( errors.size() > 0 )
    {
      VcsUtil.showErrorMessage( project, cmd.getErrors().get( 0 ).getMessage(),
                                VssBundle.message("message.title.check.status"));
    }
    else
    {
      for( String path : writableFiles )
      {
        String oldPath = VssChangeProvider.discoverOldName( host, path ).toLowerCase();
        if( !cmd.isInProject( oldPath ) )
          newFiles.add( path );
        else
        if( !cmd.isCheckedOut( oldPath ))
          hijacked.add( path );
        else
          changed.add( path );
      }
    }
  }
  //---------------------------------------------------------------------------
  //  For a given file which is known that it is new, check also its direct
  //  parent folder for presence in the VSS repository, and then all its indirect
  //  parent folders until we reach project boundaries.
  //---------------------------------------------------------------------------
  private void  analyzeParentFolderStructureForPresence( String file, List<String> newFolders,
                                                         HashSet<String> processedFolders )
  {
    String fileParent = new File( file ).getParentFile().getPath();
    String fileParentNorm = VssUtil.getCanonicalLocalPath( fileParent );
    String refParentName = discoverOldName( host, fileParentNorm );

    if( VcsUtil.isPathUnderProject( project, fileParent ) && !processedFolders.contains( fileParent ) )
    {
      processedFolders.add( fileParent );

      if( !isFolderExists( refParentName ) )
      {
        newFolders.add( fileParentNorm );
        analyzeParentFolderStructureForPresence( fileParent, newFolders, processedFolders );
      }
    }
  }

  /**
   * Process exceptions of different kind when normal computation of file
   * statuses is cheated by the IDEA:
   * 1. "Extract Superclass" refactoring with "Rename original class" option set.
   *    Refactoring renamed the original class (right) but writes new content to
   *    the file with the olf name (fuck!).
   *    Remedy: Find such file in the list of "Changed" files, check whether its
   *            name is in the list of New files (from VFSListener), and check
   *            whether its name is in the record for renamed files, then move
   *            it into "New" files list.
   */
  private void processStatusExceptions()
  {
    // 1.
    for( Iterator<String> it = filesChanged.iterator(); it.hasNext(); )
    {
      String fileName = it.next();
      if( host.isNewOverRenamed( fileName ) )
      {
        it.remove();
        filesNew.add( fileName );
      }
    }
  }

  /**
   * File is either:
   * - "new" - it is not contained in the repository, but host contains
   *           a record about it (that is, it was manually moved to the
   *           list of files to be added to the commit.
   * - "unversioned" - it is not contained in the repository yet.
   */
  private void addAddedFiles( final ChangelistBuilder builder )
  {
    for( String fileName : filesNew )
    {
      //  In the case of file rename or parent folder rename we should
      //  refer to the list of new files by the 
      String refName = discoverOldName( host, fileName );

      //  New file could be added AFTER and BEFORE the package rename.
      if( host.containsNew( fileName ) || host.containsNew( refName ))
      {
        FilePath path = VcsUtil.getFilePath( fileName );
        builder.processChange( new Change( null, new CurrentContentRevision( path ) ));
      }
      else
      {
        builder.processUnversionedFile( VcsUtil.getVirtualFile( fileName ) );
      }
    }
  }

  private void addHijackedFiles( final ChangelistBuilder builder )
  {
    for( String fileName : filesHijacked )
    {
      String validRefName = discoverOldName( host, fileName );
      final FilePath fp = VcsUtil.getFilePath( validRefName );
      final FilePath currfp = VcsUtil.getFilePath( fileName );
      VssContentRevision revision = ContentRevisionFactory.getRevision( fp, project );
      builder.processChange( new Change( revision, new CurrentContentRevision( currfp ), FileStatus.HIJACKED ));
    }
  }

  private void addObsoleteFiles( final ChangelistBuilder builder )
  {
    for( String fileName : filesObsolete )
    {
      final FilePath fp = VcsUtil.getFilePath( fileName );
      VssContentRevision revision = ContentRevisionFactory.getRevision( fp, project );
      builder.processChange( new Change( revision, new CurrentContentRevision( fp ), FileStatus.OBSOLETE ));
    }
  }

  /**
   * Add all files which were determined to be changed (somehow - modified,
   * renamed, etc) and folders which were renamed.
   * NB: adding folders information actually works only in either batch refresh
   * of statuses or when some folder is in the list of changes.  
   */
  private void addChangedFiles( final ChangelistBuilder builder )
  {
    for( String fileName : filesChanged )
    {
      String validRefName = discoverOldName( host, fileName );
      final FilePath refPath = VcsUtil.getFilePath( validRefName );
      final FilePath currPath = VcsUtil.getFilePath( fileName );
      
      VssContentRevision revision = ContentRevisionFactory.getRevision( refPath, project );
      builder.processChange( new Change( revision, new CurrentContentRevision( currPath )));
    }

    for( String folderName : host.renamedFolders.keySet() )
    {
      String oldFolderName = host.renamedFolders.get( folderName );
      final FilePath refPath = VcsUtil.getFilePathForDeletedFile( oldFolderName, true );
      final FilePath currPath = VcsUtil.getFilePath( folderName );

      builder.processChange( new Change( new VssContentRevision( refPath, project ), new CurrentContentRevision( currPath )));
    }
  }

  private void addRemovedFiles( final ChangelistBuilder builder )
  {
    for( String path : host.removedFolders )
      builder.processLocallyDeletedFile( VcsUtil.getFilePathForDeletedFile( path, true ) );

    for( String path : host.removedFiles )
      builder.processLocallyDeletedFile( VcsUtil.getFilePathForDeletedFile( path, false ) );

    for( String path : host.deletedFolders )
      builder.processChange( new Change( new CurrentContentRevision( VcsUtil.getFilePathForDeletedFile( path, true )),
                                                                     null, FileStatus.DELETED ));

    for( String path : host.deletedFiles )
    {
      FilePath refPath = VcsUtil.getFilePathForDeletedFile( path, false );
      VssContentRevision revision = ContentRevisionFactory.getRevision( refPath, project );
      builder.processChange( new Change( revision, null, FileStatus.DELETED ));
    }
  }

  private void addIgnoredFiles( final ChangelistBuilder builder )
  {
    for( String path : filesIgnored )
      builder.processIgnoredFile( VcsUtil.getVirtualFile( path ) );
  }

  private boolean isFolderExists( String fileName )
  {
    String fileNameCanonical = VssUtil.getCanonicalLocalPath( fileName );
    PropertiesCommand cmd = new PropertiesCommand( project, fileNameCanonical, true );
    cmd.execute();

    return cmd.isValidRepositoryObject();
  }

  private boolean isParentFolderNewOrUnversioned( VirtualFile file )
  {
    FileStatus status = FileStatus.NOT_CHANGED;
    VirtualFile parent = file.getParent();
    if( parent != null )
    {
      status = FileStatusManager.getInstance( project ).getStatus( parent );
    }
    return (status == FileStatus.ADDED) || (status == FileStatus.UNKNOWN);
  }

  private static boolean isPathUnderProcessedFolders( HashSet<String> folders, String path )
  {
    String parentPathToCheck = new File( path ).getParent();
    for( String folderPath : folders )
    {
      if( FileUtil.pathsEqual( parentPathToCheck, folderPath ))
        return true;
    }
    return false;
  }

  /**
   * For the renamed or moved file we receive two change requests: one for
   * the old file and one for the new one. For renamed file old request differs
   * in filename, for the moved one - in parent path name. This request must be
   * ignored since all preliminary information is already accumulated.
   */
  private static boolean isProperNotification( final FilePath filePath )
  {
    String oldName = filePath.getName();
    String newName = (filePath.getVirtualFile() == null) ? "" : filePath.getVirtualFile().getName();
    String oldParent = (filePath.getVirtualFileParent() == null) ? "" : filePath.getVirtualFileParent().getPath();
    String newParent = filePath.getPath().substring( 0, filePath.getPath().length() - oldName.length() - 1 );
    newParent = VssUtil.getCanonicalLocalPath( newParent );

    //  Check the case when the file is deleted - its FilePath's VirtualFile
    //  component is null and thus new name is empty.
    return newParent.equals( oldParent ) &&
          ( newName.equals( oldName ) || (newName == "" && oldName != "") );
  }

  /**
   * Given the current file path find out its original path:<br>
   * <li>if the file was renamed;
   * <li>if the folder was renamed;
   * <li>if the file resides under the renamed folder.
   */
  public static String discoverOldName( VssVcs hostVcs, String file )
  {
    String oldName = hostVcs.renamedFiles.get( VssUtil.getCanonicalLocalPath( file ) );
    if( oldName == null )
    {
      oldName = hostVcs.renamedFolders.get( VssUtil.getCanonicalLocalPath( file ) );
      if( oldName == null )
      {
        oldName = findOldInRenamedParentFolder( hostVcs, file );
        if( oldName == null )
          oldName = file;
      }
    }

    return oldName;
  }

  private static String findOldInRenamedParentFolder( VssVcs hostVcs, String name )
  {
    String fileInOldFolder = name;
    for( String folder : hostVcs.renamedFolders.keySet() )
    {
      if( name.startsWith( folder ) )
      {
        String oldFolderName = hostVcs.renamedFolders.get( folder );
        fileInOldFolder = oldFolderName + name.substring( folder.length() );
        break;
      }
    }
    return fileInOldFolder;
  }

  /**
   * Given the file path from the repository find out its current path:<br>
   * <li>if the file was renamed;
   * <li>if the folder was renamed;
   * <li>if the file resides under the renamed folder.
   */
  /*
  public static String discoverNewName( VssVcs hostVcs, String file )
  {
    String canonFile = VssUtil.getCanonicalLocalPath( file );
    String oldName = hostVcs.getNewFileFromRenamed( canonFile );
    if( oldName == null )
    {
      oldName = hostVcs.getNewFolderFromRenamed( canonFile );
      if( oldName == null )
      {
        oldName = findNewInRenamedParentFolder( hostVcs, file );
        if( oldName == null )
          oldName = file;
      }
    }

    return oldName;
  }

  private static String findNewInRenamedParentFolder( VssVcs hostVcs, String name )
  {
    String fileInOldFolder = name;
    for( String folder : hostVcs.renamedFolders.keySet() )
    {
      String oldFolderName = hostVcs.renamedFolders.get( folder ).toLowerCase();
      if( name.startsWith( oldFolderName ) )
      {
        fileInOldFolder = folder + name.substring( oldFolderName.length() );
        break;
      }
    }
    return fileInOldFolder;
  }
  */

  private boolean isUnderRenamedFolder( String fileName )
  {
    for( String folder : host.renamedFolders.keySet() )
    {
      if( fileName.startsWith( folder ) )
        return true;
    }
    return false;
  }

  private boolean isBatchUpdate( VcsDirtyScope scope )
  {
    boolean isBatch = false;
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
    VirtualFile[] roots = mgr.getRootsUnderVcs( host );
    for( FilePath path : scope.getRecursivelyDirtyDirectories() )
    {
      for( VirtualFile root : roots )
      {
        VirtualFile vfScopePath = path.getVirtualFile();
        //  VFile may be null in the case of deleted folders (IDEADEV-18855)
        isBatch = isBatch || (vfScopePath != null &&
                              vfScopePath.getPath().equalsIgnoreCase( root.getPath() ) );
      }
    }
    return isBatch;
  }
  
  private void initInternals()
  {
    filesNew.clear();
    filesHijacked.clear();
    filesChanged.clear();
    filesObsolete.clear();
    filesIgnored.clear();
  }

  /**
   * Return true if:
   * - file is not null & writable
   * - file is not a folder
   * - files is under the project
   */
  private boolean isFileVssProcessable( VirtualFile file )
  {
    return (file != null) && file.isWritable() && !file.isDirectory() &&
           VcsUtil.isPathUnderProject( project, file.getPath() );
  }

  /**
   * Do not perform any actions if:
   * - we have no VSS-related content roots configured, or
   * - roots are configured inconsistently, e.g. no VSS project is set. 
   */
  private boolean checkDirectoryMappings()
  {
    boolean checkPassed = true;
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
    List<VcsDirectoryMapping> mappings = mgr.getDirectoryMappings( host );

    if( mappings.size() == 0 )
    {
      checkPassed = false;
    }
    else
    {
      for( VcsDirectoryMapping mapping : mappings )
      {
        VssRootSettings settings = (VssRootSettings)mapping.getRootSettings();
        if( settings == null || StringUtil.isEmptyOrSpaces( settings.getVssProject() ) )
        {
          checkPassed = false;
          break;
        }
      }
    }

    if( !checkPassed && showInvalidConfigMessage )
    {
      VcsUtil.showErrorMessage( project, VssBundle.message( "message.text.specify.content.roots" ),
                                         VssBundle.message( "message.text.operation.failed" ) );
      showInvalidConfigMessage = false;
    }
    
    return checkPassed;
  }

  private boolean checkCommandPath()
  {
    VssConfiguration config = VssConfiguration.getInstance( project );
    final String checkMsg = config.checkCmdPath();
    if( checkMsg != null )
    {
      VcsUtil.showErrorMessage( project, checkMsg, VssBundle.message( "message.text.operation.failed" ) );
    }
    return checkMsg == null;
  }

  private void validateChangesOverTheHost( final VcsDirtyScope scope )
  {
    ApplicationManager.getApplication().runReadAction( new Runnable() {
      public void run() {
        //  protect over being called in the forbidden phase
        if( !project.isDisposed() )
        {
          HashSet<FilePath> set = new HashSet<FilePath>();
          set.addAll( scope.getDirtyFiles() );
          set.addAll( scope.getRecursivelyDirtyDirectories() );

          ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
          for( FilePath path : set )
          {
            AbstractVcs fileHost = mgr.getVcsFor( path );
            LOG.assertTrue( fileHost == host, "Not valid scope for current Vcs: " + path.getPath() );
          }
        }
      }
    });
  }

  private static void logChangesContent( final VcsDirtyScope scope )
  {
    LOG.info( "-- ChangeProvider: Dirty files: " + scope.getDirtyFiles().size() +
              ", dirty recursive directories: " + scope.getRecursivelyDirtyDirectories().size() );
    for( FilePath path : scope.getDirtyFiles() )
      LOG.info( "                                " + path.getPath() );
    LOG.info( "                                ---" );
    for( FilePath path : scope.getRecursivelyDirtyDirectories() )
      LOG.info( "                                " + path.getPath() );
  }
}
