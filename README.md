# teya-tiny-ledger

A tiny in-memory ledger API: record deposits and withdrawals, view the current balance and the transaction history.

## Tech stack

- Java 17
- Spring Boot 3.5 (web, validation)
- Maven (via the bundled wrapper — no local Maven install needed)
- In-memory storage, no database

## Requirements

- JDK 17 or newer on your `PATH`

## Run

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

## Test

```bash
./mvnw test
```

## Build a runnable jar

```bash
./mvnw package
java -jar target/tiny-ledger-0.0.1-SNAPSHOT.jar
```

## API

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/v1/transactions` | `{"type":"DEPOSIT\|WITHDRAWAL","amount":"100.00","description":"optional"}` | `201 Created` + transaction JSON, `Location` header | `400` validation, `422` insufficient funds |
| `GET` | `/api/v1/transactions` | — | `200` list of transactions, newest first | — |
| `GET` | `/api/v1/transactions/{id}` | — | `200` transaction JSON | `400` malformed id, `404` unknown id |
| `GET` | `/api/v1/balance` | — | `200` `{"balance":"100.00"}` | — |

Amounts are sent and returned as strings with two decimal places (numeric JSON values are also accepted on input).
All errors (including unknown paths, unsupported methods and media types) are returned as [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json`.

### Record a deposit

```bash
curl -i -X POST localhost:8080/api/v1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"DEPOSIT","amount":"100.00","description":"salary"}'
```

```http
HTTP/1.1 201
Location: /api/v1/transactions/889d2bb4-a863-48d9-b6d1-64754fd9fd56
Content-Type: application/json

{"id":"889d2bb4-a863-48d9-b6d1-64754fd9fd56","type":"DEPOSIT","amount":"100.00","description":"salary","timestamp":"2026-10-04T13:30:55.670449Z"}
```

### Record a withdrawal

```bash
curl -i -X POST localhost:8080/api/v1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"WITHDRAWAL","amount":"30.00","description":"groceries"}'
```

```http
HTTP/1.1 201
Location: /api/v1/transactions/55016b7c-ee64-439f-8247-2561ce2ea7cd
Content-Type: application/json

{"id":"55016b7c-ee64-439f-8247-2561ce2ea7cd","type":"WITHDRAWAL","amount":"30.00","description":"groceries","timestamp":"2026-10-04T13:30:55.686093Z"}
```

### View the current balance

```bash
curl localhost:8080/api/v1/balance
```

```json
{"balance":"70.00"}
```

### View the transaction history

```bash
curl localhost:8080/api/v1/transactions
```

```json
[
  {"id":"55016b7c-ee64-439f-8247-2561ce2ea7cd","type":"WITHDRAWAL","amount":"30.00","description":"groceries","timestamp":"2026-10-04T13:30:55.686093Z"},
  {"id":"889d2bb4-a863-48d9-b6d1-64754fd9fd56","type":"DEPOSIT","amount":"100.00","description":"salary","timestamp":"2026-10-04T13:30:55.670449Z"}
]
```

### View a single transaction

The `Location` header returned by `POST` points here.

```bash
curl localhost:8080/api/v1/transactions/889d2bb4-a863-48d9-b6d1-64754fd9fd56
```

```json
{"id":"889d2bb4-a863-48d9-b6d1-64754fd9fd56","type":"DEPOSIT","amount":"100.00","description":"salary","timestamp":"2026-10-04T13:30:55.670449Z"}
```

An unknown id returns `404` with `"title":"Transaction not found"`.

### Errors

Withdrawing more than the current balance:

```bash
curl -i -X POST localhost:8080/api/v1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"WITHDRAWAL","amount":"1000.00"}'
```

```http
HTTP/1.1 422
Content-Type: application/problem+json

{"type":"about:blank","title":"Insufficient funds","status":422,"detail":"Insufficient funds: requested 1000.00 but current balance is 70.00","instance":"/api/v1/transactions","requestedAmount":"1000.00","currentBalance":"70.00"}
```

Invalid amount (non-positive, more than 2 decimal places, or missing):

```bash
curl -i -X POST localhost:8080/api/v1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"DEPOSIT","amount":"-5"}'
```

```http
HTTP/1.1 400
Content-Type: application/problem+json

{"type":"about:blank","title":"Invalid request","status":400,"detail":"Request validation failed","instance":"/api/v1/transactions","errors":{"amount":"must be greater than 0"}}
```

Malformed JSON or an unknown `type` returns `400` with `"title":"Malformed request"`.

## Assumptions

- One ledger only (no accounts / multi-tenancy); the task does not mention accounts, so the simplest model is used.
- Single currency; amounts carry no currency code.
- Amounts are decimals, must be strictly positive, at most 2 decimal places; direction is given by `type` (`DEPOSIT` / `WITHDRAWAL`), not by sign.
- A withdrawal greater than the current balance is rejected (balance can never go negative) with HTTP 422.
- Transactions are immutable: no update, delete, or reversal endpoints.
- Balance is a running total updated on each write (O(1) read), kept consistent with the stored history.
- History is returned newest-first, unpaginated (in-memory, small data volumes). "Newest" means most recently
  recorded; timestamps are assigned under the same lock as the append, so the two orders always agree.
- Transaction IDs are server-generated UUIDs; timestamps are server-generated UTC instants.
- Optional free-text `description` field (max 255 characters) on a transaction.
- Data is lost on restart.
- Out of scope: authentication/authorization, logging/monitoring, persistence, idempotency keys.
  Concurrency is handled with a single lock in the `Ledger` (see below), so it stays consistent under concurrent
  requests without extra infrastructure.

## Design decisions / trade-offs

- **Single ledger.** Keeps the model and API minimal; adding accounts would mean an `/accounts/{id}` path prefix and a
  ledger per account.
- **Running balance.** The balance is updated alongside each append instead of being recomputed from history, giving
  O(1) reads. Both are mutated under the same lock, so they can never disagree.
- **`Ledger` owns its invariant.** The in-memory store `Ledger` (package `data`) holds the history, an id index and the running balance,
  and is the only way to record a transaction. Creating the transaction (timestamp), checking that the balance stays
  non-negative and appending all happen under one lock, so concurrent withdrawals cannot overdraw and history order
  matches timestamp order. There is no unchecked "append" to bypass the rule. In a database this would become a
  conditional update, `SELECT … FOR UPDATE`, or optimistic locking on a version column.
- **Data structures: `ArrayList` + `HashMap` behind one lock, not concurrent collections.** The invariant spans three
  pieces of state (list, index, balance), and concurrent collections only make *individual* operations thread-safe —
  the check-then-append would still need a lock, so they would add cost without removing it. `ArrayList` gives O(1)
  amortised appends and index access (cheap offset pagination later); `HashMap` gives O(1) lookup by id.
  `CopyOnWriteArrayList` was rejected because it copies the whole array on every write, the wrong trade-off for a
  write-heavy ledger.
- **Known limitation: one lock serialises all traffic,** and `GET /transactions` copies the full history while holding
  it. Fine for an in-memory demo. Next steps would be pagination (copy only one page under the lock) or publishing an
  immutable snapshot (`AtomicReference` to a persistent list + balance) so reads never take the lock.
- **`BigDecimal` serialised as strings.** Avoids floating-point rounding in clients and keeps a stable `"70.00"` format.
- **What would change for production:** a database with transactional writes (or row-level locking / optimistic
  concurrency) instead of the in-memory ledger, accounts and currencies, idempotency keys on `POST` so retries don't
  double-post, pagination of history, authentication/authorization, and structured logging/metrics.

