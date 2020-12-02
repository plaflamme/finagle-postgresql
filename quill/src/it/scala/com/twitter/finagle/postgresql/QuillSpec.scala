package com.twitter.finagle.postgresql

import com.twitter.finagle.postgresql.Types.Oid
import io.getquill.SnakeCase

class QuillSpec extends PgSqlIntegrationSpec {

  def withCtx[R](
    config: ConnectionCfg = defaultConnectionCfg,
    cfg: ClientCfg = identity
  )(spec: FinaglePostgresqlContext[SnakeCase.type] => R): R =
    withRichClient(config, cfg) { client =>
      lazy val ctx = new FinaglePostgresqlContext(SnakeCase, client)
      spec(ctx)
    }

  case class PgType(typname: String, typnamespace: Oid, typlen: Short, typbyval: Boolean)

  "Quill" should {
    "select" in withCtx() { implicit ctx =>
      import ctx._
      run {
        quote {
          query[PgType].filter(_.typname == "int4")
        }
      }.map { result =>
        result must_== PgType("int4", Oid(11), 4, true) :: Nil
      }
    }
  }

}
