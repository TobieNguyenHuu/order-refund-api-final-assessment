# Progress Log

## Completed

- [x] Repo setup, `CLAUDE.md`, `.gitignore`, Maven wrapper
- [x] `pom.xml` — Spring Boot 4.1.0, Java 21
- [x] Docker Compose Postgres (host port 5433)
- [x] Flyway V1-V4: identity tables, products, orders + indexes, seed users
- [x] `BaseEntity`, `Role`, `User`, `Product` entities — verified against
      `ddl-auto=validate`
- [x] `ErrorCode`, `AppException`, `ErrorResponse`, `GlobalExceptionHandler`
- [x] Authentication: JWT login, `SecurityConfig`, `AuthUtils`,
      security exception handlers
- [x] `api-tests.http` — manual verification of 11 request scenarios

## In Progress

- [ ] Order domain: enums, `Order` / `OrderItem` entities, repositories

## Next

- [ ] `OrderService.createOrder()` — transaction, locking, stock deduction
- [ ] List / detail / cancel / pay APIs
- [ ] Admin status update API
- [ ] Automated tests (minimum 10, spec section 11)
- [ ] Swagger annotations, Postman collection, run instructions

## Build and Environment Issues Resolved

| Issue | Resolution |
|---|---|
| Boot 4 renamed starters (`spring-boot-starter-webmvc` etc.) | Verified against the Boot 4.0 migration guide; names kept as-is. |
| Flyway not auto-configured from the bare jar in Boot 4 | Switched to `spring-boot-starter-flyway`. |
| springdoc 2.8.x targets Boot 3 / Jackson 2 | Upgraded to springdoc 3.1.0. |
| `nimbus-jose-jwt` not managed by the Boot 4.1.0 BOM | Pinned to 10.9, matching what `spring-security-oauth2-jose:7.1.0` pulls in transitively. Pinning the older 9.48 would have silently downgraded it. |
| Testcontainers 2.x renamed all module artifacts | Use `testcontainers-postgresql` / `testcontainers-junit-jupiter`. Container classes also moved to `org.testcontainers.<module>`. |
| `password authentication failed for user "postgres"` | A native PostgreSQL 18 Windows service held port 5432; the app was reaching the wrong server. Compose remapped to 5433. |
| Security exceptions bypassed `@RestControllerAdvice` | `AuthenticationEntryPoint` and `AccessDeniedHandler` delegate to the shared `HandlerExceptionResolver`, so the error body is built in one place. |
| Unknown URLs returned 500 | Added an explicit `NoResourceFoundException` handler ahead of the catch-all. |
