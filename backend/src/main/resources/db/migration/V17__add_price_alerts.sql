CREATE TABLE price_alerts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    market VARCHAR(8) NOT NULL DEFAULT 'US',
    alert_type VARCHAR(20) NOT NULL COMMENT 'ABOVE/BELOW/CHANGE_PERCENT/RSI_OVERBOUGHT/RSI_OVERSOLD',
    threshold DECIMAL(15,4) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    triggered_at DATETIME(6),
    notification_sent TINYINT(1) DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_price_alerts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_price_alerts_user_enabled (user_id, enabled, created_at DESC),
    INDEX idx_price_alerts_ticker_enabled (ticker, market, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
