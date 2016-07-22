package com.intellij.vssSupport.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Mar 19, 2007
 */
public abstract class VssCheckoutAbstractCommand extends VssCommandAbstract 
{
  @NonNls public static final String CHECKED_OUT_BY_ANOTHER_USER_MESSAGE = "is checked out by";
  @NonNls public static final String HAVE_FILE_MESSAGE = "currently have file";
  @NonNls public static final String NOT_EXISTING_MESSAGE = "is not an existing";
  @NonNls public static final String DELETED_MESSAGE = "has been deleted";
  @NonNls public static final String ALREADY_CHECKED_MESSAGE = "currently have file ";
  @NonNls public static final String CHECKED_OUT_SUFFIX = " checked out";
  @NonNls public static final String WRITABLE_COPY_MESSAGE = "writable copy of ";

  public HashSet<String>  successFiles = new HashSet<>();
  public HashSet<String>  checkedAlready = new HashSet<>();
  public HashSet<String>  checkedByOther = new HashSet<>();
  public HashSet<String>  writableFiles = new HashSet<>();
  public HashSet<String>  deletedFiles = new HashSet<>();
  public HashSet<String>  notexistingFiles = new HashSet<>();

  public static final Key<Boolean> SUCCESSFUL_CHECKOUT = new Key<>("CHECKOUT_SUCCESS");

  public VssCheckoutAbstractCommand( Project project, List<VcsException> errors )
  {
    super( project, errors );
  }
}
