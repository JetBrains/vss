/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.vssSupport.commands;

import com.intellij.vssSupport.DiffDirParser;
import junit.framework.TestCase;

public class VssDiffDirParserTest extends TestCase
{
  public void testCase0()
  {
    String testString = "Diffing: $/vsstest\n" + "Against: D:\\Projects\\VssDev\n" + "\n" + "Local files not in the current project:\n" +
                        "  out  VssTest.iml  VssTest.ipr  VssTest.iws\n" + "Diffing: $/vsstest/SRC\n" +
                        "Against: D:\\Projects\\VssDev\\SRC\n" + "\n" + "Local files not in the current project:\n" + "  out\n" +
                        "Diffing: $/vsstest/SRC/DIR2\n" + "Against: D:\\Projects\\VssDev\\SRC\\DIR2\n" + "\n" +
                        "SourceSafe files different from local files:\n" + "  File2.java\n" + "  File5.java\n" + "\n" +
                        "Diffing: $/vsstest/SRC/DIR2/DIRINTERNAL\n" + "Against: D:\\Projects\\VssDev\\SRC\\DIR2\\DIRINTERNAL\n" + "\n" +
                        "Diffing: $/vsstest/SRC/DIR3\n" + "Against: D:\\Projects\\VssDev\\SRC\\DIR3\n";
    testString = testString.replace( "\n", "\r\n" );

    DiffDirParser.parse( testString );
    System.out.println( "Amount of new files is: " + DiffDirParser.filesNew.size() );
    System.out.println( "Amount of deletedfiles is: " + DiffDirParser.filesDeleted.size() );
    System.out.println( "Amount of changedfiles is: " + DiffDirParser.filesChanged.size() );

    assertTrue( "Amount of new files is invalid", DiffDirParser.filesNew.size() == 5 );
    assertTrue( "Amount of deleted files is invalid", DiffDirParser.filesDeleted.size() == 0 );
    assertTrue( "Amount of changed files is invalid", DiffDirParser.filesChanged.size() == 2 );
  }

  public void testCase1()
  {
    String testString = "Diffing: $/vsstest\r\n" + "Against: D:\\Projects\\VssDev\r\n" + "\r\n" + "Local files not in the current project:\r\n" +
                        "  out  VssTest.iml  VssTest.ipr  VssTest.iws\r\n" + "Diffing: $/vsstest/SRC\r\n" +
                        "Against: D:\\Projects\\VssDev\\SRC\r\n" + "\r\n" + "Local files not in the current project:\r\n" + "  out\r\n" +
                        "Diffing: $/vsstest/SRC/DIR2\r\n" + "Against: D:\\Projects\\VssDev\\SRC\\DIR2\r\n" + "\r\n" +
                        "SourceSafe files different from local files:\r\n" + "  File2.java\r\n" + "  File5.java\r\n" + "\r\n" +
                        "Diffing: $/vsstest/SRC/DIR2/DIRINTERNAL\r\n" + "Against: D:\\Projects\\VssDev\\SRC\\DIR2\\DIRINTERNAL\r\n" + "\r\n" +
                        "Diffing: $/vsstest/SRC/DIR3\r\n" + "Against: D:\\Projects\\VssDev\\SRC\\DIR3\r\n";

    DiffDirParser.parse( testString );
    System.out.println( "Amount of new files is: " + DiffDirParser.filesNew.size() );
    System.out.println( "Amount of deletedfiles is: " + DiffDirParser.filesDeleted.size() );
    System.out.println( "Amount of changedfiles is: " + DiffDirParser.filesChanged.size() );

    assertTrue( "Amount of new files is invalid", DiffDirParser.filesNew.size() == 5 );
    assertTrue( "Amount of deleted files is invalid", DiffDirParser.filesDeleted.size() == 0 );
    assertTrue( "Amount of changed files is invalid", DiffDirParser.filesChanged.size() == 2 );
  }
}
