ALTER TABLE workflow_option ADD COLUMN category VARCHAR(80);

UPDATE workflow_option SET category = '学院检查' WHERE kind = 'INSPECTION' AND code = 'COLLEGE';
UPDATE workflow_option SET category = '学校检查' WHERE kind = 'INSPECTION' AND code = 'SCHOOL';
