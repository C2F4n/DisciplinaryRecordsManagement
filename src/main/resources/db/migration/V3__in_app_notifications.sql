CREATE TABLE app_notification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    item_count INT,
    created_at BIGINT NOT NULL,
    read_at BIGINT,
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_user(id),
    CONSTRAINT uq_notification_delivery UNIQUE (recipient_id, event_type, subject_id, revision)
);

CREATE INDEX idx_notification_inbox ON app_notification (recipient_id, created_at, id);
CREATE INDEX idx_notification_unread ON app_notification (recipient_id, read_at);
CREATE INDEX idx_violation_pending_age ON violation_record (status, deleted_at, created_at, id);
