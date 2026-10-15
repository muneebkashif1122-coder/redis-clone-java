# Redis Clone in Java

A small Redis-compatible server written from scratch in Java, built to learn how servers, protocols and databases work. It speaks the real RESP protocol, so the official `redis-cli` can connect to it directly.

## Features

- TCP server that handles many clients at once (one thread per client)
- RESP protocol parser and reply writer
- Strings: `SET`, `GET`, `DEL`, `EXISTS`
- Key expiry: `EXPIRE`, `TTL` (lazy expiry)
- Lists: `LPUSH`, `RPUSH`, `LRANGE`
- Hashes: `HSET`, `HGET`, `HGETALL`, `HDEL`
- Persistence: `SAVE` command, auto-save on shutdown, auto-load on startup (`dump.dat`)
- `PING`

## Project structure

| File | Purpose |
|---|---|
| `Main.java` | Starts the server and accepts clients |
| `ClientHandler.java` | Talks to one client: read, execute, reply |
| `RespParser.java` | Turns RESP bytes into a command list |
| `RespWriter.java` | Builds RESP replies |
| `CommandHandler.java` | Logic for every command |
| `Database.java` | In-memory stores (strings, lists, hashes, expiry) |
| `Persistence.java` | Saves and loads `dump.dat` |

## Run it

Requires Java 17 or newer.

```
javac *.java
java Main
```

The server listens on port 6379. In another terminal, connect with `redis-cli`:

```
redis-cli -p 6379
127.0.0.1:6379> SET name John
OK
127.0.0.1:6379> GET name
"John"
127.0.0.1:6379> HSET user:1 name John age 20
(integer) 2
127.0.0.1:6379> RPUSH fruits apple banana
(integer) 2
127.0.0.1:6379> LRANGE fruits 0 -1
1) "apple"
2) "banana"
```

## Known limitations

- The parser ignores the `$length` lines, so values containing newlines are not supported.
- One thread per client does not scale to thousands of connections (real Redis uses an event loop).
- Expired keys are only removed when accessed (no background cleaner).
- Persistence is snapshot-only, so a crash can lose data written since the last save.

## Ideas for next steps

Sets, `INCR`/`DECR`, a background expiry cleaner, and an append-only file (AOF).