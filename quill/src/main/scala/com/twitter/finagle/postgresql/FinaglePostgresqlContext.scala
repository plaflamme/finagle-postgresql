package com.twitter.finagle.postgresql

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.postgresql.BackendMessage.CommandTag
import com.twitter.util.Await
import com.twitter.util.Future
import com.typesafe.config.Config
import io.getquill.NamingStrategy
import io.getquill.ReturnAction
import io.getquill.context.Context
import io.getquill.context.StreamingContext
import io.getquill.context.TranslateContext
import io.getquill.context.sql.SqlContext
import io.getquill.util.ContextLogger
import io.getquill.util.LoadConfig

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

  def this(naming: N, config: FinaglePostgresContextConfig) = this(naming, config.client)
  def this(naming: N, config: Config) = this(naming, FinaglePostgresContextConfig(config))
  def this(naming: N, configPrefix: String) = this(naming, LoadConfig(configPrefix))

  private val logger = ContextLogger(classOf[FinaglePostgresqlContext[_]])

  def executeQuery[T](
    sql: String,
    prepare: Prepare = identityPrepare,
    extractor: Extractor[T] = identityExtractor
  ): Future[List[T]] = {
    val (params, prepared) = prepare(Nil)
    logger.logQuery(sql, params)
    client.prepare(sql).select(prepared)(extractor).map(_.toList)
  }

  def executeQuerySingle[T](
    sql: String,
    prepare: Prepare = identityPrepare,
    extractor: Extractor[T] = identityExtractor
  ): Future[T] =
    executeQuery(sql, prepare, extractor).map(handleSingleResult)

  def transaction[T](f: => Future[T]): Future[T] =
    f.map(_ => ???) // avoids dead code warning

  def executeAction[T](sql: String, prepare: Prepare = identityPrepare): Future[Long] = {
    val (params, prepared) = prepare(Nil)
    logger.logQuery(sql, params)
    client.prepare(sql)
      .modify(prepared)
      .map {
        case Response.Command(CommandTag.AffectedRows(_, rows)) => rows.toLong
        case _ => 0
      }
  }

  def executeActionReturning[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T], returningAction: ReturnAction): Future[T] = {
    val (params, prepared) = prepare(Nil)
    logger.logQuery(sql, params)
    val _ = returningAction
    client.prepare(sql)
      .select(prepared)(extractor).map {
        case row :: Nil => row
        case _ => throw new IllegalStateException
      }
  }

  private[this] def batch[B, T](groups: List[B])(prep: B => List[Prepare])(f: (B, Prepare) => Future[T]): Future[List[T]] =
    Future.collect {
      groups.map { batch =>
        prep(batch).foldLeft(Future.value(List.newBuilder[T])) {
          case (acc, prepare) =>
            acc.flatMap { list =>
              f(batch, prepare).map(list += _)
            }
        }.map(_.result())
      }
    }.map(_.flatten.toList)

  def executeBatchAction[B](groups: List[BatchGroup]): Future[List[Long]] =
    batch(groups)(_.prepare) {
      case (BatchGroup(sql, _), prepare) => executeAction(sql, prepare)
    }

  def executeBatchActionReturning[T](groups: List[BatchGroupReturning], extractor: Extractor[T]): Future[List[T]] =
    batch(groups)(_.prepare) {
      case (BatchGroupReturning(sql, column, _), prepare) => executeActionReturning(sql, prepare, extractor, column)
    }

  override def performIO[T](io: IO[T, _], transactional: Boolean = false): Result[T] =
    transactional match {
      case false => super.performIO(io)
      case true => transaction(super.performIO(io))
    }

  override def idiom: FinaglePostgresqlDialect = FinaglePostgresqlDialect

  override def probe(statement: String): Try[_] =
    Try(Await.result(client.query(statement)))

  override def close(): Unit =
    Await.result(client.close())

  override def prepareParams(statement: String, prepare: Prepare): TranslateResult[Seq[String]] =
    prepare(Nil)._2.map(param => prepareParam(param.value))

}
