package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import types.result.TypingContext
import types.{Nothing, Bounds, ScType}
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor
;
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMatchStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMatchStmt {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "MatchStatement"

  protected override def innerType(ctx: TypingContext) = {
    val branchTypes = getBranches.map(_.getType(ctx))
    collectFailures(branchTypes, Nothing)(_.foldLeft(Nothing : ScType)((t, b) => Bounds.lub(t, b)))
  }
}