--liquibase formatted sql

--changeset trung:009-01-promotions labels:phase-2
CREATE TABLE promotions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(255)  NOT NULL,
    description         TEXT,

    benefit_type        VARCHAR(20)   NOT NULL,
    discount_percent    NUMERIC(5, 2),
    discount_amount_vnd BIGINT,
    max_discount_vnd    BIGINT,
    buy_quantity        SMALLINT,
    get_quantity        SMALLINT,

    min_order_vnd       BIGINT        NOT NULL DEFAULT 0,
    min_tickets         SMALLINT      NOT NULL DEFAULT 1,

    valid_from          TIMESTAMPTZ   NOT NULL,
    valid_to            TIMESTAMPTZ   NOT NULL,

    total_usage_limit   INTEGER,
    per_user_limit      SMALLINT      NOT NULL DEFAULT 1,
    used_count          INTEGER       NOT NULL DEFAULT 0,

    stackable           BOOLEAN       NOT NULL DEFAULT FALSE,
    channel             VARCHAR(20)   NOT NULL DEFAULT 'ALL',
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by          BIGINT        REFERENCES users (id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_promotions_benefit CHECK (
        benefit_type IN ('PERCENT', 'FIXED_AMOUNT', 'BUY_X_GET_Y')
    ),
    CONSTRAINT ck_promotions_channel CHECK (channel IN ('ALL', 'ONLINE', 'COUNTER')),
    CONSTRAINT ck_promotions_dates   CHECK (valid_to > valid_from),
    CONSTRAINT ck_promotions_percent CHECK (
        benefit_type <> 'PERCENT' OR
        (discount_percent IS NOT NULL AND discount_percent BETWEEN 0 AND 100)
    ),
    CONSTRAINT ck_promotions_fixed CHECK (
        benefit_type <> 'FIXED_AMOUNT' OR
        (discount_amount_vnd IS NOT NULL AND discount_amount_vnd > 0)
    ),
    CONSTRAINT ck_promotions_bxgy CHECK (
        benefit_type <> 'BUY_X_GET_Y' OR
        (buy_quantity IS NOT NULL AND get_quantity IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_promotions_public_id ON promotions (public_id);
CREATE INDEX ix_promotions_active ON promotions (valid_from, valid_to) WHERE is_active;

COMMENT ON COLUMN promotions.used_count IS
    'Denormalised counter for fast cap checks. The source of truth remains '
    'promo_redemptions. Update with UPDATE ... WHERE used_count < total_usage_limit so '
    'the cap cannot be exceeded under contention.';
--rollback DROP TABLE promotions;

--changeset trung:009-02-promotion-scopes labels:phase-2
--comment Absence of any row for a scope type means unrestricted on that dimension.
CREATE TABLE promotion_scopes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    promotion_id BIGINT      NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    scope_type   VARCHAR(20) NOT NULL,
    movie_id     BIGINT      REFERENCES movies (id),
    cinema_id    BIGINT      REFERENCES cinemas (id),
    day_of_week  SMALLINT,
    time_from    TIME,
    time_to      TIME,
    seat_type    VARCHAR(20),

    CONSTRAINT ck_promotion_scopes_type CHECK (
        scope_type IN ('MOVIE', 'CINEMA', 'TIME_SLOT', 'SEAT_TYPE')
    ),
    CONSTRAINT ck_promotion_scopes_dow CHECK (day_of_week IS NULL OR day_of_week BETWEEN 0 AND 6)
);

CREATE INDEX ix_promotion_scopes ON promotion_scopes (promotion_id, scope_type);
--rollback DROP TABLE promotion_scopes;

--changeset trung:009-03-promo-codes labels:phase-2
CREATE TABLE promo_codes (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    promotion_id  BIGINT      NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    code          VARCHAR(50) NOT NULL,
    is_single_use BOOLEAN     NOT NULL DEFAULT FALSE,
    assigned_to   BIGINT      REFERENCES users (id),
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_promo_codes_code ON promo_codes (upper(code));
CREATE INDEX ix_promo_codes_promotion   ON promo_codes (promotion_id);

COMMENT ON COLUMN promo_codes.is_single_use IS
    'TRUE for codes issued to one named customer, e.g. a distributor sponsorship. '
    'FALSE for a shared code published on social media.';
--rollback DROP TABLE promo_codes;

--changeset trung:009-04-promo-redemptions labels:phase-2
CREATE TABLE promo_redemptions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    promotion_id  BIGINT      NOT NULL REFERENCES promotions (id),
    promo_code_id BIGINT      REFERENCES promo_codes (id),
    booking_id    BIGINT      NOT NULL REFERENCES bookings (id),
    user_id       BIGINT      REFERENCES users (id),
    discount_vnd  BIGINT      NOT NULL,
    redeemed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_promo_redemptions_amount CHECK (discount_vnd >= 0)
);

CREATE UNIQUE INDEX uq_promo_redemptions_booking ON promo_redemptions (booking_id, promotion_id);
CREATE INDEX ix_promo_redemptions_user ON promo_redemptions (user_id, promotion_id);

COMMENT ON TABLE promo_redemptions IS
    'Source of truth for per-user cap checks. Count here rather than trusting the '
    'denormalised counter on promotions.';
--rollback DROP TABLE promo_redemptions;
