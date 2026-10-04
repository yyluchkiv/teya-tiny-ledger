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

_TBD — endpoints and examples will be added with the ledger implementation._

## Assumptions

_TBD._
