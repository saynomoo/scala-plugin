package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.ResolveResult
import resolve.ScalaResolveResult

trait ScStableCodeReferenceElement extends ScReferenceElement with ScPathElement {
  def qualifier: Option[ScStableCodeReferenceElement] =
    getFirstChild match {case s: ScStableCodeReferenceElement => Some(s) case _ => None}
  def pathQualifier = getFirstChild match {case s: ScPathElement => Some(s) case _ => None}

  def qualName: String = (qualifier match {
    case Some(x) => x.qualName + "."
    case _ => ""
  }) + refName

  def isConstructorReference: Boolean
  def getConstructor: Option[ScConstructor]

  def shapeResolve: Array[ResolveResult]
}
