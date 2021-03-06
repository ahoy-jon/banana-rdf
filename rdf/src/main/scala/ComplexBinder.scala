package org.w3.banana

import scalaz.{ Validation, Success, Failure }

object RecordBinder {

  // partial informations for building a PointedGraph
  // it separates the current found subject and the Predicate/Object pairs found at some point
  type Acc[Rdf <: RDF] = (Rdf#Node, List[(Rdf#URI, PointedGraph[Rdf])])

  type PGBElem[Rdf <: RDF, T] = Either[URIBinder[Rdf, T], (Rdf#URI, PointedGraphBinder[Rdf, T])]

  private def m[Rdf <: RDF, T](acc: Acc[Rdf], t: T, elem: PGBElem[Rdf, T]): Acc[Rdf] = {
    val (subject, pos) = acc
    elem match {
      case Left(uriBinder) => (uriBinder.toUri(t), pos)
      case Right((predicate, oBinder)) => (subject, (predicate, oBinder.toPointedGraph(t)) :: pos)
    }
  }

  private def accStart[Rdf <: RDF](implicit ops: RDFOperations[Rdf]): Acc[Rdf] = (ops.makeBNode(), List.empty)

  private def accToGraph[Rdf <: RDF](acc: Acc[Rdf])(implicit ops: RDFOperations[Rdf]): PointedGraph[Rdf] = {
    val (subject, pos) = acc
    var triples: Set[Rdf#Triple] = Set.empty
    for (po <- pos) {
      val (p, pg) = po
      triples += ops.makeTriple(subject, p, pg.node)
      triples ++= ops.graphToIterable(pg.graph)
    }
    PointedGraph(subject, ops.makeGraph(triples))
  }

}

/**
 * helper functions for binding Scala records (typically case classes)
 *
 * here is the recipe, given one type T:
 * - start by declaring the various elements that make T
 *   this is done through [PGBElem]s, and RecordBinder provide some helpers (see constant, property and uriTemplate)
 * - say how to combine the elements with a contructor (apply-like function) and an extractor (unapply-like function)
 *   there is done with the pgb helper function
 */
trait RecordBinder[Rdf <: RDF] {
  self: Diesel[Rdf] =>

  import RecordBinder._
  import ops._

  /**
   * binds a type T to one unique URI
   *
   * consT is typically a singleton object and T is its singleton type
   */
  def constant[T](constT: T, constUri: Rdf#URI): PointedGraphBinder[Rdf, T] = {

    val uriBinder = new URIBinder[Rdf, T] {
      def fromUri(uri: Rdf#URI): Validation[BananaException, T] =
        if (constUri == uri)
          Success(constT)
        else
          Failure(WrongExpectation(constUri + " does not equal " + uri))

      def toUri(t: T): Rdf#URI = constUri
    }

    NodeToPointedGraphBinder(UriToNodeBinder(uriBinder))

  }

  /**
   * declares a Property/Object element where T is in the object position
   */
  def property[T](uri: Rdf#URI)(implicit objectBinder: PointedGraphBinder[Rdf, T]): PGBElem[Rdf, T] =
    Right((uri, objectBinder))

  implicit def URIBinderToPGBElem[T](uriBinder: URIBinder[Rdf, T]): PGBElem[Rdf, T] = Left(uriBinder)

  /**
   * declares a uri template where an object T can be extracted from the URI
   * this is particularly usefull when the uri encodes an id for the given entity
   *
   * f is used to map the extracted string to a T
   *
   * example: http://example.com/foo/{id}   or    #{id}
   * for now, supports only exactly one group in the regex
   * for now, we assume that T.toString does the right thing. TODO: use Show[T]
   */
  def uriTemplate[T](template: String)(f: String => Validation[BananaException, T]): URIBinder[Rdf, T] =
    new URIBinder[Rdf, T] {

      // replace the pattern with (.+?) and make sure that the entire string is read
      val regex = {
        val t = template.replaceAll("""\{.+?\}""", "(.+?)")
        ("^.*?" + t + "$").r
      }

      def toUri(t: T): Rdf#URI = {
        val subject = template.replaceAll("""\{.*?\}""", t.toString())
        makeUri(subject)
      }

      def fromUri(subject: Rdf#URI): Validation[BananaException, T] = {
        regex findFirstIn ops.fromUri(subject) match {
          case Some(regex(id)) => f(id)
          case None => Failure(WrongExpectation("could not apply template " + template + " to " + subject.toString))
        }
      }
    }

  /** extract some graph information from a pointed graph and an declared element */
  private def extract[T](pointed: PointedGraph[Rdf], elem: PGBElem[Rdf, T]): Validation[BananaException, T] = elem match {
    case Left(uriBinder) => pointed.as[Rdf#URI] flatMap uriBinder.fromUri
    case Right((predicate, oBinder)) => (pointed / predicate).as[T](oBinder)
  }

  /**
   * combine PointedGraphBinder elements and apply/unapply functions to build binders
   *
   * TODO
   * - provide a trait to avoid reflection
   * - provide other apply methods with different arity
   * - use shapeless to generalize
   */
  def pgb[T] = new Object {

    def apply[T1](el1: PGBElem[Rdf, T1])(apply: (T1) => T, unapply: T => Option[T1]): PointedGraphBinder[Rdf, T] = new PointedGraphBinder[Rdf, T] {

      def toPointedGraph(t: T): PointedGraph[Rdf] = {
        val Some(t1) = unapply(t)
        val acc = m(accStart, t1, el1)
        accToGraph(acc)
      }

      def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, T] = {
        val v1 = extract(pointed, el1)
        for (t1 <- v1) yield apply(t1)
      }

    }

    def apply[T1, T2](el1: PGBElem[Rdf, T1], el2: PGBElem[Rdf, T2])(apply: (T1, T2) => T, unapply: T => Option[(T1, T2)]): PointedGraphBinder[Rdf, T] = new PointedGraphBinder[Rdf, T] {

      def toPointedGraph(t: T): PointedGraph[Rdf] = {
        val Some((t1, t2)) = unapply(t)
        val acc = m(m(accStart, t1, el1), t2, el2)
        accToGraph(acc)
      }

      def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, T] = {
        val v1 = extract(pointed, el1)
        val v2 = extract(pointed, el2)
        for (t1 <- v1; t2 <- v2) yield apply(t1, t2)
      }

    }

    def apply[T1, T2, T3](el1: PGBElem[Rdf, T1], el2: PGBElem[Rdf, T2], el3: PGBElem[Rdf, T3])(apply: (T1, T2, T3) => T, unapply: T => Option[(T1, T2, T3)]): PointedGraphBinder[Rdf, T] = new PointedGraphBinder[Rdf, T] {

      def toPointedGraph(t: T): PointedGraph[Rdf] = {
        val Some((t1, t2, t3)) = unapply(t)
        val acc = m(m(m(accStart, t1, el1), t2, el2), t3, el3)
        accToGraph(acc)
      }

      def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, T] = {
        val v1 = extract(pointed, el1)
        val v2 = extract(pointed, el2)
        val v3 = extract(pointed, el3)
        for (t1 <- v1; t2 <- v2; t3 <- v3) yield apply(t1, t2, t3)
      }

    }

    def apply[T1, T2, T3, T4](el1: PGBElem[Rdf, T1], el2: PGBElem[Rdf, T2], el3: PGBElem[Rdf, T3], el4: PGBElem[Rdf, T4])(apply: (T1, T2, T3, T4) => T, unapply: T => Option[(T1, T2, T3, T4)]): PointedGraphBinder[Rdf, T] = new PointedGraphBinder[Rdf, T] {

      def toPointedGraph(t: T): PointedGraph[Rdf] = {
        val Some((t1, t2, t3, t4)) = unapply(t)
        val acc = m(m(m(m(accStart, t1, el1), t2, el2), t3, el3), t4, el4)
        accToGraph(acc)
      }

      def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, T] = {
        val v1 = extract(pointed, el1)
        val v2 = extract(pointed, el2)
        val v3 = extract(pointed, el3)
        val v4 = extract(pointed, el4)
        for (t1 <- v1; t2 <- v2; t3 <- v3; t4 <- v4) yield apply(t1, t2, t3, t4)
      }

    }

    def apply[T1, T2, T3, T4, T5](el1: PGBElem[Rdf, T1], el2: PGBElem[Rdf, T2], el3: PGBElem[Rdf, T3], el4: PGBElem[Rdf, T4], el5: PGBElem[Rdf, T5])(apply: (T1, T2, T3, T4, T5) => T, unapply: T => Option[(T1, T2, T3, T4, T5)]): PointedGraphBinder[Rdf, T] = new PointedGraphBinder[Rdf, T] {

      def toPointedGraph(t: T): PointedGraph[Rdf] = {
        val Some((t1, t2, t3, t4, t5)) = unapply(t)
        val acc = m(m(m(m(m(accStart, t1, el1), t2, el2), t3, el3), t4, el4), t5, el5)
        accToGraph(acc)
      }

      def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, T] = {
        val v1 = extract(pointed, el1)
        val v2 = extract(pointed, el2)
        val v3 = extract(pointed, el3)
        val v4 = extract(pointed, el4)
        val v5 = extract(pointed, el5)
        for (t1 <- v1; t2 <- v2; t3 <- v3; t4 <- v4; t5 <- v5) yield apply(t1, t2, t3, t4, t5)
      }

    }

  }

}
