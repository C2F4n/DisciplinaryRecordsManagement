ALTER TABLE class_group MODIFY COLUMN counselor VARCHAR(242) NOT NULL;
ALTER TABLE violation_record MODIFY COLUMN counselor VARCHAR(242) NOT NULL;
ALTER TABLE monthly_report_item MODIFY COLUMN counselor VARCHAR(242) NOT NULL;

CREATE TABLE class_counselor (
    class_id BIGINT NOT NULL,
    counselor_id BIGINT NOT NULL,
    position SMALLINT NOT NULL,
    PRIMARY KEY (class_id, counselor_id),
    CONSTRAINT uq_class_counselor_position UNIQUE (class_id, position),
    CONSTRAINT ck_class_counselor_position CHECK (position BETWEEN 1 AND 3),
    CONSTRAINT fk_class_counselor_class FOREIGN KEY (class_id) REFERENCES class_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_class_counselor_counselor FOREIGN KEY (counselor_id) REFERENCES counselor(id)
);

INSERT INTO counselor(name)
SELECT DISTINCT TRIM(c.counselor) FROM class_group c
WHERE TRIM(c.counselor) <> ''
  AND NOT EXISTS (SELECT 1 FROM counselor a WHERE a.name = TRIM(c.counselor));

INSERT INTO class_counselor(class_id, counselor_id, position)
SELECT c.id, a.id, 1 FROM class_group c JOIN counselor a ON a.name = TRIM(c.counselor);
