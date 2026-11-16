UPDATE student_number_rule
SET min_length = 12, max_length = 12, version = version + 1
WHERE id = 1 AND version = 0 AND min_length = 6 AND max_length = 24 AND number_prefix = '';
