package org.w3.banana.sesame

import org.w3.banana._
import org.openrdf.model.{ Graph => SesameGraph, Literal => SesameLiteral, BNode => SesameBNode, URI => SesameURI, _ }
import org.openrdf.repository._
import org.openrdf.query._
import org.openrdf.query.parser._
import info.aduna.iteration.CloseableIteration

trait Sesame extends RDF {
  type Graph = SesameGraph
  type Triple = Statement
  type Node = Value
  type URI = SesameURI
  type BNode = SesameBNode
  type Literal = SesameLiteral
  type TypedLiteral = SesameLiteral
  type LangLiteral = SesameLiteral
  type Lang = String

  type Query = ParsedQuery
  type SelectQuery = ParsedTupleQuery
  type ConstructQuery = ParsedGraphQuery
  type AskQuery = ParsedBooleanQuery
  type Solution = BindingSet
  type Solutions = TupleQueryResult
}

object Sesame {

  implicit val ops: RDFOperations[Sesame] = SesameOperations

  implicit val diesel: Diesel[Sesame] = Diesel[Sesame]

  implicit val rdfxmlReader: RDFReader[Sesame, RDFXML] = SesameRDFXMLReader

  implicit val sparqlOps: SPARQLOperations[Sesame] = SesameSPARQLOperations

  implicit val graphQuery: RDFGraphQuery[Sesame] = SesameGraphQuery

}
