# PostgreSQL Client on Finagle

## Status

### Protocol

- [x] [start-up](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.3)
    - [x] Clear text password authentication
    - [x] MD5 password authentication
    - [ ] [SASL authentication](https://www.postgresql.org/docs/current/sasl-authentication.html)
- [x] [simple query support](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.4)
- [x] [multi-line simple query support](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-MULTI-STATEMENT)
- [ ] [COPY operations](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-COPY)
- [ ] [extended query support](https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-EXT-QUERY)
- [ ] [function call](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.6)
- [x] [TLS](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.11)
- [ ] [cancellation](https://www.postgresql.org/docs/current/protocol-flow.html#id-1.10.5.7.9)

### Client

- [ ] error decoding and handling
- [ ] value encoding / decoding
- [ ] custom types
- [ ] non `Service`-based client (a.k.a: rich client), something like [finagle-mysql](https://github.com/twitter/finagle/blob/develop/finagle-mysql/src/main/scala/com/twitter/finagle/mysql/Client.scala#L66)
