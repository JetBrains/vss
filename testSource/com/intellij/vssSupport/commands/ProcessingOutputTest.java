package com.intellij.vssSupport.commands;

import junit.framework.TestCase;

/**
 * author: lesya
 */
public class ProcessingOutputTest extends TestCase{
  public void testFileCheckedOutByAnotherUser(){
    String userName = CheckoutFileCommand.getUserNameFrom("File.txt is checked out by User Name; deleting it will cancel the check out. Continue?");
    assertEquals("User Name", userName);

    userName = CheckoutFileCommand.getUserNameFrom("File.txt is checked out by user; deleting it will cancel the check out. Continue?");
    assertEquals("user", userName);

  }
}
