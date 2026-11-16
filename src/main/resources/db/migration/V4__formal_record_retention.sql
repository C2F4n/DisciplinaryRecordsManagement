CREATE TABLE retention_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    executed_at BIGINT NOT NULL,
    removed_records BIGINT NOT NULL
);

CREATE INDEX idx_retention_runs_order ON retention_run (executed_at, id);
