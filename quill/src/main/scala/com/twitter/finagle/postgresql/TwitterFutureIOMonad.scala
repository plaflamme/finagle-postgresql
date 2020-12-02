package com.twitter.finagle.postgresql

import language.experimental.macros
import com.twitter.util.Future
import io.getquill.context.Context
import com.twitter.util.Try
import io.getquill.monad.IOMonad
import io.getquill.monad.IOMonadMacro

import scala.collection.compat._
import io.getquill.Action
import io.getquill.ActionReturning
import io.getquill.BatchAction
import io.getquill.Query

// NOTE: this is copied from the quill repo with some modifications around Sequence() use foldLeft instead of Future.collect
trait TwitterFutureIOMonad extends IOMonad {
  this: Context[_, _] =>

  type Result[T] = Future[T]

  def runIO[T](quoted: Quoted[T]): IO[RunQuerySingleResult[T], Effect.Read] = macro IOMonadMacro.runIO
  def runIO[T](quoted: Quoted[Query[T]]): IO[RunQueryResult[T], Effect.Read] = macro IOMonadMacro.runIO
  def runIO(quoted: Quoted[Action[_]]): IO[RunActionResult, Effect.Write] = macro IOMonadMacro.runIO
  def runIO[T](quoted: Quoted[ActionReturning[_, T]]): IO[RunActionReturningResult[T], Effect.Write] =
    macro IOMonadMacro.runIO
  def runIO(quoted: Quoted[BatchAction[Action[_]]]): IO[RunBatchActionResult, Effect.Write] = macro IOMonadMacro.runIO
  def runIO[T](quoted: Quoted[BatchAction[ActionReturning[_, T]]]): IO[RunBatchActionReturningResult[T], Effect.Write] =
    macro IOMonadMacro.runIO

  case class Run[T, E <: Effect](f: () => Result[T]) extends IO[T, E]

  def performIO[T](io: IO[T, _], transactional: Boolean = false): Result[T] = {
    val _ = transactional
    io match {
      case FromTry(t) => Future.const(Try.fromScala(t))
      case Run(f) => f()
      case Sequence(in, cbf) =>
        in.iterator.foldLeft(Future.value(cbf())) { (previous, io) =>
          for {
            builder <- previous
            value <- performIO(io)
          } yield builder += value
        }.map(_.result())
      case TransformWith(a, fA) =>
        performIO(a)
          .liftToTry.map(_.asScala)
          .flatMap(v => performIO(fA(v)))
      case Transactional(io) =>
        performIO(io, transactional = true)
    }
  }
}
