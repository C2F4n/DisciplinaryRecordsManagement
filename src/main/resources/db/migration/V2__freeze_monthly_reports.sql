CREATE TABLE monthly_report_lock (
    id INT NOT NULL PRIMARY KEY
);

INSERT INTO monthly_report_lock (id) VALUES (1);

CREATE TABLE monthly_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    month_start DATE NOT NULL UNIQUE,
    generated_at BIGINT NOT NULL,
    record_count INT NOT NULL
);

CREATE TABLE monthly_report_item (
    report_id BIGINT NOT NULL,
    source_record_id BIGINT NOT NULL,
    occurred_on DATE NOT NULL,
    student_no VARCHAR(32) NOT NULL,
    student_name VARCHAR(80) NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    counselor VARCHAR(80) NOT NULL,
    department_name VARCHAR(80) NOT NULL,
    type_name VARCHAR(80) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    disposition VARCHAR(250) NOT NULL,
    inspection_scope VARCHAR(16) NOT NULL,
    remarks VARCHAR(250),
    CONSTRAINT pk_monthly_item PRIMARY KEY (report_id, source_record_id),
    CONSTRAINT uq_monthly_source UNIQUE (source_record_id),
    CONSTRAINT fk_monthly_item_report FOREIGN KEY (report_id) REFERENCES monthly_report(id)
);

CREATE INDEX idx_monthly_items_order ON monthly_report_item (report_id, occurred_on, source_record_id);
CREATE INDEX idx_violation_report_candidates ON violation_record (status, deleted_at, handed_back_at, id);

CREATE TABLE monthly_report_file (
    report_id BIGINT NOT NULL PRIMARY KEY,
    file_name VARCHAR(120) NOT NULL,
    file_content LONGBLOB NOT NULL,
    CONSTRAINT fk_monthly_file_report FOREIGN KEY (report_id) REFERENCES monthly_report(id)
);
