CREATE TABLE backtest_runs (
   id BIGINT NOT NULL AUTO_INCREMENT,
   user_id BIGINT NOT NULL,
   ticker VARCHAR(40) NOT NULL,
   market VARCHAR(10) NOT NULL,
   strategy VARCHAR(30) NOT NULL,
   request_json LONGTEXT NOT NULL,
   response_json LONGTEXT NOT NULL,
   total_return DOUBLE,
   max_drawdown DOUBLE,
   sharpe DOUBLE,
   cagr DOUBLE,
   created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
   PRIMARY KEY (id),
   CONSTRAINT fk_backtest_runs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
   INDEX idx_backtest_runs_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
