/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.vssSupport.commands;

import junit.framework.TestCase;

import java.util.ArrayList;

public class VssHistoryParserTest extends TestCase
{
  public void testCase0()
  {
    String testString ="History of $/VssTest ...\n\n" + "*****  file3.java  *****\n" + "Version 3\n" +
                 "User: Lloix        Date: 24-04-06   Time:  6:33p\n" + "Checked in $/vsstest/src/DIR2\n" + "\n" +
                 "*****  file3.java  *****\n" + "Version 2\n" + "User: Lloix        Date: 24-04-06   Time:  6:33p\n" +
                 "Checked in $/VssTest/SRC/DIR2\n" + "\n" + "*****  DIR2  *****\n" + "Version 3\n" +
                 "User: Lloix        Date: 24-04-06   Time:  2:32p\n" + "file3.java added\n";
    ArrayList<HistoryParser.SubmissionData> changes = HistoryParser.parse( testString );
    assertTrue( "Amount of changes is invalid", changes.size() == 3 );
    for( HistoryParser.SubmissionData change : changes )
    {
      assertTrue( "User is invalid", change.submitter.equals("Lloix") );
    }
  }
  public void testCase1()
  {
    String test1 = "Building list for $/VssTest...\n" + "\n" +
                   "*****  DIRINTERNAL  *****\n" +
                   "Version 1\n" +
                   "User: Lloix        Date: 29-05-06   Time:  6:34p\n" +
                   "Created\n" +
                   "Comment: \n" +"\n" +
                   "*****  DIR2  *****\n" +
                   "Version 6\n" +
                   "User: Lloix        Date: 29-05-06   Time:  6:34p\n" +
                   "$DIRINTERNAL added\n" + "\n" +
                   "*****  DIR2  *****\n" + "Version 5\n" + "User: Lloix        Date: 29-05-06   Time:  2:17p\n" + "File5.java added\n" +
                   "\n" + "*****  File2.java  *****\n" + "Version 5\n" + "User: Lloix        Date: 29-05-06   Time:  1:29p\n" +
                   "Checked in $/vsstest/src/DIR2\n" + "\n" + "*****  SRC  *****\n" + "Version 57\n" +
                   "User: Lloix        Date: 10-05-06   Time:  6:10p\n" + "ConsoleOutputNotInVss2.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 56\n" + "User: Lloix        Date: 10-05-06   Time:  6:10p\n" +
                   "ConsoleOutputNotInVss.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 55\n" +
                   "User: Lloix        Date: 10-05-06   Time:  6:09p\n" + "ConsoleOutputNotInVss.java deleted\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 54\n" + "User: Lloix        Date: 10-05-06   Time:  6:09p\n" +
                   "ConsoleOutputNotInVss.java purged\n" + "\n" + "*****  SRC  *****\n" + "Version 53\n" +
                   "User: Lloix        Date: 10-05-06   Time:  6:09p\n" + "ConsoleOutputNotInVss2.java deleted\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 52\n" + "User: Lloix        Date: 10-05-06   Time:  6:09p\n" +
                   "ConsoleOutputNotInVss2.java purged\n" + "\n" + "*****************  Version 17  *****************\n" +
                   "User: Lloix        Date: 10-05-06   Time:  6:08p\n" + "ConsoleOutputNotInVss2.java deleted\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 51\n" + "User: Lloix        Date: 10-05-06   Time:  6:01p\n" +
                   "ConsoleOutputNotInVss2.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 50\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:59p\n" + "ConsoleOutputNotInVss.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 49\n" + "User: Lloix        Date: 10-05-06   Time:  5:59p\n" +
                   "ConsoleOutputNotInVss.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 48\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:59p\n" + "ConsoleOutputNotInVss.java purged\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 47\n" + "User: Lloix        Date: 10-05-06   Time:  5:59p\n" +
                   "ConsoleOutputNotInVss2.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 46\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:59p\n" + "ConsoleOutputNotInVss2.java purged\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 45\n" + "User: Lloix        Date: 10-05-06   Time:  5:40p\n" +
                   "ConsoleOutputNotInVss.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 44\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:37p\n" + "ConsoleOutputNotInVss2.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 43\n" + "User: Lloix        Date: 10-05-06   Time:  5:36p\n" +
                   "ConsoleOutputNotInVss.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 42\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:36p\n" + "ConsoleOutputNotInVss.java purged\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 41\n" + "User: Lloix        Date: 10-05-06   Time:  5:36p\n" +
                   "ConsoleOutputNotInVss2.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 40\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:36p\n" + "ConsoleOutputNotInVss2.java purged\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 39\n" + "User: Lloix        Date: 10-05-06   Time:  5:36p\n" +
                   "ConsoleOutputNotInVss.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 38\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:28p\n" + "ConsoleOutputNotInVss2.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 37\n" + "User: Lloix        Date: 10-05-06   Time:  5:28p\n" +
                   "ConsoleOutputNotInVss2.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 36\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:27p\n" + "ConsoleOutputNotInVss2.java recovered\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 35\n" + "User: Lloix        Date: 10-05-06   Time:  5:02p\n" +
                   "ConsoleOutputNotInVss.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 34\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:02p\n" + "ConsoleOutputNotInVss.java purged\n" + "\n" +
                   "*****************  Version 16  *****************\n" + "User: Lloix        Date: 10-05-06   Time:  5:02p\n" +
                   "ConsoleOutputNotInVss2.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 33\n" +
                   "User: Lloix        Date: 10-05-06   Time:  5:01p\n" + "ConsoleOutputNotInVss2.java deleted\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 32\n" + "User: Lloix        Date: 10-05-06   Time:  5:01p\n" +
                   "ConsoleOutputNotInVss2.java purged\n" + "\n" + "*****  SRC  *****\n" + "Version 31\n" +
                   "User: Lloix        Date: 10-05-06   Time:  4:51p\n" + "ConsoleOutputNotInVss2.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 30\n" + "User: Lloix        Date: 10-05-06   Time:  4:51p\n" +
                   "ConsoleOutputNotInVss2.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 29\n" +
                   "User: Lloix        Date: 10-05-06   Time:  4:51p\n" + "ConsoleOutputNotInVss2.java purged\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 28\n" + "User: Lloix        Date: 10-05-06   Time:  4:30p\n" +
                   "ConsoleOutputNotInVss2.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 27\n" +
                   "User: Lloix        Date: 10-05-06   Time:  4:30p\n" + "ConsoleOutputNotInVss2.java deleted\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 26\n" + "User: Lloix        Date: 10-05-06   Time:  4:30p\n" +
                   "ConsoleOutputNotInVss2.java purged\n" + "\n" + "*****  SRC  *****\n" + "Version 25\n" +
                   "User: Lloix        Date: 10-05-06   Time:  3:59p\n" + "ConsoleOutputNotInVss2.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 24\n" + "User: Lloix        Date: 10-05-06   Time:  3:57p\n" +
                   "ConsoleOutputNotInVss2.java destroyed\n" + "\n" + "*****  SRC  *****\n" + "Version 23\n" +
                   "User: Lloix        Date: 10-05-06   Time:  3:56p\n" + "ConsoleOutputNotInVss2.java added\n" + "\n" +
                   "*****************  Version 15  *****************\n" + "User: Lloix        Date: 10-05-06   Time:  3:25p\n" +
                   "ConsoleOutputNotInVss.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 22\n" +
                   "User: Lloix        Date: 10-05-06   Time:  3:13p\n" + "ConsoleOutputNotInVss2.java deleted\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 21\n" + "User: Lloix        Date: 10-05-06   Time:  3:13p\n" +
                   "ConsoleOutputNotInVss2.java purged\n" + "\n" + "*****************  Version 14  *****************\n" +
                   "User: Lloix        Date: 10-05-06   Time:  3:12p\n" + "ConsoleOutputNotInVss.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 20\n" + "User: Lloix        Date: 10-05-06   Time:  3:04p\n" +
                   "ConsoleOutputNotInVss.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 19\n" +
                   "User: Lloix        Date: 10-05-06   Time:  2:55p\n" + "ConsoleOutputNotInVss2.java added\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 18\n" + "User: Lloix        Date: 10-05-06   Time:  2:38p\n" +
                   "ConsoleOutputNotInVss.java deleted\n" + "\n" + "*****  SRC  *****\n" + "Version 17\n" +
                   "User: Lloix        Date: 10-05-06   Time:  2:38p\n" + "ConsoleOutputNotInVss2.java deleted\n" + "\n" +
                   "*****  SRC  *****\n" + "Version 16\n" + "User: Lloix        Date: 10-05-06   Time:  2:32p\n" +
                   "ConsoleOutputNotInVss2.java added\n" + "\n" + "*****  SRC  *****\n" + "Version 15\n" +
                   "User: Lloix        Date: 10-05-06   Time:  2:25p\n" + "ConsoleOutputNotInVss.java added\n" + "\n" +
                   "*****  ConsoleOutput1.java  *****\n" + "Version 3\n" + "User: Lloix        Date: 10-05-06   Time:  2:25p\n" +
                   "Checked in $/VssTest/SRC\n" + "\n" + "*****  File2.java  *****\n" + "Version 4\n" +
                   "User: Lloix        Date: 10-05-06   Time:  2:25p\n" + "Checked in $/vsstest/src/DIR2\n" + "\n" +
                   "*****  DIR3  *****\n" + "Version 1\n" + "User: Lloix        Date: 27-04-06   Time:  6:48p\n" + "Created\n" +
                   "Comment: \n" + "\n" + "*****  SRC  *****\n" + "Version 14\n" + "User: Lloix        Date: 27-04-06   Time:  6:48p\n" +
                   "$DIR3 added\n" + "\n" + "*****  file3.java  *****\n" + "Version 4\n" +
                   "User: Lloix        Date: 26-04-06   Time:  9:34p\n" + "Checked in $/vsstest/src/DIR2\n" + "\n" +
                   "*****  File2.java  *****\n" + "Version 3\n" + "User: Lloix        Date: 26-04-06   Time:  8:51p\n" +
                   "Checked in $/vsstest/src/DIR2\n" + "\n" + "*****  ConsoleOutput3.java  *****\n" + "Version 2\n" +
                   "User: Lloix        Date: 26-04-06   Time:  7:47p\n" + "Checked in $/vsstest/src\n" + "\n" +
                   "*****  File2.java  *****\n" + "Version 2\n" + "User: Lloix        Date: 26-04-06   Time:  7:47p\n" +
                   "Checked in $/vsstest/src/DIR2\n" + "\n" + "*****  file4.java  *****\n" + "Version 2\n" +
                   "User: Lloix        Date: 24-04-06   Time:  6:51p\n" + "Checked in $/VssTest/SRC/DIR2\n" + "Comment: \n" + "\n" +
                   "*****  DIR2  *****\n" + "Version 4\n" + "User: Lloix        Date: 24-04-06   Time:  6:47p\n" + "file4.java added\n" +
                   "\n" + "*****  file3.java  *****\n" + "Version 3\n" + "User: Lloix        Date: 24-04-06   Time:  6:33p\n" +
                   "Checked in $/vsstest/src/DIR2\n" + "\n" + "*****  file3.java  *****\n" + "Version 2\n" +
                   "User: Lloix        Date: 24-04-06   Time:  6:33p\n" + "Checked in $/VssTest/SRC/DIR2\n" + "\n" +
                   "*****  DIR2  *****\n" + "Version 3\n" + "User: Lloix        Date: 24-04-06   Time:  2:32p\n" + "file3.java added\n";
    ArrayList<HistoryParser.SubmissionData> changes = HistoryParser.parse( test1 );
    assertTrue( "Amount of changes is invalid", changes.size() == 64 );
    for( HistoryParser.SubmissionData change : changes )
    {
      assertTrue( "User is valid", change.submitter.equals("Lloix") );
    }
  }
}
