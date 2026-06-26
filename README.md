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





Event Ledger – Production Architecture & Enhancement Roadmap
Overview
This document describes how the Event Ledger application can evolve from a take-home assignment into a production-ready, enterprise-grade financial transaction platform.
The current implementation demonstrates the core requirements of the assignment:
•	Spring Boot Microservices
•	Event Gateway
•	Account Service
•	Idempotency
•	Event Ordering
•	Validation
•	Distributed Tracing
•	Resilience4j
•	H2 Database
•	Docker
•	Automated Tests
While these are sufficient for the coding exercise, a real banking system requires additional architectural considerations for scalability, security, observability, reliability, and operational excellence.
________________________________________
Current Architecture
                Client
                   |
                   |
          Event Gateway Service
                   |
               REST API
                   |
           Account Service
                   |
                H2 Database
Current capabilities:
•	Synchronous REST communication
•	Duplicate event detection
•	Event ordering
•	Balance calculation
•	Validation
•	Distributed tracing
•	Circuit breaker
•	Health endpoints
•	Metrics
•	Docker deployment
________________________________________
Production Roadmap
Phase 1 – Database Improvements
Replace H2
H2 is excellent for development and testing but is not suitable for production workloads.
Recommended databases:
•	PostgreSQL
•	Aurora PostgreSQL
•	Oracle Database
•	MySQL
Each microservice should continue to own its own database.
________________________________________
Database Versioning
Instead of allowing Hibernate to generate tables automatically, introduce Flyway.
db/migration

V1__Create_Event_Table.sql

V2__Create_Transaction_Table.sql

V3__Add_Indexes.sql
Benefits:
•	Version-controlled schema
•	Safe deployments
•	Rollbacks
•	Auditability
________________________________________
Indexing
Create indexes on frequently queried columns.
Gateway:
•	eventId (Unique)
•	accountId
•	eventTimestamp
•	status
Account Service:
•	accountId
•	transactionId
•	eventId
________________________________________
Phase 2 – Configuration Management
Use Spring Profiles.
application.yml

application-dev.yml

application-test.yml

application-qa.yml

application-prod.yml
Avoid hardcoded values.
________________________________________
Secret Management
Never store:
•	Database passwords
•	API Keys
•	JWT Secrets
Use:
•	HashiCorp Vault
•	AWS Secrets Manager
•	Azure Key Vault
•	Kubernetes Secrets
________________________________________
Phase 3 – Security
Implement:
•	Spring Security
•	OAuth2 Resource Server
•	JWT Authentication
•	Role-based Authorization
•	mTLS between services
Possible flow:
Client

↓

API Gateway

↓

OAuth2 Authentication

↓

Gateway Service

↓

Account Service
________________________________________
Phase 4 – Event Driven Architecture
The current implementation uses synchronous REST.
Production systems typically use Kafka.
Gateway

↓

Kafka

↓

Account Service
Advantages:
•	Loose coupling
•	Better scalability
•	Higher throughput
•	Built-in retry
•	Buffering
•	Replay capability
Partition events by:
accountId
This guarantees ordering for events belonging to the same account.
________________________________________
Phase 5 – Reliability Patterns
Transactional Outbox
Instead of directly publishing an event after updating the database:
Transaction

↓

Save Business Data

↓

Save Outbox Record

↓

Commit

↓

Background Publisher

↓

Kafka
Benefits:
•	No lost messages
•	Atomic updates
•	Reliable event publishing
________________________________________
Dead Letter Queue
Failed events should be redirected to a Dead Letter Queue.
Gateway

↓

Kafka

↓

Account Service

↓

Failure

↓

DLQ
________________________________________
Retry Strategy
Use exponential backoff.
Example:
Attempt 1

↓

5 seconds

↓

Attempt 2

↓

15 seconds

↓

Attempt 3

↓

45 seconds
________________________________________
Phase 6 – Distributed Tracing
Current implementation propagates:
X-Trace-Id
Production systems typically use:
•	OpenTelemetry
•	Jaeger
•	Grafana Tempo
•	Zipkin
Benefits:
•	End-to-end request tracing
•	Performance bottleneck analysis
•	Dependency visualization
________________________________________
Phase 7 – Logging
Current:
Structured JSON logs.
Production:
Application

↓

Fluent Bit

↓

OpenSearch / Elasticsearch

↓

Kibana
Logs should contain:
•	Trace ID
•	Event ID
•	Account ID
•	Timestamp
•	Service Name
•	Thread Name
•	Log Level
________________________________________
Phase 8 – Monitoring
Current:
Spring Boot Actuator
Future:
Micrometer

↓

Prometheus

↓

Grafana
Monitor:
•	Request count
•	Response time
•	Error rate
•	JVM Memory
•	CPU
•	Thread Pool
•	Database Connections
•	Kafka Lag
•	Retry Count
________________________________________
Phase 9 – Caching
Introduce Redis.
Use it for:
•	Frequently accessed balances
•	Account metadata
•	Configuration
Gateway

↓

Redis

↓

Database
________________________________________
Phase 10 – High Availability
Deploy multiple service instances.
Load Balancer

↓

Gateway-1

Gateway-2

Gateway-3
Likewise for the Account Service.
________________________________________
Phase 11 – Database High Availability
Primary Database

↓

Read Replica

↓

Read Replica
Automatic failover should be configured.
________________________________________
Phase 12 – Kubernetes
Replace Docker Compose with Kubernetes.
Resources:
•	Deployment
•	Service
•	Ingress
•	ConfigMap
•	Secret
•	Horizontal Pod Autoscaler
•	StatefulSet (if required)
________________________________________
Phase 13 – CI/CD
A production deployment pipeline should include:
GitHub

↓

GitHub Actions

↓

Build

↓

Unit Tests

↓

Integration Tests

↓

SonarQube

↓

Security Scan

↓

Docker Build

↓

Push Image

↓

Deploy Kubernetes
________________________________________
Phase 14 – API Documentation
Use OpenAPI 3.
Provide:
•	Swagger UI
•	API Examples
•	Request/Response Models
•	Error Documentation
________________________________________
Phase 15 – Contract Testing
Implement Pact to verify compatibility between the Gateway and Account Service.
Benefits:
•	Prevent breaking API changes
•	Independent deployments
•	Better service evolution
________________________________________
Phase 16 – Auditing
Every transaction should store:
•	Event ID
•	Trace ID
•	Account ID
•	Source System
•	Timestamp
•	Previous Balance
•	New Balance
•	Processing Status
This enables compliance and forensic analysis.
________________________________________
Phase 17 – Disaster Recovery
Implement:
•	Automated backups
•	Cross-region replication
•	Periodic restore testing
•	Recovery Time Objective (RTO)
•	Recovery Point Objective (RPO)
________________________________________
Future Enhancements
The architecture can be further enhanced using:
•	Hexagonal Architecture
•	CQRS
•	Saga Pattern
•	Transactional Outbox
•	Domain Events
•	Optimistic Locking
•	Feature Flags
•	Rate Limiting
•	API Gateway (Kong/Spring Cloud Gateway)
•	Service Mesh (Istio/Linkerd)
________________________________________
Target Enterprise Architecture
                   Internet
                       |
                 API Gateway
                       |
          OAuth2 / JWT / mTLS
                       |
             Spring Cloud Gateway
                       |
             Event Gateway Service
                       |
                 Kafka Cluster
                       |
        ---------------------------------
        |               |               |
 Account Service   Fraud Service   Notification Service
        |
   PostgreSQL Cluster
        |
      Redis Cache
        |
 OpenTelemetry
        |
 Prometheus
        |
 Grafana
        |
 Elasticsearch
        |
 Kibana
________________________________________
Conclusion
The current Event Ledger implementation successfully demonstrates the functional and non-functional requirements expected in the take-home assignment.
The enhancements described in this document illustrate how the solution can evolve into a resilient, secure, scalable, and observable enterprise banking platform capable of handling millions of financial transactions while maintaining reliability, traceability, and operational excellence.
