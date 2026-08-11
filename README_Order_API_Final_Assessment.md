# Final Assessment: Order Processing and Refund API

## 1. General Information

- **Assessment type:** Individual
- **Duration:** 2 days
- **Scope:** Backend API only
- **Domain:** E-commerce Order Processing
- **Repository:** Starter repository provided by mentor

## 2. Available Features in Starter Repository

The starter repository already provides:

- User login with JWT authentication
- Roles: `USER` and `ADMIN`
- User information available from the authentication context
- Product creation API
- Product entity and repository
- Product fields: `id`, `name`, `price`, `stock`, `active`
- PostgreSQL and Flyway configuration
- Basic global response structure

The student must not rebuild login or product creation. The assessment focuses on completing the order processing and refund flow.

## 3. Assessment Objectives

Implement the complete order flow:

1. Create an order from request items.
2. Validate products and stock.
3. Calculate the order amount using prices from the database.
4. Reduce product stock safely.
5. Retrieve the current user's orders.
6. Retrieve order details.
7. Cancel an order.
8. Restore product stock after cancellation.
9. Complete a mock refund for a paid order.
10. Allow an administrator to update order status.
11. Protect data using authentication, authorization, and ownership validation.
12. Add automated tests for critical business flows.

## 4. Out of Scope

The following features are not required:

- Frontend implementation
- Shopping cart
- Redis
- RabbitMQ
- Real payment gateway integration
- Email notification
- Product CRUD changes
- User registration or login changes
- Shipping provider integration

The refund process is a mock internal process. No external payment provider is required.

## 5. Database Requirements

Create a Flyway migration for the following tables and indexes.

### 5.1 Orders Table

```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_code VARCHAR(30) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    total_amount DECIMAL(12,2) NOT NULL,
    shipping_address VARCHAR(500) NOT NULL,
    note VARCHAR(500),
    cancelled_at TIMESTAMP,
    refunded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 5.2 Order Items Table

```sql
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL
);
```

### 5.3 Required Indexes

```sql
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
```

## 6. Status Definitions

### 6.1 Order Status

```text
PENDING -> CONFIRMED -> PROCESSING -> COMPLETED
   |
   +-> CANCELLED
```

Valid transitions:

| Current status | Allowed next status |
|---|---|
| `PENDING` | `CONFIRMED`, `CANCELLED` |
| `CONFIRMED` | `PROCESSING`, `CANCELLED` |
| `PROCESSING` | `COMPLETED` |
| `COMPLETED` | None |
| `CANCELLED` | None |

### 6.2 Payment Status

| Status | Description |
|---|---|
| `UNPAID` | Payment has not been completed |
| `PAID` | Mock payment has been completed |
| `REFUNDED` | Paid amount has been refunded |

## 7. Required APIs

### 7.1 Create Order

```http
POST /api/v1/orders
Authorization: Bearer {user-token}
Content-Type: application/json
```

Request:

```json
{
  "shippingAddress": "123 Nguyen Tat Thanh, Quy Nhon",
  "note": "Call before delivery",
  "items": [
    {
      "productId": 1,
      "quantity": 2
    },
    {
      "productId": 2,
      "quantity": 1
    }
  ]
}
```

Main rules:

- Obtain `userId` from the authentication context.
- Do not accept `userId`, product name, or price from the client.
- `shippingAddress` is required and has a maximum length of 500 characters.
- `items` must not be empty.
- `quantity` must be greater than zero.
- Duplicate product IDs are not allowed.
- Every product must exist and be active.
- Product price must be loaded from the database.
- Sort product IDs in ascending order before locking.
- Lock products using `PESSIMISTIC_WRITE`.
- Validate all items before reducing any stock.
- Stock must never become negative.
- Save product name and unit price snapshots in `order_items`.
- Create the order with `PENDING` and `UNPAID` statuses.
- The complete operation must run inside one transaction.
- If any item is invalid, the complete operation must roll back.

Expected response:

```json
{
  "id": 1001,
  "orderCode": "OFL-20260811-000001",
  "status": "PENDING",
  "paymentStatus": "UNPAID",
  "totalAmount": 1500000,
  "shippingAddress": "123 Nguyen Tat Thanh, Quy Nhon",
  "note": "Call before delivery",
  "items": [
    {
      "productId": 1,
      "productName": "Mechanical Keyboard",
      "quantity": 2,
      "unitPrice": 500000,
      "subtotal": 1000000
    }
  ],
  "createdAt": "2026-08-11T10:00:00"
}
```

### 7.2 List My Orders

```http
GET /api/v1/orders?page=0&size=10
Authorization: Bearer {user-token}
```

Rules:

- Return only orders belonging to the authenticated user.
- Default page is `0`.
- Default size is `10`.
- Maximum size is `50`.
- Sort by `createdAt DESC` by default.

### 7.3 Get My Order Detail

```http
GET /api/v1/orders/{id}
Authorization: Bearer {user-token}
```

Rules:

- A user can retrieve only their own order.
- Return `404 Not Found` if the order does not exist.
- Also return `404 Not Found` if the order belongs to another user.
- Include order items in the response.
- Do not return JPA entities directly.

### 7.4 Cancel Order and Restore Stock

```http
PUT /api/v1/orders/{id}/cancel
Authorization: Bearer {user-token}
```

Rules:

- A user can cancel only their own order.
- Only `PENDING` orders can be cancelled by a user.
- Lock related products in ascending product ID order.
- Restore stock using quantities stored in `order_items`.
- Update the order status to `CANCELLED`.
- Stock restoration and status update must run in the same transaction.
- Repeated cancellation must return a business error.
- Stock must not be restored more than once.
- If payment status is `PAID`, the cancellation must also complete the mock refund and update payment status to `REFUNDED`.
- If payment status is `UNPAID`, no refund is required.

### 7.5 Mock Payment

```http
PUT /api/v1/orders/{id}/pay
Authorization: Bearer {user-token}
```

Rules:

- A user can pay only for their own order.
- Only a `PENDING` order with `UNPAID` payment status can be paid.
- This is a mock operation. Do not integrate an external payment gateway.
- Update payment status from `UNPAID` to `PAID`.
- Repeated payment must return a business error.

### 7.6 Admin Update Order Status

```http
PUT /api/v1/admin/orders/{id}/status
Authorization: Bearer {admin-token}
Content-Type: application/json
```

Request:

```json
{
  "status": "CONFIRMED"
}
```

Rules:

- Only `ADMIN` can call this API.
- Validate the order status transition.
- Do not allow skipped transitions.
- Do not update `COMPLETED` or `CANCELLED` orders.
- Cancelling a paid order must update payment status to `REFUNDED` and restore stock.

## 8. Transaction and Locking Requirements

The product repository must provide a locking method equivalent to:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT p
    FROM Product p
    WHERE p.id IN :productIds
    ORDER BY p.id ASC
    """)
List<Product> findAllByIdForUpdate(
    @Param("productIds") Collection<Long> productIds
);
```

The create-order service flow should follow this order:

```text
1. Validate request structure
2. Reject duplicate product IDs
3. Sort product IDs
4. Lock products in ascending ID order
5. Validate product existence and active status
6. Validate stock for all products
7. Calculate prices using database values
8. Reduce stock
9. Create order and order items
10. Commit the transaction
```

The cancel-order service flow should follow this order:

```text
1. Load the order with ownership validation
2. Validate that cancellation is allowed
3. Sort product IDs from order items
4. Lock products in ascending ID order
5. Restore product stock
6. Update order status to CANCELLED
7. Update payment status to REFUNDED when payment status is PAID
8. Commit the transaction
```

## 9. Required Error Codes

Use a consistent error response structure.

```json
{
  "timestamp": "2026-08-11T10:15:30",
  "status": 409,
  "error": "Conflict",
  "code": "INSUFFICIENT_STOCK",
  "message": "Product 1 has only 2 items remaining",
  "path": "/api/v1/orders",
  "fieldErrors": []
}
```

Required error codes:

| Error code | HTTP status | Description |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `EMPTY_ORDER_ITEMS` | 400 | Order item list is empty |
| `DUPLICATE_PRODUCT` | 400 | Product ID appears more than once |
| `INVALID_QUANTITY` | 400 | Quantity is invalid |
| `PRODUCT_NOT_FOUND` | 404 | Product does not exist |
| `PRODUCT_NOT_ACTIVE` | 409 | Product is inactive |
| `INSUFFICIENT_STOCK` | 409 | Product stock is insufficient |
| `ORDER_NOT_FOUND` | 404 | Order is missing or inaccessible |
| `ORDER_CANNOT_BE_CANCELLED` | 409 | Current order cannot be cancelled |
| `INVALID_STATUS_TRANSITION` | 409 | Order status transition is invalid |
| `PAYMENT_ALREADY_COMPLETED` | 409 | Order has already been paid |
| `ORDER_CANNOT_BE_PAID` | 409 | Current order cannot be paid |
| `REFUND_ALREADY_COMPLETED` | 409 | Refund has already been completed |
| `ACCESS_DENIED` | 403 | User does not have permission |

## 10. Detailed Requirements Checklist

### Domain and Database

- [ ] Create Flyway migration for `orders`, `order_items`, and indexes.
- [ ] Create `Order` and `OrderItem` entities.
- [ ] Create `OrderStatus` and `PaymentStatus` enums.
- [ ] Configure the relationship between Order and OrderItem.
- [ ] Use `BigDecimal` for all monetary values.

### Order APIs

- [ ] Implement Create Order API.
- [ ] Implement List My Orders API.
- [ ] Implement Get My Order Detail API.
- [ ] Implement Cancel Order API.
- [ ] Implement Mock Payment API.
- [ ] Implement Admin Update Order Status API.
- [ ] Use request and response DTOs.
- [ ] Obtain the current user from the authentication context.

### Stock, Refund, and Transaction

- [ ] Use `@Transactional` for create, cancel, and refund flows.
- [ ] Sort product IDs before locking.
- [ ] Use `PESSIMISTIC_WRITE` for stock changes.
- [ ] Validate all products before changing stock.
- [ ] Roll back the complete order when one item fails.
- [ ] Restore stock exactly once after cancellation.
- [ ] Refund only an order with `PAID` payment status.
- [ ] Prevent repeated payment, cancellation, or refund.

### Security

- [ ] USER can view only their own orders.
- [ ] USER can cancel or pay only their own orders.
- [ ] USER cannot access Admin APIs.
- [ ] ADMIN role is required for status updates.
- [ ] An inaccessible order returns `404`, not ownership details.

### Error Handling and Documentation

- [ ] Add Bean Validation for request DTOs.
- [ ] Add global exception handling.
- [ ] Do not expose stack traces to clients.
- [ ] Add Swagger/OpenAPI documentation for all required APIs.
- [ ] Add instructions for running the application and tests.

## 11. Minimum Automated Tests

Implement at least 10 automated tests. The following cases are mandatory:

1. Create an order successfully and verify stock is reduced.
2. Reject an empty item list.
3. Reject duplicate product IDs.
4. Reject a missing or inactive product.
5. Reject insufficient stock.
6. Roll back all stock changes when one order item fails.
7. Use the product price from the database.
8. Reject access to another user's order.
9. Cancel a pending unpaid order and restore stock.
10. Cancel a paid order, restore stock, and set payment status to `REFUNDED`.
11. Reject repeated cancellation without restoring stock again.
12. Reject an invalid order status transition.
13. Reject USER access to Admin APIs.

Bonus test:

- Product stock is `5`.
- Execute `10` concurrent order requests for one unit each.
- At most `5` requests succeed.
- Final stock is `0`.
- Stock never becomes negative.

## 12. Suggested Two-Day Plan

### Day 1

- Create database migrations.
- Create entities, enums, DTOs, repositories, and mappers.
- Implement Create Order API.
- Implement pessimistic product locking.
- Implement List My Orders and Order Detail APIs.
- Add tests for successful order creation, validation, insufficient stock, and rollback.

### Day 2

- Implement Mock Payment API.
- Implement Cancel Order, stock restoration, and refund flow.
- Implement Admin Update Order Status API.
- Complete ownership and role validation.
- Complete global exception handling.
- Complete at least 10 automated tests.
- Complete Swagger and final documentation.

## 13. Coding Rules

- Source code and comments must be written in English.
- Use Java 17 or later.
- Use constructor injection.
- Do not use field injection.
- Do not place business logic in controllers.
- Do not return JPA entities from controllers.
- Do not hard-code user IDs.
- Do not use `double` or `float` for monetary calculations.
- Do not accept price or product name from the client.
- Do not use Java `synchronized` as a replacement for database locking.
- Do not use `printStackTrace()`.
- Avoid catching generic `Exception` without appropriate handling.
- Keep transaction boundaries in the service layer.

## 14. Critical Failure Conditions

The submission does not pass if any of the following occurs:

- Source code does not compile.
- Application cannot start.
- Flyway migration fails on a new database.
- Create Order API is missing.
- Create Order does not use a transaction.
- There is no database stock-locking mechanism.
- Stock can become negative.
- Product price is accepted from the client.
- USER can access another user's order.
- USER can access Admin APIs.
- Cancellation does not restore stock.
- Repeated cancellation restores stock multiple times.
- A paid, cancelled order is not marked as `REFUNDED`.
- There are no automated tests.

## 15. Required Deliverables

1. Completed source code in the assigned repository.
2. Flyway migration scripts.
3. Unit and integration tests.
4. Swagger/OpenAPI documentation.
5. Postman collection or equivalent API request file.
6. Updated project README with run instructions.
7. `FINAL_REPORT.md` containing:
   - Completed requirements
   - Partially completed requirements
   - Unfinished requirements
   - Transaction design
   - Locking design
   - Refund behavior
   - Test summary
   - Known issues

## 16. Final Demo Scenarios

### Scenario 1: Successful Order

1. Login as USER.
2. Create an order containing two products.
3. Verify total amount.
4. Verify product stock is reduced.
5. Retrieve the order list and order detail.

### Scenario 2: Transaction Rollback

1. Submit an order containing two products.
2. The first product has enough stock.
3. The second product has insufficient stock.
4. Verify no order is created.
5. Verify stock for both products is unchanged.

### Scenario 3: Ownership

1. User A creates an order.
2. User B requests User A's order detail.
3. Verify the API returns `404 Not Found`.

### Scenario 4: Unpaid Cancellation

1. Create an unpaid order.
2. Cancel the order.
3. Verify stock is restored.
4. Verify status is `CANCELLED`.
5. Verify payment status remains `UNPAID`.

### Scenario 5: Paid Cancellation and Refund

1. Create an order.
2. Complete the mock payment.
3. Cancel the paid order.
4. Verify stock is restored.
5. Verify order status is `CANCELLED`.
6. Verify payment status is `REFUNDED`.
7. Repeat cancellation and verify stock is not restored again.

### Scenario 6: Admin Status Validation

1. Login as ADMIN.
2. Update `PENDING` to `CONFIRMED`.
3. Update `CONFIRMED` to `PROCESSING`.
4. Attempt to update `PROCESSING` directly to `PENDING` or another invalid status.
5. Verify the API returns `INVALID_STATUS_TRANSITION`.

## 17. Review Questions

The student must be able to explain:

1. Why Create Order requires `@Transactional`.
2. Why product price must be loaded from the database.
3. How pessimistic locking prevents overselling.
4. Why product IDs are sorted before locking.
5. Why Java `synchronized` is not sufficient in a distributed application.
6. How complete rollback is guaranteed when one item fails.
7. How repeated cancellation and repeated refund are prevented.
8. Why OrderItem stores product name and unit price snapshots.
9. How order ownership is validated.
10. Why an inaccessible order returns `404`.
11. Why `BigDecimal` is required for monetary values.
12. Where transaction boundaries are placed and why.
