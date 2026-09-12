CREATE SEQUENCE url_id_seq START WITH 100000 INCREMENT BY 1;

CREATE TABLE urls (
    id          BIGINT PRIMARY KEY DEFAULT nextval('url_id_seq'),
    short_key   VARCHAR(8)  NOT NULL UNIQUE,
    long_url    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    click_count BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_urls_long_url_hash ON urls (md5(long_url));