package org.jetbrains.plugins.scala.lang.resolve.processor

import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import com.intellij.psi._

/**
 * @author Alexander Podkhalyuzin
 */

class CollectMethodsProcessor(place: PsiElement, name: String)
        extends ResolveProcessor(StdKinds.methodsOnly, place, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY) match {
      case null => None
      case x => Some(x)
    }
    val implFunction: Option[PsiNamedElement] = state.get(CachesUtil.IMPLICIT_FUNCTION) match {
      case null => None
      case x => Some(x)
    }
    val implType: Option[ScType] = state.get(CachesUtil.IMPLICIT_TYPE) match {
      case null => None
      case x => Some(x)
    }
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      val s = getSubst(state)
      element match {
        case m: PsiMethod => {
          addResult(new ScalaResolveResult(m, s, getImports(state), None, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType))
          true
        }
        case _ =>
      }
    }
    true
  }
}