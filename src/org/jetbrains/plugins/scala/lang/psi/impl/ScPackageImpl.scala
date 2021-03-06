package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.impl.file.PsiPackageImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi._
import impl.PsiManagerEx
import scope.PsiScopeProcessor.Event
import scope.PsiScopeProcessor
import java.lang.String
import com.intellij.openapi.util.Key
import collection.Iterator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScClass}
import statements.ScFunctionImpl
import toplevel.synthetic.{ScSyntheticPackage, SyntheticClasses}
import org.jetbrains.plugins.scala.caches.{CachesUtil, ScalaCachesManager}
import util.{PsiModificationTracker, CachedValue}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */

class ScPackageImpl(pack: PsiPackage) extends PsiPackageImpl(pack.getManager.asInstanceOf[PsiManagerEx],
        pack.getQualifiedName) with ScPackage {
  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!pack.processDeclarations(processor, state, lastParent, place)) return false
    /*pack match {
      case synth: ScSyntheticPackage =>
      case _ =>
        val synth = ScSyntheticPackage.get(getQualifiedName, getProject)
        if (synth != null && !synth.processDeclarations(processor, state, lastParent, place)) return false
    }*/

    //for Scala
    if (place.getLanguage == ScalaFileType.SCALA_LANGUAGE) {
      //Process synthetic classes for scala._ package
      if (pack.getQualifiedName == "scala") {
        for (synth <- SyntheticClasses.get(getProject).getAll) {
          processor.execute(synth, ResolveState.initial)
        }
        for (synthObj <- SyntheticClasses.get(getProject).syntheticObjects) {
          processor.execute(synthObj, ResolveState.initial)
        }
      }
      
      val manager = ScalaCachesManager.getInstance(getProject)
      if (getQualifiedName == "scala") {
        val iterator: Iterator[PsiClass] = ImplicitlyImported.implicitlyImportedObjects(place.getManager,
          place.getResolveScope, "scala").iterator
        while (!iterator.isEmpty) {
          val obj = iterator.next
          if (!obj.processDeclarations(processor, state, lastParent, place)) return false
        }
      } else {
        var tuple = pack.getUserData(CachesUtil.PACKAGE_OBJECT_KEY)
        val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
        if (tuple == null || tuple._2.longValue != count) {
          val cache = manager.getNamesCache
          val clazz = cache.getPackageObjectByName(getQualifiedName, place.getResolveScope)
          tuple = (clazz, java.lang.Long.valueOf(count))
          pack.putUserData(CachesUtil.PACKAGE_OBJECT_KEY, tuple)
        }
        val obj = tuple._1
        if (obj != null) {
          if (!obj.processDeclarations(processor, state, lastParent, place)) return false
        }
      }
    }
    return true
  }

  override def getParentPackage: PsiPackage = {
    val myQualifiedName = getQualifiedName
    val myManager = getManager
    if (myQualifiedName.length == 0) return null
    val lastDot: Int = myQualifiedName.lastIndexOf('.')
    if (lastDot < 0) {
      return ScPackageImpl.findPackage(getProject, "")
    } else {
      return ScPackageImpl.findPackage(getProject, myQualifiedName.substring(0, lastDot))
    }
  }

  override def getSubPackages: Array[PsiPackage] = {
    super.getSubPackages.map(ScPackageImpl(_))
  }

  override def getSubPackages(scope: GlobalSearchScope): Array[PsiPackage] = {
    super.getSubPackages(scope).map(ScPackageImpl(_))
  }
}

object ScPackageImpl {
  def apply(pack: PsiPackage): ScPackageImpl = {
    if (pack == null) null
    else if (pack.isInstanceOf[ScPackageImpl]) pack.asInstanceOf[ScPackageImpl]
    else new ScPackageImpl(pack)
  }

  def findPackage(project: Project, pName: String) = {
    ScPackageImpl(JavaPsiFacade.getInstance(project).findPackage(pName))
  }
}