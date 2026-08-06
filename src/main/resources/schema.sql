-- The Control Plane's durable store. One table holds every subsystem's aggregates
-- as JSON documents, keyed by (collection, id). Portable across H2 (default) and
-- Postgres (the `postgres` profile); run on every startup via spring.sql.init and
-- guarded by IF NOT EXISTS so it is idempotent.
CREATE TABLE IF NOT EXISTS cp_document (
    collection VARCHAR(128)     NOT NULL,
    id         VARCHAR(512)     NOT NULL,
    doc        VARCHAR(1000000) NOT NULL,
    PRIMARY KEY (collection, id)
);
