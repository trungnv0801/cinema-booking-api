--liquibase formatted sql

--changeset trung:005-01-ticket-types
CREATE TABLE ticket_types (
    code             VARCHAR(30)   PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL,
    discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    requires_proof   BOOLEAN       NOT NULL DEFAULT FALSE,
    proof_note       VARCHAR(255),
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order    SMALLINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_ticket_types_percent CHECK (discount_percent BETWEEN 0 AND 100)
);

COMMENT ON TABLE ticket_types IS 'Reference data. Populated by the reference-data changelog.';
COMMENT ON COLUMN ticket_types.requires_proof IS
    'The customer self-declares when buying online; staff verify at the door. '
    'Without valid proof the customer pays the difference on the spot.';
--rollback DROP TABLE ticket_types;

--changeset trung:005-02-seat-type-prices
--comment Surcharge per seat type, kept out of the seats table so that changing a price
--comment does not mean updating a thousand seat rows.
CREATE TABLE seat_type_prices (
    seat_type      VARCHAR(20)  PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    surcharge_vnd  BIGINT       NOT NULL DEFAULT 0,
    seats_per_unit SMALLINT     NOT NULL DEFAULT 1,

    CONSTRAINT ck_seat_type_prices_type CHECK (seat_type IN ('STANDARD', 'VIP', 'COUPLE'))
);

COMMENT ON TABLE seat_type_prices IS 'Reference data. Populated by the reference-data changelog.';
--rollback DROP TABLE seat_type_prices;

--changeset trung:005-03-discount-rules
--comment Context-based discounts (weekday, time slot). These are the business's LIVE
--comment PRICE LIST, fundamentally different from the campaign codes in 009-promotion:
--comment without them the system charges the WRONG PRICE and cannot go live.
CREATE TABLE discount_rules (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code             VARCHAR(50)   NOT NULL,
    name             VARCHAR(255)  NOT NULL,
    discount_percent NUMERIC(5, 2) NOT NULL,

    day_of_week      SMALLINT,
    time_from        TIME,
    time_to          TIME,
    cinema_id        BIGINT REFERENCES cinemas (id),

    valid_from       DATE,
    valid_to         DATE,
    priority         SMALLINT      NOT NULL DEFAULT 0,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_discount_rules_percent CHECK (discount_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_discount_rules_dow     CHECK (day_of_week IS NULL OR day_of_week BETWEEN 0 AND 6),
    CONSTRAINT ck_discount_rules_time    CHECK (
        (time_from IS NULL AND time_to IS NULL) OR
        (time_from IS NOT NULL AND time_to IS NOT NULL AND time_to > time_from)
    ),
    CONSTRAINT ck_discount_rules_dates   CHECK (
        valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from
    )
);

CREATE UNIQUE INDEX uq_discount_rules_code ON discount_rules (code);
CREATE INDEX ix_discount_rules_active ON discount_rules (day_of_week) WHERE is_active;

COMMENT ON COLUMN discount_rules.day_of_week IS
    'Follows PostgreSQL EXTRACT(DOW): 0 = Sunday, 3 = Wednesday.';
--rollback DROP TABLE discount_rules;
