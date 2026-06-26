# Event Ledger Spring Boot

A GitHub-ready Java 17, Spring Boot 3, Maven, H2, JPA, Validation, Actuator, and Resilience4j take-home project for processing financial account events safely when upstream systems send duplicates or out-of-order messages.

## SBI Example

Think of an SBI account receiving transaction events from other banks and upstream systems:

- ICICI sends salary: `CREDIT INR 10000`
- Axis sends personal loan EMI: `DEBIT INR 2000`
- The same event may be delivered twice.
- A later event may arrive before an earlier business-time event.

The system stores every Gateway event using `eventId` as the idempotency key, applies each transaction to the Account Service only once, and returns account event history ordered by original `eventTimestamp`, not by receive time.

## Architecture

```text
                         X-Trace-Id: demo-sbi-trace-001

 ICICI Salary System      Axis Loan System
 CREDIT INR 10000         DEBIT INR 2000
        |                       |
        +----------+------------+
                   |
                   v
        +-------------------------------+
        | Event Gateway API             |
        | public, port 8080             |
        | - validates payloads          |
        | - stores event ledger in H2   |
        | - detects duplicate eventId   |
        | - keeps RECEIVED/APPLIED/FAILED
        | - calls Account Service with  |
        |   retry, timeout, circuit     |
        |   breaker, and X-Trace-Id     |
        +---------------+---------------+
                        |
                        | REST
                        v
        +-------------------------------+
        | Account Service               |
        | internal, port 8081           |
        | - separate H2 database        |
        | - applies unique eventId once |
        | - CREDIT adds, DEBIT subtracts
        | - stores transaction history  |
        | - returns current balance     |
        +-------------------------------+
```

## What It Covers

- Multi-module Maven project with `gateway-service` and `account-service`.
- Separate H2 databases for each service.
- Public Gateway API on port `8080`.
- Internal Account Service on port `8081`.
- Required field validation with meaningful `400` responses.
- Idempotency at both services using `eventId`.
- Gateway event statuses: `RECEIVED`, `APPLIED`, `FAILED`.
- Out-of-order event listing sorted by upstream `eventTimestamp`.
- `receivedAt` stored separately from business event time.
- `X-Trace-Id` generation, reuse, propagation, response header, and structured JSON-style logs.
- Resilience4j circuit breaker and retry around Account Service calls.
- Gateway read endpoints still work when Account Service is down.
- Actuator `/health` and `/metrics` endpoints.
- Automated tests using JUnit 5, MockMvc, and MockWebServer.
- Dockerfiles and Docker Compose.

## API Endpoints

Gateway, port `8080`:

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/events` | Submit a transaction event |
| `GET` | `/events/{eventId}` | Fetch one Gateway event |
| `GET` | `/events?account={accountId}` | Fetch account events sorted by `eventTimestamp` |
| `GET` | `/accounts/{accountId}/balance` | Proxy current balance from Account Service |
| `GET` | `/health` | Service health |
| `GET` | `/metrics` | Actuator metrics |

Account Service, port `8081`:

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | Apply one transaction |
| `GET` | `/accounts/{accountId}/balance` | Get current balance |
| `GET` | `/accounts/{accountId}` | Get account details and transactions |
| `GET` | `/health` | Service health |
| `GET` | `/metrics` | Actuator metrics |

## Event Payload

```json
{
  "eventId": "evt-icici-salary-001",
  "accountId": "sbi-acct-123",
  "type": "CREDIT",
  "amount": 10000,
  "currency": "INR",
  "eventTimestamp": "2026-05-15T09:00:00Z",
  "metadata": {
    "source": "ICICI",
    "description": "Salary credit"
  }
}
```

## Idempotency

`eventId` is the unique idempotency key.

When the Gateway receives a new `eventId`, it saves the event as `RECEIVED`, calls the Account Service, then marks it `APPLIED` on success or `FAILED` on downstream failure.

When the same `eventId` arrives again, the Gateway returns the original stored event and does not call the Account Service again. The Account Service also stores transaction `eventId` as unique, so even a direct duplicate transaction is applied only once.

## Out-Of-Order Events

Events can arrive in any order. The Gateway stores both:

- `eventTimestamp`: original upstream business time
- `receivedAt`: Gateway receive time

`GET /events?account=sbi-acct-123` always sorts by `eventTimestamp` ascending, so an older ICICI salary event appears before a later Axis EMI event even if it arrived later.

## Trace Propagation

If a client sends `X-Trace-Id`, the Gateway reuses it. If not, the Gateway generates a UUID. The same trace ID is returned in the Gateway response header and forwarded to the Account Service. Both services include trace IDs and event IDs in JSON-style logs.

## Resiliency

The Gateway wraps Account Service calls with Resilience4j retry and circuit breaker plus HTTP connect/read timeouts. This avoids hanging forever on a slow or unavailable Account Service and returns a clear `503 Service Unavailable`.

Graceful degradation:

- `POST /events`: stores the event as `FAILED` and returns `503` if Account Service is unavailable.
- `GET /events/{eventId}`: still works from Gateway H2.
- `GET /events?account=...`: still works from Gateway H2.
- `GET /accounts/{accountId}/balance`: returns `503` because live balance depends on Account Service.

## Run Tests

Prerequisites: Java 17+ and Maven 3.8+.

```bash
mvn clean test
```

Current verified result:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Run Locally

Terminal 1:

```bash
mvn -pl account-service spring-boot:run
```

Terminal 2:

```bash
mvn -pl gateway-service spring-boot:run
```

Then open:

- Gateway health: `http://localhost:8080/health`
- Account health: `http://localhost:8081/health`

## Run With Docker Compose

```bash
docker compose up --build
```

Then use the Gateway at `http://localhost:8080`.

## Sample Requests

Run the included script:

```bash
chmod +x scripts/sample-requests.sh
./scripts/sample-requests.sh
```

Or submit the SBI example manually:

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: demo-sbi-trace-001" \
  -d '{
    "eventId": "evt-icici-salary-001",
    "accountId": "sbi-acct-123",
    "type": "CREDIT",
    "amount": 10000,
    "currency": "INR",
    "eventTimestamp": "2026-05-15T09:00:00Z",
    "metadata": { "source": "ICICI", "description": "Salary credit" }
  }'
```

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: demo-sbi-trace-001" \
  -d '{
    "eventId": "evt-axis-emi-001",
    "accountId": "sbi-acct-123",
    "type": "DEBIT",
    "amount": 2000,
    "currency": "INR",
    "eventTimestamp": "2026-05-15T10:00:00Z",
    "metadata": { "source": "Axis", "description": "Personal loan EMI" }
  }'
```

Check balance:

```bash
curl http://localhost:8080/accounts/sbi-acct-123/balance
```

Expected balance:

```json
{"accountId":"sbi-acct-123","balance":8000.00,"currency":"INR"}
```

## Verify Duplicate Handling

Send the same ICICI salary event again with the same `eventId`:

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-icici-salary-001",
    "accountId": "sbi-acct-123",
    "type": "CREDIT",
    "amount": 10000,
    "currency": "INR",
    "eventTimestamp": "2026-05-15T09:00:00Z"
  }'
```

The response returns the original Gateway event. The balance remains `8000`, not `18000`.

## Verify Out-Of-Order Ordering

Submit events in reverse business-time order, then list:

```bash
curl "http://localhost:8080/events?account=sbi-acct-123"
```

The response is sorted by `eventTimestamp`, not the order in which the Gateway received them.

## Push To GitHub

Create an empty GitHub repository, then run:

```bash
git remote add origin https://github.com/<your-username>/event-ledger-springboot.git
git branch -M main
git push -u origin main
```

## Project Structure

```text
event-ledger-springboot/
├── pom.xml
├── README.md
├── docker-compose.yml
├── account-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── gateway-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
└── scripts/
    └── sample-requests.sh
```
