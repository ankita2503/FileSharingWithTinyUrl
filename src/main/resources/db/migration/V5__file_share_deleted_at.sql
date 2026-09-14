ALTER TABLE file_shares ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_file_shares_deleted_at ON file_shares (deleted_at)
    WHERE deleted_at IS NOT NULL;