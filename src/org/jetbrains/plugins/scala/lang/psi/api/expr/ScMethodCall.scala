package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import types.ApplicabilityProblem

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScMethodCall extends ScExpression {
  def deepestInvokedExpr: ScExpression = {
    getInvokedExpr match {
      case call: ScMethodCall => {
        call.deepestInvokedExpr
      }
      case expr => expr
    }
  }

  def getInvokedExpr: ScExpression = findChildByClassScala(classOf[ScExpression])

  def args: ScArgumentExprList = findChildByClassScala(classOf[ScArgumentExprList])

  def argumentExpressions : Seq[ScExpression] = if (args != null) args.exprs else Nil

  def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = {
    updateExpression match {
      case Some(expr) => argumentExpressions ++ Seq(expr)
      case _ => argumentExpressions
    }
  }

  override def accept(visitor: ScalaElementVisitor) = visitor.visitMethodCallExpression(this)

  def isUpdateCall: Boolean = getContext.isInstanceOf[ScAssignStmt] &&
                      getContext.asInstanceOf[ScAssignStmt].getLExpression == this

  def updateExpression: Option[ScExpression] = {
    getContext match {
      case a: ScAssignStmt if a.getLExpression == this => a.getRExpression
      case _ => None
    }
  }

  def applicationProblems: Seq[ApplicabilityProblem]
}