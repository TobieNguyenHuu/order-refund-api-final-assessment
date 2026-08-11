# Project Context

Final assessment: Order Processing and Refund API.
Read `README_Order_API_Final_Assessment.md` for full spec. It is authoritative.

## Stack
- Spring Boot 4.1.0, Java 21, Maven
- PostgreSQL + Flyway (migrations are the ONLY source of schema truth)
- JWT via OAuth2 Resource Server (Nimbus, HS512)
- Testcontainers for integration tests
- Base package: com.assessment.orderapi

## Hard rules (violating any of these fails the assessment)
- All code and comments in English.
- Constructor injection only. Never field injection.
- No business logic in controllers. Transaction boundaries live in the service layer.
- Never return JPA entities from controllers. Always use DTOs.
- BigDecimal for all money. Never double/float.
- Never accept price, productName, or userId from the client. Read userId from
  the JWT via AuthUtils; read price/name from the database.
- spring.jpa.hibernate.ddl-auto must stay `validate`. Schema changes go in a new
  Flyway migration file. Never edit an already-committed migration.
- Stock must never go negative.
- Use `hasAuthority('ADMIN')`, NOT `hasRole('ADMIN')` — authorities are stored
  without the ROLE_ prefix.
- No printStackTrace(). No bare `catch (Exception e)`.
- Do not add Redis, RabbitMQ, email, or any payment gateway. Refund is a mock.

## Error response format
Errors must match section 9 of the spec exactly:
{ timestamp, status, error, code, message, path, fieldErrors }
Success responses are returned bare, NOT wrapped in an ApiResponse envelope.

## Workflow
- Do not implement OrderService business logic unless explicitly asked.
  The author is implementing that manually for learning purposes.
- After any change, run: ./mvnw clean compile

## Decisions made
- security.jwt.signer-key is consumed as raw UTF-8 bytes via
  SIGNER_KEY.getBytes(), NOT hex-decoded. The value must therefore be at
  least 64 ASCII characters for HS512 (512-bit minimum, RFC 7518 §3.2).
  It looks like a hex string but is not treated as one. Do not switch to
  Hex.decode() — that would yield 32 bytes and throw KeyLengthException.
  - Postgres runs on host port 5433, not 5432. A native PostgreSQL 18 Windows
  service occupies 5432 on the dev machine, and Docker's port bind does not
  reliably fail — connections silently reach the wrong server. docker-compose
  maps 5433:5432 and DB_URL points at 5433.