/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.vssSupport;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vssSupport.commands.GetFileCommand;
import com.intellij.vssSupport.commands.HistoryCommand;
import com.intellij.vssSupport.commands.HistoryParser;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 */
public class VssFileHistoryProvider implements VcsHistoryProvider
{
  @NonNls private final static String DATE_COLUMN = "Date";
  @NonNls private final static String ACTION_COLUMN = "Action";
  @NonNls private final static String LABEL_COLUMN = "Label";

  private static final Logger LOG = Logger.getInstance("#com.intellij.vssSupport.VssFileHistoryProvider");

  private final Project project;

  private static final ColumnInfo<VcsFileRevision, String> DATE = new ColumnInfo<VcsFileRevision, String>( DATE_COLUMN )
  {
    public String valueOf(VcsFileRevision vcsFileRevision) {
      if (!(vcsFileRevision instanceof VssFileRevision)) return "";
      return ((VssFileRevision) vcsFileRevision).getDate();
    }

    public Comparator<VcsFileRevision> getComparator()
    {
      return (o1, o2) -> {
        if (!(o1 instanceof VssFileRevision)) return 0;
        if (!(o2 instanceof VssFileRevision)) return 0;

        return ((VssFileRevision) o1).compareTo( o2 );
      };
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> ACTION = new ColumnInfo<VcsFileRevision, String>( ACTION_COLUMN )
  {
    public String valueOf(VcsFileRevision vcsFileRevision) {
      if (!(vcsFileRevision instanceof VssFileRevision)) return "";
      return ((VssFileRevision) vcsFileRevision).getAction();
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> LABEL = new ColumnInfo<VcsFileRevision, String>( LABEL_COLUMN )
  {
    public String valueOf(VcsFileRevision vcsFileRevision) {
      if (!(vcsFileRevision instanceof VssFileRevision)) return "";
      return ((VssFileRevision) vcsFileRevision).getLabel();
    }
  };

  public VssFileHistoryProvider( Project project )
  {
    this.project = project;
  }

  @NonNls @Nullable
  public String getHelpId() {  return null;  }

  public boolean supportsHistoryForDirectories() {
    return false;
  }

  @Override
  public DiffFromHistoryHandler getHistoryDiffHandler() {
    return null;
  }

  @Override
  public boolean canShowHistoryFor(@NotNull VirtualFile file) {
    return true;
  }

  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {  return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[] { DATE, ACTION, LABEL });   }

  public AnAction[] getAdditionalActions(final Runnable refresher) {  return AnAction.EMPTY_ARRAY;   }

  public boolean isDateOmittable() {  return true;  }

  public VcsHistorySession createSessionFor( FilePath filePath ) throws VcsException
  {
    List<VcsException> errors = new ArrayList<>();
    try
    {
      //  Take care of renamed files - refer to the original file name in
      //  the repository.
      VssVcs host = VssVcs.getInstance( project );
      String path = filePath.getPath();
      if( host.renamedFiles.containsKey( path ))
        path = host.renamedFiles.get( path );

      HistoryCommand cmd = new HistoryCommand( project, path, errors );
      cmd.execute();

      if( errors.size() > 0 )
        throw errors.get( 0 );

      ArrayList<HistoryParser.SubmissionData> changes = cmd.changes;
      ArrayList<VcsFileRevision> revisions = new ArrayList<>();
      for( HistoryParser.SubmissionData change : changes )
      {
        VcsFileRevision rev = new VssFileRevision( change, filePath );
        revisions.add( rev );
      }

      return new VssHistorySession( revisions );
    }
    catch( Throwable e )
    {
      //  This is one of the potential problems. And most common.
      throw new VcsException( VssBundle.message("message.file.deleted.or.not.in.repository") );
    }
  }

  public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VcsHistorySession session = createSessionFor(path);
    partner.reportCreatedEmptySession((VcsAbstractHistorySession) session);
  }

  private class VssFileRevision implements VcsFileRevision
  {
    private final int    version;
    private final int    order;
    private final String submitter;
    private final String comment;
    private final String action;
    private final String label;
    private final String vssDate;

    private final FilePath path;
    private byte[] content;

    public VssFileRevision( HistoryParser.SubmissionData data, FilePath path )
    {
      version = Integer.parseInt( data.version );
      action = data.action;
      label = data.label;
      submitter = data.submitter;
      comment = data.comment;
      order = data.order;
      vssDate = data.changeDate;

      this.path = path;
    }

    @Nullable
    @Override
    public RepositoryLocation getChangedRepositoryPath() {
      return null;
    }

    public VcsRevisionNumber getRevisionNumber() { return new VcsRevisionNumber.Int( version ); }
    public String getBranchName() { return null;   }
    public Date getRevisionDate() { return null;   }
    public int    getOrder()      { return order;  }
    public String getDate()       { return vssDate;}
    public String getAction()     { return action; }
    public String getLabel()      { return label;  }
    public String getAuthor()     { return submitter; }
    public String getCommitMessage() { return comment; }

    public byte[] getContent() throws IOException, VcsException { return content; }

    public byte[] loadContent() throws IOException, VcsException
    {
      ArrayList<VcsException> errors = new ArrayList<>();
      String tmpDir = FileUtil.getTempDirectory();
      
      GetFileCommand cmd = new GetFileCommand( project, path.getPath(), Integer.toString( version ), errors );
      cmd.setOutputPath( tmpDir );
      cmd.execute();

      File fileContent = new File( tmpDir, path.getName() );
      content = ArrayUtil.EMPTY_BYTE_ARRAY;
      try
      {
        content = FileUtil.loadFileBytes( fileContent );
        fileContent.delete();
      }
      catch( IOException e ){
        LOG.error( e.getMessage() );
      }
      return content;
    }

    public int compareTo( Object revision )
    {
      int revOrder = ((VssFileRevision)revision).getOrder();
      if( order > revOrder )
        return -1;
      else
      if( order < revOrder )
        return 1;
      else
        return 0;
    }
  }

  private static class VssHistorySession extends VcsAbstractHistorySession
  {
    public VssHistorySession( List<VcsFileRevision> revs )
    {
      super( revs );
    }

    protected VcsRevisionNumber calcCurrentRevisionNumber()
    {
      VcsRevisionNumber revision;
      try
      {
        int maxRevision = 0;
        for( VcsFileRevision rev : getRevisionList() )
        {
          maxRevision = Math.max( maxRevision, ((VssFileRevision)rev).getOrder() );
        }
        revision = new VcsRevisionNumber.Int( maxRevision + 1 );
      }
      catch( Exception e )
      {
        //  We can catch e.g. com.starbase.starteam.ItemNotFoundException if we
        //  try to show history records for the deleted file.
        revision = VcsRevisionNumber.NULL;
      }
      return revision;
    }

    public HistoryAsTreeProvider getHistoryAsTreeProvider() {
      return null;
    }

    @Override
    public synchronized boolean shouldBeRefreshed() {
      // Don't refresh history by timer - this is too expensive performance-wise
      return false;
    }

    @Override
    public VcsHistorySession copy() {
      return new VssHistorySession(getRevisionList());
    }
  }
}
