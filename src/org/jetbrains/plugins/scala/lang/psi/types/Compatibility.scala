package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.params.{ScParameters, ScParameter}
import api.statements.ScFunction
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import api.expr._
import api.toplevel.typedef.ScClass
import api.base.types.ScSequenceArg
import com.intellij.psi._
import result.{TypeResult, Success, TypingContext}
import api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import com.intellij.openapi.progress.ProgressManager
import search.GlobalSearchScope
import collection.Seq
import api.base.ScPrimaryConstructor

/**
 * @author ven
 */
object Compatibility {
  private var seqClass: Option[PsiClass] = None  
  
  def compatibleWithViewApplicability(l : ScType, r : ScType): Boolean = r conforms l

  case class Expression(expr: ScExpression) {
    var typez: ScType = null
    def this(tp: ScType) = {
      this(null: ScExpression)
      typez = tp
    }
    def getTypeAfterImplicitConversion(checkImplicits: Boolean, isShape: Boolean,
                                       expectedOption: Option[ScType]): (TypeResult[ScType], collection.Set[ImportUsed]) = {
      if (expr != null) {
        val expressionTypeResult = expr.getTypeAfterImplicitConversion(checkImplicits, isShape, expectedOption)
        (expressionTypeResult.tr, expressionTypeResult.importsUsed)
      }
      else (Success(typez, None), Set.empty)
    }
  }

  object Expression {
    implicit def scExpression2Expression(expr: ScExpression): Expression = Expression(expr)
    implicit def seq2ExpressionSeq(seq: Seq[ScExpression]): Seq[Expression] = seq.map(Expression(_))
    implicit def args2ExpressionArgs(list: List[Seq[ScExpression]]): List[Seq[Expression]] = {
      list.map(_.map(Expression(_)))
    }
  }

  def seqClassFor(expr: ScTypedStmt): PsiClass = {
    seqClass.getOrElse {
      JavaPsiFacade.getInstance(expr.getProject).findClass("scala.collection.Seq", expr.getResolveScope)
    }
  }

  // provides means for dependency injection in tests
  def mockSeqClass(aClass: PsiClass) {
    seqClass = Some(aClass)
  }
  
  def checkConformance(checkNames: Boolean,
                                 parameters: Seq[Parameter],
                                 exprs: Seq[Expression],
                                 checkWithImplicits: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    val r = checkConformanceExt(checkNames, parameters, exprs, checkWithImplicits, false)
    (r.problems.isEmpty, r.undefSubst)
  }  
  
  def clashedAssignmentsIn(exprs: Seq[Expression]): Seq[ScAssignStmt] = {
    val pairs = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs) yield (name, assignment)
    val names = pairs.unzip._1
    val clashedNames = names.diff(names.distinct)
    pairs.filter(p => clashedNames.contains(p._1)).map(_._2)    
  }

  case class ConformanceExtResult(problems: Seq[ApplicabilityProblem],
                                  undefSubst: ScUndefinedSubstitutor = new ScUndefinedSubstitutor,
                                  defaultParameterUsed: Boolean = false)
  
  def checkConformanceExt(checkNames: Boolean,
                               parameters: Seq[Parameter],
                               exprs: Seq[Expression],
                               checkWithImplicits: Boolean,
                               isShapesResolve: Boolean): ConformanceExtResult = {
    ProgressManager.checkCanceled
    var undefSubst = new ScUndefinedSubstitutor

    val clashedAssignments = clashedAssignmentsIn(exprs)
    val unresolved = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs;
                         if !parameters.exists(_.name == name)) yield assignment
        
    if(!unresolved.isEmpty || !clashedAssignments.isEmpty) {
      val problems = unresolved.map(new UnresolvedParameter(_)) ++
              clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
      return ConformanceExtResult(problems)
    }
    
    //optimization:
    val hasRepeated = parameters.exists(_.isRepeated)
    val maxParams: Int = if(hasRepeated) scala.Int.MaxValue else parameters.length

    val excess = exprs.length - maxParams

    if (excess > 0) {
      val arguments = exprs.takeRight(excess).map(_.expr)
      return ConformanceExtResult(arguments.map(ExcessArgument(_)), undefSubst)
    }
    
    val minParams = parameters.count(p => !p.isDefault && !p.isRepeated)
    if (exprs.length < minParams) 
      return ConformanceExtResult(Seq(new ApplicabilityProblem("4")), undefSubst)

    if (parameters.length == 0) 
      return ConformanceExtResult(if(exprs.length == 0) Seq.empty else Seq(new ApplicabilityProblem("5")),
        undefSubst)
    
    var k = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    var problems: List[ApplicabilityProblem] = Nil
    var defaultParameterUsed = false

    while (k < parameters.length.min(exprs.length)) {
      def doNoNamed(expr: Expression): List[ApplicabilityProblem] = {
        if (namedMode) {
          return List(new PositionalAfterNamedArgument(expr.expr))
        }
        else {
          val getIt = used.indexOf(false)
          used(getIt) = true
          val param: Parameter = parameters(getIt)
          val paramType = param.paramType
          val typeResult = expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(paramType))._1
          typeResult.toOption.toList.flatMap { exprType =>
            {
              val conforms = Conformance.conforms(paramType, exprType, true)
              if (!conforms) {
                List(new TypeMismatch(expr.expr, paramType))
              } else {
                undefSubst += Conformance.undefinedSubst(paramType, exprType, true)
                List.empty
              }
            }
          }
        }
      }

      exprs(k) match {
        case Expression(expr: ScTypedStmt) if expr.getLastChild.isInstanceOf[ScSequenceArg] => {
          val seqClass: PsiClass = seqClassFor(expr)
          if (seqClass != null) {
            val getIt = used.indexOf(false)
            used(getIt) = true
            val param: Parameter = parameters(getIt)
            val paramType = param.paramType
            
            if (!param.isRepeated) 
              problems ::= new ExpansionForNonRepeatedParameter(expr)
            
            val tp = ScParameterizedType(ScDesignatorType(seqClass), Seq(paramType))
            
            for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(tp)).tr) yield {
              val conforms = Conformance.conforms(tp, exprType, true)
              if (!conforms) {
                return ConformanceExtResult(Seq(new TypeMismatch(expr, tp)), undefSubst, defaultParameterUsed)
              } else {
                undefSubst += Conformance.undefinedSubst(tp, exprType, true)
              }
            }
          } else {
            problems :::= doNoNamed(Expression(expr)).reverse 
          }
        }
        case Expression(assign@NamedAssignStmt(name)) => {
          val ind = parameters.findIndexOf(_.name == name)
          if (ind == -1 || used(ind) == true) {
            problems :::= doNoNamed(Expression(assign)).reverse
          }
          else {
            if (!checkNames)
              return ConformanceExtResult(Seq(new ApplicabilityProblem("9")), undefSubst, defaultParameterUsed)
            namedMode = true
            used(ind) = true
            val param: Parameter = parameters(ind)
            assign.getRExpression match {
              case Some(expr: ScExpression) => {
                val paramType = param.paramType
                for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits,
                  isShapesResolve, Some(paramType)).tr) {
                  val conforms = Conformance.conforms(paramType, exprType, true)
                  if (!conforms) {
                    problems ::= TypeMismatch(expr, paramType)
                  } else {
                    undefSubst += Conformance.undefinedSubst(paramType, exprType, true)
                  }
                }
              }
              case _ =>
                return ConformanceExtResult(Seq(new ApplicabilityProblem("11")), undefSubst, defaultParameterUsed)
            }
          }
        }
        case expr: Expression => {
          problems :::= doNoNamed(expr).reverse
        }
      }
      k = k + 1
    }
    
    if(!problems.isEmpty) return ConformanceExtResult(problems.reverse, undefSubst, defaultParameterUsed)
    
    if (exprs.length == parameters.length) return ConformanceExtResult(Seq.empty, undefSubst, defaultParameterUsed)
    else if (exprs.length > parameters.length) {
      if (namedMode)
        return ConformanceExtResult(Seq(new ApplicabilityProblem("12")), undefSubst, defaultParameterUsed)
      if (!parameters.last.isRepeated)
        return ConformanceExtResult(Seq(new ApplicabilityProblem("13")), undefSubst, defaultParameterUsed)
      val paramType: ScType = parameters.last.paramType
      while (k < exprs.length) {
        for (exprType <- exprs(k).getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(paramType))._1) {
          val conforms = Conformance.conforms(paramType, exprType, true)
          if (!conforms) {
            return ConformanceExtResult(Seq(new ApplicabilityProblem("14")), undefSubst, defaultParameterUsed)
          } else {
            undefSubst += Conformance.undefinedSubst(paramType, exprType, true)
          }
        }
        k = k + 1
      }
    }
    else {
      if (exprs.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated) 
        return ConformanceExtResult(Seq.empty, undefSubst, defaultParameterUsed)
      
      val missed = for ((parameter: Parameter, b) <- parameters.zip(used);
                        if (!b && !parameter.isDefault)) yield MissedValueParameter(parameter)
      defaultParameterUsed = parameters.zip(used).find{case (p, b) => !b && p.isDefault} != None
      if(!missed.isEmpty) return ConformanceExtResult(missed, undefSubst, defaultParameterUsed)
    }
    return ConformanceExtResult(Seq.empty, undefSubst, defaultParameterUsed)
  }

  def toParameter(p: ScParameter, substitutor: ScSubstitutor) = {
    val t = substitutor.subst(p.getType(TypingContext.empty).getOrElse(Nothing))
    Parameter(p.getName, t, p.isDefaultParam, p.isRepeatedParameter)
  }
  def toParameter(p: PsiParameter) = {
    val t = ScType.create(p.getType, p.getProject, paramTopLevel = true)
    Parameter(p.getName, t, false, p.isVarArgs)
  }

  // TODO refactor a lot of duplication out of this method 
  def compatible(named: PsiNamedElement, 
                 substitutor: ScSubstitutor,
                 argClauses: List[Seq[Expression]],
                 checkWithImplicits: Boolean,
                 scope: GlobalSearchScope,
                 isShapesResolve: Boolean): ConformanceExtResult = {
    val exprs: Seq[Expression] = argClauses.headOption match {case Some(seq) => seq case _ => Seq.empty}
    named match {
      case synthetic: ScSyntheticFunction => {
        checkConformanceExt(false, synthetic.parameters.map(p => Parameter(p.name, substitutor.subst(p.paramType),
          p.isDefault, p.isRepeated)), exprs, checkWithImplicits, isShapesResolve)
      }
      case fun: ScFunction => {
        
        if(!fun.hasParameterClause && !argClauses.isEmpty)
          return ConformanceExtResult(Seq(new DoesNotTakeParameters))
                  
        val parameters: Seq[ScParameter] = fun.paramClauses.clauses.firstOption.toList.flatMap(_.parameters) 
        
        val clashedAssignments = clashedAssignmentsIn(exprs)
        val unresolved = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs;
                             if !parameters.exists(_.name == name)) yield assignment
        
        if(!unresolved.isEmpty || !clashedAssignments.isEmpty) {
          val problems = unresolved.map(new UnresolvedParameter(_)) ++
                  clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
          return ConformanceExtResult(problems)
        }
        
        //optimization:
        val hasRepeated = parameters.exists(_.isRepeatedParameter)
        val maxParams: Int = if(hasRepeated) scala.Int.MaxValue else parameters.length
        
        val excess = exprs.length - maxParams
        
        if (excess > 0) {
          val arguments = exprs.takeRight(excess).map(_.expr)
          return ConformanceExtResult(arguments.map(ExcessArgument(_)))
        }
        
        val obligatory = parameters.filter(p => !p.isDefaultParam && !p.isRepeatedParameter)
        val shortage = obligatory.size - exprs.length  
        if (shortage > 0) 
          return ConformanceExtResult(obligatory.takeRight(shortage).
                  map(p => MissedValueParameter(toParameter(p, substitutor))))

        val res = checkConformanceExt(true, parameters.map(toParameter(_, substitutor)),
          exprs, checkWithImplicits, isShapesResolve)

        return res
      }
      case constructor: ScPrimaryConstructor => {
        val parameters: Seq[ScParameter] =
          if (constructor.parameterList.clauses.length == 0) Seq.empty
          else constructor.parameterList.clauses.apply(0).parameters

        val clashedAssignments = clashedAssignmentsIn(exprs)
        val unresolved = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs;
                             if !parameters.exists(_.name == name)) yield assignment

        if(!unresolved.isEmpty || !clashedAssignments.isEmpty) {
          val problems = unresolved.map(new UnresolvedParameter(_)) ++
                  clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
          return ConformanceExtResult(problems)
        }

        //optimization:
        val hasRepeated = parameters.exists(_.isRepeatedParameter)
        val maxParams: Int = if(hasRepeated) scala.Int.MaxValue else parameters.length

        val excess = exprs.length - maxParams

        if (excess > 0) {
          val part = exprs.takeRight(excess).map(_.expr)
          return ConformanceExtResult(part.map(ExcessArgument(_)))
        }
        
        val obligatory = parameters.filter(p => !p.isDefaultParam && !p.isRepeatedParameter)
        val shortage = obligatory.size - exprs.length;
        
        if (shortage > 0) { 
          val part = obligatory.takeRight(shortage).map { p =>
            val t = p.getType(TypingContext.empty).getOrElse(org.jetbrains.plugins.scala.lang.psi.types.Any)
            Parameter(p.name, t, p.isDefaultParam, p.isRepeatedParameter) 
          }
          return ConformanceExtResult(part.map(new MissedValueParameter(_)))
        }

        val res = checkConformanceExt(true, parameters.map{param: ScParameter => Parameter(param.getName, {
          substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
        }, param.isDefaultParam, param.isRepeatedParameter)}, exprs, checkWithImplicits, isShapesResolve)
        return res
      }
      case method: PsiMethod => {
        val parameters: Seq[PsiParameter] = method.getParameterList.getParameters.toSeq

        val excess = exprs.length - parameters.length

        //optimization:
        val hasRepeated = parameters.exists(_.isVarArgs)
        if (excess > 0 && !hasRepeated) {
          val arguments = exprs.takeRight(excess).map(_.expr)
          return ConformanceExtResult(arguments.map(ExcessArgument(_)))
        }

        val obligatory = parameters.filterNot(_.isVarArgs)
        val shortage = obligatory.size - exprs.length
        if (shortage > 0)
          return ConformanceExtResult(obligatory.takeRight(shortage).map(p => MissedValueParameter(toParameter(p))))


        checkConformanceExt(false, parameters.map {param: PsiParameter => Parameter("", {
          val tp = substitutor.subst(ScType.create(param.getType, method.getProject, scope, paramTopLevel = true))
          if (param.isVarArgs) tp match {
            case ScParameterizedType(_, args) if args.length == 1 => args(0)
            case JavaArrayType(arg) => arg
            case _ => tp
          }
          else tp
        }, false, param.isVarArgs)}, exprs, checkWithImplicits, isShapesResolve)
      }
      /*case cc: ScClass if cc.isCase => {
        val parameters: Seq[ScParameter] = {
          cc.clauses match {
            case Some(params: ScParameters) if params.clauses.length != 0 => params.clauses.apply(0).parameters
            case _ => Seq.empty
          }
        }

        //optimization:
        val hasRepeated = parameters.find(_.isRepeatedParameter) != None
        val maxParams = parameters.length
        if (exprs.length > maxParams && !hasRepeated)
          return ConformanceExtResult(Seq(new ApplicabilityProblem("20")))
        val minParams = parameters.count(p => !p.isDefaultParam && !p.isRepeatedParameter)
        if (exprs.length < minParams)
          return ConformanceExtResult(Seq(new ApplicabilityProblem("21")))

        checkConformanceExt(true, parameters.map{param: ScParameter => Parameter(param.getName, {
          substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
        }, param.isDefaultParam, param.isRepeatedParameter)}, exprs, checkWithImplicits, isShapesResolve)
      }*/

      case _ => ConformanceExtResult(Seq(new ApplicabilityProblem("22")))
    }
  }
}
