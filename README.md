# PostgreSQL Client on Finagle

[![CI](https://github.com/plaflamme/finagle-postgresql/workflows/CI/badge.svg)](https://github.com/plaflamme/finagle-postgresql/actions)
[![codecov](https://codecov.io/gh/plaflamme/finagle-postgresql/branch/master/graph/badge.svg?token=TDHKM1J9TY)](https://codecov.io/gh/plaflamme/finagle-postgresql)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

This implements a client and a rich client for the Postgres protocol on [Finagle](https://github.com/twitter/finagle).

## Status

This project has been archived since it was merged into finagle mainline.
You can find it [here](https://github.com/twitter/finagle/tree/develop/finagle-postgresql).
It is now being developed internally at Twitter, PRs and issues should be reported to the finagle repository directly.

## Prior art

There are at least 2 other PostgreSQL clients for Finagle: [finagle-postgres](https://github.com/finagle/finagle-postgres)
and [roc](https://github.com/finagle/roc).

The `roc` client is incomplete and seems unmaintained at this point.

The `finagle-postgres` client is more complete, but also suffers from lack of maintenance love.
One main concern is the fact that it is still on the old style of using netty pipelines to implement protocols with Finagle.
Upgrading it to the new style would require rewriting a large portion, so it seemed equivalently risky to write a new one from scratch.
Furthermore, it opens the door to new features like properly implementing multi-line queries, implementing the `COPY` protocol and cancellation.
Lastly, one original goal of this new client was to implement streaming (for result sets and multi-line queries).

### Protocol

- [x] [start-up](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.3)
    - [x] Clear text password authentication
    - [x] MD5 password authentication
    - [ ] [SASL authentication](https://www.postgresql.org/docs/current/sasl-authentication.html)
- [x] [simple query support](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.4)
- [x] [multi-line simple query support](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-MULTI-STATEMENT)
- [ ] [COPY operations](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-COPY)
- [x] [extended query support](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-EXT-QUERY)
    - [x] portal suspension / scanning
- [ ] [function call](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.6)
- [x] [TLS](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.11)
- [ ] [cancellation](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.9)
    - [ ] support cancelling [infnite SQL queries](https://www.quora.com/Is-it-possible-to-write-an-SQL-query-that-runs-infinitely)

### Client

- [x] non `Service`-based client (a.k.a: rich client), something like [finagle-mysql](https://github.com/twitter/finagle/blob/develop/finagle-mysql/src/main/scala/com/twitter/finagle/mysql/Client.scala#L66)
- [x] [reusable prepared statements](https://github.com/twitter/finagle/blob/develop/finagle-mysql/src/main/scala/com/twitter/finagle/mysql/PreparedStatement.scala#L9-L19)
    - [x] cache and close prepared statements
- [ ] [transactions](https://github.com/twitter/finagle/blob/develop/finagle-mysql/src/main/scala/com/twitter/finagle/mysql/Client.scala#L210)
- [ ] [cursor](https://github.com/twitter/finagle/blob/develop/finagle-mysql/src/main/scala/com/twitter/finagle/mysql/CursoredStatement.scala#L26-L37)
- [ ] error decoding and handling
- [x] wire value decoding
- [x] wire value encoding
- [ ] custom types
