package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import impl.ScalaFileImpl
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi.stubs.StubElement
import templates.{ScExtendsBlock, ScTemplateBody}
import com.intellij.psi.impl.source.PsiFileImpl
import collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 * Date: 04.05.2008
 */

trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {
  def getContainingClass: ScTemplateDefinition = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScalaStubBasedElementImpl[_] => st.getStub
      case _ => null
    }
    if (stub != null) {
      stub.getParentStubOfType(classOf[ScTemplateDefinition])
    } else {
      child match {
        case c: ScClass if c.isCase => {
          //this is special case for synthetic apply and unapply methods
          ScalaPsiUtil.getCompanionModule(c) match {
            case Some(td) => return td
            case _ =>
          }
        }
        case _ =>
      }
      PsiTreeUtil.getContextOfType(this, true, classOf[ScTemplateDefinition])
    }
  }

  override def hasModifierProperty(name: String) = {
    if (name == PsiModifier.STATIC) {
      getContainingClass match {
        case obj: ScObject => true
        case _ => false
      }
    } else if (name == PsiModifier.PUBLIC) {
      !hasModifierProperty("private") && !hasModifierProperty("protected")
    } else super.hasModifierProperty(name)
  }

  protected def isSimilarMemberForNavigation(m: ScMember, isStrict: Boolean) = false

  override def getNavigationElement: PsiElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorMember
    case _ => this
  }

  private def getSourceMirrorMember: ScMember = getParent match {
    case tdb: ScTemplateBody => tdb.getParent match {
      case eb: ScExtendsBlock => eb.getParent match {
        case td: ScTypeDefinition => td.getNavigationElement match {
          case c: ScTypeDefinition => {
            val membersIterator = c.members.iterator
            val buf: ArrayBuffer[ScMember] = new ArrayBuffer[ScMember]
            while (membersIterator.hasNext) {
              val member = membersIterator.next
              if (isSimilarMemberForNavigation(member, false)) buf += member
            }
            if (buf.length == 0) this
            else if (buf.length == 1) buf(0)
            else {
              val filter = buf.filter(isSimilarMemberForNavigation(_, true))
              if (filter.length == 0) buf(0)
              else filter(0)
            }
          }
          case _ => this
        }
        case _ => this
      }
      case _ => this
    }
    case _ => this
  }


}