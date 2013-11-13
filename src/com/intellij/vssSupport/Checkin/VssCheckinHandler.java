package com.intellij.vssSupport.Checkin;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vssSupport.VssBundle;
import com.intellij.vssSupport.VssVcs;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 16, 2007
 */
public class VssCheckinHandler extends CheckinHandler {
  private final VssVcs host;
  private final CheckinProjectPanel panel;

  public VssCheckinHandler( VssVcs host, final CheckinProjectPanel panel )
  {
    this.host = host;
    this.panel = panel;
  }

  public ReturnResult beforeCheckin()
  {
    Collection<VirtualFile> files = panel.getVirtualFiles();
    Set<VirtualFile> set = new HashSet<VirtualFile>();

    //  Add those folders which are renamed and are parents for the files
    //  marked for checkin.
    for( VirtualFile file : files )
    {
      for( String newFolderName : host.renamedFolders.keySet() )
      {
        if( file.getPath().startsWith( newFolderName ) )
        {
          VirtualFile parent = VcsUtil.getVirtualFile( newFolderName );
          set.add( parent );
        }
      }
    }

    //  Remove all folders which are marked for checkin, leave only those
    //  which are absent in the list.
    for( VirtualFile file : files )
      set.remove( file );
    
    if( set.size() > 0 )
    {
      int result = Messages.showOkCancelDialog( VssBundle.message("message.add.renamed.folders"),
                                                VssBundle.message("message.add.renamed.folders.title"),
                                                Messages.getWarningIcon() );
      if( result != Messages.OK )
        return ReturnResult.CANCEL;
    }
    return ReturnResult.COMMIT;
  }
}
