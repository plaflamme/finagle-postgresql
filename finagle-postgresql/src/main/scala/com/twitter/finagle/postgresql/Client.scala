package com.twitter.finagle.postgresql

import java.nio.charset.Charset

import com.twitter.finagle.ServiceFactory
import com.twitter.finagle.postgresql.Response.Command
import com.twitter.finagle.postgresql.Response.QueryResponse
import com.twitter.finagle.postgresql.Types.Name
import com.twitter.finagle.postgresql.Types.WireValue
import com.twitter.finagle.postgresql.types.PgType
import com.twitter.finagle.postgresql.types.ValueWrites
import com.twitter.io.Reader
import com.twitter.util.Closable
import com.twitter.util.Future
import com.twitter.util.Time

import scala.util.hashing.MurmurHash3

trait QueryClient[Q] {

  def query(sql: Q): Future[QueryResponse]

  def read(sql: Q): Future[ResultSet] =
    query(sql)
      .flatMap(Client.Expect.ResultSet)
      .flatMap(ResultSet(_))

  def select[T](sql: Q)(f: Row => T): Future[Iterable[T]] =
    read(sql)
      .map(rs => rs.rows.map(f))

  def modify(sql: Q): Future[Command] =
    query(sql).flatMap(Client.Expect.Command)
}

trait Client extends QueryClient[String] with Closable {

  def multiQuery(sql: String): Future[Reader[QueryResponse]]

  def prepare(sql: String): PreparedStatement

  def prepare(name: Name, sql: String): PreparedStatement

  def cursor(sql: String): CursoredStatement

}

object Client {

  // NOTE: we could use ClassTag, but that goes through reflection.
  //   We could use a macro for this, but there are only a few, so copy-pasta is fine.
  object Expect {
    def QueryResponse(r: Response): Future[Response.QueryResponse] = r match {
      case t: Response.QueryResponse => Future.value(t)
      case r => Future.exception(new IllegalStateException(s"invalid response $r"))
    }
    def ResultSet(r: Response): Future[Response.ResultSet] = r match {
      case t: Response.ResultSet => Future.value(t)
      case Response.Empty => Future.value(Response.Result.empty)
      case r => Future.exception(new IllegalStateException(s"invalid response $r"))
    }
    def Command(r: Response): Future[Response.Command] = r match {
      case t: Response.Command => Future.value(t)
      case r => Future.exception(new IllegalStateException(s"invalid response $r"))
    }
    def SimpleQueryResponse(r: Response): Future[Response.SimpleQueryResponse] = r match {
      case t: Response.SimpleQueryResponse => Future.value(t)
      case r => Future.exception(new IllegalStateException(s"invalid response $r"))
    }
    def ParseComplete(r: Response): Future[Response.ParseComplete] = r match {
      case t: Response.ParseComplete => Future.value(t)
      case r => Future.exception(new IllegalStateException(s"invalid response $r"))
    }
    def ConnectionParameters(r: Response): Future[Response.ConnectionParameters] = r match {
      case t: Response.ConnectionParameters => Future.value(t)
      case r => Future.exception(new IllegalStateException(s"invalid response $r"))
    }
  }

  def apply(factory: ServiceFactory[Request, Response]): Client = new Client {

    private[this] val service = factory.toService

    override def multiQuery(sql: String): Future[Reader[QueryResponse]] =
      service(Request.Query(sql))
        .flatMap(Expect.SimpleQueryResponse)
        .map(_.responses)

    override def query(sql: String): Future[QueryResponse] =
      // this uses an unnamed prepared statement to guarantee that the sql string only has one statement
      prepare(Name.Unnamed, sql).query(Seq.empty)

    override def prepare(sql: String): PreparedStatement =
      prepare(Name.Named(MurmurHash3.stringHash(sql).toString), sql)

    override def prepare(name: Name, sql: String): PreparedStatement = new PreparedStatement {
      // NOTE: this assumes that caching is done down the stack so that named statements aren't re-prepared on the same connection
      //   The rationale is that it allows releasing the connection earlier at the expense
      //   of re-preparing statements on each connection and potentially more than once (but not every time)
      override def query(parameters: Seq[Parameter[_]]): Future[QueryResponse] =
        factory()
          .flatMap { svc =>
            val params = svc(Request.ConnectionParameters).flatMap(Expect.ConnectionParameters)
            val prepare = svc(Request.Prepare(sql, name)).flatMap(Expect.ParseComplete)

            (params join prepare)
              .flatMap { case(params, prepared) =>
                val values = (prepared.statement.parameterTypes zip parameters)
                  .map { case (tpe, p) =>
                    p.encode(PgType.pgTypeByOid(tpe), params.parsedParameters.clientEncoding)
                  }
                svc(Request.ExecutePortal(prepared.statement, values))
              }
              .flatMap(Expect.QueryResponse)
              .ensure {
                // TODO: not sure this is correct
                val _ = svc.close()
              }
          }
    }

    override def cursor(sql: String): CursoredStatement = ???

    override def close(deadline: Time): Future[Unit] = factory.close(deadline)
  }
}

case class Parameter[T](value: T)(implicit val valueWrites: ValueWrites[T]) {
  def encode(tpe: PgType, charset: Charset): WireValue = {
    if (!valueWrites.accepts(tpe)) {
      throw PgSqlUnsupportedError(
        s"Cannot encode parameter value with provided ValueWrites instance; it does not support type ${tpe.name} (oid ${tpe.oid.value})."
      )
    }
    valueWrites.writes(tpe, value, charset)
  }
}

trait PreparedStatement extends QueryClient[Seq[Parameter[_]]]

// TODO
trait CursoredStatement
