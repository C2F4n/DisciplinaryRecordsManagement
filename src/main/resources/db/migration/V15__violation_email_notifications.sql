ALTER TABLE app_user ADD COLUMN email VARCHAR(254);

CREATE TABLE notification_email (
    notification_id BIGINT NOT NULL PRIMARY KEY,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at BIGINT NOT NULL,
    lease_id VARCHAR(36),
    finished_at BIGINT,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_email_notification FOREIGN KEY (notification_id) REFERENCES app_notification(id) ON DELETE CASCADE,
    CONSTRAINT ck_email_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_notification_email_due ON notification_email(status, next_attempt_at, notification_id);
