CREATE TABLE file_shares (
    id              BIGINT PRIMARY KEY,
    secret_key      VARCHAR(32)  NOT NULL UNIQUE,
    object_key      VARCHAR(128) NOT NULL UNIQUE,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(255),
    size_bytes      BIGINT,
    status          VARCHAR(16)  NOT NULL,
    max_downloads   INT,
    download_count  INT          NOT NULL DEFAULT 0,
    burn_after_read BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_file_shares_expires_at ON file_shares (expires_at);
CREATE INDEX idx_file_shares_status ON file_shares (status) WHERE status = 'PENDING';