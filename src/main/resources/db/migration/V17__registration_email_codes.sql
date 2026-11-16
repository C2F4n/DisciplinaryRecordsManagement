CREATE TABLE registration_email_code (
    student_id BIGINT NOT NULL PRIMARY KEY,
    challenge_id CHAR(36) NOT NULL,
    email VARCHAR(254) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    issued_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_email_code_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE
);

CREATE INDEX idx_registration_email_code_expiry ON registration_email_code(expires_at);
