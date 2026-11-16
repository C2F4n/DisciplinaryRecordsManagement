ALTER TABLE student DROP COLUMN invite_digest;
ALTER TABLE student DROP COLUMN invite_expires_at;

CREATE TABLE permission_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    requester_name VARCHAR(80) NOT NULL,
    role_code VARCHAR(24) NOT NULL,
    scope_key VARCHAR(32) NOT NULL,
    scope_label VARCHAR(100) NOT NULL,
    reason VARCHAR(250) NOT NULL,
    status VARCHAR(12) NOT NULL,
    pending_slot INT,
    submitted_at BIGINT NOT NULL,
    decided_by BIGINT,
    reviewer_name VARCHAR(80),
    decided_at BIGINT,
    decision_note VARCHAR(250),
    CONSTRAINT fk_permission_request_user FOREIGN KEY (requester_id) REFERENCES app_user(id),
    CONSTRAINT fk_permission_request_reviewer FOREIGN KEY (decided_by) REFERENCES app_user(id),
    CONSTRAINT uq_permission_pending UNIQUE (requester_id, role_code, scope_key, pending_slot),
    CONSTRAINT ck_permission_decision CHECK (
        (status = 'PENDING' AND pending_slot IS NOT NULL AND pending_slot = 1
            AND decided_by IS NULL AND reviewer_name IS NULL AND decided_at IS NULL AND decision_note IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND pending_slot IS NULL
            AND decided_by IS NOT NULL AND reviewer_name IS NOT NULL AND decided_at IS NOT NULL AND decision_note IS NOT NULL)
    )
);

CREATE INDEX idx_permission_queue ON permission_request(status, submitted_at, id);
CREATE INDEX idx_permission_requester ON permission_request(requester_id, submitted_at, id);
