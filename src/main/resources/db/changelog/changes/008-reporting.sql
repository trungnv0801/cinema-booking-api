--liquibase formatted sql

--changeset trung:008-01-shift-reconciliations
--comment End-of-day till reconciliation. This is not an analytics dashboard: it is the
--comment tool staff use to check the cash in the drawer against what the system recorded.
CREATE TABLE shift_reconciliations (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cinema_id         BIGINT      NOT NULL REFERENCES cinemas (id),
    business_date     DATE        NOT NULL,

    system_cash_vnd   BIGINT      NOT NULL DEFAULT 0,
    system_online_vnd BIGINT      NOT NULL DEFAULT 0,
    system_refund_vnd BIGINT      NOT NULL DEFAULT 0,
    tickets_sold      INTEGER     NOT NULL DEFAULT 0,
    tickets_cancelled INTEGER     NOT NULL DEFAULT 0,

    counted_cash_vnd  BIGINT,
    difference_vnd    BIGINT,
    difference_note   VARCHAR(500),

    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_by         BIGINT      REFERENCES users (id),
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_shift_reconciliations_status CHECK (status IN ('OPEN', 'CLOSED', 'DISPUTED')),
    CONSTRAINT ck_shift_reconciliations_closed CHECK (
        status <> 'CLOSED' OR
        (closed_by IS NOT NULL AND closed_at IS NOT NULL AND counted_cash_vnd IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_shift_reconciliations_cinema_date
    ON shift_reconciliations (cinema_id, business_date);

COMMENT ON TABLE shift_reconciliations IS
    'One row per cinema per business day. Once CLOSED it is immutable: any correction '
    'must move it to DISPUTED with a recorded reason.';
COMMENT ON COLUMN shift_reconciliations.difference_vnd IS
    'counted_cash_vnd minus system_cash_vnd. A non-zero value requires difference_note.';
--rollback DROP TABLE shift_reconciliations;

--changeset trung:008-02-audit-logs
--comment Trail for sensitive operations: cancelling screenings, editing prices,
--comment approving refunds, closing shifts.
CREATE TABLE audit_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id     BIGINT      REFERENCES users (id),
    action       VARCHAR(50) NOT NULL,
    entity_type  VARCHAR(50) NOT NULL,
    entity_id    BIGINT,
    before_state JSONB,
    after_state  JSONB,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX ix_audit_logs_actor  ON audit_logs (actor_id, created_at DESC);
--rollback DROP TABLE audit_logs;

--changeset trung:008-03-view-daily-sales runOnChange:true
--comment Computed directly from source data rather than materialised, so the report can
--comment never drift away from what actually happened.
CREATE OR REPLACE VIEW v_daily_sales AS
SELECT
    b.cinema_id,
    b.business_date,
    b.channel,
    p.method                             AS payment_method,
    count(DISTINCT b.id)                 AS booking_count,
    count(bi.id)                         AS ticket_count,
    coalesce(sum(bi.final_price_vnd), 0) AS revenue_vnd
FROM bookings b
JOIN booking_items bi ON bi.booking_id = b.id AND bi.status = 'ACTIVE'
LEFT JOIN payments p  ON p.booking_id = b.id AND p.status = 'PAID'
WHERE b.status IN ('PAID', 'REFUNDED')
GROUP BY b.cinema_id, b.business_date, b.channel, p.method;
--rollback DROP VIEW IF EXISTS v_daily_sales;

--changeset trung:008-04-view-screening-performance runOnChange:true
--comment Occupancy rate is the figure the owner actually needs. Comparing total revenue
--comment between a five-screen site and a three-screen site is meaningless; comparing how
--comment full they get is not.
CREATE OR REPLACE VIEW v_screening_performance AS
SELECT
    s.id                                   AS screening_id,
    s.cinema_id,
    s.business_date,
    s.starts_at,
    s.movie_id,
    m.title                                AS movie_title,
    a.name                                 AS auditorium_name,
    cap.total_seats,
    count(bi.id)                           AS seats_sold,
    round(count(bi.id)::numeric * 100
          / nullif(cap.total_seats, 0), 1)  AS occupancy_percent,
    coalesce(sum(bi.final_price_vnd), 0)   AS revenue_vnd
FROM screenings s
JOIN movies m      ON m.id = s.movie_id
JOIN auditoriums a ON a.id = s.auditorium_id
JOIN LATERAL (
    SELECT count(*) AS total_seats
    FROM seats st
    WHERE st.auditorium_id = s.auditorium_id AND st.is_sellable
) cap ON TRUE
LEFT JOIN bookings      b  ON b.screening_id = s.id AND b.status IN ('PAID', 'REFUNDED')
LEFT JOIN booking_items bi ON bi.booking_id = b.id AND bi.status = 'ACTIVE'
WHERE s.status <> 'CANCELLED'
GROUP BY s.id, s.cinema_id, s.business_date, s.starts_at, s.movie_id,
         m.title, a.name, cap.total_seats;
--rollback DROP VIEW IF EXISTS v_screening_performance;
