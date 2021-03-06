package org.jetbrains.plugins.scala
package codeInsight.template.macros

import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.codeInsight.template._
import com.intellij.psi.{PsiDocumentManager, PsiNamedElement}
import lang.psi.api.toplevel.ScTypedDefinition
import lang.refactoring.namesSuggester.NameSuggester
import lang.psi.types.result.{Success, TypingContext}
import lang.psi.types.{JavaArrayType, ScParameterizedType, ScType}

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.01.2009
 */

/**
 * Macro for suggesting name.
 */
class SuggestScalaVariableNameMacro extends Macro {
  def calculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    val a = SuggestNamesUtil.getNames(params, context)
    if (a.length < 2) return null
    a.map((s: String) => new LookupItem(s, s))
  }

  def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val a = SuggestNamesUtil.getNames(params, context)
    if (a.length == 0) return null
    return new TextResult(a(0))
  }

  def getDescription: String = "Macro for suggesting name"

  def getName: String = "suggestScalaVariableName"

  def getDefaultValue: String = "value"

  def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = null
}

object SuggestNamesUtil {
  def getNames(params: Array[Expression], context: ExpressionContext): Array[String] = {
    val p: Array[String] = params.map(_.calculateResult(context).toString)
    val offset = context.getStartOffset
    val editor = context.getEditor
    val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)
    val element = file.findElementAt(offset)
    val typez: ScType = p match {
      case x if x.length == 0 => return Array[String]("x") //todo:
      case x if x(0) == "option" || x(0) == "foreach" => {
        try {
          val items = (new ScalaVariableOfTypeMacro).calculateLookupItems(Array[String](x(0) match {
            case "option" => "scala.Option"
            case "foreach" => "foreach"
          }), context, true).
                  map(_.asInstanceOf[LookupItem[_]].getObject.asInstanceOf[PsiNamedElement]).
                  filter(_.getName == x(1))
          if (items.length == 0) return Array[String]("x")
          items(0) match {
            case typed: ScTypedDefinition => typed.getType(TypingContext.empty) match {
              case Success(ScParameterizedType(_, typeArgs), _) => typeArgs(0)
              case Success(JavaArrayType(arg), _) => arg
              case _ => return Array[String]("x")
            }
            case _ => return Array[String]("x")
          }
        }
        catch {
          case e => {
            e.printStackTrace
            return Array[String]("x")
          }
        }
      }
      case _ => return Array[String]("x")
    }
    NameSuggester.suggestNamesByType(typez)
  }
}