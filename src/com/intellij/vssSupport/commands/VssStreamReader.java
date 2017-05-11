package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.impl.CancellableRunnable;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.vssSupport.VssBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public final class VssStreamReader implements CancellableRunnable
{
  @NonNls private static final String NO_DATABASE_MESSAGE = "No VSS database";
  @NonNls private static final String NO_ACCESS_RIGHTS_MESSAGE = "do not have access rights";
  @NonNls private static final String USERNAME_PREFIX = "Username:";

  private final ByteArrayOutputStream myByteContents;
  private final InputStream is;
  @NotNull private final Project myProject;
  private String myOutput;
  private String reason;

  public VssStreamReader(final InputStream is, @NotNull Project project)
  {
    this.is = is;
    myProject = project;
    myByteContents = new ByteArrayOutputStream();
    reason = null;
  }

  public String getReason()  {  return reason;  }

  public void run() {
    byte[] buffer = new byte[8 * 1024];
    try {
      int read;
      while (((read = is.read(buffer, 0, buffer.length)) != -1))
      {
        myByteContents.write(buffer, 0, read);
        if( buffer[ read - 1 ] == '\n' )
          checkTextAvailable();
      }
    }
    catch (IOException ioe) {
    }
  }

  public String getReadString()
  {
    if( myOutput == null )
    {
      try {
        myOutput = StringUtil.convertLineSeparators( myByteContents.toString( EncodingProjectManager.getInstance(myProject).getDefaultCharset().name() ) );
      }
      catch( UnsupportedEncodingException e )
      {
      }
    }

    return myOutput;
  }

  private void checkTextAvailable()
  {
    String text = StringUtil.convertLineSeparators( myByteContents.toString() );
    if( text.startsWith( USERNAME_PREFIX ))
      reason = VssBundle.message("exception.text.incorrect.username.or.password");
    else
    if( text.indexOf( NO_DATABASE_MESSAGE ) != -1 )
      reason = VssBundle.message("exception.text.no.database.found");
    else
    if( text.indexOf( NO_ACCESS_RIGHTS_MESSAGE ) != -1 )
      reason = VssBundle.message("exception.text.no.rights.to.perform.operation");
  }

  public void cancel() {
  }
}
