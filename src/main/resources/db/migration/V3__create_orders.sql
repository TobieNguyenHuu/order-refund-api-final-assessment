CREATE TABLE orders (
    id               BIGSERIAL     PRIMARY KEY,
    order_code       VARCHAR(30)   NOT NULL UNIQUE,
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payment_status   VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
    total_amount     DECIMAL(12,2) NOT NULL,
    shipping_address VARCHAR(500)  NOT NULL,
    note             VARCHAR(500),
    cancelled_at     TIMESTAMP,
    refunded_at      TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_orders_status
        CHECK (status IN ('PENDING','CONFIRMED','PROCESSING','COMPLETED','CANCELLED')),
    CONSTRAINT chk_orders_payment_status
        CHECK (payment_status IN ('UNPAID','PAID','REFUNDED'))
);

CREATE TABLE order_items (
    id           BIGSERIAL     PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders(id),
    product_id   BIGINT        NOT NULL REFERENCES products(id),
    product_name VARCHAR(255)  NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,
    subtotal     DECIMAL(12,2) NOT NULL,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0)
);

-- Required indexes
CREATE INDEX idx_orders_user        ON orders(user_id);
CREATE INDEX idx_orders_status      ON orders(status);
CREATE INDEX idx_orders_created_at  ON orders(created_at);
CREATE INDEX idx_order_items_order  ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);

-- Sequence sinh order_code, an toan khi nhieu request chay song song
CREATE SEQUENCE order_code_seq START WITH 1 INCREMENT BY 1;
