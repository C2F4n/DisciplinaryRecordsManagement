ALTER TABLE class_group ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE student ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE student_number_rule (
    id INT NOT NULL PRIMARY KEY,
    min_length INT NOT NULL,
    max_length INT NOT NULL,
    number_prefix VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_student_number_rule CHECK (id = 1 AND min_length >= 1
        AND min_length <= max_length AND max_length <= 32 AND CHAR_LENGTH(number_prefix) <= max_length)
);

INSERT INTO student_number_rule(id, min_length, max_length, number_prefix) VALUES (1, 6, 24, '');

CREATE TABLE export_template (
    kind_code VARCHAR(32) NOT NULL PRIMARY KEY,
    title VARCHAR(80) NOT NULL,
    columns_csv VARCHAR(400) NOT NULL,
    version BIGINT NOT NULL
);
