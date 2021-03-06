package org.jetbrains.plugins.scala.lang.psi.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import java.awt.Container
import com.intellij.ui.{SimpleTextAttributes, SimpleColoredComponent}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer, MethodCellRenderer}
import javax.swing.{Icon, JList}
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement, PsiMethod}
import java.lang.String

/**
 * Created by IntelliJ IDEA.
 * User: Alexander.Podkhalyuz
 * Date: 15.06.2010
 * Time: 15:03:33
 * To change this template use File | Settings | File Templates.
 */

class ScImplicitFunctionListCellRenderer(actual: PsiNamedElement) extends PsiElementListCellRenderer[PsiNamedElement] {
  override def getListCellRendererComponent(list: JList, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean) = {
    val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    comp match {
      case container: Container => {
        val colored = container.getComponents.apply(2).asInstanceOf[SimpleColoredComponent]
        if (value == actual) {
          colored.clear
          colored.setIcon(actual.getIcon(0))
          colored.append(getElementText(actual), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }
      }
      case _ =>
    }
    comp
  }

  override def getElementText(element: PsiNamedElement) = {
    element match {
      case method: ScFunction => {
        method.getName + PresentationUtil.presentationString(method.paramClauses) + ": " +
                PresentationUtil.presentationString(method.returnType.
                        getOrElse(org.jetbrains.plugins.scala.lang.psi.types.Any))
      }
      case b: ScBindingPattern => b.getName + ": " + PresentationUtil.presentationString(b.getType(TypingContext.empty).
              getOrElse(org.jetbrains.plugins.scala.lang.psi.types.Any))
      case _ => element.getName
    }
  }

  def getIconFlags: Int = {
    return 0
  }

  def getContainerText(element: PsiNamedElement, name: String) = null //todo: add package name
}