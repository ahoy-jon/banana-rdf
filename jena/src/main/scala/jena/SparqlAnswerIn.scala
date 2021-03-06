package org.w3.banana.jena

import com.hp.hpl.jena.query.{ ResultSet, ResultSetFactory }
import java.io.InputStream
import org.w3.banana._
import com.hp.hpl.jena.sparql.resultset.{ XMLInput, JSONInput, SPARQLResult }

/**
 * typeclass for serialising special
 * @tparam T
 */
trait SparqlAnswerIn[T] {
  def parse(in: InputStream): SPARQLResult
}

object SparqlAnswerIn {

  implicit val Json: SparqlAnswerIn[SparqlAnswerJson] =
    new SparqlAnswerIn[SparqlAnswerJson] {
      def parse(in: InputStream) = JSONInput.make(in)
    }

  implicit val XML: SparqlAnswerIn[SparqlAnswerXML] =
    new SparqlAnswerIn[SparqlAnswerXML] {
      def parse(in: InputStream) = XMLInput.make(in)
    }

}

