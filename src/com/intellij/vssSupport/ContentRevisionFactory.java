package com.intellij.vssSupport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 14, 2007
 */
public class ContentRevisionFactory
{
  private static final VFSKeysListener listener;
  private static final HashMap<FilePath, VssContentRevision> cachedRevisions;

  static
  {
    cachedRevisions = new HashMap<>();
    listener = new VFSKeysListener();
    LocalFileSystem.getInstance().addVirtualFileListener( listener );
  }

  public static void detachListeners()
  {
    LocalFileSystem.getInstance().removeVirtualFileListener( listener );
  }

  private ContentRevisionFactory() {}

  public static VssContentRevision getRevision( @NotNull FilePath path, Project project )
  {
    VssContentRevision revision = cachedRevisions.get( path );
    if( revision == null )
    {
      revision = new VssContentRevision( path, project );
      cachedRevisions.put( path, revision );
    }
    return revision;
  }

  public static void clearCacheForFile( String file )
  {
    FilePath path = VcsUtil.getFilePath( file );
    cachedRevisions.remove( path );
  }

  private static class VFSKeysListener extends VirtualFileAdapter
  {
    public VFSKeysListener() {}

    public void beforeFileMovement( @NotNull VirtualFileMoveEvent e )
    {
      String oldPath = e.getOldParent().getPath() + "/" + e.getFileName();
      analyzeEvent( oldPath );
    }

    public void beforePropertyChange( @NotNull VirtualFilePropertyEvent e )
    {
      final VirtualFile parent = e.getFile().getParent();
      if (parent == null) return;
      String oldName = parent.getPath() + "/" + e.getOldValue();
      analyzeEvent( oldName );
    }

    private static void analyzeEvent( String filePath )
    {
      FilePath path = VcsUtil.getFilePath( filePath );
      VssContentRevision revision = cachedRevisions.get( path );
      if( revision != null )
      {
        cachedRevisions.remove( path );
      }

      //  If the given path is a folder, we need to remove cached revisions
      //  for ALL files under that folder since all of them will change
      //  VirtualFile value inside their FilePath keys.
      if( path.isDirectory() )
      {
        List<FilePath> keys = new ArrayList<>(cachedRevisions.keySet());
        for( FilePath file : keys )
        {
          if( file.isUnder( path, false ) )
            cachedRevisions.remove( file );
        }
      }
    }
  }
}
