# Tiny Ledger REST API

## Goal
After this task the existing Spring Boot skeleton exposes a working, locally runnable REST API for a single in-memory ledger: clients can record deposits and withdrawals, read the current balance, and list the transaction history. Invalid input and overdrawing withdrawals are rejected with clear JSON error responses. The behaviour is covered by unit and web-layer tests, and the README documents how to run the app, example `curl` calls for every feature, and all assumptions made.

## Assumptions
- One ledger only (no accounts / multi-tenancy); TASK.md does not mention accounts, so the simplest model is used.
- Single currency; amounts carry no currency code.
- Amounts are `BigDecimal`, must be strictly positive, at most 2 decimal places; direction is given by `type` (`DEPOSIT` / `WITHDRAWAL`), not by sign.
- A withdrawal greater than the current balance is rejected (balance can never go negative) with HTTP 422.
- Transactions are immutable: no update, delete, or reversal endpoints.
- Balance is derived by keeping a running total updated on each write (O(1) read), kept consistent with the stored history.
- History is returned newest-first, unpaginated (in-memory, small data volumes).
- Transaction IDs are server-generated UUIDs; timestamps are server-generated `Instant` (UTC).
- Optional free-text `description` field on a transaction.
- Data is lost on restart — acceptable per TASK.md.
- Out of scope per TASK.md: authN/authZ, logging/monitoring, persistence, idempotency keys. Concurrency is handled minimally by making the store's write+balance update `synchronized`, so the ledger stays consistent under concurrent requests without extra infrastructure.

## Stack / constraints
- Java 17, Spring Boot 3.5.6 (`spring-boot-starter-web`, `spring-boot-starter-validation`) — already in `pom.xml`; no new dependencies.
- Maven via bundled wrapper (`./mvnw`).
- In-memory storage (`ArrayList` + running `BigDecimal` balance) behind a repository class.
- Tests: JUnit 5, Spring `@WebMvcTest` + MockMvc, AssertJ — all from `spring-boot-starter-test`. Tests follow AAA structure per CLAUDE.md.
- Java `record`s for DTOs and the domain transaction.

### API
| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/v1/transactions` | `{"type":"DEPOSIT\|WITHDRAWAL","amount":"100.00","description":"optional"}` | `201 Created` + transaction JSON, `Location` header | `400` validation, `422` insufficient funds |
| `GET` | `/api/v1/transactions` | — | `200` list of transactions, newest first | — |
| `GET` | `/api/v1/balance` | — | `200` `{"balance":"100.00"}` | — |

Errors use Spring's `ProblemDetail` (RFC 7807) JSON.

## Affected files
```
.
├── README.md                                                  (modify: API docs, curl examples, assumptions, design notes)
├── plans/
│   └── 2026-10-04-tiny-ledger-rest-api.md                     (create: this plan)
└── src/
    ├── main/java/com/teya/ledger/
    │   ├── domain/
    │   │   ├── Transaction.java                               (create: record id, type, amount, description, timestamp)
    │   │   ├── TransactionType.java                           (create: enum DEPOSIT, WITHDRAWAL)
    │   │   └── InsufficientFundsException.java                (create)
    │   ├── repository/
    │   │   └── InMemoryLedgerRepository.java                  (create: list + running balance, synchronized writes)
    │   ├── service/
    │   │   └── LedgerService.java                             (create: record/withdraw rules, balance, history)
    │   └── api/
    │       ├── LedgerController.java                          (create: 3 endpoints)
    │       ├── GlobalExceptionHandler.java                    (create: @RestControllerAdvice → ProblemDetail)
    │       └── dto/
    │           ├── CreateTransactionRequest.java              (create: record + Bean Validation)
    │           ├── TransactionResponse.java                   (create)
    │           └── BalanceResponse.java                       (create)
    └── test/java/com/teya/ledger/
        ├── service/
        │   └── LedgerServiceTest.java                         (create: plain unit tests)
        ├── repository/
        │   └── InMemoryLedgerRepositoryTest.java              (create: ordering, balance, concurrency smoke test)
        ├── api/
        │   └── LedgerControllerTest.java                      (create: @WebMvcTest + MockMvc)
        └── LedgerApiIntegrationTest.java                      (create: @SpringBootTest end-to-end flow)
```

## Ordered steps
1. **Domain model** — create `TransactionType` enum, `Transaction` record (`UUID id`, `TransactionType type`, `BigDecimal amount`, `String description`, `Instant timestamp`), and `InsufficientFundsException` (carries requested amount and current balance).
2. **Repository** — create `InMemoryLedgerRepository` (`@Repository`) holding an `ArrayList<Transaction>` and a `BigDecimal balance`. Methods: `synchronized Transaction append(Transaction)` (adds and updates balance by +/- amount), `synchronized BigDecimal balance()`, `synchronized List<Transaction> findAllNewestFirst()` (returns an unmodifiable reversed copy). Add `InMemoryLedgerRepositoryTest` covering append/balance, newest-first ordering, defensive copy, and a concurrent-deposits test (e.g. 1000 parallel deposits of 1.00 → balance 1000.00).
3. **Service** — create `LedgerService` with `recordTransaction(type, amount, description)`, `getBalance()`, `getHistory()`. Generates UUID + `Instant.now()` (inject a `java.time.Clock` bean for testability). For `WITHDRAWAL`, check balance and throw `InsufficientFundsException` if amount > balance; the check-and-append must happen atomically (do the check inside a repository method `synchronized Transaction appendIfSufficient(...)` or synchronize the service method — pick one, keep the check and write under the same lock). Add `LedgerServiceTest` for deposit, withdrawal, exact-balance withdrawal, overdraft rejection (balance and history unchanged), history ordering.
4. **DTOs + validation** — create `CreateTransactionRequest` (`@NotNull type`, `@NotNull @Positive @Digits(integer=15, fraction=2) amount`, `@Size(max=255) description`), `TransactionResponse` (with static `from(Transaction)`), `BalanceResponse`. Serialize amounts as strings to avoid floating-point formatting (`@JsonFormat(shape = STRING)`), scale 2.
5. **Controller + error handling** — create `LedgerController` (`@RestController @RequestMapping("/api/v1")`) with the three endpoints from the API table; `POST` returns `201` with `Location: /api/v1/transactions/{id}`. Create `GlobalExceptionHandler` mapping `MethodArgumentNotValidException` → 400 (field errors listed), `HttpMessageNotReadableException` (bad JSON / unknown enum) → 400, `InsufficientFundsException` → 422. Add `LedgerControllerTest` (`@WebMvcTest`, mocked service) for each endpoint's happy path and each error mapping.
6. **End-to-end test** — add `LedgerApiIntegrationTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, or `@AutoConfigureMockMvc`) running: deposit 100 → withdraw 30 → balance is 70.00 → history has 2 items newest-first → withdraw 1000 returns 422 and balance still 70.00. Use `@DirtiesContext` so the in-memory state doesn't leak between test classes.
7. **README** — replace the `_TBD_` sections: endpoint table, `curl` examples for deposit, withdrawal, balance, history, and an overdraft error example with sample responses; full Assumptions list (copy from this plan); short "Design decisions / trade-offs" section (single ledger, running balance, synchronized in-memory store, what would change for production: DB with transactions, accounts, idempotency keys, pagination).
8. **Final check** — run the full Verification section below and fix anything failing.

## Risks
- Check-then-act race on withdrawals could let two concurrent withdrawals overdraw the balance. Mitigation: perform the sufficiency check and the append under the same lock (step 3) and cover it with a concurrency test.
- `BigDecimal` equality and scale issues (`100` vs `100.00`) could cause flaky assertions and inconsistent JSON output. Mitigation: normalise all amounts with `setScale(2)` at the service boundary, compare with `compareTo`/`isEqualByComparingTo` in tests, and serialize as strings.
- Unknown enum values or malformed JSON produce Spring's default 400 HTML/whitelabel-ish body instead of a consistent error. Mitigation: explicitly handle `HttpMessageNotReadableException` in `GlobalExceptionHandler` and test it.
- Singleton in-memory state leaks between Spring test contexts, making integration tests order-dependent. Mitigation: `@DirtiesContext` on the integration test class and fresh repository instances in unit tests.
- Scope creep (accounts, pagination, persistence) could blow the "few hours" budget. Mitigation: stick to the API table above and list extensions only as README notes.

## Verification
```bash
./mvnw clean verify
./mvnw spring-boot:run &
sleep 15
curl -s -i -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' -d '{"type":"DEPOSIT","amount":"100.00","description":"salary"}'
curl -s -i -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' -d '{"type":"WITHDRAWAL","amount":"30.00","description":"groceries"}'
curl -s localhost:8080/api/v1/balance
curl -s localhost:8080/api/v1/transactions
curl -s -i -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' -d '{"type":"WITHDRAWAL","amount":"1000.00"}'
curl -s -i -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' -d '{"type":"DEPOSIT","amount":"-5"}'
kill %1
```
Expected: deposits/withdrawals return `201`, balance returns `{"balance":"70.00"}`, history lists 2 transactions newest-first, overdraft returns `422`, negative amount returns `400`.

Done when: `./mvnw clean verify` passes and every smoke-test call above returns the expected status and body, with the README documenting run instructions, examples, and assumptions.
