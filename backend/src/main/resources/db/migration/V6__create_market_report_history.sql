CREATE TABLE IF NOT EXISTS market_report_history (
   id BIGINT AUTO_INCREMENT PRIMARY KEY,
   cache_id BIGINT NOT NULL,
   report_text LONGTEXT NOT NULL,
   created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

   INDEX idx_hist_cache_created (cache_id, created_at),
   CONSTRAINT fk_hist_cache FOREIGN KEY (cache_id) REFERENCES market_cache(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
