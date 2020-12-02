package com.twitter.finagle.postgresql

import io.getquill.PostgresDialect
import io.getquill.context.sql.idiom.PositionalBindVariables

trait FinaglePostgresqlDialect extends PostgresDialect with PositionalBindVariables

object FinaglePostgresqlDialect extends FinaglePostgresqlDialect
