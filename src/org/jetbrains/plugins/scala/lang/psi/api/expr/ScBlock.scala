package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import toplevel.typedef.ScMember
import impl.ScalaPsiElementFactory
/**
 * @author ilyas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder with ScImportsHolder {

  def isAnonymousFunction: Boolean = false

  def exprs: Seq[ScExpression] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScExpression]).toSeq: _*)
  def statements: Seq[ScBlockStatement] =
    collection.immutable.Seq(findChildrenByClassScala(classOf[ScBlockStatement]).toSeq: _*)

  def lastExpr = findLastChild(classOf[ScExpression])
  def lastStatement = findLastChild(classOf[ScBlockStatement])

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.getNode,before.getNode)
    getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager), before.getNode)
    return true
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean =
    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
    super[ScImportsHolder].processDeclarations(processor, state, lastParent, place)
}
