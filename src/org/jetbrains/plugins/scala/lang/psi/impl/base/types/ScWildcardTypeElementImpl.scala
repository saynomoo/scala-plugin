package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import base.ScTypeBoundsOwnerImpl
import collection.Set
import psi.types.result.{Success, TypingContext}
import psi.types.{ScDesignatorType, ScExistentialType, ScExistentialArgument}

/**
* @author Alexander Podkhalyuzin
* Date: 11.04.2008
*/

class ScWildcardTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeBoundsOwnerImpl with ScWildcardTypeElement {
  override def toString: String = "WildcardType"

  protected def innerType(ctx: TypingContext) = for (
    lb <- lowerBound;
    ub <- upperBound
  ) yield new ScExistentialArgument("_", Nil, lb, ub)
}