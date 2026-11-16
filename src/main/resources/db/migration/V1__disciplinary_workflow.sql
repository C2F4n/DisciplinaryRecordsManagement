CREATE TABLE class_group (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    counselor VARCHAR(80) NOT NULL
);

CREATE TABLE department (
    code VARCHAR(24) NOT NULL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE
);

INSERT INTO department (code, name) VALUES
    ('DISCIPLINE', '自律管理部'),
    ('ACADEMIC', '学风建设部'),
    ('INSPECTION', '自律督察部'),
    ('DORM', '公寓建设部');

CREATE TABLE violation_type (
    code VARCHAR(30) NOT NULL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    department_code VARCHAR(24) NOT NULL,
    CONSTRAINT fk_type_department FOREIGN KEY (department_code) REFERENCES department(code)
);

INSERT INTO violation_type (code, name, department_code) VALUES
    ('CLASS_ATTENDANCE', '课堂考勤抽查', 'ACADEMIC'),
    ('DORM_SAFETY', '宿舍安全卫生抽查', 'DORM'),
    ('MORNING_EXERCISE', '早操检查', 'INSPECTION'),
    ('DORM_HYGIENE', '宿舍卫生普查', 'DORM'),
    ('LATE_RETURN', '晚归抽查', 'INSPECTION'),
    ('EVENING_ATTENDANCE', '晚自习考勤', 'ACADEMIC');

CREATE TABLE student (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    class_id BIGINT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    valid_until DATE NOT NULL,
    invite_digest CHAR(64),
    invite_expires_at BIGINT,
    CONSTRAINT fk_student_class FOREIGN KEY (class_id) REFERENCES class_group(id)
);

CREATE INDEX idx_student_class ON student (class_id, student_no);

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(48) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    student_id BIGINT UNIQUE,
    core_admin_slot INT UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT ck_core_admin_slot CHECK (core_admin_slot = 1)
);

CREATE TABLE user_grant (
    user_id BIGINT NOT NULL,
    role_code VARCHAR(24) NOT NULL,
    scope_key VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role_code, scope_key),
    CONSTRAINT fk_grant_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE violation_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    student_no VARCHAR(32) NOT NULL,
    student_name VARCHAR(80) NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    counselor VARCHAR(80) NOT NULL,
    occurred_on DATE NOT NULL,
    department_code VARCHAR(24) NOT NULL,
    type_code VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    disposition VARCHAR(250) NOT NULL,
    inspection_scope VARCHAR(16) NOT NULL,
    remarks VARCHAR(250),
    status VARCHAR(16) NOT NULL,
    rejection_reason VARCHAR(250),
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    submission_id CHAR(36) NOT NULL,
    handed_back_at BIGINT,
    deleted_at BIGINT,
    CONSTRAINT uq_violation_submission UNIQUE (created_by, submission_id),
    CONSTRAINT fk_violation_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_violation_class FOREIGN KEY (class_id) REFERENCES class_group(id),
    CONSTRAINT fk_violation_department FOREIGN KEY (department_code) REFERENCES department(code),
    CONSTRAINT fk_violation_type FOREIGN KEY (type_code) REFERENCES violation_type(code),
    CONSTRAINT fk_violation_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_violation_student ON violation_record (student_id, occurred_on, id);
CREATE INDEX idx_violation_class ON violation_record (class_id, occurred_on, id);
CREATE INDEX idx_violation_department ON violation_record (department_code, occurred_on, id);
CREATE INDEX idx_violation_status ON violation_record (status, occurred_on, id);

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id BIGINT NOT NULL,
    previous_state VARCHAR(40),
    new_state VARCHAR(40),
    request_id VARCHAR(40),
    created_at BIGINT NOT NULL
);
