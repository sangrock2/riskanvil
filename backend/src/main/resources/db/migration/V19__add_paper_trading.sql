-- Paper Trading: 모의투자 계좌, 포지션, 주문 이력

CREATE TABLE paper_accounts (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    user_id        BIGINT         NOT NULL,
    market         VARCHAR(4)     NOT NULL COMMENT 'US or KR',
    balance        DECIMAL(20, 4) NOT NULL,
    initial_balance DECIMAL(20, 4) NOT NULL,
    currency       VARCHAR(4)     NOT NULL COMMENT 'USD or KRW',
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_accounts_user_market (user_id, market),
    CONSTRAINT fk_paper_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE paper_positions (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    account_id   BIGINT         NOT NULL,
    ticker       VARCHAR(32)    NOT NULL,
    quantity     DECIMAL(15, 4) NOT NULL,
    avg_price    DECIMAL(15, 4) NOT NULL,
    total_cost   DECIMAL(20, 4) NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_positions_account_ticker (account_id, ticker),
    CONSTRAINT fk_paper_positions_account FOREIGN KEY (account_id) REFERENCES paper_accounts (id) ON DELETE CASCADE
);

CREATE TABLE paper_orders (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    account_id   BIGINT         NOT NULL,
    ticker       VARCHAR(32)    NOT NULL,
    direction    VARCHAR(4)     NOT NULL COMMENT 'BUY or SELL',
    quantity     DECIMAL(15, 4) NOT NULL,
    price        DECIMAL(15, 4) NOT NULL,
    amount       DECIMAL(20, 4) NOT NULL COMMENT 'quantity * price',
    commission   DECIMAL(20, 4) NOT NULL COMMENT '0.1% commission',
    created_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_paper_orders_account FOREIGN KEY (account_id) REFERENCES paper_accounts (id) ON DELETE CASCADE,
    INDEX idx_paper_orders_account_created (account_id, created_at DESC)
);
