package scalax.collection

import java.io._

import language.higherKinds

import org.scalatest.Suite
import org.scalatest.{FlatSpec, Suites}
import org.scalatest.Informer
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterEach

import GraphPredef._, GraphEdge._
import generic.GraphCoreCompanion

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class TSerializableRootTest
  extends Suites( new TSerializable[immutable.Graph](immutable.Graph),
                  new TSerializable[  mutable.Graph](  mutable.Graph))

/**	Tests for standard java serialization.
 */
class TSerializable[CC[N,E[X] <: EdgeLikeIn[X]] <: Graph[N,E] with GraphLike[N,E,CC]]
    (val factory: GraphCoreCompanion[CC])
	extends	FlatSpec
	with	  Matchers
	with    BeforeAndAfterEach
{
  s"Tests based on ${factory.getClass}" should "start" in {
  }
  // resolves ClassNotFound issue with SBT
  private class CustomObjectInputStream(in: InputStream, cl: ClassLoader) extends ObjectInputStream(in) {
    override def resolveClass(cd: ObjectStreamClass): Class[_] =
      try {
        cl.loadClass(cd.getName())
      } catch {
        case cnf: ClassNotFoundException =>
          super.resolveClass(cd)
      }
    override def resolveProxyClass(interfaces: Array[String]): Class[_] =
      try {
        val ifaces = interfaces map { iface => cl.loadClass(iface) }
        java.lang.reflect.Proxy.getProxyClass(cl, ifaces: _*)
      } catch {
        case e: ClassNotFoundException =>
          super.resolveProxyClass(interfaces)
      }
  }
  trait GraphStore {
    def save   [N, E[X] <: EdgeLikeIn[X]](g: CC[N,E])
    def restore[N, E[X] <: EdgeLikeIn[X]]:   CC[N,E]
    def test   [N, E[X] <: EdgeLikeIn[X]](g: CC[N,E]): CC[N,E] = {
      save[N,E](g)
      val r = restore[N,E]
      r should be (g)
      r
    }
  }
  /** to save and restore graphs to/from files */
  object GraphFile extends GraphStore {
    var cnt = 0
    /** renames output file to contain the name of the test method */
    def renameOutFile(fromFilename: String,
                      toFileExt:    String) {
      val fromFile = new File(fromFilename)
      val toFile   = new File(fromFile.getParent + File.separator +
                              this.getClass.getSimpleName + "." +
                              testNames.toArray.apply(cnt) + toFileExt)
      toFile.delete
      fromFile.renameTo(toFile) should be (true)
      cnt += 1
    }
    def rename = renameOutFile(filename + ext, ext)
    val (filename, ext) = ("c:\\temp\\ser\\graph", ".ser")
    def save[N, E[X] <: EdgeLikeIn[X]](g: CC[N,E]) {
      var fos: FileOutputStream = null
      try {
        fos = new FileOutputStream(filename + ext)
        val out = new ObjectOutputStream(fos)
        out writeObject g
      } catch {
        case e: Exception => fail("Couldn't write: " + g, e)
      } finally
        fos.close
    }
    def restore[N, E[X] <: EdgeLikeIn[X]] = {
      var fis: FileInputStream = null
      try {
        fis = new FileInputStream(filename + ext)
        val in = new ObjectInputStream(fis)
        val read = in.readObject
        read.asInstanceOf[CC[N,E]]
      } finally {
        fis.close
      }
    }
  }
  override def afterEach {
    if (store == GraphFile)
      GraphFile.rename
  }
  /** to save and restore graphs to/from byte arrays */
  class GraphByteArray(cl: ClassLoader) extends GraphStore {
    private var _saved: Array[Byte] = null
    def saved = _saved
    def save[N, E[X] <: EdgeLikeIn[X]](g: CC[N,E]) {
      val bos = new ByteArrayOutputStream 
      try {
        val out = new ObjectOutputStream(bos)
        out writeObject g
        _saved = bos.toByteArray 
      } catch {
        case e: Exception => println(e); fail("Couldn't write: " + g, e)
      } finally {
        bos.close
      }
    }
    def restore[N, E[X] <: EdgeLikeIn[X]] = {
      val bis = new ByteArrayInputStream(_saved) 
      val in = if (cl != null)
          new CustomObjectInputStream(bis, cl)
        else
          new ObjectInputStream(bis)
      val read = in.readObject
      bis.close
      read.asInstanceOf[CC[N,E]]
    }
  }
  lazy val cl = classOf[TSerializableRootTest].getClassLoader

  /** normally we test with byte arrays but may be set to GraphFile instead */
  private lazy val store: GraphStore = new GraphByteArray(cl)
  
  private val work = "be serializable"

  "An empty graph" should work in {
    val g = factory.empty[Nothing,Nothing]
    store.test[Nothing,Nothing] (g)
  }
  "A graph of type [Int,Nothing]" should work in {
    val g = factory[Int,Nothing](-1,1,2)
    store.test[Int,Nothing] (g)
  }
  "A graph of type [Int,UnDiEdge]" should work in {
    val g = factory(-1~1, 2~>1)
    store.test[Int,UnDiEdge] (g)
  }
  "A graph of type [String,UnDiEdge]" should work in {
    val g = factory("a"~"b", "b"~>"c")
    store.test[String,UnDiEdge] (g)
  }
  "A graph of type [Int,DiEdge]" should work in {
    import Data.elementsOfDi_1
    val g = factory(elementsOfDi_1: _*)
    store.test[Int,DiEdge] (g)
  }
  "A graph of [MyNode,WLDiEdge]" should work in {
    import edge.WLDiEdge

    val (a1,a2,b1,b2) = ("a1","a2","b1","b2")
    val (n1, n2) = (MyNode(List(a1, b1)), MyNode(List(a2, b2)))
    val label = MyLabel("abab")
    val e = WLDiEdge(n1, n2)(11, label)

    { /* if MyNode is declared as an inner class, it is not serializable;
       * so we assert first, that both the node class and the label class are serializable 
       */
      val bos = new ByteArrayOutputStream 
      val out = new ObjectOutputStream(bos)
      out writeObject n1
      out writeObject label
      bos.close
    }

    val g = factory(e)
    val back = store.test[MyNode,WLDiEdge] (g)
    
    back.graphSize should be (1)
    
    val inner_1 = (back get n1) 
    inner_1.diSuccessors should have size (1)
    inner_1.diSuccessors.head should be (n2)
    
    val backEdge = back.edges.head
    backEdge.source.s should be (List(a1, b1)) 
    backEdge.target.s should be (List(a2, b2))
    backEdge.label should be (label)
  }
  "After calling diSuccessors the graph" should work in {
    import Data.elementsOfDi_1
    val g = factory(elementsOfDi_1: _*)
    g.nodes.head.diSuccessors
    val back = store.test[Int,DiEdge] (g)
    back should be (g)
  }
  "After calling pathTo the graph" should work in {
    import Data.elementsOfDi_1
    val g = factory(elementsOfDi_1: _*)
    g.nodes.head.diSuccessors
    val n = g.nodes.head
    n.pathTo(n)
    val back = store.test[Int,DiEdge] (g)
    back should be (g)
  }
  "A deserialized graph" should "be traversable" in {
    import Data.elementsOfDi_1
    val g = factory(elementsOfDi_1: _*)
    val back = store.test[Int,DiEdge] (g)
    def op(g: CC[Int,DiEdge]): Int = g.nodes.head.outerNodeTraverser.size
    op(back) should be (op(g))
  }
  "A deserialized graph" should "have the same successors" in {
    import Data.elementsOfDi_1
    val g = factory(elementsOfDi_1: _*)
    def outerSuccessors(g: CC[Int,DiEdge])= g.nodes map(innerNode => innerNode.value -> innerNode.diSuccessors.map(_.value)) 
    val diSuccBefore = outerSuccessors(g)
    val back = store.test[Int,DiEdge] (g)
    outerSuccessors(back) should be (diSuccBefore)
  }

  trait EdgeStore {
    def save   [N, E[X] <: EdgeLikeIn[X]](e: Iterable[InParam[N,E]])
    def restore[N, E[X] <: EdgeLikeIn[X]]:   Iterable[InParam[N,E]]
    def test   [N, E[X] <: EdgeLikeIn[X]](e: Iterable[InParam[N,E]])
        : Iterable[InParam[N,E]] = {
      save[N,E](e)
      val r = restore[N,E]
      r should be (e)
      r
    }
  }
  class EdgeByteArray(cl: ClassLoader) extends EdgeStore {
    private var _saved: Array[Byte] = null
    def saved = _saved
    def save[N, E[X] <: EdgeLikeIn[X]](e: Iterable[InParam[N,E]]) {
      val bos = new ByteArrayOutputStream 
      val out = new ObjectOutputStream(bos)
      out writeObject e
      _saved = bos.toByteArray 
      bos.close
    }
    def restore[N, E[X] <: EdgeLikeIn[X]] = {
      val bis = new ByteArrayInputStream(_saved) 
      val in = if (cl != null)
          new CustomObjectInputStream(bis, cl)
        else
          new ObjectInputStream(bis)
      val read = in.readObject
      bis.close
      read.asInstanceOf[Iterable[InParam[N,E]]]
    }
  }
  "A graph of [Int,WUnDiEdge]" should work in {
    import edge.WUnDiEdge, Data.elementsOfWUnDi_2
    new EdgeByteArray(cl).test[Int,WUnDiEdge] (elementsOfWUnDi_2)
  }
}
/* To be serializable, node and label classes must not be defined as inner classes */
case class MyNode (val s: List[String]) extends Serializable
case class MyLabel(val s: String)       extends Serializable
