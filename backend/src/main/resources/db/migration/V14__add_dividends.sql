CREATE TABLE dividends (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_position_id BIGINT NOT NULL,
    amount DECIMAL(15,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    ex_date DATE NOT NULL,
    payment_date DATE,
    record_date DATE,
    declared_date DATE,
    frequency VARCHAR(20) COMMENT 'monthly/quarterly/annually',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_dividends_position FOREIGN KEY (portfolio_position_id) REFERENCES portfolio_positions(id) ON DELETE CASCADE,
    INDEX idx_dividends_position_date (portfolio_position_id, ex_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
