--liquibase formatted sql

--changeset trung:003-01-movies
CREATE TABLE movies (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID         NOT NULL DEFAULT gen_random_uuid(),
    slug           VARCHAR(255) NOT NULL,
    title          VARCHAR(255) NOT NULL,
    original_title VARCHAR(255),
    synopsis       TEXT,
    duration_min   SMALLINT     NOT NULL,
    age_rating     VARCHAR(5)   NOT NULL,
    country        VARCHAR(100),
    director       VARCHAR(255),
    cast_list      TEXT,
    poster_url     VARCHAR(500),
    backdrop_url   VARCHAR(500),
    trailer_url    VARCHAR(500),
    release_date   DATE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'COMING_SOON',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_movies_rating   CHECK (age_rating IN ('P', 'K', 'T13', 'T16', 'T18', 'C')),
    CONSTRAINT ck_movies_status   CHECK (status IN ('COMING_SOON', 'NOW_SHOWING', 'ENDED', 'ARCHIVED')),
    CONSTRAINT ck_movies_duration CHECK (duration_min BETWEEN 1 AND 600)
);

CREATE UNIQUE INDEX uq_movies_public_id ON movies (public_id);
CREATE UNIQUE INDEX uq_movies_slug      ON movies (slug);
CREATE INDEX ix_movies_status ON movies (status) WHERE status IN ('NOW_SHOWING', 'COMING_SOON');

COMMENT ON COLUMN movies.age_rating IS
    'Vietnamese classification: P all ages, K under 13 with an adult, '
    'T13/T16/T18 by age, C banned from distribution.';
COMMENT ON COLUMN movies.slug IS 'URL segment used by the public site instead of an id.';
--rollback DROP TABLE movies;

--changeset trung:003-02-genres
CREATE TABLE genres (
    code VARCHAR(30)  PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

COMMENT ON TABLE genres IS 'Reference data. Populated by the reference-data changelog.';
--rollback DROP TABLE genres;

--changeset trung:003-03-movie-genres
CREATE TABLE movie_genres (
    movie_id   BIGINT      NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    genre_code VARCHAR(30) NOT NULL REFERENCES genres (code),
    PRIMARY KEY (movie_id, genre_code)
);
--rollback DROP TABLE movie_genres;

--changeset trung:003-04-cinemas
CREATE TABLE cinemas (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id  UUID         NOT NULL DEFAULT gen_random_uuid(),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(500) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    district   VARCHAR(100),
    phone      VARCHAR(20),
    latitude   NUMERIC(9, 6),
    longitude  NUMERIC(9, 6),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_cinemas_code      ON cinemas (code);
CREATE UNIQUE INDEX uq_cinemas_public_id ON cinemas (public_id);
--rollback DROP TABLE cinemas;

--changeset trung:003-05-user-roles-cinema-fk
--comment Deferred from 002 because cinemas did not exist yet.
ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_cinema FOREIGN KEY (cinema_id) REFERENCES cinemas (id);
--rollback ALTER TABLE user_roles DROP CONSTRAINT fk_user_roles_cinema;

--changeset trung:003-06-auditoriums
CREATE TABLE auditoriums (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID        NOT NULL DEFAULT gen_random_uuid(),
    cinema_id   BIGINT      NOT NULL REFERENCES cinemas (id),
    name        VARCHAR(50) NOT NULL,
    screen_type VARCHAR(20) NOT NULL DEFAULT '2D',
    cleanup_min SMALLINT    NOT NULL DEFAULT 15,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_auditoriums_screen  CHECK (screen_type IN ('2D', '3D', 'IMAX')),
    CONSTRAINT ck_auditoriums_cleanup CHECK (cleanup_min BETWEEN 0 AND 120)
);

CREATE UNIQUE INDEX uq_auditoriums_name      ON auditoriums (cinema_id, name);
CREATE UNIQUE INDEX uq_auditoriums_public_id ON auditoriums (public_id);

COMMENT ON COLUMN auditoriums.cleanup_min IS
    'Turnaround time between screenings. Added to ends_at when checking for schedule overlap.';
--rollback DROP TABLE auditoriums;

--changeset trung:003-07-seats
--comment Seats are PHYSICAL entities belonging to an auditorium. Whether a seat is
--comment taken is NOT stored here: that state belongs to an individual screening and
--comment is derived by joining against booking_items and seat_holds.
CREATE TABLE seats (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID        NOT NULL DEFAULT gen_random_uuid(),
    auditorium_id BIGINT      NOT NULL REFERENCES auditoriums (id) ON DELETE CASCADE,
    row_label     VARCHAR(4)  NOT NULL,
    seat_number   SMALLINT    NOT NULL,
    seat_type     VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    couple_group  VARCHAR(20),
    grid_row      SMALLINT    NOT NULL,
    grid_col      SMALLINT    NOT NULL,
    is_sellable   BOOLEAN     NOT NULL DEFAULT TRUE,
    disabled_note VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_seats_type CHECK (seat_type IN ('STANDARD', 'VIP', 'COUPLE')),
    CONSTRAINT ck_seats_couple_group CHECK (
        (seat_type = 'COUPLE' AND couple_group IS NOT NULL) OR
        (seat_type <> 'COUPLE' AND couple_group IS NULL)
    )
);

CREATE UNIQUE INDEX uq_seats_position  ON seats (auditorium_id, row_label, seat_number);
CREATE UNIQUE INDEX uq_seats_grid      ON seats (auditorium_id, grid_row, grid_col);
CREATE UNIQUE INDEX uq_seats_public_id ON seats (public_id);
CREATE INDEX ix_seats_auditorium       ON seats (auditorium_id);
CREATE INDEX ix_seats_couple_group     ON seats (auditorium_id, couple_group)
    WHERE couple_group IS NOT NULL;

COMMENT ON COLUMN seats.couple_group IS
    'Both seats of a love-seat pair share this value. Selecting one must pull in the other.';
COMMENT ON COLUMN seats.grid_row IS
    'Rendering coordinate, deliberately separate from row_label and seat_number: real '
    'auditoriums have aisles and gaps, so seat D6 and D7 need not be adjacent on screen.';
COMMENT ON COLUMN seats.is_sellable IS
    'Temporarily broken seat. Never deleted, so historical tickets remain resolvable.';
--rollback DROP TABLE seats;
