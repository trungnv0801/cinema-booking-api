--liquibase formatted sql

--changeset trung:011-01-product-categories labels:phase-2
CREATE TABLE product_categories (
    code          VARCHAR(30)  PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    display_order SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE product_categories IS
    'Reference data. Populated by the reference-data changelog.';
--rollback DROP TABLE product_categories;

--changeset trung:011-02-products labels:phase-2
CREATE TABLE products (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID         NOT NULL DEFAULT gen_random_uuid(),
    sku           VARCHAR(50)  NOT NULL,
    category_code VARCHAR(30)  NOT NULL REFERENCES product_categories (code),
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    image_url     VARCHAR(500),
    price_vnd     BIGINT       NOT NULL,
    is_combo      BOOLEAN      NOT NULL DEFAULT FALSE,
    track_stock   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_products_price CHECK (price_vnd >= 0)
);

CREATE UNIQUE INDEX uq_products_sku       ON products (sku);
CREATE UNIQUE INDEX uq_products_public_id ON products (public_id);
CREATE INDEX ix_products_active ON products (category_code) WHERE is_active;
--rollback DROP TABLE products;

--changeset trung:011-03-combo-items labels:phase-2
--comment Combo composition. A popcorn-and-drink bundle costs less than the parts.
CREATE TABLE combo_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    combo_id   BIGINT   NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    product_id BIGINT   NOT NULL REFERENCES products (id),
    quantity   SMALLINT NOT NULL DEFAULT 1,

    CONSTRAINT ck_combo_items_qty  CHECK (quantity > 0),
    CONSTRAINT ck_combo_items_self CHECK (combo_id <> product_id)
);

CREATE UNIQUE INDEX uq_combo_items ON combo_items (combo_id, product_id);
--rollback DROP TABLE combo_items;

--changeset trung:011-04-product-stocks labels:phase-2
--comment Stock is per cinema: Cau Giay running out of popcorn must not affect Ha Dong.
CREATE TABLE product_stocks (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cinema_id     BIGINT      NOT NULL REFERENCES cinemas (id),
    product_id    BIGINT      NOT NULL REFERENCES products (id),
    quantity      INTEGER     NOT NULL DEFAULT 0,
    low_threshold INTEGER     NOT NULL DEFAULT 10,
    is_available  BOOLEAN     NOT NULL DEFAULT TRUE,
    version       INTEGER     NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_product_stocks_qty CHECK (quantity >= 0)
);

CREATE UNIQUE INDEX uq_product_stocks ON product_stocks (cinema_id, product_id);
--rollback DROP TABLE product_stocks;

--changeset trung:011-05-stock-movements labels:phase-2
CREATE TABLE stock_movements (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cinema_id  BIGINT      NOT NULL REFERENCES cinemas (id),
    product_id BIGINT      NOT NULL REFERENCES products (id),
    kind       VARCHAR(20) NOT NULL,
    quantity   INTEGER     NOT NULL,
    reference  VARCHAR(100),
    note       VARCHAR(255),
    created_by BIGINT      REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_stock_movements_kind CHECK (
        kind IN ('RECEIVE', 'SELL', 'WASTE', 'ADJUST', 'RETURN')
    )
);

CREATE INDEX ix_stock_movements ON stock_movements (cinema_id, product_id, created_at DESC);
--rollback DROP TABLE stock_movements;

--changeset trung:011-06-concession-orders labels:phase-2
--comment May be attached to a ticket booking or stand alone as a walk-up purchase.
CREATE TABLE concession_orders (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(20) NOT NULL,
    public_id     UUID        NOT NULL DEFAULT gen_random_uuid(),
    cinema_id     BIGINT      NOT NULL REFERENCES cinemas (id),
    business_date DATE        NOT NULL,
    booking_id    BIGINT      REFERENCES bookings (id),
    user_id       BIGINT      REFERENCES users (id),

    channel       VARCHAR(20) NOT NULL,
    sold_by       BIGINT      REFERENCES users (id),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    subtotal_vnd  BIGINT      NOT NULL DEFAULT 0,
    discount_vnd  BIGINT      NOT NULL DEFAULT 0,
    total_vnd     BIGINT      NOT NULL DEFAULT 0,

    pickup_code   VARCHAR(10),
    picked_up_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at       TIMESTAMPTZ,

    CONSTRAINT ck_concession_orders_channel CHECK (channel IN ('ONLINE', 'COUNTER')),
    CONSTRAINT ck_concession_orders_status  CHECK (
        status IN ('PENDING', 'PAID', 'PREPARING', 'READY', 'PICKED_UP', 'CANCELLED')
    ),
    CONSTRAINT ck_concession_orders_amounts CHECK (total_vnd = subtotal_vnd - discount_vnd)
);

CREATE UNIQUE INDEX uq_concession_orders_code      ON concession_orders (code);
CREATE UNIQUE INDEX uq_concession_orders_public_id ON concession_orders (public_id);
CREATE INDEX ix_concession_orders_report  ON concession_orders (cinema_id, business_date, status);
CREATE INDEX ix_concession_orders_booking ON concession_orders (booking_id)
    WHERE booking_id IS NOT NULL;

COMMENT ON COLUMN concession_orders.booking_id IS
    'NULL when the customer buys food without a ticket. That possibility is precisely why '
    'this lives in its own table rather than as extra rows on booking_items.';
--rollback DROP TABLE concession_orders;

--changeset trung:011-07-concession-order-items labels:phase-2
CREATE TABLE concession_order_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       BIGINT       NOT NULL REFERENCES concession_orders (id) ON DELETE CASCADE,
    product_id     BIGINT       NOT NULL REFERENCES products (id),
    product_name   VARCHAR(255) NOT NULL,
    quantity       SMALLINT     NOT NULL,
    unit_price_vnd BIGINT       NOT NULL,
    discount_vnd   BIGINT       NOT NULL DEFAULT 0,
    line_total_vnd BIGINT       NOT NULL,

    CONSTRAINT ck_concession_order_items_qty   CHECK (quantity > 0),
    CONSTRAINT ck_concession_order_items_total CHECK (
        line_total_vnd = unit_price_vnd * quantity - discount_vnd
    )
);

CREATE INDEX ix_concession_order_items_order ON concession_order_items (order_id);

COMMENT ON COLUMN concession_order_items.product_name IS
    'Product name snapshotted at sale time. Renaming a product must not alter old receipts.';
--rollback DROP TABLE concession_order_items;
