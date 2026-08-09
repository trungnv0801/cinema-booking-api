--liquibase formatted sql

--changeset trung:006-01-seat-holds
--comment Layer 1: temporary holds. The customer picks seats and they are reserved for
--comment ten minutes. Catches contention early, before any money changes hands.
CREATE TABLE seat_holds (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hold_group   UUID        NOT NULL,
    screening_id BIGINT      NOT NULL REFERENCES screenings (id) ON DELETE CASCADE,
    seat_id      BIGINT      NOT NULL REFERENCES seats (id),
    user_id      BIGINT      REFERENCES users (id),
    session_key  VARCHAR(64),
    channel      VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    expires_at   TIMESTAMPTZ NOT NULL,
    released_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_seat_holds_channel CHECK (channel IN ('ONLINE', 'COUNTER')),
    CONSTRAINT ck_seat_holds_owner   CHECK (user_id IS NOT NULL OR session_key IS NOT NULL)
);

CREATE UNIQUE INDEX uq_seat_holds_active ON seat_holds (screening_id, seat_id)
    WHERE released_at IS NULL;

CREATE INDEX ix_seat_holds_group  ON seat_holds (hold_group);
CREATE INDEX ix_seat_holds_expiry ON seat_holds (expires_at) WHERE released_at IS NULL;

COMMENT ON COLUMN seat_holds.hold_group IS
    'Groups the seats chosen in one action. Released as a whole, never partially.';
COMMENT ON COLUMN seat_holds.session_key IS
    'Allows holding seats before signing in. The customer authenticates at a later step.';
--rollback DROP TABLE seat_holds;

--changeset trung:006-02-bookings
CREATE TABLE bookings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(20) NOT NULL,
    public_id       UUID        NOT NULL DEFAULT gen_random_uuid(),

    screening_id    BIGINT      NOT NULL REFERENCES screenings (id),
    cinema_id       BIGINT      NOT NULL REFERENCES cinemas (id),
    business_date   DATE        NOT NULL,

    user_id         BIGINT      REFERENCES users (id),
    customer_name   VARCHAR(255),
    customer_email  VARCHAR(255),
    customer_phone  VARCHAR(20),

    channel         VARCHAR(20) NOT NULL,
    sold_by         BIGINT      REFERENCES users (id),

    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    subtotal_vnd    BIGINT      NOT NULL DEFAULT 0,
    discount_vnd    BIGINT      NOT NULL DEFAULT 0,
    total_vnd       BIGINT      NOT NULL DEFAULT 0,

    hold_expires_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(500),
    version         INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT ck_bookings_channel CHECK (channel IN ('ONLINE', 'COUNTER')),
    CONSTRAINT ck_bookings_status  CHECK (
        status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED', 'REFUNDED')
    ),
    CONSTRAINT ck_bookings_amounts CHECK (
        subtotal_vnd >= 0 AND discount_vnd >= 0 AND total_vnd >= 0
        AND total_vnd = subtotal_vnd - discount_vnd
    ),
    CONSTRAINT ck_bookings_counter_seller CHECK (channel <> 'COUNTER' OR sold_by IS NOT NULL),
    CONSTRAINT ck_bookings_online_user CHECK (
        channel <> 'ONLINE' OR (user_id IS NOT NULL AND customer_email IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_bookings_code      ON bookings (code);
CREATE UNIQUE INDEX uq_bookings_public_id ON bookings (public_id);

CREATE INDEX ix_bookings_user      ON bookings (user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX ix_bookings_screening ON bookings (screening_id);
CREATE INDEX ix_bookings_report    ON bookings (cinema_id, business_date, status);
CREATE INDEX ix_bookings_pending   ON bookings (hold_expires_at) WHERE status = 'PENDING';

COMMENT ON COLUMN bookings.channel IS
    'ONLINE or COUNTER. Without this column the end-of-day report cannot tell which '
    'money is cash in the till and which has already landed in the bank account.';
COMMENT ON COLUMN bookings.cinema_id IS
    'Denormalised from screenings. Reports always filter by cinema and date; joining '
    'two extra tables on a continuously running query is not worth it.';
COMMENT ON COLUMN bookings.hold_expires_at IS
    'Payment deadline. The reconciliation job scans this column to expire stale '
    'bookings and release their seats.';
COMMENT ON CONSTRAINT ck_bookings_counter_seller ON bookings IS
    'Counter sales must record the seller, otherwise the till cannot be reconciled.';
--rollback DROP TABLE bookings;

--changeset trung:006-03-booking-items
CREATE TABLE booking_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,

    screening_id BIGINT NOT NULL REFERENCES screenings (id),
    seat_id      BIGINT NOT NULL REFERENCES seats (id),

    seat_label       VARCHAR(10) NOT NULL,
    seat_type        VARCHAR(20) NOT NULL,
    ticket_type_code VARCHAR(30) NOT NULL REFERENCES ticket_types (code),

    base_price_vnd   BIGINT        NOT NULL,
    surcharge_vnd    BIGINT        NOT NULL DEFAULT 0,
    discount_vnd     BIGINT        NOT NULL DEFAULT 0,
    final_price_vnd  BIGINT        NOT NULL,
    discount_code    VARCHAR(50),
    discount_label   VARCHAR(255),
    discount_percent NUMERIC(5, 2),

    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_booking_items_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_booking_items_price  CHECK (
        base_price_vnd >= 0 AND surcharge_vnd >= 0 AND discount_vnd >= 0
        AND final_price_vnd = base_price_vnd + surcharge_vnd - discount_vnd
        AND final_price_vnd >= 0
    )
);

CREATE INDEX ix_booking_items_booking   ON booking_items (booking_id);
CREATE INDEX ix_booking_items_screening ON booking_items (screening_id) WHERE status = 'ACTIVE';

COMMENT ON COLUMN booking_items.seat_label IS
    'Denormalised "D5" so historical tickets stay readable after an admin edits the seat map.';
COMMENT ON COLUMN booking_items.discount_label IS
    'Name of the rule that was applied, e.g. "Marquee Wednesdays". Changing the price list '
    'must never alter a historical invoice.';
--rollback DROP TABLE booking_items;

--changeset trung:006-04-booking-items-no-double-sell
--comment THE SINGLE MOST IMPORTANT CONSTRAINT IN THE SYSTEM.
--comment A seat of a screening can belong to exactly one live booking. Even if the
--comment application has a bug, even with the counter POS and the website running in
--comment parallel, the database will not permit a double sale.
--comment The WHERE clause lets a seat be resold after a cancellation while preserving history.
--comment A violation raises SQLSTATE 23505.
CREATE UNIQUE INDEX uq_booking_items_seat_active
    ON booking_items (screening_id, seat_id)
    WHERE status = 'ACTIVE';
--rollback DROP INDEX uq_booking_items_seat_active;

--changeset trung:006-05-tickets
--comment One ticket per seat, each with its own QR code.
CREATE TABLE tickets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL,
    booking_item_id BIGINT       NOT NULL REFERENCES booking_items (id) ON DELETE CASCADE,
    booking_id      BIGINT       NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    screening_id    BIGINT       NOT NULL REFERENCES screenings (id),

    qr_token        VARCHAR(128) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'VALID',

    checked_in_at   TIMESTAMPTZ,
    checked_in_by   BIGINT       REFERENCES users (id),
    voided_at       TIMESTAMPTZ,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_tickets_status CHECK (status IN ('VALID', 'USED', 'VOID')),
    CONSTRAINT ck_tickets_checkin CHECK (
        (status = 'USED' AND checked_in_at IS NOT NULL) OR
        (status <> 'USED' AND checked_in_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_tickets_code     ON tickets (code);
CREATE UNIQUE INDEX uq_tickets_qr_token ON tickets (qr_token);
CREATE UNIQUE INDEX uq_tickets_item     ON tickets (booking_item_id);
CREATE INDEX ix_tickets_screening       ON tickets (screening_id, status);

COMMENT ON COLUMN tickets.code IS
    'Human-readable code, e.g. SM-4X9K-2201. Used when the scanner fails and staff type it in.';
COMMENT ON COLUMN tickets.qr_token IS
    'Random string embedded in the QR image. Kept separate from code so that a photo of '
    'the printed code is not enough to forge a ticket.';
COMMENT ON COLUMN tickets.status IS
    'VALID becomes USED on the first scan. A second scan must fail, which is what stops '
    'a screenshot of the QR code from being reused.';
--rollback DROP TABLE tickets;

--changeset trung:006-06-check-in-logs
--comment Records failed scans as well, so disputes at the auditorium door can be investigated.
CREATE TABLE check_in_logs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT      REFERENCES tickets (id),
    screening_id  BIGINT      REFERENCES screenings (id),
    scanned_by    BIGINT      REFERENCES users (id),
    scanned_token VARCHAR(128),
    result        VARCHAR(30) NOT NULL,
    note          VARCHAR(255),
    scanned_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_check_in_logs_result CHECK (
        result IN ('VALID', 'ALREADY_USED', 'WRONG_SCREENING', 'NOT_FOUND',
                   'VOIDED', 'TOO_EARLY', 'TOO_LATE')
    )
);

CREATE INDEX ix_check_in_logs_screening ON check_in_logs (screening_id, scanned_at DESC);
CREATE INDEX ix_check_in_logs_ticket    ON check_in_logs (ticket_id);
--rollback DROP TABLE check_in_logs;
