package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import expr.ScNewTemplateDefinition
import impl.toplevel.synthetic.JavaIdentifier
import impl.ScalaPsiElementFactory
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import psi.ScalaPsiElement
import stubs.NamedStub
import templates.ScTemplateBody
import typedef._

trait ScNamedElement extends ScalaPsiElement with PsiNameIdentifierOwner with NavigatablePsiElement {

  def name() : String = {
    this match {
      case st: StubBasedPsiElement[_] =>  st.getStub match {
        case namedStub: NamedStub[_] => namedStub.getName
        case _ if nameId == null => throw new AssertionError("NameId is null for type definition: " + st.getText)
        case _ => nameId.getText
      }
      case _ => nameId.getText
    }
  }

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getName = name

  def nameId() : PsiElement

  override def getNameIdentifier: PsiIdentifier = if (nameId != null) new JavaIdentifier(nameId) else null

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id, ScalaPsiElementFactory.createIdentifier(name, getManager))
    return this
  }

  override def getPresentation: ItemPresentation = {
    val clazz: ScTemplateDefinition = if (getParent.isInstanceOf[ScTemplateBody])
      PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition], true)
    else null
    var parent: PsiElement = this
    while (parent != null && !(parent.isInstanceOf[ScMember])) parent = parent.getParent
    return new ItemPresentation {
      def getPresentableText(): String = name
      def getTextAttributesKey(): TextAttributesKey = null
      def getLocationString(): String = clazz match {
        case _: ScTypeDefinition => "(" + clazz.getQualifiedName + ")"
        case x: ScNewTemplateDefinition => "(<anonymous>)"
        case _ => ""
      }
      override def getIcon(open: Boolean) = parent match {case mem: ScMember => mem.getIcon(0) case _ => null}
    }
  }

  override def getIcon(flags: Int) = ScalaPsiUtil.nameContext(this) match {case null => null case x => x.getIcon(flags)}
}