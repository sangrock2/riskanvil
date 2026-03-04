ALTER TABLE api_usage_log
    ADD COLUMN request_id VARCHAR(64) NULL,
    ADD COLUMN http_status INT NULL,
    ADD COLUMN duration_ms BIGINT NULL;

CREATE INDEX idx_usage_request_id ON api_usage_log(request_id);
