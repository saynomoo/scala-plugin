package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import com.intellij.psi._
import psi.types.result.TypeResult
import psi.types.ScType
import lang.resolve.ScalaResolveResult

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScReferenceExpression extends ScalaPsiElement with ScExpression with ScReferenceElement {
  def qualifier: Option[ScExpression] = getFirstChild match {case e: ScExpression => Some(e) case _ => None}

  /**
   * This method returns all possible types for this place.
   * It's useful for expressions, which has two or more valid resolve results.
   * For example scala package, and scala package object.
   * Another usecase is when our type inference failed to decide to which method
   * we should resolve. If all methods has same result type, then we will give valid completion and resolve.
   */
  def multiType: Array[ScType]

  def shapeResolve: Array[ResolveResult]

  def shapeType: TypeResult[ScType]
}