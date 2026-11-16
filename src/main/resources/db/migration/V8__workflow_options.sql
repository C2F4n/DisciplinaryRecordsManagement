CREATE TABLE workflow_option (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    code VARCHAR(16) NOT NULL UNIQUE,
    label VARCHAR(80),
    resolution VARCHAR(16),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_option_label UNIQUE (kind, label),
    CONSTRAINT ck_workflow_option_label CHECK (label IS NOT NULL OR enabled = FALSE),
    CONSTRAINT ck_workflow_option_resolution CHECK (
        (kind = 'INSPECTION' AND resolution IS NULL) OR
        (kind = 'APPEAL' AND resolution IS NOT NULL AND resolution IN ('HANDED_BACK', 'APPEALED'))
    )
);

INSERT INTO workflow_option (kind, code, label) VALUES
    ('INSPECTION', 'COLLEGE', '学院检查'), ('INSPECTION', 'SCHOOL', '学校检查');
INSERT INTO workflow_option (kind, code, resolution, enabled) VALUES
    ('APPEAL', 'RETURN_1', 'HANDED_BACK', FALSE), ('APPEAL', 'RETURN_2', 'HANDED_BACK', FALSE),
    ('APPEAL', 'APPEAL_1', 'APPEALED', FALSE), ('APPEAL', 'APPEAL_2', 'APPEALED', FALSE);

ALTER TABLE violation_type ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE violation_record ADD COLUMN inspection_label VARCHAR(80);
ALTER TABLE violation_record ADD COLUMN appeal_label VARCHAR(80);
ALTER TABLE monthly_report_item ADD COLUMN inspection_label VARCHAR(80);

CREATE INDEX idx_audit_created ON audit_log (created_at, id);
