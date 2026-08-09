--liquibase formatted sql

--changeset trung:007-01-payments
CREATE TABLE payments (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    booking_id         BIGINT      NOT NULL REFERENCES bookings (id),

    method             VARCHAR(20) NOT NULL,
    amount_vnd         BIGINT      NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    provider_txn_ref   VARCHAR(100),
    provider_txn_no    VARCHAR(100),
    provider_bank_code VARCHAR(50),
    provider_message   VARCHAR(500),

    received_cash_vnd  BIGINT,
    change_vnd         BIGINT,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    expires_at         TIMESTAMPTZ,
    reconciled_at      TIMESTAMPTZ,

    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'VNPAY', 'MOMO', 'BANK_TRANSFER')),
    CONSTRAINT ck_payments_status CHECK (
        status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED')
    ),
    CONSTRAINT ck_payments_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_payments_ref CHECK (
        (method = 'CASH' AND provider_txn_ref IS NULL) OR
        (method <> 'CASH' AND provider_txn_ref IS NOT NULL)
    ),
    CONSTRAINT ck_payments_cash CHECK (
        method <> 'CASH' OR received_cash_vnd IS NULL OR received_cash_vnd >= amount_vnd
    )
);

CREATE UNIQUE INDEX uq_payments_public_id ON payments (public_id);
CREATE INDEX ix_payments_booking ON payments (booking_id);
CREATE INDEX ix_payments_stale ON payments (created_at)
    WHERE status = 'PENDING' AND method <> 'CASH';

COMMENT ON COLUMN payments.reconciled_at IS
    'When the reconciliation job confirmed this payment with the gateway. NULL means unconfirmed.';
COMMENT ON COLUMN payments.status IS
    'Transitions are ONE-WAY: PENDING to PAID, FAILED, EXPIRED or CANCELLED. Once PAID it '
    'never reverts -- a refund is a separate entity, not a reversed status.';
--rollback DROP TABLE payments;

--changeset trung:007-02-payments-idempotency
--comment PREVENTS DUPLICATE CALLBACK PROCESSING.
--comment Payment gateways WILL invoke the callback more than once for the same
--comment transaction; that is their design, not a fault. This index guarantees one
--comment reference produces exactly one payment row however many callbacks arrive.
CREATE UNIQUE INDEX uq_payments_provider_ref ON payments (provider_txn_ref)
    WHERE provider_txn_ref IS NOT NULL;
--rollback DROP INDEX uq_payments_provider_ref;

--changeset trung:007-03-payment-events
--comment Append-only log of every callback. Records duplicates and invalid signatures
--comment too. When a customer disputes a charge, this is the only evidence available.
--comment Deliberately has NO unique constraint: deduplication is the job of
--comment uq_payments_provider_ref, not of this log.
CREATE TABLE payment_events (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id       BIGINT      REFERENCES payments (id),
    provider         VARCHAR(20) NOT NULL,
    event_type       VARCHAR(50) NOT NULL,
    provider_txn_ref VARCHAR(100),
    raw_payload      JSONB       NOT NULL,
    signature_valid  BOOLEAN,
    http_status      SMALLINT,
    source_ip        INET,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at     TIMESTAMPTZ,
    process_result   VARCHAR(50),
    error_message    TEXT
);

CREATE INDEX ix_payment_events_payment ON payment_events (payment_id, received_at DESC);
CREATE INDEX ix_payment_events_ref     ON payment_events (provider_txn_ref);
CREATE INDEX ix_payment_events_payload ON payment_events USING gin (raw_payload);
--rollback DROP TABLE payment_events;

--changeset trung:007-04-refund-requests
--comment Refunds are their own entity with their own lifecycle. In phase 1 the process
--comment is deliberately semi-manual: the system releases the seat immediately so it can
--comment be resold, while an admin transfers the money by hand afterwards.
CREATE TABLE refund_requests (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    booking_id        BIGINT      NOT NULL REFERENCES bookings (id),
    payment_id        BIGINT      REFERENCES payments (id),

    reason            VARCHAR(50) NOT NULL,
    reason_note       VARCHAR(500),

    original_vnd      BIGINT      NOT NULL,
    fee_vnd           BIGINT      NOT NULL DEFAULT 0,
    refund_vnd        BIGINT      NOT NULL,

    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    refund_method     VARCHAR(20),
    bank_account_no   VARCHAR(50),
    bank_account_name VARCHAR(255),
    bank_name         VARCHAR(100),
    transfer_ref      VARCHAR(100),

    requested_by      BIGINT      REFERENCES users (id),
    requested_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by       BIGINT      REFERENCES users (id),
    reviewed_at       TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    review_note       VARCHAR(500),

    CONSTRAINT ck_refund_requests_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')
    ),
    CONSTRAINT ck_refund_requests_reason CHECK (
        reason IN ('CUSTOMER_CANCEL', 'SCREENING_CANCELLED', 'SCREENING_MOVED',
                   'SYSTEM_ERROR', 'OTHER')
    ),
    CONSTRAINT ck_refund_requests_method CHECK (
        refund_method IS NULL OR refund_method IN ('CASH', 'BANK_TRANSFER', 'GATEWAY')
    ),
    CONSTRAINT ck_refund_requests_amount CHECK (
        original_vnd > 0 AND fee_vnd >= 0 AND refund_vnd >= 0
        AND refund_vnd = original_vnd - fee_vnd
    )
);

CREATE UNIQUE INDEX uq_refund_requests_public_id ON refund_requests (public_id);
CREATE INDEX ix_refund_requests_pending ON refund_requests (requested_at) WHERE status = 'PENDING';
CREATE INDEX ix_refund_requests_booking ON refund_requests (booking_id);

COMMENT ON COLUMN refund_requests.fee_vnd IS
    'Customer-initiated cancellation retains a 10% handling fee. When the cinema cancels '
    'a screening the fee is zero and the full amount is returned.';
--rollback DROP TABLE refund_requests;
