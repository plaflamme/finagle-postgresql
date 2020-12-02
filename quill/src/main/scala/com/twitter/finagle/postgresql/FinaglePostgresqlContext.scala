package com.twitter.finagle.postgresql

import com.twitter.concurrent.AsyncStream
import com.twitter.util.Await
import com.twitter.util.Future
import io.getquill.NamingStrategy
import io.getquill.context.Context
import io.getquill.context.StreamingContext
import io.getquill.context.TranslateContext
import io.getquill.context.sql.SqlContext

import scala.util.Try

class FinaglePostgresqlContext[N <: NamingStrategy](val naming: N, client: Client)
    extends Context[FinaglePostgresqlDialect, N]
    with TranslateContext
    with SqlContext[FinaglePostgresqlDialect, N]
    with StreamingContext[FinaglePostgresqlDialect, N]
    with FinaglePostgresqlEncoders
    with FinaglePostgresqlDecoders
    with TwitterFutureIOMonad {

  override type PrepareRow = Seq[Parameter[_]]
  override type ResultRow = Row

  override type Result[T] = Future[T]
  override type RunQueryResult[T] = List[T]
  override type RunQuerySingleResult[T] = T
  override type RunActionResult = Long
  override type RunActionReturningResult[T] = T
  override type RunBatchActionResult = List[Long]
  override type RunBatchActionReturningResult[T] = List[T]
  override type StreamResult[T] = Future[AsyncStream[T]]

  override def performIO[T](io: IO[T, _], transactional: Boolean = false): Result[T] =
    transactional match {
      case false => super.performIO(io)
      case true => ???
    }

  override def idiom: FinaglePostgresqlDialect = FinaglePostgresqlDialect

  override def probe(statement: String): Try[_] =
    Try(Await.result(client.query(statement)))

  override def close(): Unit =
    Await.result(client.close())

  override def prepareParams(statement: String, prepare: Prepare): TranslateResult[Seq[String]] =
    prepare(Nil)._2.map(param => prepareParam(param.value))

}
