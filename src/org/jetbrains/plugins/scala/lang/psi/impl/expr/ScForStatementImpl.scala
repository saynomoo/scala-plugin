package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import types.result.{Failure, TypeResult, TypingContext}
import types._
import api.ScalaElementVisitor

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScForStatementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForStatement {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ForStatement"

  def isYield: Boolean = findChildByType(ScalaTokenTypes.kYIELD) != null

  def enumerators: Option[ScEnumerators] = findChild(classOf[ScEnumerators])

  // Binding patterns in reverse order
  def patterns: Seq[ScPattern] = enumerators match {
    case None => return Seq.empty
    case Some(x) => return x.namings.reverse.map(_.pattern)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val enumerators: ScEnumerators = this.enumerators match {
      case None => return true
      case Some(x) => x
    }
    if (lastParent == enumerators) return true
    enumerators.processDeclarations(processor, state, null, place)
  }


  private def getDesugarisedExprText: Option[String] = {
    val exprText: StringBuilder = new StringBuilder
    val (enums, gens, guards) = enumerators match {
      case None => return None
      case Some(x) => {
        (x.enumerators, x.generators, x.guards)
      }
    }
    if (guards.length == 0 && enums.length == 0 && gens.length == 1) {
      val gen = gens(0)
      if (gen.rvalue == null) return None
      exprText.append("(").append(gen.rvalue.getText).append(")").append(".").append(if (isYield) "map" else "foreach")
              .append(" { case ")
      gen.pattern.desugarizedPatternIndex = exprText.length
      exprText.append(gen.pattern.getText).append(" => ")
      body match {
        case Some(x) => exprText.append(x.getText)
        case _ => exprText.append("{}")
      }
      exprText.append(" } ")
    } else if (gens.length > 0) {
      val gen = gens(0)
      if (gen.rvalue == null) return None
      var next = gens(0).getNextSibling
      while (next != null && !next.isInstanceOf[ScGuard] && !next.isInstanceOf[ScEnumerator] &&
              !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
      next match {
        case null =>
        case guard: ScGuard => {
          exprText.append("for {")
          gen.pattern.desugarizedPatternIndex = exprText.length
          exprText.append(gen.pattern.getText).
                  append(" <- ((").append(gen.rvalue.getText).append(").filter { case ").
                  append(gen.pattern.bindings.map(b => b.name).mkString("(", ", ", ")")).append(" => ").
                  append(guard.expr.map(_.getText).getOrElse("true")).append(";true").append("})")
          next = next.getNextSibling
          while (next != null && !next.isInstanceOf[ScGuard] && !next.isInstanceOf[ScEnumerator] &&
                  !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
          if (next != null) exprText.append(" ; ")
          while (next != null) {
            next match {
              case gen: ScGenerator =>
                gen.pattern.desugarizedPatternIndex = exprText.length
              case _ =>
            }
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append x.getText
            case _ => exprText append "{}"
          }
        }
        case gen2: ScGenerator => {
          exprText.append("(").append(gen.rvalue.getText).append(")").append(".").
                  append(if (isYield) "flatMap " else "foreach ").append("{case ")
          gen.pattern.desugarizedPatternIndex = exprText.length
          exprText.append(gen.pattern.getText).append(" => ").append("for {")
          while (next != null) {
            next match {
              case gen: ScGenerator =>
                gen.pattern.desugarizedPatternIndex = exprText.length
              case _ =>
            }
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append x.getText
            case _ => exprText append "{}"
          }
          exprText.append("\n}")
        }
        case enum: ScEnumerator => {
          if (enum.rvalue == null) return None
          exprText.append("for {(").append(enum.pattern.getText).append(", ")
          gen.pattern.desugarizedPatternIndex = exprText.length
          exprText.append(gen.pattern.getText).
                  append(") <- (for (freshNameForIntelliJIDEA1@(").append(gen.pattern.getText).append(") <- ").
                  append(gen.rvalue.getText).append(") yield {val freshNameForIntelliJIDEA2@(").
                  append(enum.pattern.getText).append(") = ").append(enum.rvalue.getText).
                  append("; (freshNameForIntelliJIDEA2, freshNameForIntelliJIDEA1)})")
          next = next.getNextSibling
          while (next != null && !next.isInstanceOf[ScGuard] && !next.isInstanceOf[ScEnumerator] &&
                  !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
          if (next != null) exprText.append(" ; ")
          while (next != null) {
            next match {
              case gen: ScGenerator =>
                gen.pattern.desugarizedPatternIndex = exprText.length
              case _ =>
            }
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append x.getText
            case _ => exprText append "{}"
          }
        }
        case _ =>
      }
    }
    Some(exprText.toString)
  }

  @volatile
  private var desugarizedExpr: Option[ScExpression] = null
  @volatile
  private var desugarizedExprModCount: Long = 0

  def getDesugarisedExpr: Option[ScExpression] = {
    synchronized[Option[ScExpression]] {
      var res = desugarizedExpr
      val currModCount = getManager.getModificationTracker.getModificationCount
      if (res != null && desugarizedExprModCount == currModCount) {
        return res
      }
      res = getDesugarisedExprText match {
        case Some(text) =>
          if (text == "") None
          else {
            try {
              Some(ScalaPsiElementFactory.createExpressionWithContextFromText(text, this.getContext, this))
            }
            catch {
              case e: Exception => None
            }
          }
        case _ => None
      }
      res match {
        case Some(expr: ScExpression) =>
          enumerators.map(e => e.generators.map(g => g.pattern)).foreach(patts =>
            patts.foreach(patt => {
              if (patt != null && patt.desugarizedPatternIndex != -1) {
                var element = expr.findElementAt(patt.desugarizedPatternIndex)
                while (element != null && (element.getTextLength < patt.getTextLength ||
                        (!element.isInstanceOf[ScPattern] && element.getTextLength == patt.getTextLength)))
                  element = element.getParent
                if (element != null && element.getText == patt.getText) {
                  element match {
                    case p: ScPattern => patt.analog = p
                    case _ =>
                  }
                }
              }
            })
          )
        case _ =>
      }
      desugarizedExpr = res
      desugarizedExprModCount = currModCount
      res
    }
  }

  override protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val failure = Failure("Cannot create expression", Some(this))
    getDesugarisedExpr match {
      case Some(newExpr) => {
          return newExpr.getNonValueType(ctx)
      }
      case None => return failure
    }
  }
}