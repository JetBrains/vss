package com.intellij.vssSupport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 * @author Michael Gerasimov
 */
public class VssUtil extends VcsUtil
{
   //  Something went wrong. For example, VSS could not find its data files, or a
   //  file you want to check out is already checked out.
  public static final int EXIT_CODE_FAILURE = 100;

  //  Indicates a milder sort of failure, and occurs in three circumstances:
  //  When you run ss Dir and no items are found. When you run ss Status and at
  //  least one item is checked out. When you run ss Diff, and at least one
  //  file is different. All of these circumstances indicate that your next VSS
  //  command may fail, even though this command ran successfully.
  public static final int EXIT_CODE_WARNING = 1;

  //  VSS executed successfully.
  public static final int EXIT_CODE_SUCCESS = 0;

  public static String getCanonicalVssPath(String vssPath)
  {
    vssPath = chopTrailingChars( vssPath.trim().replace('\\', '/').toLowerCase(), ourCharsToBeChopped );
    if( "$".equals( vssPath ) )
      vssPath = "$/";
    return vssPath;
  }

  /**
   * If we know EXACTLY that this path belongs to the folder, trim the possible
   * slash/backslash from the end due to the requirements of other parts of the
   * plugin's logic.
   * NB: most urgent usage is in normalizing VcsContentRoot directory paths. 
   */
  public static String normalizeDirPath( final String directory )
  {
    if( directory.endsWith( "/" ) || directory.endsWith( "\\" ) )
      return directory.substring( 0, directory.length() - 2 );
    else
      return directory; 
  }

  /**
   * @return full local path for specified VSS item. Retruns <code>null</code>
   *         if local path cannot be resolved.
   */
  @Nullable
  public static String getLocalPath( String vssPath, Project project )
  {
    VcsDirectoryMapping nearestItem = getNearestMapItemForVssPath( vssPath, project );
    if( nearestItem == null )
      return null;

    String vssProject = ((VssRootSettings)nearestItem.getRootSettings()).getVssProject();
    String pathDifference = vssPath.substring( vssProject.length() );
    StringBuffer sb = new StringBuffer( nearestItem.getDirectory() );
    if( !StringUtil.endsWithChar( nearestItem.getDirectory(), '/'))
      sb.append("/");

    if( StringUtil.startsWithChar( pathDifference, '/') )
    {
      if( pathDifference.length() > 1 )
        sb.append( pathDifference.substring( 1 ) );
    }
    else
    {
      sb.append( pathDifference );
    }
    String localPath = sb.toString();
    if (!StringUtil.endsWithChar(localPath, '/')) {
      return localPath.replace('/', File.separatorChar);
    }
    else {
      return localPath.substring(0, localPath.length() - 1).replace('/', File.separatorChar);
    }
  }

  /**
   * @return nearest <code>MapItem</code> to the specified <code>vssPath</code>. Returns
   *         <code>null</code> if there is no any item found.
   */
  @Nullable
  private static VcsDirectoryMapping getNearestMapItemForVssPath( String vssPath, Project project )
  {
    vssPath = vssPath.toLowerCase();

    String vssProject = null;
    VcsDirectoryMapping nearestMapping = null;

    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
    List<VcsDirectoryMapping> roots = mgr.getDirectoryMappings( VssVcs.getInstance( project ) );
    
    for( VcsDirectoryMapping mapping : roots )
    {
      VssRootSettings vssProjectRoot = (VssRootSettings)mapping.getRootSettings();
      if( StringUtil.startsWithIgnoreCase( vssPath, vssProjectRoot.getVssProject() ) &&
         ( nearestMapping == null || vssProject.length() < vssProjectRoot.getVssProject().length() ))
      {
        nearestMapping = mapping;
        vssProject = vssProjectRoot.getVssProject();
      }
    }
    return nearestMapping;
  }

  /**
   * @return VSS path for the specified local file or <code>null</code> if
   *         the file isn't under VSS control.
   */
  @Nullable
  public static String getVssPath( File localFile, Project project ) {
    FilePath path = VcsUtil.getFilePath( localFile );
    return getVssPath( path, project );
  }

  /**
   * @return VSS path for the specified virtual file or <code>null</code>
   *         if virtual file isn't under VSS control.
   */
  @Nullable
  public static String getVssPath( VirtualFile file, Project project) {
    FilePath path = VcsUtil.getFilePath( file.getPath(), file.isDirectory() );
    return getVssPath( path, project );
  }

  @Nullable
  public static String getVssPath( String localPath, boolean isDirectory, Project project ) {
    FilePath path = VcsUtil.getFilePath( localPath, isDirectory );
    return getVssPath( path, project );
  }

  /**
   * @param localPath local path with UNIX separator chars.
   */
  @Nullable
  public static String getVssPath( FilePath localPath, Project project )
  {
    VcsDirectoryMapping rootMapping = ProjectLevelVcsManager.getInstance( project ).getDirectoryMappingFor( localPath );
    if( rootMapping == null || rootMapping.getRootSettings() == null )
      return null;

    VirtualFile rootFile = ProjectLevelVcsManager.getInstance( project ).getVcsRootFor( localPath );
    String rootPath = rootFile.getPath();
    String pathDifference = localPath.getPath().substring( rootPath.length() ).replace('\\', '/');
    String rootVssPath = ((VssRootSettings)rootMapping.getRootSettings()).getVssProject();
    StringBuffer vssPath = new StringBuffer( rootVssPath );
    if( !StringUtil.endsWithChar( rootVssPath, '/' ) )
      vssPath.append('/');

    if( StringUtil.startsWithChar( pathDifference, '/') ) {
      if (pathDifference.length() > 1)
        vssPath.append( pathDifference.substring( 1 ) );
    }
    else {
      vssPath.append( pathDifference );
    }
    return vssPath.toString();
  }

  public static void showErrorOutput( @NonNls String message, Project project) {
    showErrorMessage(project, VssBundle.message("message.text.operation.failed.text", message), VssBundle.message("message.title.error"));
  }
}
