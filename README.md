# Order Processing and Refund API

Individual final assessment. A Spring Boot REST API covering the order
lifecycle: order creation with safe stock deduction, ownership-scoped reads,
cancellation with stock restoration, mock payment, mock refund, and
administrative status transitions.

## Stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Security | JWT (OAuth2 Resource Server, HS512) |
| Testing | JUnit 5, Testcontainers |
| Docs | Swagger / OpenAPI |

## Prerequisites

- JDK 21
- Docker Desktop (required for both the database and the tests)

Maven is not required — the project ships with the Maven wrapper.

## Running the application

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Run the application
./mvnw spring-boot:run
```

The API is then available at `http://localhost:8080`.

Flyway creates the entire schema on first start, so no manual database setup
is needed. `spring.jpa.hibernate.ddl-auto` is set to `validate`: Hibernate
never modifies the schema, it only verifies that the entity mappings match
what the migrations created.

### Port note

Docker Compose maps PostgreSQL to host port **5433**, not the default 5432,
because the machine this was developed on runs a native PostgreSQL service on
5432. If 5432 is free on your machine you can change the mapping in
`docker-compose.yml` and the `DB_URL` default in `application.properties`;
nothing else depends on it.

## Running the tests

```bash
./mvnw test
```

Docker must be running. The tests are integration tests: Testcontainers starts
a real PostgreSQL container, Flyway applies all migrations to it, and the tests
run against that database. Pessimistic locking and transaction rollback only
exist at the database level, so testing them against mocks would prove nothing.

18 tests, covering all 13 mandatory cases from the specification plus the
concurrency bonus scenario.

## Seeded accounts

Created by migration `V4__seed_users.sql`.

| Username | Password | Role |
|---|---|---|
| `adminuser` | `Admin@12345` | ADMIN |
| `useralpha` | `User@12345` | USER |
| `userbeta` | `User@12345` | USER |

Two USER accounts exist so that ownership isolation can be demonstrated: one
user's order must be invisible to the other.

## API documentation

Swagger UI: `http://localhost:8080/swagger-ui`

To call a protected endpoint, log in via `POST /api/v1/auth/login`, copy the
returned `token`, then click **Authorize** in Swagger UI and paste it.

`api-tests.http` at the repository root contains the full set of requests for
every endpoint, including the error scenarios. It runs directly in VS Code with
the REST Client extension, and chains tokens automatically between requests.

## Endpoints

### Authentication

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/auth/login` | Public |

### Products

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/products` | ADMIN |
| GET | `/api/v1/products` | Public |
| GET | `/api/v1/products/{id}` | Public |

### Orders

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/orders` | USER |
| GET | `/api/v1/orders?page=0&size=10` | USER |
| GET | `/api/v1/orders/{id}` | USER (own order only) |
| PUT | `/api/v1/orders/{id}/cancel` | USER (own order only) |
| PUT | `/api/v1/orders/{id}/pay` | USER (own order only) |
| PUT | `/api/v1/admin/orders/{id}/status` | ADMIN |

## Order status model

PENDING ──> CONFIRMED ──> PROCESSING ──> COMPLETED
│ │
└────────────┴──> CANCELLED


`COMPLETED` and `CANCELLED` are terminal. Payment status moves
`UNPAID → PAID → REFUNDED`, and is tracked independently of order status: a
`PENDING` order can already be `PAID`, which is why cancellation has to handle
the refund case explicitly.

Users may only cancel `PENDING` orders. The `CONFIRMED → CANCELLED` transition
is reachable only through the admin endpoint.

## Project structure

src/main/java/com/assessment/orderapi/
├── common/ Shared config, error handling, base entity, auth utilities
├── identity/ User, Role, JWT authentication
├── product/ Product entity and API
└── order/ Order domain: entities, enums, service, controllers


## Further reading

`FINAL_REPORT.md` documents the transaction design, locking strategy, refund
behaviour, test summary, and known deviations from the specification.

## Configuration

Defaults are baked into `application.properties` so the project runs with no
setup. To override them, copy `env.example` to `.env` at the repository root;
`.env` is gitignored.

The JWT signing key and database password committed to this repository are
local development defaults, present so that the project is runnable out of the
box. They are not production credentials and would be supplied through the
environment in any real deployment.
