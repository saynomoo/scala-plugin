package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import collection.immutable.HashMap
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.Suspension
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi.{PsiElement, PsiTypeParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import result.TypingContext

/**
 * @author ilyas
 */

/**
 * This is internal type, no expression can have such type.
 */
trait NonValueType extends ScType {
  def isValue = false
}

case class Parameter(name: String, paramType: ScType, isDefault: Boolean, isRepeated: Boolean) {
  def this(param: ScParameter) {
    this(param.name, param.getType(TypingContext.empty).getOrElse(Any), param.isDefaultParam, param.isRepeatedParameter)
  }
}
case class TypeParameter(name: String, lowerType: ScType, upperType: ScType, ptp: PsiTypeParameter)
case class TypeConstructorParameter(name: String, lowerType: ScType, upperType: ScType,
                                    isCovariant: Boolean, ptp: PsiTypeParameter) {
  def isContravariant: Boolean = !isCovariant
}


case class ScMethodType private (returnType: ScType, params: Seq[Parameter], isImplicit: Boolean) extends NonValueType {
  var project: Project = DecompilerUtil.obtainProject
  var scope: GlobalSearchScope = GlobalSearchScope.allScope(project)

  def this(returnType: ScType, params: Seq[Parameter], isImplicit: Boolean, project: Project,
        scope: GlobalSearchScope) {
    this(returnType, params, isImplicit)
    this.project = project
    this.scope = scope
  }

  def inferValueType: ValueType = {
    return new ScFunctionType(returnType.inferValueType, params.map(_.paramType.inferValueType), project, scope)
  }

  override def removeAbstracts = new ScMethodType(returnType.removeAbstracts,
    params.map(p => Parameter(p.name, p.paramType.removeAbstracts, p.isDefault, p.isRepeated)), isImplicit, project, scope)

  override def updateThisType(place: PsiElement) = new ScMethodType(returnType.updateThisType(place),
    params.map(p => Parameter(p.name, p.paramType.updateThisType(place), p.isDefault, p.isRepeated)), isImplicit, project, scope)

  override def updateThisType(tp: ScType) = new ScMethodType(returnType.updateThisType(tp),
    params.map(p => Parameter(p.name, p.paramType.updateThisType(tp), p.isDefault, p.isRepeated)), isImplicit, project, scope)
}

case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter]) extends NonValueType {
  if (internalType.isInstanceOf[ScTypePolymorphicType]) {
    throw new IllegalArgumentException("Polymorphic type can't have wrong internal type")
  }

  def polymorphicTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            if (tp.upperType.equiv(Any)) tp.lowerType else if (tp.lowerType.equiv(Nothing)) tp.upperType else tp.lowerType))),
      Map.empty, None) //todo: possible check lower type conforms upper type

  def abstractTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            new ScAbstractType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty), tp.lowerType, tp.upperType)))), Map.empty, None)

  def typeParameterTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))), Map.empty, None)

  def existentialTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            ScExistentialArgument(tp.name, List.empty, tp.lowerType, tp.upperType)))), Map.empty, None)

  def inferValueType: ValueType = {
    polymorphicTypeSubstitutor.subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def removeAbstracts = ScTypePolymorphicType(internalType.removeAbstracts, typeParameters.map(tp => {
    TypeParameter(tp.name, tp.lowerType.removeAbstracts, tp.upperType.removeAbstracts, tp.ptp)
  }))

  override def updateThisType(place: PsiElement) = ScTypePolymorphicType(internalType.updateThisType(place), typeParameters.map(tp => {
    TypeParameter(tp.name, tp.lowerType.updateThisType(place), tp.upperType.updateThisType(place), tp.ptp)
  }))

  override def updateThisType(typez: ScType) = ScTypePolymorphicType(internalType.updateThisType(typez), typeParameters.map(tp => {
    TypeParameter(tp.name, tp.lowerType.updateThisType(typez), tp.upperType.updateThisType(typez), tp.ptp)
  }))
}

/*case class ScTypeConstructorType(internalType: ScType, params: Seq[TypeConstructorParameter]) extends NonValueType {
  def inferValueType: ValueType = {
    //todo: implement
    throw new UnsupportedOperationException("Type Constuctors not implemented yet")
  }
  //todo: equiv
}*/
