CREATE TABLE IF NOT EXISTS market_cache (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    market VARCHAR(8) NOT NULL,
    test_mode TINYINT(1) NOT NULL DEFAULT 0,
    days INT NOT NULL,
    news_limit INT NOT NULL,

    insights_json LONGTEXT NULL,
    insights_updated_at DATETIME(6) NULL,

    report_text LONGTEXT NULL,
    report_updated_at DATETIME(6) NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_market_cache_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_market_cache_key (user_id, ticker, market, test_mode, days, news_limit)
);
