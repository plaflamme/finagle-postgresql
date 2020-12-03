package com.twitter.finagle.postgresql

import com.twitter.finagle.PostgreSql
import com.twitter.finagle.client.DefaultPool
import com.twitter.util.Try
import com.typesafe.config.Config
import com.twitter.conversions.DurationOps._

case class FinaglePostgresContextConfig(config: Config) {

  def user = config.getString("user")
  def password = Try(config.getString("password")).getOrElse(null)
  def database = config.getString("database")
  def dest = config.getString("dest")
  def lowWatermark = Try(config.getInt("pool.watermark.low")).getOrElse(0)
  def highWatermark = Try(config.getInt("pool.watermark.high")).getOrElse(10)
  def idleTime = Try(config.getLong("pool.idleTime")).getOrElse(5L)
  def bufferSize = Try(config.getInt("pool.bufferSize")).getOrElse(0)
  def maxWaiters = Try(config.getInt("pool.maxWaiters")).getOrElse(Int.MaxValue)
  def maxPrepareStatements = Try(config.getInt("maxPrepareStatements")).getOrElse(20)
  def connectTimeout = Try(config.getLong("connectTimeout")).getOrElse(1L)
  def noFailFast = Try(config.getBoolean("noFailFast")).getOrElse(false)

  private def baseClient: PostgreSql.Client =
    PostgreSql.Client()
      .withDatabase(database)
      .withCredentials(user, Option(password))
      .withTransport
      .connectTimeout(connectTimeout.seconds)
      .configured(
        DefaultPool.Param(
          low = lowWatermark, high = highWatermark,
          idleTime = idleTime.seconds,
          bufferSize = bufferSize,
          maxWaiters = maxWaiters
        )
      )

  def client: Client = baseClient.newRichClient(dest)
}
