ALTER TABLE totp_secrets
    MODIFY COLUMN secret VARCHAR(255) NOT NULL COMMENT 'Encrypted TOTP secret payload';
