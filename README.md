# PostgreSQL Client on Finagle

[![CI](https://github.com/plaflamme/finagle-postgresql/workflows/CI/badge.svg)](https://github.com/plaflamme/finagle-postgresql/actions)
[![codecov](https://codecov.io/gh/plaflamme/finagle-postgresql/branch/master/graph/badge.svg?token=TDHKM1J9TY)](https://codecov.io/gh/plaflamme/finagle-postgresql)

This implements a client and a rich client for the Postgres protocol on [Finagle](https://github.com/twitter/finagle).

## Prior art

There are at least 2 other PostgreSQL clients for Finagle: [finagle-postgres](https://github.com/finagle/finagle-postgres)
and [roc](https://github.com/finagle/roc).

The `roc` client seems unmaintained at this point.
The `finagle-postgres` client is somewhat more maintained, but still suffers from not being updated to the latest
Finagle style for implementing clients. Upgrading it to the new style would require rewriting a large portion,
so it seemed equivalently risky to write a new one from scratch. Furthermore, it opens the door to new features like
properly implementing multi-line queries, implementing the `COPY` protocol and cancellation.
Lastly, one original goal of this new client was to implement streaming (for result sets and multi queries).

## Status

Work in progress.

NOTE: streaming is currently implemented using the `Reader` abstraction, but it leads to a more complicated client-facing API;
so it's not clear if this will be kept. Furthermore, it complicates the implementation, making it more susceptible to
nasty concurrency issues.

### Protocol

- [x] [start-up](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.3)
    - [x] Clear text password authentication
    - [x] MD5 password authentication
    - [ ] [SASL authentication](https://www.postgresql.org/docs/current/sasl-authentication.html)
- [x] [simple query support](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.4)
- [x] [multi-line simple query support](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-MULTI-STATEMENT)
- [ ] [COPY operations](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-COPY)
- [x] [extended query support](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-EXT-QUERY)
    - [ ] cache and close prepared statements
    - [x] portal suspension / scanning
- [ ] [function call](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.6)
- [x] [TLS](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.11)
- [ ] [cancellation](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.9)
    - [ ] support cancelling [infnite SQL queries](https://www.quora.com/Is-it-possible-to-write-an-SQL-query-that-runs-infinitely)

### Client

- [x] non `Service`-based client (a.k.a: rich client), something like [finagle-mysql](https://github.com/twitter/finagle/blob/develop/finagle-mysql/src/main/scala/com/twitter/finagle/mysql/Client.scala#L66)
- [ ] error decoding and handling
- [x] wire value decoding
- [ ] wire value encoding
- [ ] custom types
