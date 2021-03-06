package org.jetbrains.plugins.scala
package annotator
package gutter

import _root_.scala.collection.immutable.HashMap
import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.codeInsight.daemon.impl.{PsiElementListNavigator}
import com.intellij.codeInsight.daemon.{GutterIconNavigationHandler}
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer}
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.util.Iconable
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.awt.RelativePoint
import java.util.Arrays
import javax.swing.Icon
import com.intellij.util.NullableFunction
import java.awt.event.MouseEvent
import lang.psi.api.statements.params.ScClassParameter
import lang.psi.api.statements.{ScFunction, ScValue, ScDeclaredElementsHolder, ScVariable}
import lang.psi.api.toplevel.typedef.{ScTrait, ScMember, ScObject}
import lang.psi.impl.search.ScalaOverridengMemberSearch
import lang.psi.ScalaPsiUtil
import lang.psi.types.FullSignature
import lang.lexer.ScalaTokenTypes
import util.PsiTreeUtil
import lang.psi.api.toplevel.ScNamedElement
import lang.psi.api.base.patterns.ScBindingPattern

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.11.2008
 */

object ScalaMarkerType {
  val OVERRIDING_MEMBER = ScalaMarkerType(new NullableFunction[PsiElement, String] {
    def fun(element: PsiElement): String = {
      var elem = element
      element.getNode.getElementType match {
        case  ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR => {
          elem = PsiTreeUtil.getParentOfType(element, classOf[PsiMember])
        }
        case _ =>
      }
      elem match {
        case method: ScFunction => {
          val signatures: Seq[FullSignature] = method.superSignatures
          //removed assertion, because can be change before adding gutter, so just need to return ""
          if (signatures.length != 0) return ""
          val optionClazz = signatures(0).clazz
          assert(optionClazz != None)
          val clazz = optionClazz.get
          if (!GutterUtil.isOverrides(element)) ScalaBundle.message("implements.method.from.super", clazz.getQualifiedName)
          else ScalaBundle.message("overrides.method.from.super", clazz.getQualifiedName)
        }
        case _: ScValue | _: ScVariable => {
          val signatures = new ArrayBuffer[FullSignature]
          val bindings = elem match {case v: ScDeclaredElementsHolder => v.declaredElements case _ => return null}
          for (z <- bindings) signatures ++= ScalaPsiUtil.superValsSignatures(z)
          assert(signatures.length != 0)
          val optionClazz = signatures(0).clazz
          assert(optionClazz != None)
          val clazz = optionClazz.get
          if (!GutterUtil.isOverrides(element)) ScalaBundle.message("implements.val.from.super", clazz.getQualifiedName)
          else ScalaBundle.message("overrides.val.from.super", clazz.getQualifiedName)
        }
        case _ => null
      }
    }
  }, new GutterIconNavigationHandler[PsiElement]{
    def navigate(e: MouseEvent, element: PsiElement) {
      var elem = element
      element.getNode.getElementType match {
        case  ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR => {
          elem = PsiTreeUtil.getParentOfType(element, classOf[PsiMember])
        }
        case _ =>
      }
      elem match {
        case method: ScFunction => {
          val signatures = method.superSignatures
          val elems = new HashSet[NavigatablePsiElement]
          signatures.foreach(elems += _.element)
          elems.size match {
            case 0 =>
            case 1 => {
              val elem = elems.elements.next
              if (elem.canNavigate) elem.navigate(true)
            }
            case _ => {
              val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(elems.toArray, new ScCellRenderer,
              ScalaBundle.message("goto.override.method.declaration"))
              gotoDeclarationPopup.show(new RelativePoint(e))
            }
          }
        }
        case _: ScValue | _: ScVariable => {
          val signatures = new ArrayBuffer[FullSignature]
          val bindings = elem match {case v: ScDeclaredElementsHolder => v.declaredElements case _ => return null}
          for (z <- bindings) signatures ++= ScalaPsiUtil.superValsSignatures(z)
          val elems = new HashSet[NavigatablePsiElement]
          signatures.foreach(elems += _.element)
          elems.size match {
            case 0 =>
            case 1 => {
              val elem = elems.elements.next
              if (elem.canNavigate) elem.navigate(true)
            }
            case _ => {
              val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(elems.toArray, new ScCellRenderer,
              ScalaBundle.message("goto.override.val.declaration"))
              gotoDeclarationPopup.show(new RelativePoint(e))
            }
          }
        }
        case _ =>
      }
    }
  })

  val OVERRIDDEN_MEMBER = ScalaMarkerType(new NullableFunction[PsiElement, String]{
    def fun(element: PsiElement): String = {
      var elem = element
      element.getNode.getElementType match {
        case  ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR => {
          elem = PsiTreeUtil.getParentOfType(element, classOf[PsiMember])
        }
        case _ =>
      }
      elem match {
        case _: PsiMember =>
          if (GutterUtil.isAbstract(element)) ScalaBundle.message("has.implementations")
          else ScalaBundle.message("is.overriden.by")
        case _ => return null
      }
    }
  }, new GutterIconNavigationHandler[PsiElement] {
    def navigate(e: MouseEvent, element: PsiElement): Unit = {
      var elem = element
      element.getNode.getElementType match {
        case  ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR => {
          elem = PsiTreeUtil.getParentOfType(element, classOf[PsiMember])
        }
        case _ =>
      }
      val members = elem match {
        case memb: PsiNamedElement => Array[PsiNamedElement](memb)
        case d: ScDeclaredElementsHolder => d.declaredElements.toArray
        case _ => return
      }
      val overrides = new ArrayBuffer[PsiNamedElement]
      for (member <- members) overrides ++= ScalaOverridengMemberSearch.search(member)
      if (overrides.length == 0) return
      val title = if (GutterUtil.isAbstract(element)) ScalaBundle.
              message("navigation.title.implementation.member", members(0).getName, "" + overrides.length)
                  else ScalaBundle.message("navigation.title.overrider.member", members(0).getName, "" + overrides.length)
      val renderer = new ScCellRenderer
      Arrays.sort(overrides.map(_.asInstanceOf[PsiElement]).toArray, renderer.getComparator)
      PsiElementListNavigator.openTargets(e, overrides.map(_.asInstanceOf[NavigatablePsiElement]).toArray, title, renderer)
    }
  })

  val SUBCLASSED_CLASS = ScalaMarkerType(new NullableFunction[PsiElement, String]{
    def fun(element: PsiElement): String = {
      var elem = element
      if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
        elem = PsiTreeUtil.getParentOfType(element, classOf[ScNamedElement])
      }
      if (!elem.isInstanceOf[PsiClass]) return null
      elem match {
        case _: ScTrait => ScalaBundle.message("trait.has.implementations")
        case _: ScObject => ScalaBundle.message("object.has.subclasses")
        case _ => ScalaBundle.message("class.has.subclasses")
      }
    }
  }, new GutterIconNavigationHandler[PsiElement] {
    def navigate(e: MouseEvent, element: PsiElement): Unit = {
      var elem = element
      if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
        elem = PsiTreeUtil.getParentOfType(element, classOf[ScNamedElement])
      }
      val clazz = elem match {
        case x: PsiClass => x
        case _ => return
      }
      val inheritors = ClassInheritorsSearch.search(clazz, clazz.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY)
      if (inheritors.length == 0) return
      val title = clazz match {
        case _: ScTrait => ScalaBundle.message("goto.implementation.chooser.title", clazz.getName, "" + inheritors.length)
        case _ => ScalaBundle.message("navigation.title.subclass", clazz.getName, "" + inheritors.length)
      }
      val renderer = new PsiClassListCellRenderer
      Arrays.sort(inheritors, renderer.getComparator)
      PsiElementListNavigator.openTargets(e, inheritors.map(_.asInstanceOf[NavigatablePsiElement]), title, renderer)
    }
  })

  class ScCellRenderer extends PsiElementListCellRenderer[PsiElement] {
    def getElementText(element: PsiElement): String = {
      element match {
        case method: PsiMethod if method.getContainingClass != null => {
          val presentation = method.getContainingClass.getPresentation
          if (presentation != null)
            presentation.getPresentableText + " " + presentation.getLocationString
          else {
            throw new AssertionError("Method hasn't presentation. Method text: " + method.getText)
          }
        }
        case xlass: PsiClass => {
          val presentation = xlass.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        }
        case x: PsiNamedElement if ScalaPsiUtil.nameContext(x).isInstanceOf[ScMember] => {
          val presentation = ScalaPsiUtil.nameContext(x).asInstanceOf[ScMember].getContainingClass.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        }
        case x: ScClassParameter => {
          val presentation = x.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        }
        case x: PsiNamedElement => x.getName
        case _ => element.getText().substring(0, Math.min(element.getText().length, 20))
      }
    }

    def getContainerText(psiElement: PsiElement, s: String) = null

    def getIconFlags: Int = Iconable.ICON_FLAG_CLOSED


    override def getIcon(element: PsiElement): Icon = {
      element match {
        case _: PsiMethod => super.getIcon(element)
        case x: PsiNamedElement if ScalaPsiUtil.nameContext(x) != null => ScalaPsiUtil.nameContext(x).getIcon(getIconFlags)
        case _ => super.getIcon(element)
      }
    }
  }
}

case class ScalaMarkerType(val fun: com.intellij.util.Function[PsiElement,String], val handler: GutterIconNavigationHandler[PsiElement])