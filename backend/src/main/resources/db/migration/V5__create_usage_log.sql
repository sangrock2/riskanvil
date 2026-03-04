CREATE TABLE IF NOT EXISTS api_usage_log(
   id BIGINT AUTO_INCREMENT PRIMARY KEY,
   user_id BIGINT NOT NULL,
   endpoint VARCHAR(32) NOT NULL,         -- INSIGHTS / REPORT
   ticker VARCHAR(32) NOT NULL,
   market VARCHAR(8) NOT NULL,
   test_mode TINYINT(1) NOT NULL,
   days INT NOT NULL,
   news_limit INT NOT NULL,

   cached TINYINT(1) NOT NULL,
   refresh TINYINT(1) NOT NULL,
   web TINYINT(1) NOT NULL,

   alpha_calls INT NULL,
   openai_calls INT NULL,
   openai_model VARCHAR(64) NULL,
   openai_tokens_in INT NULL,
   openai_tokens_out INT NULL,

   error_text VARCHAR(512) NULL,
   created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

   INDEX idx_usage_user_created (user_id, created_at),
   CONSTRAINT fk_usage_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
