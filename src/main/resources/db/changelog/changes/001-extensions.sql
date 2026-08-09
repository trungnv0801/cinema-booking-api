--liquibase formatted sql

--changeset trung:001-01-pgcrypto
--comment Enables gen_random_uuid() for public identifiers
CREATE EXTENSION IF NOT EXISTS pgcrypto;
--rollback DROP EXTENSION IF EXISTS pgcrypto;

--changeset trung:001-02-btree-gist
--comment Required by the EXCLUDE constraint that prevents overlapping screenings
CREATE EXTENSION IF NOT EXISTS btree_gist;
--rollback DROP EXTENSION IF EXISTS btree_gist;
