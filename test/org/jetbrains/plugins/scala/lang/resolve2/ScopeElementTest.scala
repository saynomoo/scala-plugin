package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ScopeElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "scope/element/"
  }

  def testBlock = doTest
  def testCaseClass = doTest
  def testClass = doTest
  def testFunction = doTest
  def testObject = doTest
  def testTrait = doTest
}