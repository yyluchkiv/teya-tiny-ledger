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

### Demo data

On every startup the ledger is seeded with four demo transactions (by `DemoDataSeeder`), so there is something to
look at straight away:

| Type | Amount | Description |
|---|---|---|
| `DEPOSIT` | `1000.00` | salary |
| `WITHDRAWAL` | `45.50` | groceries |
| `WITHDRAWAL` | `120.00` | utilities |
| `DEPOSIT` | `250.00` | freelance |

Starting balance: **`1084.50`**. The examples below continue from this state.

### Postman

Import [`postman/tiny-ledger.postman_collection.json`](postman/tiny-ledger.postman_collection.json) into Postman. It
covers every endpoint plus the error cases, and each request has tests on the response. `baseUrl` defaults to
`http://localhost:8080`; "Record deposit" stores the new id in `transactionId` for "Get transaction by id".

To run it from the command line against a running app:

```bash
npx newman run postman/tiny-ledger.postman_collection.json
```

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
{"balance":"1154.50"}
```

### View the transaction history

```bash
curl localhost:8080/api/v1/transactions
```

```json
[
  {"id":"55016b7c-ee64-439f-8247-2561ce2ea7cd","type":"WITHDRAWAL","amount":"30.00","description":"groceries","timestamp":"2026-10-04T13:30:55.686093Z"},
  {"id":"889d2bb4-a863-48d9-b6d1-64754fd9fd56","type":"DEPOSIT","amount":"100.00","description":"salary","timestamp":"2026-10-04T13:30:55.670449Z"},
  ... the 4 demo transactions ...
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
  -d '{"type":"WITHDRAWAL","amount":"5000.00"}'
```

```http
HTTP/1.1 422
Content-Type: application/problem+json

{"type":"about:blank","title":"Insufficient funds","status":422,"detail":"Insufficient funds: requested 5000.00 but current balance is 1154.50","instance":"/api/v1/transactions","requestedAmount":"5000.00","currentBalance":"1154.50"}
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

- One ledger, one currency: the task mentions neither accounts nor currencies.
- Amounts are strictly positive decimals with at most 2 decimal places; the direction comes from `type`
  (`DEPOSIT` / `WITHDRAWAL`), not from the sign.
- The balance can never go negative: an overdrawing withdrawal is rejected with `422`.
- Transactions are immutable (no update, delete or reversal). IDs are server-generated UUIDs, timestamps are
  server-generated UTC instants, and `description` is optional (max 255 characters).
- History is returned newest-first and is not paginated (small in-memory data volumes).
- Data lives in memory only and is lost on restart; every start begins from the same [demo data](#demo-data).
- Out of scope: authentication/authorization, logging/monitoring, persistence, idempotency keys.

## Design decisions / trade-offs

- **`Ledger` owns the invariant.** The in-memory store `Ledger` (package `data`) is the only way to record a transaction. It holds the
  history, an id index and a running balance, which gives O(1) balance reads. Assigning the timestamp, checking the
  balance and appending all happen under one lock, so concurrent withdrawals cannot overdraw and history order always
  matches timestamp order. In a database this would become a conditional update or `SELECT … FOR UPDATE`.
- **One lock instead of concurrent collections.** The invariant spans three pieces of state. Concurrent collections
  only make *individual* operations thread-safe, so the check-then-append would still need a lock. `ArrayList` +
  `HashMap` give O(1) appends and O(1) lookup by id. `CopyOnWriteArrayList` would copy the whole array on every write.
- **Known limitation:** the single lock serialises all traffic, and `GET /transactions` copies the full history while
  holding it. That is fine for an in-memory demo; pagination or an immutable snapshot would remove it.
- **`BigDecimal` serialised as strings** to avoid floating-point rounding in clients and keep a stable `"70.00"` format.
- **For production:** a database with transactional writes, accounts and currencies, idempotency keys on `POST`,
  paginated history, auth, and structured logging/metrics.
