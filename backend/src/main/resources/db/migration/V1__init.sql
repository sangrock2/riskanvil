-- MySQL 8.x 기준
CREATE TABLE users (
   id BIGINT NOT NULL AUTO_INCREMENT,
   email VARCHAR(190) NOT NULL,
   password_hash VARCHAR(100) NOT NULL,
   role VARCHAR(30) NOT NULL,
   PRIMARY KEY (id),
   CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(40) NOT NULL,
    market VARCHAR(10) NOT NULL,
    request_json LONGTEXT NOT NULL,
    response_json LONGTEXT NOT NULL,
    action VARCHAR(10),
    confidence DOUBLE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_analysis_runs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_analysis_runs_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
