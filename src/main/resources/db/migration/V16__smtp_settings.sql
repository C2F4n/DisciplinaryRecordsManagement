CREATE TABLE smtp_settings (
    id INT NOT NULL PRIMARY KEY,
    gate_hash VARCHAR(100) NOT NULL,
    gate_version BIGINT NOT NULL DEFAULT 0,
    config_version BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    host VARCHAR(253) NOT NULL DEFAULT '',
    port INT NOT NULL DEFAULT 587,
    username VARCHAR(254) NOT NULL DEFAULT '',
    sender_address VARCHAR(254) NOT NULL DEFAULT '',
    authentication BOOLEAN NOT NULL DEFAULT TRUE,
    transport VARCHAR(12) NOT NULL DEFAULT 'STARTTLS',
    encrypted_password TEXT,
    CONSTRAINT ck_smtp_settings_id CHECK (id = 1)
);

INSERT INTO smtp_settings(id, gate_hash)
VALUES(1, '$2a$10$xN13BfFRIrlTlYZKCfHFLOnfaC0NIjAaT9Z2rg7767GocpxQQJj4u');
