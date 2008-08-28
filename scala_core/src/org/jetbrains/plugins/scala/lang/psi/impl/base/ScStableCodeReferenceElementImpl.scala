package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang._
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import org.jetbrains.annotations._
import psi.ScalaPsiElementImpl
import psi.api.base._
import psi.types._
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.api.base.types.ScSimpleTypeElement
import psi.impl.ScalaPsiElementFactory
import resolve._
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi._
import com.intellij.psi.impl._
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.psi.PsiElement
import com.intellij.openapi.util._
import com.intellij.util.IncorrectOperationException
import api.toplevel.ScTyped
import api.statements.ScTypeAlias
import api.base.patterns.ScConstructorPattern
import api.expr.{ScSuperReference, ScThisReference}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScStableCodeReferenceElement {

  def getVariants(): Array[Object] = _resolve(this, new CompletionProcessor(resolveKinds(true))).map(r => r.getElement) //todo

  override def toString: String = "CodeReferenceElement"

  object MyResolver extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElementImpl] {
    def resolve(ref: ScStableCodeReferenceElementImpl, incomplete: Boolean) = {
      _resolve(ref, new ResolveProcessor(ref.resolveKinds(false), refName))
    }
  }

  private def resolveKinds(incomplete: Boolean) = {
    import StdKinds._
    getParent match {
      case _: ScStableCodeReferenceElement => stableQualRef
      case e: ScImportExpr => if (e.selectorSet != None
              //import Class._ is not allowed
              || qualifier == None) stableQualRef else stableQualOrClass
      case _: ScSimpleTypeElement => if (incomplete) stableQualOrClass else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScConstructorPattern => constructorPattern
      case _: ScThisReference | _: ScSuperReference => stableClass
      case _: ScImportSelector => stableImportSelector
      case _ => stableQualRef
    }
  }

  private def _qualifier() = {
    if (getParent.isInstanceOf[ScImportSelector]) {
      getParent.getParent /*ScImportSelectors*/ .getParent.asInstanceOf[ScImportExpr].reference
    } else pathQualifier
  }

  def _resolve(ref: ScStableCodeReferenceElementImpl, processor: BaseProcessor): Array[ResolveResult] = {
    _qualifier match {
      case None => {
        def treeWalkUp(place: PsiElement, lastParent: PsiElement): Unit = {
          place match {
            case null => ()
            case p => {
              if (!p.processDeclarations(processor,
              ResolveState.initial,
              lastParent, ref)) return ()
              treeWalkUp(place.getParent, place)
            }
          }
        }
        treeWalkUp(ref, null)
      }
      case Some(q : ScStableCodeReferenceElement) => {
        q.bind match {
          case None =>
          case Some(ScalaResolveResult(typed: ScTyped, s)) => processor.processType(s.subst(typed.calcType), this)
          case Some(ScalaResolveResult(pack: PsiPackage, _)) if pack.getQualifiedName == "scala" => {
            import toplevel.synthetic.SyntheticClasses

            for (synth <- SyntheticClasses.get(getProject).getAll) {
              processor.execute(synth, ResolveState.initial)
            }
            pack.processDeclarations(processor, ResolveState.initial, null, ScStableCodeReferenceElementImpl.this)
          }
          case Some(other) => {
            other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
            null, ScStableCodeReferenceElementImpl.this)
          }
        }
      }
      case Some(thisQ : ScThisReference) => processor.processType(thisQ.getType, this)
      case Some(superQ : ScSuperReference) => processor.processType(superQ.getType, this) 
    }
    processor.candidates
  }

  def multiResolve(incomplete: Boolean) = {
     getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, false, incomplete)
  }

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  //  @throws(IncorrectOperationException)
  def bindToElement(@NotNull element: PsiElement): PsiElement = {
    if (isReferenceTo(element)) return this

    element match {
      case c: PsiClass => {
        val file = getContainingFile.asInstanceOf[ScalaFile]
        if (isReferenceTo(element)) return this
        val qualName = c.getQualifiedName
        if (qualName != null) {
          file.addImportForClass(c)
        }
        this
      }
      case _ => return this
    }
  }
}