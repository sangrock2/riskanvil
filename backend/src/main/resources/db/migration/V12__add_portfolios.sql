CREATE TABLE portfolios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    target_return DECIMAL(5,2) COMMENT 'Target annual return %',
    risk_profile VARCHAR(20) DEFAULT 'moderate' COMMENT 'conservative/moderate/aggressive',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_portfolios_user_name UNIQUE (user_id, name),
    INDEX idx_portfolios_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE portfolio_positions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_id BIGINT NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    market VARCHAR(8) NOT NULL DEFAULT 'US',
    quantity DECIMAL(15,4) NOT NULL,
    entry_price DECIMAL(15,4) NOT NULL,
    entry_date DATE,
    notes VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_positions_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT uk_portfolio_positions UNIQUE (portfolio_id, ticker, market),
    INDEX idx_portfolio_positions_portfolio (portfolio_id, created_at DESC),
    INDEX idx_portfolio_positions_ticker (ticker, market)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
