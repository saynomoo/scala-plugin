package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, DefaultHighlightVisitor}
import importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.project.{DumbService, DumbAware, Project}
import com.intellij.lang.annotation.Annotator
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl._
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import com.intellij.openapi.util.TextRange
import com.intellij.codeHighlighting.Pass
import collection.JavaConversions
import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.openapi.extensions.Extensions

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

class ScalaAnnotatorHighlightVisitor(project: Project) extends HighlightVisitor {
  def order: Int = 0

  private final val myAnnotationHolder: AnnotationHolderImpl = new AnnotationHolderImpl
  private var myHolder: HighlightInfoHolder = null
  private var myRefCountHolder: ScalaRefCountHolder = null
  private val myErrorFilters: Array[HighlightErrorFilter] = 
          Extensions.getExtensions(DefaultHighlightVisitor.FILTER_EP_NAME, project)

  override def suitableForFile(file: PsiFile): Boolean = {
    return file.isInstanceOf[ScalaFile]
  }

  override def visit(element: PsiElement, holder: HighlightInfoHolder): Unit = {
    myHolder = holder
    assert(!myAnnotationHolder.hasAnnotations(), myAnnotationHolder)
    if (element.isInstanceOf[PsiErrorElement]) {
      visitErrorElement(element.asInstanceOf[PsiErrorElement])
    }
    else {
      runAnnotator(element)
    }
  }

  override def analyze(action: Runnable, updateWholeFile: Boolean, file: PsiFile): Boolean = {
    var success = true
    try {
      if (updateWholeFile) {
        val project: Project = file.getProject
        val daemonCodeAnalyzer: DaemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
        val fileStatusMap: FileStatusMap = (daemonCodeAnalyzer.asInstanceOf[DaemonCodeAnalyzerImpl]).getFileStatusMap
        val refCountHolder: ScalaRefCountHolder = ScalaRefCountHolder.getInstance(file)
        myRefCountHolder = refCountHolder
        val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)
        val dirtyScope: TextRange = if (document == null) file.getTextRange else fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL)
        success = refCountHolder.analyze(action, dirtyScope, file)
      }
      else {
        myRefCountHolder = null
        action.run
      }
    }
    finally {
      myAnnotationHolder.clear
      myHolder = null
      myRefCountHolder = null
    }
    return success
  }

  override def clone: HighlightVisitor = {
    return new ScalaAnnotatorHighlightVisitor(project)
  }

  private def runAnnotator(element: PsiElement): Unit = {
    val dumb: Boolean = DumbService.getInstance(project).isDumb
    if (dumb) {
      return
    }
    val annotator: Annotator = new ScalaAnnotator
    annotator.annotate(element, myAnnotationHolder)
    if (myAnnotationHolder.hasAnnotations) {
      import JavaConversions._
      for (annotation <- myAnnotationHolder) {
        myHolder.add(HighlightInfo.fromAnnotation(annotation))
      }
      myAnnotationHolder.clear
    }
  }

  def visitErrorElement(element: PsiErrorElement): Unit = {
    for (errorFilter <- myErrorFilters) {
      if (!errorFilter.shouldHighlightErrorElement(element)) return
    }
    var info: HighlightInfo = DefaultHighlightVisitor.createErrorElementInfo(element)
    myHolder.add(info)
  }
}