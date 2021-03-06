package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import types.{Bounds, Nothing}
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import api.expr._

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.ASTNode
import types.result.{TypingContext, Success, Failure}
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScIfStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScIfStmt {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "IfStatement"

  def condition = {
    val rpar = findChildByType(ScalaTokenTypes.tRPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getPrevSiblingOfType(rpar, classOf[ScExpression]) else null
    if (c == null) None else Some(c)
  }

  def thenBranch = {
    val kElse = findChildByType(ScalaTokenTypes.kELSE)
    val t = if (kElse != null) PsiTreeUtil.getPrevSiblingOfType(kElse, classOf[ScExpression])
            else if (getLastChild.isInstanceOf[ScExpression]) getLastChild.asInstanceOf[ScExpression] 
            else PsiTreeUtil.getPrevSiblingOfType(getLastChild, classOf[ScExpression])
    if (t == null) None else condition match {
      case None => Some(t) 
      case Some(c) if c != t => Some(t)
      case  _ => None
    }
  }

  def elseBranch = {
    val kElse = findChildByType(ScalaTokenTypes.kELSE)
    val e = if (kElse != null) PsiTreeUtil.getNextSiblingOfType(kElse, classOf[ScExpression]) else null
    if (e == null) None else Some(e)
  }

  protected override def innerType(ctx: TypingContext) = {
    (thenBranch, elseBranch) match {
      case (Some(t), Some(e)) => for (tt <- t.getType(TypingContext.empty);
                                      et <- e.getType(TypingContext.empty)) yield {
        Bounds.lub(tt, et)
      }
      case (Some(t), None) => t.getType(TypingContext.empty).map(tt => Bounds.lub(tt, types.Unit))
      case _ => Failure(ScalaBundle.message("nothing.to.type"), Some(this))
    }
  }
}