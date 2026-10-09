# Multiple Accounts and Transfers

## Goal
After this task the ledger supports many accounts instead of one. Clients can create and list accounts, deposit to and withdraw from a specific account, read each account's balance and history, and transfer money between two accounts atomically. A transfer either moves the full amount or does nothing, and an account can never go below zero. The existing API (`POST/GET /api/v1/transactions`, `GET /api/v1/transactions/{id}`, `GET /api/v1/balance`) keeps its paths, payloads, status codes and demo data, and now acts on a well-known **default account**. All existing tests pass without changes to their assertions.

## Assumptions
- "Keep current API/ledger intact" means: the legacy endpoints keep their exact behaviour and response shapes, and the `Ledger` class keeps its role as the single-account invariant owner (one `Ledger` per account). Its public methods are only added to, never changed.
- The legacy endpoints act on a default account with the fixed id `00000000-0000-0000-0000-000000000001` and name `main`. It always exists and is also reachable under `/api/v1/accounts/{id}/...`.
- Accounts have a server-generated UUID, a required `name` (1–100 chars, not unique) and a `createdAt` instant. There is no update or delete (same immutability stance as transactions).
- Single currency for all accounts, as today.
- A transfer is recorded as two ordinary transactions, a `WITHDRAWAL` on the source and a `DEPOSIT` on the destination, both carrying the same `transferId`. `TransactionType` is **not** extended, so legacy clients only ever see `DEPOSIT`/`WITHDRAWAL`.
- `transferId` is added to the transaction JSON only when it is non-null (`@JsonInclude(NON_NULL)`). Responses for plain deposits and withdrawals stay byte-for-byte the same as today.
- A transfer to the same account is rejected with `400`. Unknown source or destination account → `404`. Insufficient funds on the source → `422` with the same problem shape as today.
- Transfers are stored as their own resource (`GET /api/v1/transfers/{id}`) so `POST /transfers` can return a `Location` header, consistent with `POST /transactions`.
- Account list and transfer list are unpaginated and returned in creation order, newest first, as with history today.
- Demo data: the existing 4 transactions on `main` are unchanged (balance `1084.50`). A second demo account `savings` is added with a single `DEPOSIT 500.00 "opening balance"`. No demo transfer touches `main`, so the documented legacy balance stays valid.
- Out of scope (as before): auth, persistence, idempotency keys, pagination, currencies, account closing.

## Stack / constraints
- Java 17, Spring Boot 3.5 (web, validation). No new dependencies.
- In-memory storage only. `ConcurrentHashMap` for the account and transfer registries, and the existing per-`Ledger` monitor for balance invariants.
- Errors stay RFC 7807 `ProblemDetail` via the existing `GlobalExceptionHandler`.
- Tests: JUnit 5, AssertJ, `@WebMvcTest` + MockMvc, `@SpringBootTest` integration test. All in AAA structure per CLAUDE.md.

### New API
| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/v1/accounts` | `{"name":"savings"}` | `201` + account JSON, `Location` | `400` |
| `GET` | `/api/v1/accounts` | — | `200` list of accounts (with balance) | — |
| `GET` | `/api/v1/accounts/{accountId}` | — | `200` `{"id","name","balance","createdAt"}` | `400`, `404` |
| `POST` | `/api/v1/accounts/{accountId}/transactions` | same as legacy `POST /transactions` | `201` + transaction JSON, `Location` | `400`, `404`, `422` |
| `GET` | `/api/v1/accounts/{accountId}/transactions` | — | `200` history, newest first | `400`, `404` |
| `GET` | `/api/v1/accounts/{accountId}/transactions/{id}` | — | `200` transaction JSON | `400`, `404` |
| `GET` | `/api/v1/accounts/{accountId}/balance` | — | `200` `{"balance":"…"}` | `400`, `404` |
| `POST` | `/api/v1/transfers` | `{"fromAccountId","toAccountId","amount":"10.00","description":"optional"}` | `201` + transfer JSON, `Location` | `400`, `404`, `422` |
| `GET` | `/api/v1/transfers/{id}` | — | `200` transfer JSON | `400`, `404` |

Transfer JSON: `{"id","fromAccountId","toAccountId","amount","description","timestamp","debitTransactionId","creditTransactionId"}`.

## Options considered (pros / cons)

### A. One `Ledger` per account, kept in an `AccountRegistry` (**recommended**)
- **Pros:** `Ledger` and its invariant are reused unchanged. Legacy endpoints keep working by binding the existing `Ledger` bean to the default account, so `LedgerService` and `LedgerController` stay untouched. Operations on different accounts don't contend for one global lock. The model maps directly to "account = row with a balance" in a future database.
- **Cons:** a transfer spans two monitors, so it needs ordered locking (by account id) to avoid deadlock. The code is slightly more involved than with a single lock. There is no global, cross-account transaction order, only a per-account one.

### B. A single global `Ledger` with an `accountId` on each `Transaction`
- **Pros:** one lock, so transfers are trivially atomic and deadlock-free. There is one global history order.
- **Cons:** `Ledger` has to be rewritten (a balance map, history filtering per account), which breaks the "ledger intact" requirement. Every operation on any account is serialised behind one lock. Per-account history is O(n) filtering unless more indexes are added.

### C. Transfers as a new `TransactionType` (`TRANSFER_IN` / `TRANSFER_OUT`) instead of `DEPOSIT`/`WITHDRAWAL` + `transferId`
- **Pros:** the history is self-describing without looking at `transferId`.
- **Cons:** it changes the legacy contract. `GET /transactions` on `main` could return enum values old clients don't know, and `POST /transactions` would need extra validation to reject them. `signedAmount()` and the existing tests would also change. → **Rejected** in favour of keeping `DEPOSIT`/`WITHDRAWAL` and linking the legs with `transferId`.

### D. Legacy endpoints aggregate across all accounts instead of aliasing the default account
- **Pros:** "the balance" would mean total money held.
- **Cons:** `POST /transactions` would have no account to act on, and the history would mix accounts. That changes legacy semantics. → **Rejected.**

## Affected files
```
.
├── README.md                                                   (modify: accounts/transfers API, curl examples, assumptions, options/trade-offs)
├── postman/
│   └── tiny-ledger.postman_collection.json                     (modify: add Accounts + Transfers folders with tests)
├── plans/
│   └── 2026-10-09-multiple-accounts-and-transfers.md           (create: this plan)
└── src/
    ├── main/java/com/teya/ledger/
    │   ├── TinyLedgerApplication.java                          (modify: AccountRegistry bean; Ledger bean = default account's ledger)
    │   ├── domain/
    │   │   ├── base/
    │   │   │   ├── Transaction.java                            (modify: nullable transferId component + 5-arg convenience constructor)
    │   │   │   ├── Account.java                                (create: record id, name, createdAt)
    │   │   │   └── Transfer.java                               (create: record id, from, to, amount, description, timestamp, debitTxId, creditTxId)
    │   │   └── exceptions/
    │   │       ├── AccountNotFoundException.java               (create)
    │   │       ├── TransferNotFoundException.java              (create)
    │   │       └── SameAccountTransferException.java           (create)
    │   ├── data/
    │   │   ├── Ledger.java                                     (modify: add record(type, amount, description, transferId) overload; existing method delegates)
    │   │   ├── AccountRegistry.java                            (create: accounts + ledgers + transfers; ordered two-lock transfer)
    │   │   └── DemoDataSeeder.java                             (modify: also seed "savings" account with 500.00)
    │   ├── service/
    │   │   ├── LedgerService.java                              (unchanged)
    │   │   ├── AccountService.java                             (create: create/list/get account, deposit/withdraw, balance, history, find tx)
    │   │   └── TransferService.java                            (create: validate + execute transfer, get transfer)
    │   └── api/
    │       ├── LedgerController.java                           (unchanged)
    │       ├── AccountController.java                          (create)
    │       ├── TransferController.java                         (create)
    │       ├── GlobalExceptionHandler.java                     (modify: 404 account/transfer, 400 same-account)
    │       └── dto/
    │           ├── TransactionResponse.java                    (modify: transferId, @JsonInclude(NON_NULL))
    │           ├── CreateAccountRequest.java                   (create: @NotBlank @Size(max=100) name)
    │           ├── AccountResponse.java                        (create: id, name, balance as string, createdAt)
    │           ├── CreateTransferRequest.java                  (create: @NotNull ids, same amount rules as CreateTransactionRequest, description ≤255)
    │           └── TransferResponse.java                       (create)
    └── test/java/com/teya/ledger/
        ├── LedgerApiIntegrationTest.java                       (modify: add multi-account + transfer flow; existing tests untouched)
        ├── domain/TransactionTest.java                         (unchanged — 5-arg constructor still exists)
        ├── data/
        │   ├── LedgerTest.java                                 (modify: add test for transferId overload)
        │   ├── AccountRegistryTest.java                        (create)
        │   └── DemoDataSeederTest.java                         (modify: assert savings account seeded, main unchanged)
        ├── service/
        │   ├── AccountServiceTest.java                         (create)
        │   └── TransferServiceTest.java                        (create)
        └── api/
            ├── AccountControllerTest.java                      (create)
            └── TransferControllerTest.java                     (create)
```

## Ordered steps
1. **Domain.** Add `Account` and `Transfer` records with null/positivity checks like `Transaction`. Add a nullable `transferId` component to `Transaction` and keep a 5-arg constructor that passes `null`, so existing callers and tests compile unchanged. Add `AccountNotFoundException(UUID)`, `TransferNotFoundException(UUID)` and `SameAccountTransferException(UUID)`.
2. **Ledger overload.** Add `Ledger.record(type, amount, description, UUID transferId)`. Make the existing 3-arg `record` delegate with `null`. Lock, balance check and ordering stay identical. Extend `LedgerTest` with one case asserting that `transferId` is stored and returned by `find`.
3. **AccountRegistry** (`data`). Use a `ConcurrentHashMap<UUID, Entry(Account, Ledger)>`, a `ConcurrentHashMap<UUID, Transfer>`, and an insertion-ordered list for listing, guarded by its own small lock. The constructor creates the default account (`DEFAULT_ACCOUNT_ID`, `"main"`). Methods: `create(name)`, `list()`, `find(id)`, `ledger(id)`, `defaultLedger()`, `findTransfer(id)`, and `transfer(fromId, toId, amount, description)`. `transfer` resolves both entries, rejects same-account transfers, picks the lock order by `UUID.compareTo`, and nests `synchronized(first){ synchronized(second){ … } }`. Inside, it records the source `WITHDRAWAL` first (throws `InsufficientFundsException` before anything is written), then the destination `DEPOSIT`, both with a fresh `transferId`, then stores the `Transfer`. `Ledger`'s own `synchronized` methods re-enter the held monitors safely.
4. **Wiring.** In `TinyLedgerApplication`, replace the `Ledger` bean with an `AccountRegistry` bean and expose `Ledger ledger(AccountRegistry r) { return r.defaultLedger(); }`. `LedgerService` and `LedgerController` stay byte-for-byte unchanged.
5. **AccountRegistryTest.** Cover: default account exists with the fixed id; create/list/find; a transfer moves money and links both legs by `transferId`; an insufficient-funds transfer leaves both balances and histories untouched; same-account and unknown-account transfers are rejected; a concurrency test (e.g. 2 threads × 1 000 opposite-direction transfers between A and B via `ExecutorService`, with a timeout) finishes without deadlock and conserves the total balance.
6. **Services.** `AccountService` reuses the same `normalise` (scale 2, `UNNECESSARY`) logic as `LedgerService`. Extract it into a package-private `Amounts.normalise` helper in `service`, and switch `LedgerService` to it only if that is a pure move (otherwise duplicate the 3 lines). `TransferService` normalises the amount and delegates to the registry. Add unit tests for both services against a real `AccountRegistry` with a fixed `Clock`.
7. **DTOs + controllers.** Create `AccountController` (`/api/v1/accounts…`) and `TransferController` (`/api/v1/transfers…`) per the API table, with `Location` headers on `POST`. Add `transferId` with `@JsonInclude(NON_NULL)` to `TransactionResponse`. Add `@WebMvcTest` tests for each new controller covering success, `400` validation, `404` and `422`.
8. **Error mapping.** Extend `GlobalExceptionHandler`: `AccountNotFoundException` → `404 "Account not found"` with an `id` property; `TransferNotFoundException` → `404 "Transfer not found"`; `SameAccountTransferException` → `400 "Invalid transfer"`. `InsufficientFundsException` keeps its existing mapping.
9. **Seeder.** Have `DemoDataSeeder` also create a `savings` account and deposit `500.00 "opening balance"` through `AccountService`. Leave the `main` seed list untouched. Update `DemoDataSeederTest`.
10. **Integration test.** Add a flow to `LedgerApiIntegrationTest`: create account → deposit → transfer from `main` to it → check both balances, both histories (with matching `transferId`) and `GET /transfers/{id}` → transfer too much → `422`, balances unchanged. Existing test methods are not edited. Use relative assertions (`before - amount`) where `main`'s balance is shared with the other tests.
11. **Docs + Postman.** README: add the new endpoints to the API table, curl examples for account creation, account deposit/withdraw, transfer and transfer errors, the new assumptions, and a condensed version of the options/trade-offs above. Postman: add "Accounts" and "Transfers" folders, with tests, that store `accountId`/`transferId` in collection variables.

## Risks
- Two-monitor locking in `transfer` can deadlock if any code path locks two ledgers in a different order. Mitigation: all two-ledger locking lives only in `AccountRegistry.transfer`, which always orders by `UUID.compareTo`, and the concurrency test in step 5 runs under a timeout.
- Changing the `Transaction` record or `TransactionResponse` could silently alter legacy JSON (e.g. emit `"transferId":null`). Mitigation: use `@JsonInclude(NON_NULL)` and keep the existing `LedgerControllerTest` and integration assertions unedited, so they act as a regression guard on the legacy shape.
- Replacing the `Ledger` bean with one derived from `AccountRegistry` could create a second, disconnected `Ledger` if wired wrongly, so legacy calls and `/accounts/{default}` would diverge. Mitigation: add an integration assertion that a deposit via `POST /transactions` is visible at `GET /accounts/00000000-0000-0000-0000-000000000001/balance`.
- Integration tests share one Spring context and mutate the default account, so absolute balance assertions in new tests may become order-dependent. Mitigation: new tests create their own accounts or assert relative changes only.
- A transfer's two legs get two separate `Instant.now(clock)` readings, so their timestamps can differ by microseconds. Mitigation: document it, and use the debit leg's timestamp as the `Transfer.timestamp`. Both are taken while both locks are held, so no other write can interleave between them.

## Verification
```bash
./mvnw -q clean verify
./mvnw test -Dtest='AccountRegistryTest,AccountServiceTest,TransferServiceTest,AccountControllerTest,TransferControllerTest,LedgerApiIntegrationTest'
./mvnw spring-boot:run &
sleep 15
curl -s localhost:8080/api/v1/balance
curl -s localhost:8080/api/v1/accounts
ACC=$(curl -s -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' -d '{"name":"holiday"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
curl -s -i -X POST localhost:8080/api/v1/transfers -H 'Content-Type: application/json' -d "{\"fromAccountId\":\"00000000-0000-0000-0000-000000000001\",\"toAccountId\":\"$ACC\",\"amount\":\"84.50\",\"description\":\"save\"}"
curl -s localhost:8080/api/v1/balance
curl -s localhost:8080/api/v1/accounts/$ACC/balance
curl -s -i -X POST localhost:8080/api/v1/transfers -H 'Content-Type: application/json' -d "{\"fromAccountId\":\"$ACC\",\"toAccountId\":\"$ACC\",\"amount\":\"1.00\"}"
npx newman run postman/tiny-ledger.postman_collection.json
kill %1
```
Done when: all existing tests pass with unedited assertions, the legacy endpoints return the same responses as before (`1084.50` starting balance), and a transfer from `main` to a new account moves exactly the amount (`main` → `1000.00`, new account → `84.50`) while invalid transfers return `400`/`404`/`422` and leave both balances unchanged.
