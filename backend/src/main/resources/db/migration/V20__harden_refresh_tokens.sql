-- Harden refresh token storage:
-- store deterministic SHA-256 hash and stop using raw token lookup.
ALTER TABLE refresh_tokens
    ADD COLUMN token_hash VARCHAR(64) NULL AFTER token;

UPDATE refresh_tokens
SET token_hash = LOWER(SHA2(token, 256))
WHERE token_hash IS NULL;

ALTER TABLE refresh_tokens
    MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;

ALTER TABLE refresh_tokens
    ADD UNIQUE INDEX uk_refresh_tokens_token_hash (token_hash);
