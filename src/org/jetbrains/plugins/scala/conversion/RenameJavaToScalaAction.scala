package org.jetbrains.plugins.scala
package conversion


import com.intellij.openapi.actionSystem.{AnActionEvent, DataConstants, AnAction}
import com.intellij.psi.{PsiDocumentManager, PsiJavaFile}
import util.ScalaUtils
import com.intellij.util.IncorrectOperationException
import com.intellij.psi.codeStyle. {CodeStyleSettingsManager, CodeStyleSettings, CodeStyleManager}

/**
 * Created by IntelliJ IDEA.
 * User: Alexander
 * Date: 28.07.2009
 * Time: 20:13:48
 * To change this template use File | Settings | File Templates.
 */

class RenameJavaToScalaAction extends AnAction {
  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    def enable {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val dataContext = e.getDataContext
      val file = dataContext.getData(DataConstants.PSI_FILE)
      file match {
        case j: PsiJavaFile =>
          val dir = j.getContainingDirectory
          if (dir.isWritable) enable
          else disable
        case _ => disable
      }
    }
    catch {
      case e: Exception => disable
    }

  }

  def actionPerformed(e: AnActionEvent): Unit = {
    val file = e.getDataContext.getData(DataConstants.PSI_FILE)
    file match {
      case jFile: PsiJavaFile => {
        org.jetbrains.plugins.scala.util.ScalaUtils.runWriteAction(new Runnable {
          def run {
            val directory = jFile.getContainingDirectory
            val name = jFile.getName.substring(0, jFile.getName.length - 5)
            val file = directory.createFile(name + ".scala")
            val newText = JavaToScala.convertPsiToText(jFile).trim
            val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
            document.insertString(0, newText)
            PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
            val manager: CodeStyleManager = CodeStyleManager.getInstance(file.getProject)
            val settings = CodeStyleSettingsManager.getSettings(file.getProject)
            val keep_blank_lines_in_code = settings.KEEP_BLANK_LINES_IN_CODE
            val keep_blank_lines_in_declarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
            val keep_blank_lines_before_rbrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
            settings.KEEP_BLANK_LINES_IN_CODE = 0
            settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
            settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
            manager.reformatText(file, 0, file.getTextLength)
            settings.KEEP_BLANK_LINES_IN_CODE = keep_blank_lines_in_code
            settings.KEEP_BLANK_LINES_IN_DECLARATIONS = keep_blank_lines_in_declarations
            settings.KEEP_BLANK_LINES_BEFORE_RBRACE = keep_blank_lines_before_rbrace
          }
        }, jFile.getProject, "Convert Java to Scala")
      }
      case _ =>
    }
  }
}