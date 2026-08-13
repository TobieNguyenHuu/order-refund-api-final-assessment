# Final Report — Order Processing and Refund API

**Author:** DuyNHN3
**Assessment:** Individual final assessment, 2 days
**Repository:** order-refund-api-final-assessment

---

## 1. Completed Requirements

All six required APIs are implemented and verified, both manually through
Swagger and through automated tests.

**Domain and database.** Flyway migrations create `orders`, `order_items`, and
all five required indexes. `Order` and `OrderItem` entities, `OrderStatus` and
`PaymentStatus` enums. All monetary values use `BigDecimal`.

**Order APIs.** Create Order, List My Orders, Get My Order Detail, Cancel
Order, Mock Payment, and Admin Update Order Status. Every endpoint uses request
and response DTOs; no JPA entity is returned from a controller. The user id is
always read from the JWT, never from the request.

**Stock, refund, and transactions.** `@Transactional` on create, cancel, and
refund. Product ids sorted before locking. `PESSIMISTIC_WRITE` on every stock
change. All items validated before any stock is reduced. A single failing item
rolls back the entire order. Stock is restored exactly once on cancellation.
Refunds occur only for orders with `PAID` payment status. Repeated payment,
cancellation, and refund are all rejected.

**Security.** Users see only their own orders and can cancel or pay only their
own. Admin endpoints require the `ADMIN` authority. An inaccessible order
returns `404`, never a message revealing that it belongs to someone else.

**Error handling and documentation.** Bean Validation on request DTOs, a global
exception handler, no stack traces exposed to clients, Swagger annotations on
every endpoint, and run instructions in `README.md`.

**Tests.** 18 automated tests covering all 13 mandatory cases plus the
concurrency bonus scenario.

## 2. Partially Completed Requirements

**`EMPTY_ORDER_ITEMS` and `INVALID_QUANTITY` are defined but unreachable.**
Both error codes exist in `ErrorCode` and are mapped to the statuses the
specification requires, but neither can be returned in practice. Section 10
requires Bean Validation on request DTOs, so `@NotEmpty` on `items` and
`@Min(1)` on `quantity` reject those requests at the validation layer, before
`OrderService` runs. Such requests return `400 VALIDATION_ERROR` with the
offending field named in `fieldErrors`.

This is a tension in the specification itself: satisfying the Bean Validation
requirement makes the two dedicated codes unreachable, and making them
reachable would mean removing the annotations. The validation requirement was
treated as the stronger of the two, since it is an explicit checklist item and
the resulting response still identifies the exact problem.

## 3. Unfinished Requirements

None. Every checklist item in specification section 10 is implemented.

## 4. Transaction Design

**Boundaries live in the service layer.** Controllers do no work beyond
extracting the user id from the security context and delegating. Every method
that changes state — `createOrder`, `cancelOrder`, `payOrder`,
`updateOrderStatus` — is annotated `@Transactional`. Read-only methods use
`@Transactional(readOnly = true)`, which lets Hibernate skip dirty checking.

**Create Order runs as one unit.** Validation, price calculation, stock
deduction, and order persistence all happen inside a single transaction. There
is no point at which stock has been reduced but the order does not exist, or
vice versa.

**Rollback is guaranteed by using unchecked exceptions.** `AppException`
extends `RuntimeException`. Spring's declarative transaction management rolls
back automatically on unchecked exceptions; a checked exception would commit
the transaction by default, leaving stock deducted for a failed order.

**Validation and mutation are separated.** `createOrder` loops over the
requested items twice. The first pass validates existence, active status, and
stock for every item. Only when all items pass does the second pass build the
order and deduct stock. Merging the loops would reduce stock for early items
before discovering that a later item fails — the transaction would roll that
back, but correctness would then depend on rollback rather than on the design.
The distinction is testable: `rollsBackAllStockChangesWhenOneItemFails` places
the valid item first and asserts its stock is untouched.

**Cancellation follows the same shape.** Load with ownership check, validate
that cancellation is allowed, lock the products, restore stock, update status,
and refund if the order was paid — all in one transaction.

## 5. Locking Design

**Pessimistic locking at the database.**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id IN :productIds ORDER BY p.id")
List<Product> findAllByIdForUpdate(@Param("productIds") Collection<Long> productIds);
```

This issues `SELECT ... FOR UPDATE`, taking a row-level write lock held until
the transaction ends. A concurrent transaction touching the same product waits
rather than reading stale stock.

**Why pessimistic rather than optimistic.** Optimistic locking lets both
transactions proceed and detects the conflict at commit, forcing the loser to
retry. That suits rare contention. Stock deduction during a flash sale is the
opposite case: many requests target the same row simultaneously, so optimistic
locking would produce a storm of retries. Pessimistic locking serializes access
to the contended row instead.

**Why product ids are sorted.** Without a consistent ordering, transaction A
could lock product 3 and then wait for product 5 while transaction B holds
product 5 and waits for product 3 — a deadlock. PostgreSQL would detect it and
kill one transaction, surfacing as an unexplained failure to a user who did
nothing wrong. Acquiring locks in ascending id order in every transaction makes
that cycle impossible. The `ORDER BY p.id` in the query enforces this at the
point where it matters.

**Why `synchronized` is not a substitute.** A Java monitor is scoped to one
JVM. Two application instances behind a load balancer have separate monitors,
so both could deduct the same stock concurrently. Only a lock held by the
database — the resource both instances share — actually prevents overselling.

**Isolation level.** PostgreSQL's default `READ_COMMITTED` matters here: a
transaction blocked on a lock re-reads the committed row once the lock is
granted, so it observes the stock value the previous transaction wrote rather
than a stale snapshot.

**Verified under real concurrency.** `ConcurrentOrderIntegrationTest` starts
ten threads against a product with five in stock, releasing them simultaneously
through a `CountDownLatch` so they genuinely contend. Exactly five orders
succeed, five fail with `INSUFFICIENT_STOCK`, final stock is zero, and stock
never goes negative.

## 6. Refund Behaviour

The refund is a mock internal state change. No payment gateway is involved.

**Payment status is independent of order status.** Paying an order changes only
`payment_status` from `UNPAID` to `PAID`; the order remains `PENDING`. A
`PENDING` order that is already `PAID` is therefore a normal state, and
cancellation must handle it.

**Cancellation branches on payment status.** `applyCancellation` restores stock
and sets the order to `CANCELLED` with a `cancelled_at` timestamp in all cases.
If the order was `PAID`, it additionally sets `payment_status` to `REFUNDED`
and stamps `refunded_at`. If the order was `UNPAID`, no refund occurs and the
payment status is left alone.

**One cancellation path, shared.** Both the user cancel endpoint and the admin
transition to `CANCELLED` call the same private `applyCancellation` method.
Section 7.6 requires an admin cancellation to restock and refund exactly as a
user cancellation does, and duplicating that logic is how two paths drift apart
over time.

**Repetition is prevented by state, not by timestamps.** A second cancellation
is rejected because the order's status is already `CANCELLED`:
`isUserCancellable()` returns false for the user path and `canTransitionTo()`
returns false for the admin path, so `applyCancellation` is unreachable a
second time and stock cannot be restored twice. Checking `cancelled_at != null`
would work too, but deriving the rule from the status keeps a single source of
truth. Repeated payment is rejected the same way, returning
`PAYMENT_ALREADY_COMPLETED`.

## 7. Test Summary

18 tests across four classes, all integration tests running against a real
PostgreSQL instance started by Testcontainers. Repositories are not mocked:
pessimistic locking and transactional rollback exist only at the database
level, so tests against mocks would pass regardless of whether the locking
works.

| Class | Tests | Covers |
|---|---:|---|
| `CreateOrderIntegrationTest` | 8 | Spec cases 1, 3, 4, 5, 6, 7, 8 |
| `OrderLifecycleIntegrationTest` | 7 | Spec cases 9, 10, 11, 12 |
| `AdminAccessIntegrationTest` | 2 | Spec case 13 |
| `ConcurrentOrderIntegrationTest` | 1 | Bonus scenario |

Mandatory case 2 (reject an empty item list) is enforced by Bean Validation
rather than by service logic, as described in section 2 above.

Two tests are worth singling out. `usesDatabasePriceAndSnapshotsIt` changes the
product's price and name *after* the order is placed and asserts the order
still reports the original values — a check that the snapshot is meaningful
rather than merely present. `userIsDeniedAdminApi` goes through `MockMvc` so
that `@PreAuthorize` and the access-denied handler are genuinely exercised, and
verifies that the service method was never invoked, not merely that the
response was 403.

## 8. Known Issues and Deviations

### Authentication layer reused from RSCRM

Per the mentor's instruction that login and authorization may be reused from
the RSCRM project or cloned from elsewhere, the authentication layer is adapted
from RSCRM (`restaurant-crm-core`). The following were rebuilt rather than
copied, because RSCRM's choices conflicted with this assessment's fixed schema:

- **Primary keys.** RSCRM uses `String` UUID primary keys via a shared
  `BaseEntity`. This assessment's schema specifies `BIGINT` foreign keys and
  numeric ids in the sample responses, so `BaseEntity` was rewritten with
  `Long` / `GenerationType.IDENTITY` to match `BIGSERIAL`.
- **Flyway.** RSCRM configures Flyway in `application.properties` but has no
  Flyway dependency and no migration files; its schema is generated by
  `ddl-auto=update`. This project declares `spring-boot-starter-flyway`, sets
  `ddl-auto=validate`, and creates the full schema in migrations V1-V4.
- **Redis.** RSCRM's security filter chain depends on a Redis-backed JWT
  blacklist. Redis is out of scope per specification section 4, so the
  blacklist was removed and this project has no Redis dependency.
- **Session policy.** Changed from `SessionCreationPolicy.ALWAYS` to
  `STATELESS`, appropriate for a token-authenticated REST API.

### Product API was built, not inherited

Specification section 2 states that the starter repository provides a Product
entity, repository, and creation API. RSCRM has no Product concept at all, so
these were implemented from scratch. Scope was kept minimal — create and read
only — since section 4 lists Product CRUD changes as out of scope.

### Additional error codes beyond the required fourteen

Five codes were added so that every failure returns the response structure the
specification mandates. All fourteen required codes are implemented exactly as
specified.

| Code | Status | Why it was needed |
|---|---:|---|
| `UNAUTHENTICATED` | 401 | Missing or invalid token. Without it, 401 responses fall outside the required error structure. |
| `INTERNAL_ERROR` | 500 | Last-resort handler; ensures stack traces are never exposed. |
| `RESOURCE_NOT_FOUND` | 404 | An unknown URL otherwise reaches the catch-all handler and returns 500. |
| `MALFORMED_REQUEST` | 400 | Unparsable JSON body, or a path variable that cannot be converted to `Long`. |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP verb on an existing path. |

### Catching generic `Exception`

Section 13 advises against catching generic `Exception` without appropriate
handling. `GlobalExceptionHandler` does declare an `Exception` handler, but it
handles the exception rather than swallowing it: the full detail is logged
server-side and the client receives only a generic message, which is what
satisfies the requirement that stack traces never reach clients.

### JWT authorities carry no prefix

Roles are placed in the token's `scope` claim as bare strings (`ADMIN`, not
`ROLE_ADMIN`), and `JwtGrantedAuthoritiesConverter` is configured with an empty
authority prefix. Endpoint security therefore uses `hasAuthority('ADMIN')`.
Using `hasRole('ADMIN')` would silently fail, since Spring prepends `ROLE_`.

### Order code generation

`order_code` is generated from a PostgreSQL sequence (`order_code_seq`) rather
than a row count. `count() + 1` is subject to a race between concurrent
transactions, producing duplicate codes and violating the `UNIQUE` constraint.
The sequence is global rather than per-day, so the numeric suffix does not
reset daily; the specified format does not require it to. Sequences are
non-transactional by design, so a failed order burns a value and leaves a gap —
expected behaviour for an identifier, not a defect.

### Committed configuration values

The JWT signing key and database password in `application.properties`,
`env.example`, and `docker-compose.yml` are local development defaults,
committed so the project runs without setup. They are not production
credentials and would be supplied through the environment in a real deployment,
where `.env` (gitignored) or platform-level configuration would override them.

### Local environment note

The development machine runs a native PostgreSQL service on port 5432, so the
Docker Compose PostgreSQL is mapped to host port **5433** and `DB_URL` targets
5433. On a machine where 5432 is free, both can be changed back without any
other adjustment.

## 9. How to Run

See `README.md` for full instructions. In short:

```bash
docker compose up -d      # start PostgreSQL
./mvnw spring-boot:run    # run the application
./mvnw test               # run the test suite (Docker required)
```

Swagger UI is at `http://localhost:8080/swagger-ui`. Seeded accounts:
`adminuser` / `Admin@12345` (ADMIN), and `useralpha` / `userbeta` with
`User@12345` (USER).

## 10. Demo Video

[link Google Drive]