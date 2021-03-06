package org.jetbrains.plugins.scala
package lang
package psi
package impl


import api.expr.ScExpression
import api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import com.intellij.openapi.util.Key
import com.intellij.util.ArrayFactory
import lexer.ScalaTokenTypes
import psi.stubs.ScFileStub
import com.intellij.extapi.psi.PsiFileBase
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import psi.api.toplevel.packaging._
import com.intellij.openapi.roots._
import com.intellij.psi._
import org.jetbrains.annotations.Nullable
import api.toplevel.ScToplevelElement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import api.{ScControlFlowOwner, ScalaFile}
import psi.controlFlow.impl.ScalaControlFlowBuilder
import api.base.ScStableCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import scope.PsiScopeProcessor
import lang.resolve.processor.{ResolveProcessor, ResolverEnv}
import com.intellij.openapi.editor.Document
import decompiler.{DecompilerUtil, CompiledFileAdjuster}
import collection.mutable.ArrayBuffer
import com.intellij.psi.search.GlobalSearchScope
import finder.ScalaSourceFilterScope
import com.intellij.openapi.project.Project


class ScalaFileImpl(viewProvider: FileViewProvider)
        extends PsiFileBase(viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage())
                with ScalaFile with ScImportsHolder with ScDeclarationSequenceHolder
                with CompiledFileAdjuster with ScControlFlowOwner {
  override def getViewProvider = viewProvider

  override def getFileType = ScalaFileType.SCALA_FILE_TYPE

  override def toString = "ScalaFile"

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  def isCompiled = compiled

  def sourceName = {
    if (isCompiled) {
      if (virtualFileChanged) {
        sourceFileName = DecompilerUtil.decompile(virtualFile.contentsToByteArray, virtualFile)._2
        virtualFileChanged = false
      }
      sourceFileName
    }
    else ""
  }

  override def getVirtualFile: VirtualFile = {
    if (virtualFile != null) virtualFile
    else super.getVirtualFile
  }

  override def getNavigationElement: PsiElement = {
    if (!isCompiled) this
    else {
      val inner: String = getPackageNameInner
      val pName = inner + typeDefinitions.find(_.isPackageObject).map((if (inner.length > 0) "." else "") + _.name).getOrElse("")
      val sourceFile = sourceName
      val relPath = if (pName.length == 0) sourceFile else pName.replace(".", "/") + "/" + sourceFile

      val project = getProject

      // Look in libraries' sources
      val vFile = getContainingFile.getVirtualFile
      val index = ProjectRootManager.getInstance(getProject).getFileIndex
      val entries = index.getOrderEntriesForFile(vFile).toArray(OrderEntry.EMPTY_ARRAY)
      var entryIterator = entries.iterator
      while (entryIterator.hasNext) {
        val entry = entryIterator.next
        // Look in sources of an appropriate entry
        val files = entry.getFiles(OrderRootType.SOURCES)
        val filesIterator = files.iterator
        while (!filesIterator.isEmpty) {
          val file = filesIterator.next
          val source = file.findFileByRelativePath(relPath)
          if (source != null) {
            val psiSource = getManager.findFile(source)
            psiSource match {
              case o: PsiClassOwner => return o
              case _ =>
            }
          }
        }
      }
      entryIterator = entries.iterator
      //Look in libraries sources if file not relative to path
      while (entryIterator.hasNext) {
        val entry = entryIterator.next
        // Look in sources of an appropriate entry
        val files = entry.getFiles(OrderRootType.SOURCES)
        val filesIterator = files.iterator
        while (filesIterator.hasNext) {
          val file = filesIterator.next
          if (typeDefinitions.length == 0) return this
          val qual = typeDefinitions.apply(0).getQualifiedName
          def scanFile(file: VirtualFile): Option[PsiElement] = {
            val children: Array[VirtualFile] = file.getChildren
            if (children != null) {
              val childIterator = children.iterator
              while (childIterator.hasNext) {
                val child = childIterator.next
                if (child.getName == sourceFile) {
                  val psiSource = getManager.findFile(child)
                  psiSource match {
                    case o: PsiClassOwner => {
                      val clazzIterator = o.getClasses.iterator
                      while (clazzIterator.hasNext) {
                        val clazz = clazzIterator.next
                        if (qual == clazz.getQualifiedName) {
                          return Some(o)
                        }
                      }
                    }
                    case _ =>
                  }
                }
                scanFile(child) match {
                  case Some(s) => return Some(s)
                  case _ =>
                }
              }
            }
            None
          }
          scanFile(file) match {
            case Some(x) => return x
            case None =>
          }
        }
      }
      this
    }
  }


  @volatile
  private var isScriptFileCache: Option[Boolean] = None
  @volatile
  private var isScriptFileCacheModCount: Long = 0

  private def isScriptFileImpl: Boolean = {
    val stub = getStub
    if (stub == null) {
      val childrenIterator = getNode.getChildren(null).iterator
      while (childrenIterator.hasNext) {
        val n = childrenIterator.next
        n.getPsi match {
          case _: ScPackaging => return false
          case _: ScValue | _: ScVariable | _: ScFunction | _: ScExpression | _: ScTypeAlias => return true
          case _ => if (n.getElementType == ScalaTokenTypes.tSH_COMMENT) return true
        }
      }
      return false
    } else {
      stub.isScript
    }
  }

  def isScriptFile: Boolean = isScriptFile(true)

  def isScriptFile(withCashing: Boolean): Boolean = {
    if (!withCashing) return isScriptFileImpl
    //todo: modCount only for changed file?
    var option = isScriptFileCache
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (option != None && isScriptFileCacheModCount == curModCount) {
      return option.get
    }
    option = Some(isScriptFileImpl)
    isScriptFileCache = option
    isScriptFileCacheModCount = curModCount
    return option.get
  }

  /**
   * Inconsistent with Scala syntax (nested packages)
   */
  @Deprecated
  def setPackageName(name: String) {
    if (packageName == null || packageName == name) return

    val document: Document = PsiDocumentManager.getInstance(getProject).getDocument(this)
    try {
      stripPackagings(document)
      document.insertString(0, "package " + name + "\n\n")
    } finally {
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
    }
  }

  private def stripPackagings(document: Document) {
    depthFirst.findByType(classOf[ScPackaging]).foreach { p =>
      val startOffset = p.getTextOffset
      val endOffset = startOffset + p.getTextLength
      document.replaceString(startOffset, endOffset, p.getBodyText.trim)
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
      stripPackagings(document)
    }
  }

  override def getStub: ScFileStub = super[PsiFileBase].getStub.asInstanceOf[ScFileStub]

  def getPackagings: Array[ScPackaging] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PACKAGING, new ArrayFactory[ScPackaging] {
        def create(count: Int): Array[ScPackaging] = new Array[ScPackaging](count)
      })
    } else findChildrenByClass(classOf[ScPackaging])
  }

  def getPackageName: String = ""

  @Nullable
  def packageName: String = {
    if (isScriptFile) return null
    var res: String = ""
    var x: ScToplevelElement = this
    while (true) {
      val packs: Seq[ScPackaging] = x.packagings
      if (packs.length > 1) return null
      else if (packs.length == 0) return if (res.length == 0) res else res.substring(1)
      res += "." + packs(0).getPackageName
      x = packs(0)
    }
    null //impossible line
  }

  private def getPackageNameInner: String = {
    val ps = getPackagings

    def inner(p: ScPackaging, prefix: String): String = {
      val subs = p.packagings
      if (subs.length > 0 && !subs(0).isExplicit) inner(subs(0), prefix + "." + subs(0).getPackageName)
      else prefix
    }

    if (ps.length > 0 && !ps(0).isExplicit) {
      val prefix = ps(0).getPackageName
      inner(ps(0), prefix)
    } else ""
  }

  override def getClasses = {
    if (!isScriptFile) {
      typeDefinitions.toArray[PsiClass]
    } else PsiClass.EMPTY_ARRAY
  }

  def icon = Icons.FILE_TYPE_LOGO

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {

    if (isScriptFile && !super[ScDeclarationSequenceHolder].processDeclarations(processor,
      state, lastParent, place)) return false

    if (!super[ScImportsHolder].processDeclarations(processor,
      state, lastParent, place)) return false

    val scope = place.getResolveScope

    place match {
      case ref: ScStableCodeReferenceElement if ref.refName == "_root_" => {
        val top = ScPackageImpl(JavaPsiFacade.getInstance(getProject()).findPackage(""))
        if (top != null && !processor.execute(top, state.put(ResolverEnv.nameKey, "_root_"))) return false
        state.put(ResolverEnv.nameKey, null)
      }
      case _ => {
        val defaultPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(""))
        if (place != null && PsiTreeUtil.getParentOfType(place, classOf[ScPackaging]) == null) {
          if (defaultPackage != null && !defaultPackage.processDeclarations(processor, state, null, place)) return false
        }
        else if (defaultPackage != null) {
          //only packages resolve, no classes from default package
          val name = processor match {case rp: ResolveProcessor => rp.ScalaNameHint.getName(state) case _ => null}
          val facade = JavaPsiFacade.getInstance(getProject).asInstanceOf[com.intellij.psi.impl.JavaPsiFacadeImpl]
          if (name == null) {
            val packages = defaultPackage.getSubPackages(scope)
            val iterator = packages.iterator
            while (iterator.hasNext) {
              val pack = iterator.next
              if (!processor.execute(pack, state)) return false
            }
            val migration = facade.getCurrentMigration
            if (migration != null) {
              val list = migration.getMigrationPackages("")
              val packages = list.toArray(new Array[PsiPackage](list.size)).map(ScPackageImpl(_))
              val iterator = packages.iterator
              while (iterator.hasNext) {
                val pack = iterator.next
                if (!processor.execute(pack, state)) return false
              }
            }
          } else {
            val aPackage: PsiPackage = ScPackageImpl(facade.findPackage(name))
            if (aPackage != null && !processor.execute(aPackage, state)) return false
          }
        }
      }
    }

    val implObjIter = ImplicitlyImported.implicitlyImportedObjects(getManager, scope).iterator
    while (implObjIter.hasNext) {
      val clazz = implObjIter.next
      ProgressManager.checkCanceled
      //val clazz = JavaPsiFacade.getInstance(getProject).findClass(implObj, getResolveScope)
      if (clazz != null && !clazz.processDeclarations(processor, state, null, place)) return false
    }

    import toplevel.synthetic.SyntheticClasses

    val classes = SyntheticClasses.get(getProject)
    val synthIterator = classes.getAll.iterator
    while (synthIterator.hasNext) {
      val synth = synthIterator.next
      ProgressManager.checkCanceled
      if (!processor.execute(synth, state)) return false
    }

    val synthObjectsIterator = classes.syntheticObjects.iterator
    while (synthObjectsIterator.hasNext) {
      val synth = synthObjectsIterator.next
      ProgressManager.checkCanceled
      if (!processor.execute(synth, state)) return false
    }

    if (isScriptFile) {
      val syntheticValueIterator = SyntheticClasses.get(getProject).getScriptSyntheticValues.iterator
      while (syntheticValueIterator.hasNext) {
        val syntheticValue = syntheticValueIterator.next
        ProgressManager.checkCanceled
        if (!processor.execute(syntheticValue, state)) return false
      }
    }

    val implPIterator = ImplicitlyImported.packages.iterator
    while (implPIterator.hasNext) {
      val implP = implPIterator.next
      ProgressManager.checkCanceled
      val pack = JavaPsiFacade.getInstance(getProject()).findPackage(implP)
      if (pack != null && !pack.processDeclarations(processor, state, null, place)) return false
    }

    true
  }


  override def findReferenceAt(offset: Int): PsiReference = super.findReferenceAt(offset)

  /*private var context: PsiElement = null


  override def getContext: PsiElement = {
    if (context != null) context
    else super.getContext
  }

  def setContext(context: PsiElement): Unit = this.context = context*/

  private var myControlFlow: Seq[Instruction] = null

  def getControlFlow(cached: Boolean) = {
    if (!cached || myControlFlow == null) {
      val builder = new ScalaControlFlowBuilder(null, null)
      myControlFlow = builder.buildControlflow(this)
    }
    myControlFlow
  }

  import java.util.Set
  import java.util.HashSet
  import java.util.Collections
  def getClassNames: Set[String] = {
    if (isCompiled) {
      val name = getVirtualFile.getNameWithoutExtension()
      if (name != "package") {
        return Collections.singleton(name)
      }
    }
    val res = new HashSet[String]
    typeDefinitions.foreach(td => res.add(td.getName))
    res
  }
}

object ImplicitlyImported {
  val packages = Array("scala", "java.lang")
  val objects = Array("scala.Predef", "scala" /* package object*/)


  import collection.mutable.HashMap
  private val importedObjects: HashMap[Project, Seq[PsiClass]] = new HashMap[Project, Seq[PsiClass]]
  private val modCount: HashMap[Project, Long] = new HashMap[Project, Long]

  def implicitlyImportedObjects(manager: PsiManager, scope: GlobalSearchScope,
                                fqn: String): Seq[PsiClass] = {
    implicitlyImportedObjects(manager, scope).filter(_.getQualifiedName == fqn)
  }

  def implicitlyImportedObjects(manager: PsiManager, scope: GlobalSearchScope): Seq[PsiClass] = {
    var res: Seq[PsiClass] = importedObjects.get(manager.getProject).getOrElse(null)
    val count = manager.getModificationTracker.getModificationCount
    val count1: Option[Long] = modCount.get(manager.getProject)
    if (res != null && count1 != null && count == count1.get) {
      val filter = new ScalaSourceFilterScope(scope, manager.getProject)
      return res.filter(c => filter.contains(c.getContainingFile.getVirtualFile))
    }
    res = implicitlyImportedObjectsImpl(manager)
    importedObjects(manager.getProject) = res
    modCount(manager.getProject) = count
    val filter = new ScalaSourceFilterScope(scope, manager.getProject)
    return res.filter(c => filter.contains(c.getContainingFile.getVirtualFile))
  }
  private def implicitlyImportedObjectsImpl(manager: PsiManager): Seq[PsiClass] = {
    val res = new ArrayBuffer[PsiClass]
    for (obj <- objects) {
      res ++= JavaPsiFacade.getInstance(manager.getProject).
              findClasses(obj, GlobalSearchScope.allScope(manager.getProject))
    }
    res.toSeq
  }

}

private object ScalaFileImpl {
  val SCRIPT_KEY = new Key[java.lang.Boolean]("Is Script Key")
}
