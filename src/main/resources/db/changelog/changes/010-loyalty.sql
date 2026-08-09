--liquibase formatted sql

--changeset trung:010-01-loyalty-accounts labels:phase-2
CREATE TABLE loyalty_accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tier            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    balance_points  INTEGER     NOT NULL DEFAULT 0,
    lifetime_points INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_loyalty_accounts_tier    CHECK (tier IN ('MEMBER', 'SILVER', 'GOLD')),
    CONSTRAINT ck_loyalty_accounts_balance CHECK (balance_points >= 0)
);

CREATE UNIQUE INDEX uq_loyalty_accounts_user ON loyalty_accounts (user_id);
--rollback DROP TABLE loyalty_accounts;

--changeset trung:010-02-loyalty-transactions labels:phase-2
--comment Append-only points ledger. The balance is the sum of unexpired EARN lots.
CREATE TABLE loyalty_transactions (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES loyalty_accounts (id) ON DELETE CASCADE,
    kind       VARCHAR(20) NOT NULL,
    points     INTEGER     NOT NULL,

    booking_id BIGINT      REFERENCES bookings (id),
    reference  VARCHAR(100),
    note       VARCHAR(255),

    expires_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    remaining  INTEGER,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_loyalty_transactions_kind CHECK (
        kind IN ('EARN', 'REDEEM', 'EXPIRE', 'ADJUST', 'REVERSE')
    ),
    CONSTRAINT ck_loyalty_transactions_earn_expiry CHECK (
        kind <> 'EARN' OR (expires_at IS NOT NULL AND remaining IS NOT NULL)
    ),
    CONSTRAINT ck_loyalty_transactions_sign CHECK (
        (kind IN ('EARN', 'REVERSE') AND points > 0) OR
        (kind IN ('REDEEM', 'EXPIRE') AND points < 0) OR
        (kind = 'ADJUST')
    )
);

CREATE INDEX ix_loyalty_transactions_account ON loyalty_transactions (account_id, created_at DESC);
CREATE INDEX ix_loyalty_transactions_expiring ON loyalty_transactions (expires_at)
    WHERE kind = 'EARN' AND expired_at IS NULL AND remaining > 0;
CREATE UNIQUE INDEX uq_loyalty_transactions_booking_earn
    ON loyalty_transactions (booking_id, kind)
    WHERE booking_id IS NOT NULL AND kind = 'EARN';

COMMENT ON COLUMN loyalty_transactions.remaining IS
    'Unspent points left in this EARN lot. Redemption is FIFO -- the lot closest to '
    'expiry is consumed first, so customers do not lose points unnecessarily.';
COMMENT ON INDEX uq_loyalty_transactions_booking_earn IS
    'A booking earns points exactly once, even if the job is re-run.';
--rollback DROP TABLE loyalty_transactions;

--changeset trung:010-03-loyalty-configs labels:phase-2
--comment Date-effective configuration, never edited in place. Changing the earn rate adds
--comment a new row, so it stays possible to see which rate an old lot was earned under.
CREATE TABLE loyalty_configs (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    earn_rate_percent NUMERIC(5, 2) NOT NULL DEFAULT 5.00,
    point_value_vnd   INTEGER       NOT NULL DEFAULT 1,
    expiry_months     SMALLINT      NOT NULL DEFAULT 12,
    min_redeem_points INTEGER       NOT NULL DEFAULT 10000,
    effective_from    DATE          NOT NULL DEFAULT CURRENT_DATE,

    CONSTRAINT ck_loyalty_configs_rate CHECK (earn_rate_percent BETWEEN 0 AND 100)
);

COMMENT ON TABLE loyalty_configs IS 'Reference data. Populated by the reference-data changelog.';
--rollback DROP TABLE loyalty_configs;
