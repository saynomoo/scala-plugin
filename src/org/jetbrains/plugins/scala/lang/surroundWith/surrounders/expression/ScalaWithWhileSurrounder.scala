package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import psi.api.expr.ScParenthesisedExpr

/*
 * Surrounds expression with while: while { <Cursor> } { Expression }
 */

class ScalaWithWhileSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "while (true) {" + super.getTemplateAsString(elements) + "}"
  }

  override def getTemplateDescription = "while"

  override def getSurroundSelectionRange (withWhileNode : ASTNode ) : TextRange = {
    val element: PsiElement = withWhileNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val whileStmt = element.asInstanceOf[ScWhileStmtImpl]

    val conditionNode : ASTNode = (whileStmt.condition: @unchecked) match {
        case Some(c) => c.getNode
    }

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

    return new TextRange(startOffset, endOffset);
  }
}