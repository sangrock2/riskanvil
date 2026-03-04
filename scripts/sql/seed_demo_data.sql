-- Demo seed for Stock-AI
-- This script is idempotent for seeded rows and targets one existing user.
-- Before running this file directly in mysql client, set:
--   SET @target_user_id := <USER_ID>;
-- Then run:
--   SOURCE C:/.../scripts/sql/seed_demo_data.sql;

SET @seed_marker := 'demo-seed-v1';
SET @now := NOW(6);

START TRANSACTION;

-- -----------------------------
-- 1) User settings (insert only)
-- -----------------------------
INSERT INTO user_settings (
    user_id, totp_enabled, email_on_alerts, daily_summary_enabled,
    theme, language, default_market, created_at
)
SELECT
    @target_user_id, 0, 1, 0, 'dark', 'ko', 'US', @now
WHERE NOT EXISTS (
    SELECT 1 FROM user_settings WHERE user_id = @target_user_id
);

-- -----------------------------
-- 2) Cleanup previous seeded rows
-- -----------------------------
DELETE wit
FROM watchlist_item_tag wit
JOIN watchlist w ON w.id = wit.watchlist_item_id
WHERE w.user_id = @target_user_id
  AND w.notes LIKE CONCAT('%', @seed_marker, '%');

DELETE FROM watchlist
WHERE user_id = @target_user_id
  AND notes LIKE CONCAT('%', @seed_marker, '%');

DELETE FROM watchlist_tag
WHERE user_id = @target_user_id
  AND name LIKE 'DEMO-%';

DELETE FROM market_report_history
WHERE report_text LIKE CONCAT('%', @seed_marker, '%');

DELETE FROM market_cache
WHERE user_id = @target_user_id
  AND (
      insights_json LIKE CONCAT('%', @seed_marker, '%')
      OR report_text LIKE CONCAT('%', @seed_marker, '%')
  );

DELETE FROM analysis_runs
WHERE user_id = @target_user_id
  AND request_json LIKE CONCAT('%', @seed_marker, '%');

DELETE FROM backtest_runs
WHERE user_id = @target_user_id
  AND request_json LIKE CONCAT('%', @seed_marker, '%');

DELETE d
FROM dividends d
JOIN portfolio_positions pp ON pp.id = d.portfolio_position_id
JOIN portfolios p ON p.id = pp.portfolio_id
WHERE p.user_id = @target_user_id
  AND p.name LIKE 'DEMO %';

DELETE pp
FROM portfolio_positions pp
JOIN portfolios p ON p.id = pp.portfolio_id
WHERE p.user_id = @target_user_id
  AND p.name LIKE 'DEMO %';

DELETE FROM portfolios
WHERE user_id = @target_user_id
  AND name LIKE 'DEMO %';

DELETE FROM screener_presets
WHERE user_id = @target_user_id
  AND name LIKE 'DEMO %';

DELETE cm
FROM chat_messages cm
JOIN chat_conversations cc ON cc.id = cm.conversation_id
WHERE cc.user_id = @target_user_id
  AND cc.title LIKE '[DEMO]%';

DELETE FROM chat_conversations
WHERE user_id = @target_user_id
  AND title LIKE '[DEMO]%';

DELETE FROM price_alerts
WHERE user_id = @target_user_id
  AND (
      (ticker = 'AAPL' AND market = 'US' AND alert_type = 'ABOVE' AND threshold = 250.0000)
      OR (ticker = 'NVDA' AND market = 'US' AND alert_type = 'BELOW' AND threshold = 900.0000)
      OR (ticker = '005930' AND market = 'KR' AND alert_type = 'ABOVE' AND threshold = 78000.0000)
  );

DELETE FROM api_usage_log
WHERE user_id = @target_user_id
  AND request_id LIKE 'SEED-DEMO-%';

-- -----------------------------
-- 3) Watchlist + tags
-- -----------------------------
INSERT INTO watchlist_tag (user_id, name, color, created_at) VALUES
(@target_user_id, 'DEMO-Growth', '#3b82f6', @now),
(@target_user_id, 'DEMO-Dividend', '#10b981', @now),
(@target_user_id, 'DEMO-RiskHedge', '#f59e0b', @now);

INSERT INTO watchlist (user_id, ticker, market, test_mode, created_at, notes) VALUES
(@target_user_id, 'AAPL', 'US', 0, @now - INTERVAL 10 DAY, CONCAT('[', @seed_marker, '] Core US growth stock')),
(@target_user_id, 'NVDA', 'US', 0, @now - INTERVAL 9 DAY, CONCAT('[', @seed_marker, '] AI momentum watch')),
(@target_user_id, 'MSFT', 'US', 0, @now - INTERVAL 8 DAY, CONCAT('[', @seed_marker, '] Stable large-cap with cloud exposure')),
(@target_user_id, '005930', 'KR', 0, @now - INTERVAL 7 DAY, CONCAT('[', @seed_marker, '] KR semiconductor anchor')),
(@target_user_id, '035420', 'KR', 1, @now - INTERVAL 6 DAY, CONCAT('[', @seed_marker, '] Test mode sample item'));

SET @tag_growth := (
    SELECT id FROM watchlist_tag
    WHERE user_id = @target_user_id AND name = 'DEMO-Growth'
    LIMIT 1
);
SET @tag_dividend := (
    SELECT id FROM watchlist_tag
    WHERE user_id = @target_user_id AND name = 'DEMO-Dividend'
    LIMIT 1
);
SET @tag_risk := (
    SELECT id FROM watchlist_tag
    WHERE user_id = @target_user_id AND name = 'DEMO-RiskHedge'
    LIMIT 1
);

SET @w_aapl := (
    SELECT id FROM watchlist
    WHERE user_id = @target_user_id AND ticker = 'AAPL' AND market = 'US' AND test_mode = 0
    LIMIT 1
);
SET @w_nvda := (
    SELECT id FROM watchlist
    WHERE user_id = @target_user_id AND ticker = 'NVDA' AND market = 'US' AND test_mode = 0
    LIMIT 1
);
SET @w_msft := (
    SELECT id FROM watchlist
    WHERE user_id = @target_user_id AND ticker = 'MSFT' AND market = 'US' AND test_mode = 0
    LIMIT 1
);
SET @w_005930 := (
    SELECT id FROM watchlist
    WHERE user_id = @target_user_id AND ticker = '005930' AND market = 'KR' AND test_mode = 0
    LIMIT 1
);

INSERT IGNORE INTO watchlist_item_tag (watchlist_item_id, tag_id) VALUES
(@w_aapl, @tag_growth),
(@w_nvda, @tag_growth),
(@w_msft, @tag_dividend),
(@w_005930, @tag_dividend),
(@w_005930, @tag_risk);

-- -----------------------------
-- 4) Market cache + report history
-- -----------------------------
INSERT INTO market_cache (
    user_id, ticker, market, test_mode, days, news_limit,
    insights_json, insights_updated_at, report_text, report_updated_at, created_at
) VALUES
(
    @target_user_id, 'AAPL', 'US', 0, 252, 5,
    JSON_OBJECT(
      'seed', @seed_marker,
      'ticker', 'AAPL',
      'decision', JSON_OBJECT('action', 'BUY', 'confidence', 0.81),
      'score', 82
    ),
    @now - INTERVAL 2 DAY,
    CONCAT('[', @seed_marker, '] AAPL report summary. Strong cash flow, resilient margins, and moderate valuation risk.'),
    @now - INTERVAL 2 DAY,
    @now - INTERVAL 12 DAY
),
(
    @target_user_id, 'NVDA', 'US', 0, 252, 5,
    JSON_OBJECT(
      'seed', @seed_marker,
      'ticker', 'NVDA',
      'decision', JSON_OBJECT('action', 'HOLD', 'confidence', 0.67),
      'score', 71
    ),
    @now - INTERVAL 1 DAY,
    CONCAT('[', @seed_marker, '] NVDA report summary. Strong growth but volatility and valuation heat remain elevated.'),
    @now - INTERVAL 1 DAY,
    @now - INTERVAL 11 DAY
),
(
    @target_user_id, '005930', 'KR', 0, 252, 5,
    JSON_OBJECT(
      'seed', @seed_marker,
      'ticker', '005930',
      'decision', JSON_OBJECT('action', 'BUY', 'confidence', 0.73),
      'score', 76
    ),
    @now - INTERVAL 1 DAY,
    CONCAT('[', @seed_marker, '] 005930 report summary. Earnings cycle recovery and memory demand improvement trend.'),
    @now - INTERVAL 1 DAY,
    @now - INTERVAL 11 DAY
);

SET @cache_aapl := (
    SELECT id FROM market_cache
    WHERE user_id = @target_user_id
      AND ticker = 'AAPL'
      AND market = 'US'
      AND test_mode = 0
      AND days = 252
      AND news_limit = 5
    LIMIT 1
);

INSERT INTO market_report_history (cache_id, report_text, created_at) VALUES
(@cache_aapl, CONCAT('[', @seed_marker, '] AAPL report v1 baseline.'), @now - INTERVAL 4 DAY),
(@cache_aapl, CONCAT('[', @seed_marker, '] AAPL report v2 with updated risk commentary.'), @now - INTERVAL 2 DAY);

-- -----------------------------
-- 5) Analysis and backtest history
-- -----------------------------
INSERT INTO analysis_runs (
    user_id, ticker, market, request_json, response_json,
    action, confidence, created_at
) VALUES
(
    @target_user_id, 'AAPL', 'US',
    JSON_OBJECT('seed', @seed_marker, 'ticker', 'AAPL', 'market', 'US', 'horizonDays', 252, 'riskProfile', 'balanced'),
    JSON_OBJECT('seed', @seed_marker, 'decision', JSON_OBJECT('action', 'BUY', 'confidence', 0.81), 'score', 82),
    'BUY', 0.81, @now - INTERVAL 9 DAY
),
(
    @target_user_id, 'NVDA', 'US',
    JSON_OBJECT('seed', @seed_marker, 'ticker', 'NVDA', 'market', 'US', 'horizonDays', 252, 'riskProfile', 'aggressive'),
    JSON_OBJECT('seed', @seed_marker, 'decision', JSON_OBJECT('action', 'HOLD', 'confidence', 0.67), 'score', 71),
    'HOLD', 0.67, @now - INTERVAL 7 DAY
),
(
    @target_user_id, '005930', 'KR',
    JSON_OBJECT('seed', @seed_marker, 'ticker', '005930', 'market', 'KR', 'horizonDays', 252, 'riskProfile', 'balanced'),
    JSON_OBJECT('seed', @seed_marker, 'decision', JSON_OBJECT('action', 'BUY', 'confidence', 0.73), 'score', 76),
    'BUY', 0.73, @now - INTERVAL 6 DAY
);

INSERT INTO backtest_runs (
    user_id, ticker, market, strategy, request_json, response_json,
    total_return, max_drawdown, sharpe, cagr, created_at
) VALUES
(
    @target_user_id, 'AAPL', 'US', 'SMA_CROSS',
    JSON_OBJECT('seed', @seed_marker, 'ticker', 'AAPL', 'market', 'US', 'strategy', 'SMA_CROSS', 'initialCapital', 100000),
    JSON_OBJECT('seed', @seed_marker, 'summary', JSON_OBJECT('totalReturn', 34.2, 'maxDrawdown', -12.8, 'sharpe', 1.21, 'cagr', 11.1)),
    34.2, -12.8, 1.21, 11.1, @now - INTERVAL 8 DAY
),
(
    @target_user_id, 'NVDA', 'US', 'MOMENTUM',
    JSON_OBJECT('seed', @seed_marker, 'ticker', 'NVDA', 'market', 'US', 'strategy', 'MOMENTUM', 'initialCapital', 100000),
    JSON_OBJECT('seed', @seed_marker, 'summary', JSON_OBJECT('totalReturn', 62.5, 'maxDrawdown', -24.4, 'sharpe', 1.07, 'cagr', 19.4)),
    62.5, -24.4, 1.07, 19.4, @now - INTERVAL 5 DAY
),
(
    @target_user_id, '005930', 'KR', 'MEAN_REVERSION',
    JSON_OBJECT('seed', @seed_marker, 'ticker', '005930', 'market', 'KR', 'strategy', 'MEAN_REVERSION', 'initialCapital', 100000000),
    JSON_OBJECT('seed', @seed_marker, 'summary', JSON_OBJECT('totalReturn', 18.4, 'maxDrawdown', -10.2, 'sharpe', 0.93, 'cagr', 7.9)),
    18.4, -10.2, 0.93, 7.9, @now - INTERVAL 4 DAY
);

-- -----------------------------
-- 6) Portfolio + positions + dividends
-- -----------------------------
INSERT INTO portfolios (
    user_id, name, description, target_return, risk_profile, created_at
) VALUES
(
    @target_user_id, 'DEMO Core US',
    CONCAT('[', @seed_marker, '] US growth and quality mix for dashboard validation'),
    14.00, 'moderate', @now - INTERVAL 12 DAY
),
(
    @target_user_id, 'DEMO Korea Income',
    CONCAT('[', @seed_marker, '] KR dividend and defensive mix for calendar validation'),
    10.00, 'conservative', @now - INTERVAL 12 DAY
);

SET @pf_us := (
    SELECT id FROM portfolios
    WHERE user_id = @target_user_id AND name = 'DEMO Core US'
    LIMIT 1
);
SET @pf_kr := (
    SELECT id FROM portfolios
    WHERE user_id = @target_user_id AND name = 'DEMO Korea Income'
    LIMIT 1
);

INSERT INTO portfolio_positions (
    portfolio_id, ticker, market, quantity, entry_price, entry_date, notes, created_at
) VALUES
(@pf_us, 'AAPL', 'US', 18.0000, 172.5000, CURDATE() - INTERVAL 180 DAY, CONCAT('[', @seed_marker, '] Demo position'), @now - INTERVAL 11 DAY),
(@pf_us, 'MSFT', 'US', 12.0000, 340.0000, CURDATE() - INTERVAL 160 DAY, CONCAT('[', @seed_marker, '] Demo position'), @now - INTERVAL 11 DAY),
(@pf_us, 'NVDA', 'US', 8.0000, 780.0000, CURDATE() - INTERVAL 120 DAY, CONCAT('[', @seed_marker, '] Demo position'), @now - INTERVAL 11 DAY),
(@pf_kr, '005930', 'KR', 35.0000, 70200.0000, CURDATE() - INTERVAL 190 DAY, CONCAT('[', @seed_marker, '] Demo position'), @now - INTERVAL 11 DAY),
(@pf_kr, '035420', 'KR', 20.0000, 198000.0000, CURDATE() - INTERVAL 150 DAY, CONCAT('[', @seed_marker, '] Demo position'), @now - INTERVAL 11 DAY);

SET @pp_aapl := (
    SELECT id FROM portfolio_positions
    WHERE portfolio_id = @pf_us AND ticker = 'AAPL' AND market = 'US'
    LIMIT 1
);
SET @pp_msft := (
    SELECT id FROM portfolio_positions
    WHERE portfolio_id = @pf_us AND ticker = 'MSFT' AND market = 'US'
    LIMIT 1
);
SET @pp_005930 := (
    SELECT id FROM portfolio_positions
    WHERE portfolio_id = @pf_kr AND ticker = '005930' AND market = 'KR'
    LIMIT 1
);

INSERT INTO dividends (
    portfolio_position_id, amount, currency, ex_date, payment_date, record_date, declared_date, frequency, created_at
) VALUES
(@pp_aapl, 0.2400, 'USD', CURDATE() - INTERVAL 30 DAY, CURDATE() - INTERVAL 15 DAY, CURDATE() - INTERVAL 29 DAY, CURDATE() - INTERVAL 45 DAY, 'quarterly', @now - INTERVAL 10 DAY),
(@pp_msft, 0.7500, 'USD', CURDATE() - INTERVAL 40 DAY, CURDATE() - INTERVAL 20 DAY, CURDATE() - INTERVAL 39 DAY, CURDATE() - INTERVAL 60 DAY, 'quarterly', @now - INTERVAL 10 DAY),
(@pp_005930, 361.0000, 'KRW', CURDATE() - INTERVAL 35 DAY, CURDATE() - INTERVAL 14 DAY, CURDATE() - INTERVAL 34 DAY, CURDATE() - INTERVAL 50 DAY, 'quarterly', @now - INTERVAL 10 DAY);

-- -----------------------------
-- 7) Screener presets
-- -----------------------------
INSERT INTO screener_presets (
    user_id, name, description, filters_json, is_public, created_at
) VALUES
(
    @target_user_id, 'DEMO US Growth Quality',
    CONCAT('[', @seed_marker, '] Growth + quality screen'),
    JSON_OBJECT(
      'market', 'US',
      'peMin', 10,
      'peMax', 45,
      'roeMin', 12,
      'revenueYoYMin', 8,
      'marketCapMin', 10000000000
    ),
    0, @now - INTERVAL 9 DAY
),
(
    @target_user_id, 'DEMO KR Dividend Stability',
    CONCAT('[', @seed_marker, '] KR dividend stability screen'),
    JSON_OBJECT(
      'market', 'KR',
      'dividendYieldMin', 1.5,
      'debtRatioMax', 0.65,
      'operatingMarginMin', 7
    ),
    0, @now - INTERVAL 9 DAY
);

-- -----------------------------
-- 8) Chatbot sample conversations
-- -----------------------------
INSERT INTO chat_conversations (
    user_id, title, model, context_json, created_at, updated_at
) VALUES
(
    @target_user_id,
    '[DEMO] Earnings week risk check',
    'opus',
    JSON_OBJECT('seed', @seed_marker, 'topic', 'risk'),
    @now - INTERVAL 3 DAY,
    @now - INTERVAL 2 DAY
),
(
    @target_user_id,
    '[DEMO] Rebalance idea discussion',
    'sonnet',
    JSON_OBJECT('seed', @seed_marker, 'topic', 'rebalance'),
    @now - INTERVAL 2 DAY,
    @now - INTERVAL 1 DAY
);

SET @conv_risk := (
    SELECT id FROM chat_conversations
    WHERE user_id = @target_user_id AND title = '[DEMO] Earnings week risk check'
    LIMIT 1
);
SET @conv_reb := (
    SELECT id FROM chat_conversations
    WHERE user_id = @target_user_id AND title = '[DEMO] Rebalance idea discussion'
    LIMIT 1
);

INSERT INTO chat_messages (
    conversation_id, role, content, model, tokens_in, tokens_out, created_at
) VALUES
(@conv_risk, 'user', 'How should I size positions before earnings this week?', 'opus', 58, 0, @now - INTERVAL 3 DAY + INTERVAL 1 HOUR),
(@conv_risk, 'assistant', 'Reduce single-name concentration and tighten stop-loss levels around event windows.', 'opus', 58, 112, @now - INTERVAL 3 DAY + INTERVAL 1 HOUR + INTERVAL 30 SECOND),
(@conv_reb, 'user', 'Can you suggest a simple monthly rebalance rule?', 'sonnet', 41, 0, @now - INTERVAL 2 DAY + INTERVAL 2 HOUR),
(@conv_reb, 'assistant', 'Use threshold rebalance: if any holding deviates by 5%p from target weight, rebalance monthly.', 'sonnet', 41, 96, @now - INTERVAL 2 DAY + INTERVAL 2 HOUR + INTERVAL 30 SECOND);

-- -----------------------------
-- 9) Price alerts
-- -----------------------------
INSERT INTO price_alerts (
    user_id, ticker, market, alert_type, threshold, enabled, triggered_at, notification_sent, created_at
) VALUES
(@target_user_id, 'AAPL', 'US', 'ABOVE', 250.0000, 1, NULL, 0, @now - INTERVAL 6 DAY),
(@target_user_id, 'NVDA', 'US', 'BELOW', 900.0000, 1, NULL, 0, @now - INTERVAL 6 DAY),
(@target_user_id, '005930', 'KR', 'ABOVE', 78000.0000, 1, NULL, 0, @now - INTERVAL 6 DAY);

-- -----------------------------
-- 10) Usage logs
-- -----------------------------
INSERT INTO api_usage_log (
    user_id, endpoint, ticker, market, test_mode, days, news_limit,
    cached, refresh, web, alpha_calls, openai_calls, openai_model,
    openai_tokens_in, openai_tokens_out, error_text, created_at,
    request_id, http_status, duration_ms
) VALUES
(@target_user_id, 'INSIGHTS', 'AAPL', 'US', 0, 252, 5, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, @now - INTERVAL 7 DAY, 'SEED-DEMO-001', 200, 780),
(@target_user_id, 'REPORT', 'AAPL', 'US', 0, 252, 5, 0, 1, 1, 1, 1, 'gpt-4o-mini', 1240, 460, NULL, @now - INTERVAL 7 DAY + INTERVAL 20 MINUTE, 'SEED-DEMO-002', 200, 1820),
(@target_user_id, 'INSIGHTS', 'NVDA', 'US', 0, 252, 5, 1, 0, 0, 0, 0, NULL, NULL, NULL, NULL, @now - INTERVAL 6 DAY, 'SEED-DEMO-003', 200, 140),
(@target_user_id, 'REPORT', 'NVDA', 'US', 0, 252, 5, 0, 0, 1, 1, 1, 'gpt-4o-mini', 1380, 510, NULL, @now - INTERVAL 6 DAY + INTERVAL 15 MINUTE, 'SEED-DEMO-004', 200, 1940),
(@target_user_id, 'INSIGHTS', '005930', 'KR', 0, 252, 5, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, @now - INTERVAL 5 DAY, 'SEED-DEMO-005', 200, 690),
(@target_user_id, 'REPORT', '005930', 'KR', 0, 252, 5, 0, 0, 1, 1, 1, 'gpt-4o-mini', 1100, 420, NULL, @now - INTERVAL 5 DAY + INTERVAL 20 MINUTE, 'SEED-DEMO-006', 200, 1710),
(@target_user_id, 'INSIGHTS', 'MSFT', 'US', 0, 252, 5, 1, 0, 0, 0, 0, NULL, NULL, NULL, NULL, @now - INTERVAL 4 DAY, 'SEED-DEMO-007', 200, 130),
(@target_user_id, 'REPORT', 'MSFT', 'US', 0, 252, 5, 0, 1, 0, 1, 0, NULL, NULL, NULL, 'ai timeout during optional web step', @now - INTERVAL 4 DAY + INTERVAL 35 MINUTE, 'SEED-DEMO-008', 504, 30000),
(@target_user_id, 'VALUATION', 'AAPL', 'US', 0, 252, 5, 1, 0, 0, 0, 0, NULL, NULL, NULL, NULL, @now - INTERVAL 3 DAY, 'SEED-DEMO-009', 200, 95),
(@target_user_id, 'INSIGHTS', '035420', 'KR', 1, 252, 3, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, @now - INTERVAL 2 DAY, 'SEED-DEMO-010', 200, 760);

-- -----------------------------
-- 11) Paper trading (safe insert)
-- -----------------------------
INSERT INTO paper_accounts (
    user_id, market, balance, initial_balance, currency, created_at, updated_at
)
SELECT @target_user_id, 'US', 100000.0000, 100000.0000, 'USD', @now - INTERVAL 14 DAY, @now - INTERVAL 1 DAY
WHERE NOT EXISTS (
    SELECT 1 FROM paper_accounts WHERE user_id = @target_user_id AND market = 'US'
);

INSERT INTO paper_accounts (
    user_id, market, balance, initial_balance, currency, created_at, updated_at
)
SELECT @target_user_id, 'KR', 100000000.0000, 100000000.0000, 'KRW', @now - INTERVAL 14 DAY, @now - INTERVAL 1 DAY
WHERE NOT EXISTS (
    SELECT 1 FROM paper_accounts WHERE user_id = @target_user_id AND market = 'KR'
);

SET @us_account_id := (
    SELECT id FROM paper_accounts WHERE user_id = @target_user_id AND market = 'US' LIMIT 1
);
SET @kr_account_id := (
    SELECT id FROM paper_accounts WHERE user_id = @target_user_id AND market = 'KR' LIMIT 1
);

SET @us_pos_count := (
    SELECT COUNT(*) FROM paper_positions WHERE account_id = @us_account_id
);
SET @kr_pos_count := (
    SELECT COUNT(*) FROM paper_positions WHERE account_id = @kr_account_id
);
SET @us_order_count := (
    SELECT COUNT(*) FROM paper_orders WHERE account_id = @us_account_id
);
SET @kr_order_count := (
    SELECT COUNT(*) FROM paper_orders WHERE account_id = @kr_account_id
);

INSERT INTO paper_positions (
    account_id, ticker, quantity, avg_price, total_cost, created_at, updated_at
)
SELECT @us_account_id, 'AAPL', 20.0000, 175.0000, 3500.0000, @now - INTERVAL 13 DAY, @now - INTERVAL 1 DAY
WHERE @us_pos_count = 0;

INSERT INTO paper_positions (
    account_id, ticker, quantity, avg_price, total_cost, created_at, updated_at
)
SELECT @us_account_id, 'NVDA', 10.0000, 900.0000, 9000.0000, @now - INTERVAL 12 DAY, @now - INTERVAL 1 DAY
WHERE @us_pos_count = 0;

INSERT INTO paper_positions (
    account_id, ticker, quantity, avg_price, total_cost, created_at, updated_at
)
SELECT @kr_account_id, '005930', 30.0000, 77000.0000, 2310000.0000, @now - INTERVAL 12 DAY, @now - INTERVAL 1 DAY
WHERE @kr_pos_count = 0;

INSERT INTO paper_positions (
    account_id, ticker, quantity, avg_price, total_cost, created_at, updated_at
)
SELECT @kr_account_id, '035420', 5.0000, 350000.0000, 1750000.0000, @now - INTERVAL 11 DAY, @now - INTERVAL 1 DAY
WHERE @kr_pos_count = 0;

INSERT INTO paper_orders (
    account_id, ticker, direction, quantity, price, amount, commission, created_at
)
SELECT @us_account_id, 'AAPL', 'BUY', 20.0000, 175.0000, 3500.0000, 3.5000, @now - INTERVAL 13 DAY
WHERE @us_order_count = 0;

INSERT INTO paper_orders (
    account_id, ticker, direction, quantity, price, amount, commission, created_at
)
SELECT @us_account_id, 'NVDA', 'BUY', 10.0000, 900.0000, 9000.0000, 9.0000, @now - INTERVAL 12 DAY
WHERE @us_order_count = 0;

INSERT INTO paper_orders (
    account_id, ticker, direction, quantity, price, amount, commission, created_at
)
SELECT @kr_account_id, '005930', 'BUY', 30.0000, 77000.0000, 2310000.0000, 2310.0000, @now - INTERVAL 12 DAY
WHERE @kr_order_count = 0;

INSERT INTO paper_orders (
    account_id, ticker, direction, quantity, price, amount, commission, created_at
)
SELECT @kr_account_id, '035420', 'BUY', 5.0000, 350000.0000, 1750000.0000, 1750.0000, @now - INTERVAL 11 DAY
WHERE @kr_order_count = 0;

-- -----------------------------
-- 12) Verification summary
-- -----------------------------
SELECT 'seed_complete' AS status, @target_user_id AS user_id, @seed_marker AS marker;

COMMIT;
