--liquibase formatted sql

--changeset trung:004-01-screenings
CREATE TABLE screenings (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID        NOT NULL DEFAULT gen_random_uuid(),
    movie_id       BIGINT      NOT NULL REFERENCES movies (id),
    auditorium_id  BIGINT      NOT NULL REFERENCES auditoriums (id),
    cinema_id      BIGINT      NOT NULL REFERENCES cinemas (id),

    starts_at      TIMESTAMPTZ NOT NULL,
    ends_at        TIMESTAMPTZ NOT NULL,
    blocks_until   TIMESTAMPTZ NOT NULL,
    business_date  DATE        NOT NULL,

    audio_language VARCHAR(20) NOT NULL DEFAULT 'VI',
    subtitle       VARCHAR(20) NOT NULL DEFAULT 'VI',
    screen_type    VARCHAR(20) NOT NULL DEFAULT '2D',

    base_price_vnd BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    cancel_reason  VARCHAR(500),

    version        INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_screenings_status CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_screenings_time   CHECK (ends_at > starts_at AND blocks_until >= ends_at),
    CONSTRAINT ck_screenings_price  CHECK (base_price_vnd > 0)
);

CREATE UNIQUE INDEX uq_screenings_public_id ON screenings (public_id);

CREATE INDEX ix_screenings_lookup ON screenings (cinema_id, business_date, movie_id)
    WHERE status = 'SCHEDULED';
CREATE INDEX ix_screenings_starts ON screenings (starts_at) WHERE status = 'SCHEDULED';
CREATE INDEX ix_screenings_auditorium_date ON screenings (auditorium_id, business_date);

COMMENT ON COLUMN screenings.cinema_id IS
    'Derivable from auditorium_id but stored anyway: every report filters by cinema, '
    'and joining an extra table on a query that runs thousands of times a day is not worth it.';
COMMENT ON COLUMN screenings.business_date IS
    'Business day, NOT calendar day. A 00:30 screening belongs to the previous day '
    'because staff close the till after the last show ends.';
COMMENT ON COLUMN screenings.blocks_until IS
    'ends_at plus auditorium cleanup time. Held in its own column because '
    'timestamptz + interval is not immutable and therefore cannot be used inside an index.';
COMMENT ON COLUMN screenings.version IS
    'Optimistic lock. Prevents two admins from silently overwriting each other.';
--rollback DROP TABLE screenings;

--changeset trung:004-02-screenings-no-overlap
--comment Two screenings may not overlap in the same auditorium. Enforced by the
--comment database rather than by application code remembering to check.
--comment A violation raises SQLSTATE 23P01.
ALTER TABLE screenings ADD CONSTRAINT ex_screenings_no_overlap
    EXCLUDE USING gist (
        auditorium_id WITH =,
        tstzrange(starts_at, blocks_until, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED');
--rollback ALTER TABLE screenings DROP CONSTRAINT ex_screenings_no_overlap;
