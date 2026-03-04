CREATE TABLE IF NOT EXISTS watchlist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    market VARCHAR(8) NOT NULL,
    test_mode BIT(1) NOT NULL,
    created_at DATETIME NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_watchlist UNIQUE (user_id, ticker, market, test_mode),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
