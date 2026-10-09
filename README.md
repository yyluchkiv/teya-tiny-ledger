# teya-tiny-ledger

A tiny in-memory ledger API: create accounts, record deposits and withdrawals, transfer money between accounts, and
view balances and transaction histories.

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

On every startup the ledger is seeded (by `DemoDataSeeder`), so there is something to look at straight away. The
default account `main` (id `00000000-0000-0000-0000-000000000001`) gets four demo transactions:

| Type | Amount | Description |
|---|---|---|
| `DEPOSIT` | `1000.00` | salary |
| `WITHDRAWAL` | `45.50` | groceries |
| `WITHDRAWAL` | `120.00` | utilities |
| `DEPOSIT` | `250.00` | freelance |

Starting balance of `main`: **`1084.50`**. A second account, `savings`, is created with a single
`DEPOSIT 500.00 "opening balance"` (its id is random; find it with `GET /api/v1/accounts`). The examples below
continue from this state.

### Postman

Import [`postman/tiny-ledger.postman_collection.json`](postman/tiny-ledger.postman_collection.json) into Postman. It
covers every endpoint plus the error cases, and each request has tests on the response. `baseUrl` defaults to
`http://localhost:8080`; "Record deposit" stores the new id in `transactionId` for "Get transaction by id",
"Create account" stores `accountId` and "Transfer from main" stores `transferId` for the requests that follow them.

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
| `POST` | `/api/v1/accounts` | `{"name":"savings"}` | `201 Created` + account JSON, `Location` header | `400` validation |
| `GET` | `/api/v1/accounts` | — | `200` list of accounts with balances, newest first | — |
| `GET` | `/api/v1/accounts/{accountId}` | — | `200` `{"id","name","balance","createdAt"}` | `400` malformed id, `404` unknown account |
| `POST` | `/api/v1/accounts/{accountId}/transactions` | same as `POST /transactions` | `201 Created` + transaction JSON, `Location` header | `400`, `404`, `422` |
| `GET` | `/api/v1/accounts/{accountId}/transactions` | — | `200` the account's transactions, newest first | `400`, `404` |
| `GET` | `/api/v1/accounts/{accountId}/transactions/{id}` | — | `200` transaction JSON | `400`, `404` |
| `GET` | `/api/v1/accounts/{accountId}/balance` | — | `200` `{"balance":"100.00"}` | `400`, `404` |
| `POST` | `/api/v1/transfers` | `{"fromAccountId","toAccountId","amount":"10.00","description":"optional"}` | `201 Created` + transfer JSON, `Location` header | `400` validation or same account, `404` unknown account, `422` insufficient funds |
| `GET` | `/api/v1/transfers/{id}` | — | `200` transfer JSON | `400` malformed id, `404` unknown id |

The original endpoints (`/transactions`, `/balance`) act on the default account `main`, which is also reachable as
`/api/v1/accounts/00000000-0000-0000-0000-000000000001/...`. Their requests and responses are unchanged.

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

### Create an account

```bash
curl -i -X POST localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"name":"holiday"}'
```

```http
HTTP/1.1 201
Location: /api/v1/accounts/8028655e-5bf7-4784-a38e-c0f61595277a
Content-Type: application/json

{"id":"8028655e-5bf7-4784-a38e-c0f61595277a","name":"holiday","balance":"0.00","createdAt":"2026-10-09T11:24:51.350195Z"}
```

`GET /api/v1/accounts` lists every account with its balance, newest first. A blank or over-100-character `name`
returns `400`.

### Deposit to and withdraw from an account

Same body as `POST /api/v1/transactions`, scoped to one account:

```bash
curl -i -X POST localhost:8080/api/v1/accounts/8028655e-5bf7-4784-a38e-c0f61595277a/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"DEPOSIT","amount":"20.00","description":"pocket money"}'
```

```http
HTTP/1.1 201
Location: /api/v1/accounts/8028655e-5bf7-4784-a38e-c0f61595277a/transactions/9077308f-8c97-4bb7-ac7a-50e8c3770d0e
Content-Type: application/json

{"id":"9077308f-8c97-4bb7-ac7a-50e8c3770d0e","type":"DEPOSIT","amount":"20.00","description":"pocket money","timestamp":"2026-10-09T11:24:51.384365Z"}
```

Withdrawals use `"type":"WITHDRAWAL"`. An unknown account returns `404` with `"title":"Account not found"`, and
overdrawing the account returns the same `422` as above.

### Transfer between accounts

```bash
curl -i -X POST localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountId":"00000000-0000-0000-0000-000000000001","toAccountId":"8028655e-5bf7-4784-a38e-c0f61595277a","amount":"84.50","description":"save"}'
```

```http
HTTP/1.1 201
Location: /api/v1/transfers/97f4832d-9e85-4c68-bb9c-bd0c58285dee
Content-Type: application/json

{"id":"97f4832d-9e85-4c68-bb9c-bd0c58285dee","fromAccountId":"00000000-0000-0000-0000-000000000001","toAccountId":"8028655e-5bf7-4784-a38e-c0f61595277a","amount":"84.50","description":"save","timestamp":"2026-10-09T11:24:51.426542Z","debitTransactionId":"7fb6f2fa-5b53-4d41-ad34-f70707e062a0","creditTransactionId":"b6d35370-36e2-4081-88c8-608b5347f440"}
```

`main` is now at `1000.00` (from `1084.50`) and the new account at `84.50`. The transfer appears in each account's
history as an ordinary transaction (a `WITHDRAWAL` on the source, a `DEPOSIT` on the destination), and both legs
carry the transfer's id:

```bash
curl localhost:8080/api/v1/accounts/8028655e-5bf7-4784-a38e-c0f61595277a/transactions
```

```json
[
  {"id":"b6d35370-36e2-4081-88c8-608b5347f440","type":"DEPOSIT","amount":"84.50","description":"save","timestamp":"2026-10-09T11:24:51.426542Z","transferId":"97f4832d-9e85-4c68-bb9c-bd0c58285dee"},
  ... earlier transactions, without transferId ...
]
```

### Transfer errors

A failed transfer changes neither balance.

Same source and destination (`400`):

```json
{"type":"about:blank","title":"Invalid transfer","status":400,"detail":"Cannot transfer from account 8028655e-5bf7-4784-a38e-c0f61595277a to itself","instance":"/api/v1/transfers"}
```

Not enough money on the source (`422`):

```json
{"type":"about:blank","title":"Insufficient funds","status":422,"detail":"Insufficient funds: requested 1000.00 but current balance is 84.50","instance":"/api/v1/transfers","requestedAmount":"1000.00","currentBalance":"84.50"}
```

Unknown source or destination account (`404`):

```json
{"type":"about:blank","title":"Account not found","status":404,"detail":"Account 22222222-2222-2222-2222-222222222222 not found","instance":"/api/v1/transfers","id":"22222222-2222-2222-2222-222222222222"}
```

## Assumptions

- One currency for all accounts.
- Accounts have a server-generated UUID, a required `name` (1–100 characters, not unique) and a `createdAt`
  instant. Accounts cannot be renamed or closed.
- The original endpoints act on a default account `main` with the fixed id
  `00000000-0000-0000-0000-000000000001`, which always exists. Their behaviour and response shapes are unchanged.
- A transfer is recorded as two ordinary transactions, a `WITHDRAWAL` on the source and a `DEPOSIT` on the
  destination, linked by `transferId`. There is no `TRANSFER` transaction type, so existing clients only ever see
  `DEPOSIT`/`WITHDRAWAL`. `transferId` is left out of the JSON for plain deposits and withdrawals.
- A transfer either moves the full amount or does nothing. Transfers to the same account are rejected with `400`.
- Amounts are strictly positive decimals with at most 2 decimal places; the direction comes from `type`
  (`DEPOSIT` / `WITHDRAWAL`), not from the sign.
- The balance can never go negative: an overdrawing withdrawal is rejected with `422`.
- Transactions are immutable (no update, delete or reversal). IDs are server-generated UUIDs, timestamps are
  server-generated UTC instants, and `description` is optional (max 255 characters).
- History, account and transfer lists are returned newest-first and are not paginated (small in-memory data
  volumes).
- Data lives in memory only and is lost on restart; every start begins from the same [demo data](#demo-data).
- Out of scope: authentication/authorization, logging/monitoring, persistence, idempotency keys.

## Design decisions / trade-offs

- **`Ledger` owns the invariant.** The in-memory store `Ledger` (package `data`) is the only way to record a
  transaction. It holds every account, each with its own history and running balance (O(1) balance reads, no
  filtering for per-account history), plus id indexes for transactions and transfers. Assigning the timestamp,
  checking the balance and appending all happen under one lock, so concurrent withdrawals cannot overdraw and history
  order always matches timestamp order. In a database this would become a conditional update or
  `SELECT … FOR UPDATE`.
- **One global ledger for all accounts (chosen) vs. one `Ledger` per account.** With a single lock, a transfer's two
  legs are written atomically inside one critical section: no lock ordering, no deadlock risk, and both legs share one
  timestamp. The alternative, a registry of per-account ledgers, would let operations on different accounts run in
  parallel, but a transfer would then need two locks taken in a fixed order. For an in-memory demo, the simpler and
  obviously-correct option wins. The trade-off is that every operation on any account is serialised behind one lock.
- **Transfer legs as `DEPOSIT`/`WITHDRAWAL` + `transferId`** rather than new `TRANSFER_IN`/`TRANSFER_OUT` types, so the
  existing contract (and `POST /transactions` validation) does not change.
- **Original endpoints alias the default account** rather than aggregating across all accounts. A `POST` needs exactly
  one account to act on, and mixing accounts in one history would change what the endpoints mean.
- **One lock instead of concurrent collections.** The invariant spans several pieces of state. Concurrent collections
  only make *individual* operations thread-safe, so the check-then-append would still need a lock. `ArrayList` +
  `HashMap` give O(1) appends and O(1) lookup by id. `CopyOnWriteArrayList` would copy the whole array on every write.
- **Known limitation:** the single lock serialises all traffic, and history reads copy the full history while
  holding it. That is fine for an in-memory demo; pagination or an immutable snapshot would remove it.
- **`BigDecimal` serialised as strings** to avoid floating-point rounding in clients and keep a stable `"70.00"` format.
- **For production:** a database with transactional writes (a row per account balance, locked per account),
  currencies, idempotency keys on `POST`, paginated history, auth, and structured logging/metrics.
