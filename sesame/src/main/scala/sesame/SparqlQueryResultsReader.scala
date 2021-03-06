package org.w3.banana.sesame

import java.io._
import org.w3.banana._
import org.openrdf.query.resultio.{ QueryResultParseException, UnsupportedQueryResultFormatException, QueryResultIO }
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import org.openrdf.query.UnsupportedQueryLanguageException
import scala.Left
import scala.Right
import collection.mutable.ArrayBuffer
import scalaz.Validation

/**
 *
 * typeclass for a blocking BlockingReader of Sparql Query Results
 * such as those defined
 * <ul>
 *   <li><a href="http://www.w3.org/TR/rdf-sparql-XMLres/">SPARQL Query Results XML Format</a></li>
 *   <li><a href="http://www.w3.org/TR/rdf-sparql-json-res/">SPARQL Query Results in JSON</a></li>
 * </ul>
 *
 * In Sesame the implementation is very ugly.
 * We first have to assume it is a tuple query, parse it, then parse it as a boolean query
 * See Issue <a href="http://www.openrdf.org/issues/browse/SES-1054">SES-1054</a>
 * We are waiting for a fix for this problem.
 *
 * If you can't wait for the fix, it would be better to write
 * a parser directly or a mapper from Jena's implementation. Parsing sparql or json queries can't be
 * that difficult.
 *
 */
object SparqlQueryResultsReader {

  def apply[Syntax](implicit sesameSparqlSyntax: SparqlAnswerIn[Syntax]): SparqlQueryResultsReader[Sesame, Syntax] =
    new SparqlQueryResultsReader[Sesame, Syntax] {

      def read(in: InputStream, base: String) = {
        val bytes: Array[Byte] = Iterator.continually(in.read).takeWhile(-1 !=).map(_.toByte).toArray
        parse(bytes)
      }

      def parse(bytes: Array[Byte]): Validation[BananaException, Either[Sesame#Solutions, Boolean]] = {
        WrappedThrowable.fromTryCatch {
          try {
            val parsed = QueryResultIO.parse(new ByteArrayInputStream(bytes),
              sesameSparqlSyntax.booleanFormat)
            Right(parsed)
          } catch {
            case e: QueryResultParseException => {
              Left(QueryResultIO.parse(new ByteArrayInputStream(bytes),
                sesameSparqlSyntax.tupleFormat))
            }
          }
        }
      }

      def read(reader: Reader, base: String) = {
        val queri = Iterator.continually(reader.read).takeWhile(-1 !=).map(_.toChar).toArray
        //it is really horrible to have to turn a nice char array into bytes for parsing!
        parse(new String(queri).getBytes("UTF-8"))
      }

    }

  implicit val Json: SparqlQueryResultsReader[Sesame, SparqlAnswerJson] =
    SparqlQueryResultsReader[SparqlAnswerJson]

  implicit val forXML: SparqlQueryResultsReader[Sesame, SparqlAnswerXML] =
    SparqlQueryResultsReader[SparqlAnswerXML]

}
