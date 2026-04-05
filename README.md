# rideflow-saga-choreography

> Ride booking system built with Java Spring Boot microservices using the **Choreography-based Saga pattern**. Services communicate exclusively via Kafka events — no service calls another directly.

---

## What is this project?

RideFlow simulates the core backend of a ride-booking platform (like Uber/Ola). When a rider books a cab, a chain of independent events flows through Kafka — pricing is calculated, a driver is matched using geospatial search, the ride is tracked in real time via WebSocket, and payment is collected after the ride ends.

Every step has a compensating action. If payment fails, the ride is flagged. If no driver is found, the ride is auto-cancelled. This is the **Saga pattern** — a chain of steps where each step has a planned undo action in case of failure.

---

## Architecture

```
Rider/Driver App
      │
      ▼
 API Gateway (Spring Cloud Gateway)
      │
      ├──────────────────────────────────────────────┐
      │                                              │
      ▼                                              ▼
 Ride Service ──── ride.requested ──────► Pricing Service
      ▲                                              │
      │                                     price.calculated
      │                                              │
      │                                              ▼
      │                                   Driver Matching Service
      │                                              │
      │                              driver.assigned / driver.unavailable
      │                                              │
      ◄──────────────────────────────────────────────┘
      │
      ├── ride.started ──────► Location Tracking Service (WebSocket)
      │
      ├── ride.completed ────► Payment Service
      │                              │
      │                        payment.success / payment.failed
      │
      └── All events ────────► Notification Service (push + SMS)

                    [ Kafka Event Bus ]
```

**Rule:** No service calls another via REST. The only communication is Kafka events. This is pure choreography.

---

## Services

| Service | Port | Responsibility | DB |
|---|---|---|---|
| `ride-service` | 8081 | Ride lifecycle state machine | PostgreSQL |
| `pricing-service` | 8082 | Surge fare calculation | Redis only |
| `driver-matching-service` | 8083 | Geospatial driver search | PostgreSQL + Redis |
| `location-tracking-service` | 8084 | Real-time GPS via WebSocket | Redis only |
| `payment-service` | 8085 | Idempotent payment + refunds | PostgreSQL |
| `notification-service` | 8086 | Push + SMS fan-out | None |

---

## Kafka Topics

| Topic | Producer | Consumer | Trigger |
|---|---|---|---|
| `ride.requested` | Ride Service | Pricing Service | Rider books a cab |
| `price.calculated` | Pricing Service | Driver Matching | Fare computed |
| `driver.assigned` | Driver Matching | Ride Service, Notification | Driver accepted |
| `driver.unavailable` | Driver Matching | Ride Service | No driver in 2 min |
| `ride.started` | Ride Service | Location Tracking, Notification | Driver picks up rider |
| `ride.completed` | Ride Service | Payment Service, Notification | Ride ends |
| `ride.cancelled` | Ride Service | Payment (refund), Notification | Rider/driver cancels |
| `payment.success` | Payment Service | Notification | Payment charged |
| `payment.failed` | Payment Service | Ride Service | Charge failed |
| `payment.refunded` | Payment Service | Notification | Refund processed |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Messaging | Apache Kafka |
| Cache / Geospatial | Redis 7 (GEOADD, GEOSEARCH, Pub/Sub) |
| Database | PostgreSQL 15 |
| Real-time | WebSocket (Spring) |
| Service Discovery | Spring Cloud Gateway |
| Resilience | Resilience4j (Circuit Breaker) |
| Observability | Spring Sleuth + Zipkin |
| Testing | JUnit 5 + Testcontainers |
| Containerization | Docker + Docker Compose |
| CI | GitHub Actions |

---

## Key Design Patterns

### 1. Choreography-based Saga
No central orchestrator. Each service listens to Kafka events and reacts independently. If a step fails, the responsible service fires a failure event and other services compensate on their own.

```
ride.requested → [Pricing] → price.calculated → [Driver Matching] → driver.assigned
                                                        │
                                                 (if no driver)
                                                        │
                                               driver.unavailable → [Ride Service] → CANCELLED
```

### 2. Outbox Pattern
Prevents event loss when a service crashes after writing to DB but before publishing to Kafka.

```
POST /rides
  │
  ├── INSERT INTO rides (status=REQUESTED)        ─┐  same
  └── INSERT INTO outbox_events (ride.requested)  ─┘  transaction
  
  Separate poller thread:
  SELECT unpublished FROM outbox_events
    → publish to Kafka
    → mark published = true
```

### 3. Idempotent Payment
Kafka delivers messages at least once. The same `ride.completed` event can arrive twice. The Payment Service uses a `UNIQUE` constraint on `ride_id` to guarantee a rider is never charged twice.

```java
// If ride_id already exists → INSERT throws unique constraint
// → catch exception → return silently → no double charge
INSERT INTO payments (ride_id, amount, status) VALUES (?, ?, 'SUCCESS')
```

### 4. Redis Geospatial Driver Matching
All online drivers are stored in a Redis sorted set with lat/lng coordinates. When a ride is requested, `GEOSEARCH` finds the nearest 3 drivers in O(log N) time.

```
GEOADD drivers:locations 77.5946 12.9716 "driver-uuid-1"
GEOSEARCH drivers:locations FROMMEMBER pickup BYRADIUS 3 km ASC COUNT 3
```

---

## Project Structure

```
rideflow-saga-choreography/
├── docker-compose.yml
├── pom.xml                          ← parent Maven POM
├── ride-service/
│   ├── src/main/java/com/rideflow/ride/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/                  ← Ride entity, RideStatus enum
│   │   ├── kafka/
│   │   │   ├── producer/            ← fires ride.requested etc.
│   │   │   └── consumer/            ← listens to driver.assigned etc.
│   │   ├── outbox/                  ← OutboxEvent entity + poller
│   │   └── repository/
│   └── src/main/resources/
│       └── application.yml
├── pricing-service/
│   └── src/main/java/com/rideflow/pricing/
│       ├── kafka/consumer/          ← listens to ride.requested
│       ├── kafka/producer/          ← fires price.calculated
│       └── service/SurgePricingService.java
├── driver-matching-service/
│   └── src/main/java/com/rideflow/driver/
│       ├── kafka/
│       ├── geo/                     ← Redis GEOSEARCH logic
│       ├── controller/              ← driver registration, status
│       └── repository/
├── location-tracking-service/
│   └── src/main/java/com/rideflow/location/
│       ├── websocket/               ← driver + rider WS handlers
│       └── redis/                   ← pub/sub bridge
├── payment-service/
│   └── src/main/java/com/rideflow/payment/
│       ├── kafka/
│       ├── service/                 ← idempotency + refund logic
│       └── repository/
└── notification-service/
    └── src/main/java/com/rideflow/notification/
        ├── kafka/consumer/          ← listens to all topics
        ├── push/                    ← Firebase FCM
        └── sms/                    ← Twilio
```

---

## How to Run Locally

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker + Docker Compose

### Step 1 — Clone the repo
```bash
git clone https://github.com/YOUR_USERNAME/rideflow-saga-choreography.git
cd rideflow-saga-choreography
```

### Step 2 — Start infrastructure
```bash
docker-compose up -d
```

This starts Kafka, Zookeeper, PostgreSQL, Redis, and Zipkin. Wait ~30 seconds for everything to be ready.

### Step 3 — Verify infrastructure
```bash
# Check Kafka is running
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

# Check Redis
docker exec -it redis redis-cli ping   # should return PONG

# Check PostgreSQL
docker exec -it postgres psql -U rideflow -c "\l"
```

### Step 4 — Run all services
Open 6 terminal tabs and run each service:

```bash
# Tab 1
cd ride-service && mvn spring-boot:run

# Tab 2
cd pricing-service && mvn spring-boot:run

# Tab 3
cd driver-matching-service && mvn spring-boot:run

# Tab 4
cd location-tracking-service && mvn spring-boot:run

# Tab 5
cd payment-service && mvn spring-boot:run

# Tab 6
cd notification-service && mvn spring-boot:run
```

### Step 5 — Test the full flow
```bash
# 1. Register a driver
curl -X POST http://localhost:8083/api/drivers \
  -H "Content-Type: application/json" \
  -d '{"name":"Ravi Kumar","vehicle":"KA01AB1234","lat":12.9716,"lng":77.5946}'

# 2. Place a ride request
curl -X POST http://localhost:8081/api/rides \
  -H "Content-Type: application/json" \
  -d '{"riderId":"rider-001","pickupLat":12.9716,"pickupLng":77.5946,"dropLat":12.9352,"dropLng":77.6245}'

# 3. Check ride status
curl http://localhost:8081/api/rides/{rideId}
```

Watch the logs across all services — you'll see the Kafka events flowing in real time.

---

## Ride State Machine

```
REQUESTED
    │
    ├── (Pricing calculates fare)
    │
    ▼
PRICE_CALCULATED
    │
    ├── (Driver Matching finds driver)
    │
    ├──── (No driver found) ──────► CANCELLED
    │
    ▼
DRIVER_ASSIGNED
    │
    ├── (Driver starts ride)
    │
    ▼
STARTED
    │
    ├── (Driver ends ride)
    │
    ▼
COMPLETED
    │
    ├── (Payment charged)
    │
    ▼
PAID
```

---

## Distributed Tracing

Every request gets a `traceId` that flows through all Kafka messages. View the full journey of any ride at:

```
http://localhost:9411  ← Zipkin UI
```

Search by `rideId` tag to see exactly which service handled what and how long each step took.

---

## Running Tests

```bash
# Unit tests (no infrastructure needed)
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify -P integration-tests
```

Integration tests spin up real Kafka, PostgreSQL, and Redis using Testcontainers — no mocks.

---

## LLD:
### Flow-1: Full happy path end to end
<img width="2656" height="1434" alt="Image" src="https://github.com/user-attachments/assets/32002910-4fb8-4ba1-8b7f-8d63fd60bc6f" />
<img width="2584" height="1494" alt="Image" src="https://github.com/user-attachments/assets/f9aa0f24-ec49-4784-a39f-18864a20ea29" />

### Flow-2: No driver found
<img width="2404" height="1488" alt="image" src="https://github.com/user-attachments/assets/6c013c3e-baef-4cc5-b004-5e5f2ac16bbb" />

### Flow-3: Failure Path — Driver Offered But Nobody Accepts (Timeout)
<img width="2404" height="1106" alt="Image" src="https://github.com/user-attachments/assets/a2632ab7-631e-4872-80c1-34d62f82759a" />

### Flow-4: Payment fails
<img width="2110" height="1500" alt="Image" src="https://github.com/user-attachments/assets/91442ba6-5d37-47c7-8b35-f6d743f65001" />

### Flow-5: Driver cancels after accepting
<img width="2820" height="1364" alt="Image" src="https://github.com/user-attachments/assets/76b6daa3-f395-420d-86bc-cf3e570fb93c" />

---

## License

MIT
