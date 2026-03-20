\if :{?target_user_id}
\else
\echo 'target_user_id psql variable is required'
\quit 1
\endif

-- Demo seed for Stock-AI (PostgreSQL)
-- This script is idempotent for tagged seed rows and targets one existing user.
-- Example:
--   psql -h localhost -U postgres -d stock_ai -v target_user_id=1 -f scripts/sql/seed_demo_data.sql

BEGIN;

DO $seed$
DECLARE
    target_user_id bigint := :target_user_id;
    seed_marker text := 'demo-seed-v1';
    now_ts timestamp := CURRENT_TIMESTAMP;
    tag_growth bigint;
    tag_dividend bigint;
    tag_risk bigint;
    w_aapl bigint;
    w_nvda bigint;
    w_msft bigint;
    w_005930 bigint;
    cache_aapl bigint;
    pf_us bigint;
    pf_kr bigint;
    pp_aapl bigint;
    pp_msft bigint;
    pp_005930 bigint;
    conv_risk bigint;
    conv_reb bigint;
    us_account_id bigint;
    kr_account_id bigint;
    us_pos_count bigint;
    kr_pos_count bigint;
    us_order_count bigint;
    kr_order_count bigint;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE id = target_user_id) THEN
        RAISE EXCEPTION 'Target user % not found in users', target_user_id;
    END IF;

    INSERT INTO user_settings (
        user_id, totp_enabled, email_on_alerts, daily_summary_enabled,
        theme, language, default_market, created_at
    )
    SELECT target_user_id, false, true, false, 'dark', 'ko', 'US', now_ts
    WHERE NOT EXISTS (
        SELECT 1 FROM user_settings WHERE user_id = target_user_id
    );

    DELETE FROM watchlist_item_tag wit
    USING watchlist w
    WHERE w.id = wit.watchlist_item_id
      AND w.user_id = target_user_id
      AND COALESCE(w.notes, '') LIKE '%' || seed_marker || '%';

    DELETE FROM watchlist
    WHERE user_id = target_user_id
      AND COALESCE(notes, '') LIKE '%' || seed_marker || '%';

    DELETE FROM watchlist_tag
    WHERE user_id = target_user_id
      AND name LIKE 'DEMO-%';

    DELETE FROM market_report_history
    WHERE report_text LIKE '%' || seed_marker || '%';

    DELETE FROM market_cache
    WHERE user_id = target_user_id
      AND (
          COALESCE(insights_json, '') LIKE '%' || seed_marker || '%'
          OR COALESCE(report_text, '') LIKE '%' || seed_marker || '%'
      );

    DELETE FROM analysis_runs
    WHERE user_id = target_user_id
      AND request_json LIKE '%' || seed_marker || '%';

    DELETE FROM backtest_runs
    WHERE user_id = target_user_id
      AND request_json LIKE '%' || seed_marker || '%';

    DELETE FROM dividends d
    USING portfolio_positions pp, portfolios p
    WHERE p.id = pp.portfolio_id
      AND d.portfolio_position_id = pp.id
      AND p.user_id = target_user_id
      AND p.name LIKE 'DEMO %';

    DELETE FROM portfolio_positions pp
    USING portfolios p
    WHERE p.id = pp.portfolio_id
      AND p.user_id = target_user_id
      AND p.name LIKE 'DEMO %';

    DELETE FROM portfolios
    WHERE user_id = target_user_id
      AND name LIKE 'DEMO %';

    DELETE FROM screener_presets
    WHERE user_id = target_user_id
      AND name LIKE 'DEMO %';

    DELETE FROM chat_messages cm
    USING chat_conversations cc
    WHERE cc.id = cm.conversation_id
      AND cc.user_id = target_user_id
      AND cc.title LIKE '[DEMO]%';

    DELETE FROM chat_conversations
    WHERE user_id = target_user_id
      AND title LIKE '[DEMO]%';

    DELETE FROM price_alerts
    WHERE user_id = target_user_id
      AND (
          (ticker = 'AAPL' AND market = 'US' AND alert_type = 'ABOVE' AND threshold = 250.0000)
          OR (ticker = 'NVDA' AND market = 'US' AND alert_type = 'BELOW' AND threshold = 900.0000)
          OR (ticker = '005930' AND market = 'KR' AND alert_type = 'ABOVE' AND threshold = 78000.0000)
      );

    DELETE FROM api_usage_log
    WHERE user_id = target_user_id
      AND request_id LIKE 'SEED-DEMO-%';

    INSERT INTO watchlist_tag (user_id, name, color, created_at)
    VALUES
        (target_user_id, 'DEMO-Growth', '#3b82f6', now_ts),
        (target_user_id, 'DEMO-Dividend', '#10b981', now_ts),
        (target_user_id, 'DEMO-RiskHedge', '#f59e0b', now_ts)
    ON CONFLICT (user_id, name) DO NOTHING;

    INSERT INTO watchlist (user_id, ticker, market, test_mode, created_at, notes)
    VALUES
        (target_user_id, 'AAPL', 'US', false, now_ts - INTERVAL '10 days', '[' || seed_marker || '] Core US growth stock'),
        (target_user_id, 'NVDA', 'US', false, now_ts - INTERVAL '9 days', '[' || seed_marker || '] AI momentum watch'),
        (target_user_id, 'MSFT', 'US', false, now_ts - INTERVAL '8 days', '[' || seed_marker || '] Stable large-cap with cloud exposure'),
        (target_user_id, '005930', 'KR', false, now_ts - INTERVAL '7 days', '[' || seed_marker || '] KR semiconductor anchor'),
        (target_user_id, '035420', 'KR', true, now_ts - INTERVAL '6 days', '[' || seed_marker || '] Test mode sample item')
    ON CONFLICT (user_id, ticker, market, test_mode) DO NOTHING;

    SELECT id INTO tag_growth
    FROM watchlist_tag
    WHERE user_id = target_user_id AND name = 'DEMO-Growth'
    LIMIT 1;

    SELECT id INTO tag_dividend
    FROM watchlist_tag
    WHERE user_id = target_user_id AND name = 'DEMO-Dividend'
    LIMIT 1;

    SELECT id INTO tag_risk
    FROM watchlist_tag
    WHERE user_id = target_user_id AND name = 'DEMO-RiskHedge'
    LIMIT 1;

    SELECT id INTO w_aapl
    FROM watchlist
    WHERE user_id = target_user_id AND ticker = 'AAPL' AND market = 'US' AND test_mode = false
    LIMIT 1;

    SELECT id INTO w_nvda
    FROM watchlist
    WHERE user_id = target_user_id AND ticker = 'NVDA' AND market = 'US' AND test_mode = false
    LIMIT 1;

    SELECT id INTO w_msft
    FROM watchlist
    WHERE user_id = target_user_id AND ticker = 'MSFT' AND market = 'US' AND test_mode = false
    LIMIT 1;

    SELECT id INTO w_005930
    FROM watchlist
    WHERE user_id = target_user_id AND ticker = '005930' AND market = 'KR' AND test_mode = false
    LIMIT 1;

    INSERT INTO watchlist_item_tag (watchlist_item_id, tag_id)
    VALUES
        (w_aapl, tag_growth),
        (w_nvda, tag_growth),
        (w_msft, tag_dividend),
        (w_005930, tag_dividend),
        (w_005930, tag_risk)
    ON CONFLICT DO NOTHING;

    INSERT INTO market_cache (
        user_id, ticker, market, test_mode, days, news_limit,
        insights_json, insights_updated_at, report_text, report_updated_at, created_at
    )
    VALUES
        (
            target_user_id, 'AAPL', 'US', false, 252, 5,
            jsonb_build_object(
                'seed', seed_marker,
                'ticker', 'AAPL',
                'decision', jsonb_build_object('action', 'BUY', 'confidence', 0.81),
                'score', 82
            )::text,
            now_ts - INTERVAL '2 days',
            '[' || seed_marker || '] AAPL report summary. Strong cash flow, resilient margins, and moderate valuation risk.',
            now_ts - INTERVAL '2 days',
            now_ts - INTERVAL '12 days'
        ),
        (
            target_user_id, 'NVDA', 'US', false, 252, 5,
            jsonb_build_object(
                'seed', seed_marker,
                'ticker', 'NVDA',
                'decision', jsonb_build_object('action', 'HOLD', 'confidence', 0.67),
                'score', 71
            )::text,
            now_ts - INTERVAL '1 day',
            '[' || seed_marker || '] NVDA report summary. Strong growth but volatility and valuation heat remain elevated.',
            now_ts - INTERVAL '1 day',
            now_ts - INTERVAL '11 days'
        ),
        (
            target_user_id, '005930', 'KR', false, 252, 5,
            jsonb_build_object(
                'seed', seed_marker,
                'ticker', '005930',
                'decision', jsonb_build_object('action', 'BUY', 'confidence', 0.73),
                'score', 76
            )::text,
            now_ts - INTERVAL '1 day',
            '[' || seed_marker || '] 005930 report summary. Earnings cycle recovery and memory demand improvement trend.',
            now_ts - INTERVAL '1 day',
            now_ts - INTERVAL '11 days'
        )
    ON CONFLICT (user_id, ticker, market, test_mode, days, news_limit)
    DO UPDATE SET
        insights_json = EXCLUDED.insights_json,
        insights_updated_at = EXCLUDED.insights_updated_at,
        report_text = EXCLUDED.report_text,
        report_updated_at = EXCLUDED.report_updated_at,
        created_at = EXCLUDED.created_at;

    SELECT id INTO cache_aapl
    FROM market_cache
    WHERE user_id = target_user_id
      AND ticker = 'AAPL'
      AND market = 'US'
      AND test_mode = false
      AND days = 252
      AND news_limit = 5
    LIMIT 1;

    INSERT INTO market_report_history (cache_id, report_text, created_at)
    VALUES
        (cache_aapl, '[' || seed_marker || '] AAPL report v1 baseline.', now_ts - INTERVAL '4 days'),
        (cache_aapl, '[' || seed_marker || '] AAPL report v2 with updated risk commentary.', now_ts - INTERVAL '2 days');

    INSERT INTO analysis_runs (
        user_id, ticker, market, request_json, response_json,
        action, confidence, created_at
    )
    VALUES
        (
            target_user_id, 'AAPL', 'US',
            jsonb_build_object('seed', seed_marker, 'ticker', 'AAPL', 'market', 'US', 'horizonDays', 252, 'riskProfile', 'balanced')::text,
            jsonb_build_object('seed', seed_marker, 'decision', jsonb_build_object('action', 'BUY', 'confidence', 0.81), 'score', 82)::text,
            'BUY', 0.81, now_ts - INTERVAL '9 days'
        ),
        (
            target_user_id, 'NVDA', 'US',
            jsonb_build_object('seed', seed_marker, 'ticker', 'NVDA', 'market', 'US', 'horizonDays', 252, 'riskProfile', 'aggressive')::text,
            jsonb_build_object('seed', seed_marker, 'decision', jsonb_build_object('action', 'HOLD', 'confidence', 0.67), 'score', 71)::text,
            'HOLD', 0.67, now_ts - INTERVAL '7 days'
        ),
        (
            target_user_id, '005930', 'KR',
            jsonb_build_object('seed', seed_marker, 'ticker', '005930', 'market', 'KR', 'horizonDays', 252, 'riskProfile', 'balanced')::text,
            jsonb_build_object('seed', seed_marker, 'decision', jsonb_build_object('action', 'BUY', 'confidence', 0.73), 'score', 76)::text,
            'BUY', 0.73, now_ts - INTERVAL '6 days'
        );

    INSERT INTO backtest_runs (
        user_id, ticker, market, strategy, request_json, response_json,
        total_return, max_drawdown, sharpe, cagr, created_at
    )
    VALUES
        (
            target_user_id, 'AAPL', 'US', 'SMA_CROSS',
            jsonb_build_object('seed', seed_marker, 'ticker', 'AAPL', 'market', 'US', 'strategy', 'SMA_CROSS', 'initialCapital', 100000)::text,
            jsonb_build_object('seed', seed_marker, 'summary', jsonb_build_object('totalReturn', 34.2, 'maxDrawdown', -12.8, 'sharpe', 1.21, 'cagr', 11.1))::text,
            34.2, -12.8, 1.21, 11.1, now_ts - INTERVAL '8 days'
        ),
        (
            target_user_id, 'NVDA', 'US', 'MOMENTUM',
            jsonb_build_object('seed', seed_marker, 'ticker', 'NVDA', 'market', 'US', 'strategy', 'MOMENTUM', 'initialCapital', 100000)::text,
            jsonb_build_object('seed', seed_marker, 'summary', jsonb_build_object('totalReturn', 62.5, 'maxDrawdown', -24.4, 'sharpe', 1.07, 'cagr', 19.4))::text,
            62.5, -24.4, 1.07, 19.4, now_ts - INTERVAL '5 days'
        ),
        (
            target_user_id, '005930', 'KR', 'MEAN_REVERSION',
            jsonb_build_object('seed', seed_marker, 'ticker', '005930', 'market', 'KR', 'strategy', 'MEAN_REVERSION', 'initialCapital', 100000000)::text,
            jsonb_build_object('seed', seed_marker, 'summary', jsonb_build_object('totalReturn', 18.4, 'maxDrawdown', -10.2, 'sharpe', 0.93, 'cagr', 7.9))::text,
            18.4, -10.2, 0.93, 7.9, now_ts - INTERVAL '4 days'
        );

    INSERT INTO portfolios (
        user_id, name, description, target_return, risk_profile, created_at
    )
    VALUES
        (
            target_user_id, 'DEMO Core US',
            '[' || seed_marker || '] US growth and quality mix for dashboard validation',
            14.00, 'moderate', now_ts - INTERVAL '12 days'
        ),
        (
            target_user_id, 'DEMO Korea Income',
            '[' || seed_marker || '] KR dividend and defensive mix for calendar validation',
            10.00, 'conservative', now_ts - INTERVAL '12 days'
        )
    ON CONFLICT (user_id, name)
    DO UPDATE SET
        description = EXCLUDED.description,
        target_return = EXCLUDED.target_return,
        risk_profile = EXCLUDED.risk_profile,
        created_at = EXCLUDED.created_at;

    SELECT id INTO pf_us
    FROM portfolios
    WHERE user_id = target_user_id AND name = 'DEMO Core US'
    LIMIT 1;

    SELECT id INTO pf_kr
    FROM portfolios
    WHERE user_id = target_user_id AND name = 'DEMO Korea Income'
    LIMIT 1;

    INSERT INTO portfolio_positions (
        portfolio_id, ticker, market, quantity, entry_price, entry_date, notes, created_at
    )
    VALUES
        (pf_us, 'AAPL', 'US', 18.0000, 172.5000, CURRENT_DATE - 180, '[' || seed_marker || '] Demo position', now_ts - INTERVAL '11 days'),
        (pf_us, 'MSFT', 'US', 12.0000, 340.0000, CURRENT_DATE - 160, '[' || seed_marker || '] Demo position', now_ts - INTERVAL '11 days'),
        (pf_us, 'NVDA', 'US', 8.0000, 780.0000, CURRENT_DATE - 120, '[' || seed_marker || '] Demo position', now_ts - INTERVAL '11 days'),
        (pf_kr, '005930', 'KR', 35.0000, 70200.0000, CURRENT_DATE - 190, '[' || seed_marker || '] Demo position', now_ts - INTERVAL '11 days'),
        (pf_kr, '035420', 'KR', 20.0000, 198000.0000, CURRENT_DATE - 150, '[' || seed_marker || '] Demo position', now_ts - INTERVAL '11 days')
    ON CONFLICT (portfolio_id, ticker, market)
    DO UPDATE SET
        quantity = EXCLUDED.quantity,
        entry_price = EXCLUDED.entry_price,
        entry_date = EXCLUDED.entry_date,
        notes = EXCLUDED.notes,
        created_at = EXCLUDED.created_at;

    SELECT id INTO pp_aapl
    FROM portfolio_positions
    WHERE portfolio_id = pf_us AND ticker = 'AAPL' AND market = 'US'
    LIMIT 1;

    SELECT id INTO pp_msft
    FROM portfolio_positions
    WHERE portfolio_id = pf_us AND ticker = 'MSFT' AND market = 'US'
    LIMIT 1;

    SELECT id INTO pp_005930
    FROM portfolio_positions
    WHERE portfolio_id = pf_kr AND ticker = '005930' AND market = 'KR'
    LIMIT 1;

    INSERT INTO dividends (
        portfolio_position_id, amount, currency, ex_date, payment_date, record_date, declared_date, frequency, created_at
    )
    VALUES
        (pp_aapl, 0.2400, 'USD', CURRENT_DATE - 30, CURRENT_DATE - 15, CURRENT_DATE - 29, CURRENT_DATE - 45, 'quarterly', now_ts - INTERVAL '10 days'),
        (pp_msft, 0.7500, 'USD', CURRENT_DATE - 40, CURRENT_DATE - 20, CURRENT_DATE - 39, CURRENT_DATE - 60, 'quarterly', now_ts - INTERVAL '10 days'),
        (pp_005930, 361.0000, 'KRW', CURRENT_DATE - 35, CURRENT_DATE - 14, CURRENT_DATE - 34, CURRENT_DATE - 50, 'quarterly', now_ts - INTERVAL '10 days');

    INSERT INTO screener_presets (
        user_id, name, description, filters_json, is_public, created_at
    )
    VALUES
        (
            target_user_id, 'DEMO US Growth Quality',
            '[' || seed_marker || '] Growth + quality screen',
            jsonb_build_object(
                'market', 'US',
                'peMin', 10,
                'peMax', 45,
                'roeMin', 12,
                'revenueYoYMin', 8,
                'marketCapMin', 10000000000
            )::text,
            false, now_ts - INTERVAL '9 days'
        ),
        (
            target_user_id, 'DEMO KR Dividend Stability',
            '[' || seed_marker || '] KR dividend stability screen',
            jsonb_build_object(
                'market', 'KR',
                'dividendYieldMin', 1.5,
                'debtRatioMax', 0.65,
                'operatingMarginMin', 7
            )::text,
            false, now_ts - INTERVAL '9 days'
        )
    ON CONFLICT (user_id, name)
    DO UPDATE SET
        description = EXCLUDED.description,
        filters_json = EXCLUDED.filters_json,
        is_public = EXCLUDED.is_public,
        created_at = EXCLUDED.created_at;

    INSERT INTO chat_conversations (
        user_id, title, model, context_json, created_at, updated_at
    )
    VALUES
        (
            target_user_id,
            '[DEMO] Earnings week risk check',
            'gpt-4o-mini',
            jsonb_build_object('seed', seed_marker, 'topic', 'risk')::text,
            now_ts - INTERVAL '3 days',
            now_ts - INTERVAL '2 days'
        ),
        (
            target_user_id,
            '[DEMO] Rebalance idea discussion',
            'gpt-4o-mini',
            jsonb_build_object('seed', seed_marker, 'topic', 'rebalance')::text,
            now_ts - INTERVAL '2 days',
            now_ts - INTERVAL '1 day'
        );

    SELECT id INTO conv_risk
    FROM chat_conversations
    WHERE user_id = target_user_id AND title = '[DEMO] Earnings week risk check'
    LIMIT 1;

    SELECT id INTO conv_reb
    FROM chat_conversations
    WHERE user_id = target_user_id AND title = '[DEMO] Rebalance idea discussion'
    LIMIT 1;

    INSERT INTO chat_messages (
        conversation_id, role, content, model, tokens_in, tokens_out, created_at
    )
    VALUES
        (conv_risk, 'user', 'How should I size positions before earnings this week?', 'gpt-4o-mini', 58, 0, now_ts - INTERVAL '3 days' + INTERVAL '1 hour'),
        (conv_risk, 'assistant', 'Reduce single-name concentration and tighten stop-loss levels around event windows.', 'gpt-4o-mini', 58, 112, now_ts - INTERVAL '3 days' + INTERVAL '1 hour' + INTERVAL '30 seconds'),
        (conv_reb, 'user', 'Can you suggest a simple monthly rebalance rule?', 'gpt-4o-mini', 41, 0, now_ts - INTERVAL '2 days' + INTERVAL '2 hours'),
        (conv_reb, 'assistant', 'Use threshold rebalance: if any holding deviates by 5%p from target weight, rebalance monthly.', 'gpt-4o-mini', 41, 96, now_ts - INTERVAL '2 days' + INTERVAL '2 hours' + INTERVAL '30 seconds');

    INSERT INTO price_alerts (
        user_id, ticker, market, alert_type, threshold, enabled, triggered_at, notification_sent, created_at
    )
    VALUES
        (target_user_id, 'AAPL', 'US', 'ABOVE', 250.0000, true, NULL, false, now_ts - INTERVAL '6 days'),
        (target_user_id, 'NVDA', 'US', 'BELOW', 900.0000, true, NULL, false, now_ts - INTERVAL '6 days'),
        (target_user_id, '005930', 'KR', 'ABOVE', 78000.0000, true, NULL, false, now_ts - INTERVAL '6 days');

    INSERT INTO api_usage_log (
        user_id, endpoint, ticker, market, test_mode, days, news_limit,
        cached, refresh, web, alpha_calls, openai_calls, openai_model,
        openai_tokens_in, openai_tokens_out, error_text, created_at,
        request_id, http_status, duration_ms
    )
    VALUES
        (target_user_id, 'INSIGHTS', 'AAPL', 'US', false, 252, 5, false, false, false, 1, 0, NULL, NULL, NULL, NULL, now_ts - INTERVAL '7 days', 'SEED-DEMO-001', 200, 780),
        (target_user_id, 'REPORT', 'AAPL', 'US', false, 252, 5, false, true, true, 1, 1, 'gpt-4o-mini', 1240, 460, NULL, now_ts - INTERVAL '7 days' + INTERVAL '20 minutes', 'SEED-DEMO-002', 200, 1820),
        (target_user_id, 'INSIGHTS', 'NVDA', 'US', false, 252, 5, true, false, false, 0, 0, NULL, NULL, NULL, NULL, now_ts - INTERVAL '6 days', 'SEED-DEMO-003', 200, 140),
        (target_user_id, 'REPORT', 'NVDA', 'US', false, 252, 5, false, false, true, 1, 1, 'gpt-4o-mini', 1380, 510, NULL, now_ts - INTERVAL '6 days' + INTERVAL '15 minutes', 'SEED-DEMO-004', 200, 1940),
        (target_user_id, 'INSIGHTS', '005930', 'KR', false, 252, 5, false, false, false, 1, 0, NULL, NULL, NULL, NULL, now_ts - INTERVAL '5 days', 'SEED-DEMO-005', 200, 690),
        (target_user_id, 'REPORT', '005930', 'KR', false, 252, 5, false, false, true, 1, 1, 'gpt-4o-mini', 1100, 420, NULL, now_ts - INTERVAL '5 days' + INTERVAL '20 minutes', 'SEED-DEMO-006', 200, 1710),
        (target_user_id, 'INSIGHTS', 'MSFT', 'US', false, 252, 5, true, false, false, 0, 0, NULL, NULL, NULL, NULL, now_ts - INTERVAL '4 days', 'SEED-DEMO-007', 200, 130),
        (target_user_id, 'REPORT', 'MSFT', 'US', false, 252, 5, false, true, false, 1, 0, NULL, NULL, NULL, 'ai timeout during optional web step', now_ts - INTERVAL '4 days' + INTERVAL '35 minutes', 'SEED-DEMO-008', 504, 30000),
        (target_user_id, 'VALUATION', 'AAPL', 'US', false, 252, 5, true, false, false, 0, 0, NULL, NULL, NULL, NULL, now_ts - INTERVAL '3 days', 'SEED-DEMO-009', 200, 95),
        (target_user_id, 'INSIGHTS', '035420', 'KR', true, 252, 3, false, false, false, 1, 0, NULL, NULL, NULL, NULL, now_ts - INTERVAL '2 days', 'SEED-DEMO-010', 200, 760);

    INSERT INTO paper_accounts (
        user_id, market, balance, initial_balance, currency, created_at, updated_at
    )
    VALUES
        (target_user_id, 'US', 100000.0000, 100000.0000, 'USD', now_ts - INTERVAL '14 days', now_ts - INTERVAL '1 day'),
        (target_user_id, 'KR', 100000000.0000, 100000000.0000, 'KRW', now_ts - INTERVAL '14 days', now_ts - INTERVAL '1 day')
    ON CONFLICT (user_id, market) DO UPDATE SET
        balance = EXCLUDED.balance,
        initial_balance = EXCLUDED.initial_balance,
        currency = EXCLUDED.currency,
        created_at = EXCLUDED.created_at,
        updated_at = EXCLUDED.updated_at;

    SELECT id INTO us_account_id
    FROM paper_accounts
    WHERE user_id = target_user_id AND market = 'US'
    LIMIT 1;

    SELECT id INTO kr_account_id
    FROM paper_accounts
    WHERE user_id = target_user_id AND market = 'KR'
    LIMIT 1;

    SELECT COUNT(*) INTO us_pos_count
    FROM paper_positions
    WHERE account_id = us_account_id;

    SELECT COUNT(*) INTO kr_pos_count
    FROM paper_positions
    WHERE account_id = kr_account_id;

    SELECT COUNT(*) INTO us_order_count
    FROM paper_orders
    WHERE account_id = us_account_id;

    SELECT COUNT(*) INTO kr_order_count
    FROM paper_orders
    WHERE account_id = kr_account_id;

    IF us_pos_count = 0 THEN
        INSERT INTO paper_positions (
            account_id, ticker, quantity, avg_price, total_cost, created_at, updated_at
        )
        VALUES
            (us_account_id, 'AAPL', 20.0000, 175.0000, 3500.0000, now_ts - INTERVAL '13 days', now_ts - INTERVAL '1 day'),
            (us_account_id, 'NVDA', 10.0000, 900.0000, 9000.0000, now_ts - INTERVAL '12 days', now_ts - INTERVAL '1 day');
    ELSE
        RAISE NOTICE 'paper_positions_skip account_id=% existing_count=%', us_account_id, us_pos_count;
    END IF;

    IF kr_pos_count = 0 THEN
        INSERT INTO paper_positions (
            account_id, ticker, quantity, avg_price, total_cost, created_at, updated_at
        )
        VALUES
            (kr_account_id, '005930', 30.0000, 77000.0000, 2310000.0000, now_ts - INTERVAL '12 days', now_ts - INTERVAL '1 day'),
            (kr_account_id, '035420', 5.0000, 350000.0000, 1750000.0000, now_ts - INTERVAL '11 days', now_ts - INTERVAL '1 day');
    ELSE
        RAISE NOTICE 'paper_positions_skip account_id=% existing_count=%', kr_account_id, kr_pos_count;
    END IF;

    IF us_order_count = 0 THEN
        INSERT INTO paper_orders (
            account_id, ticker, direction, quantity, price, amount, commission, created_at
        )
        VALUES
            (us_account_id, 'AAPL', 'BUY', 20.0000, 175.0000, 3500.0000, 3.5000, now_ts - INTERVAL '13 days'),
            (us_account_id, 'NVDA', 'BUY', 10.0000, 900.0000, 9000.0000, 9.0000, now_ts - INTERVAL '12 days');
    ELSE
        RAISE NOTICE 'paper_orders_skip account_id=% existing_count=%', us_account_id, us_order_count;
    END IF;

    IF kr_order_count = 0 THEN
        INSERT INTO paper_orders (
            account_id, ticker, direction, quantity, price, amount, commission, created_at
        )
        VALUES
            (kr_account_id, '005930', 'BUY', 30.0000, 77000.0000, 2310000.0000, 2310.0000, now_ts - INTERVAL '12 days'),
            (kr_account_id, '035420', 'BUY', 5.0000, 350000.0000, 1750000.0000, 1750.0000, now_ts - INTERVAL '11 days');
    ELSE
        RAISE NOTICE 'paper_orders_skip account_id=% existing_count=%', kr_account_id, kr_order_count;
    END IF;

    RAISE NOTICE 'seed_complete user_id=% marker=%', target_user_id, seed_marker;
END
$seed$;

SELECT 'seed_complete' AS status, :target_user_id::bigint AS user_id, 'demo-seed-v1' AS marker;

COMMIT;
