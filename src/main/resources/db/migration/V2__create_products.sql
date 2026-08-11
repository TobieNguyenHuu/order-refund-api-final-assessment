CREATE TABLE products (
    id         BIGSERIAL     PRIMARY KEY,
    name       VARCHAR(255)  NOT NULL,
    price      DECIMAL(12,2) NOT NULL,
    stock      INT           NOT NULL DEFAULT 0,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_products_stock_non_negative CHECK (stock >= 0),
    CONSTRAINT chk_products_price_non_negative CHECK (price >= 0)
);
