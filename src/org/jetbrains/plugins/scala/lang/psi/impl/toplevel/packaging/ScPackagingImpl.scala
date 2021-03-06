package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package packaging

import api.base.ScStableCodeReferenceElement
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.stubs.IStubElementType
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.ScalaPsiElementImpl
import api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackageContainerStub
import psi.stubs.elements.wrappers.DummyASTNode
import com.intellij.openapi.progress.ProgressManager
import java.lang.String
import api.toplevel.typedef.{ScObject, ScTypeDefinition}
import caches.ScalaCachesManager
import tree.{IElementType, TokenSet}
import com.intellij.util.ArrayFactory
import collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScPackagingImpl extends ScalaStubBasedElementImpl[ScPackageContainer] with ScPackaging with ScImportsHolder with ScDeclarationSequenceHolder {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScPackageContainerStub) = {this (); setStub(stub); setNode(null)}

  def fullPackageName: String = (if (prefix.length == 0) "" else prefix + ".") + getPackageName

  override def toString = "ScPackaging"

  def reference: Option[ScStableCodeReferenceElement] = {
    getFirstChild.getNextSibling.getNextSibling match {
      case ref: ScStableCodeReferenceElement => Some(ref)
      case _ => findChild(classOf[ScStableCodeReferenceElement])
    }
  }

  def getPackageName = ownNamePart

  private def innerRefName: String = {
    def _innerRefName(ref: ScStableCodeReferenceElement): String = ref.qualifier match {
      case Some(q) => _innerRefName(q)
      case None => ref.refName
    }
    reference match {case Some(r) => _innerRefName(r) case None => ""}
  }

  def isExplicit = findChildByType(ScalaTokenTypes.tLBRACE) != null

  def ownNamePart: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPackageContainerStub].ownNamePart
    }
    reference match {case Some(r) => r.qualName case None => ""}
  }

  def prefix: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPackageContainerStub].prefix
    }
    def parentPackageName(e: PsiElement): String = e.getParent match {
      case p: ScPackaging => {
        val _packName = parentPackageName(p)
        if (_packName.length > 0) _packName + "." + p.getPackageName else p.getPackageName
      }
      case f: ScalaFileImpl => "" //f.getPackageName
      case null => ""
      case parent => parentPackageName(parent)
    }
    parentPackageName(this)
  }

  def typeDefs = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(TokenSet.create(
        ScalaElementTypes.OBJECT_DEF,
        ScalaElementTypes.CLASS_DEF,
        ScalaElementTypes.TRAIT_DEF
        ), new ArrayFactory[ScTypeDefinition] {
        def create(count: Int): Array[ScTypeDefinition] = new Array[ScTypeDefinition](count)
      })
    } else {
      val buffer = new ArrayBuffer[ScTypeDefinition]
      var curr = getFirstChild
      while (curr != null) {
        if (curr.isInstanceOf[ScTypeDefinition]) buffer += curr.asInstanceOf[ScTypeDefinition]
        curr = curr.getNextSibling
      }
      buffer.toSeq
      //findChildrenByClass[ScTypeDefinition](classOf[ScTypeDefinition])
    }
  }

  def declaredElements = {
    val _prefix = prefix
    val packageName = getPackageName
    val topRefName = if (packageName.indexOf(".") != -1) {
      packageName.substring(0, packageName.indexOf("."))
    } else packageName
    val top = if (_prefix.length > 0) _prefix + "." + topRefName else topRefName
    val p = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(top))
    if (p == null) Seq.empty else Seq.singleton(p)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val pName = (if (prefix.length == 0) "" else prefix + ".") + getPackageName
    ProgressManager.checkCanceled
    var p = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(pName))
    if (!(p == null || p.processDeclarations(processor, state, lastParent, place))) {
      return false
    }

    /*val manager = ScalaCachesManager.getInstance(getProject)
    val cache = manager.getNamesCache
    val obj = cache.getPackageObjectByName(pName, getResolveScope)
    if (obj != null) {
      if (!obj.processDeclarations(processor, state, lastParent, place)) return false
    }*/

    if (lastParent != null && lastParent.getContext == this) {
      if (!super[ScImportsHolder].processDeclarations(processor,
        state, lastParent, place)) return false
    }

    true
  }

  def getBodyText: String = {
    if (isExplicit) {
      val startOffset = findChildByType(ScalaTokenTypes.tLBRACE).getTextRange.getEndOffset - getTextRange.getStartOffset
      val text = getText
      val endOffset = if (text.apply(text.length - 1) == '}') {text.length - 1} else text.length
      return text.substring(startOffset, endOffset)
    } else {
      val text = getText
      val endOffset = text.length
      val startOffset = findChildByType(ScalaElementTypes.REFERENCE).getTextRange.getEndOffset + 1 -
              getTextRange.getStartOffset
      return text.substring(startOffset, endOffset)
    }
  }
}