-- Align the generated PostgreSQL baseline with production constraints and query indexes.
-- Existing production databases are baselined at version 2, so this script only runs on
-- fresh PostgreSQL databases that start from V1__baseline_schema.sql.

ALTER TABLE analysis_runs
    DROP CONSTRAINT IF EXISTS fkgvfcg4aw75wblrl4akr70j2tg,
    ADD CONSTRAINT fk_analysis_runs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE backtest_runs
    DROP CONSTRAINT IF EXISTS fksfepb9la3evn5kk3ujhrwqx1e,
    ADD CONSTRAINT fk_backtest_runs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE backup_codes
    DROP CONSTRAINT IF EXISTS fk4uqupj7j6eldpff5ycw8vnpm6,
    ADD CONSTRAINT fk_backup_codes_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE chat_conversations
    DROP CONSTRAINT IF EXISTS fk40okueacd48n616c3r1nvjwpr,
    ADD CONSTRAINT fk_chat_conversations_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS fkqgkanrr90j46564w4ww63jcna,
    ADD CONSTRAINT fk_chat_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE;

ALTER TABLE dividends
    DROP CONSTRAINT IF EXISTS fknw4gk7p1lrmtyjtdrp3xcsu4l,
    ADD CONSTRAINT fk_dividends_position
        FOREIGN KEY (portfolio_position_id) REFERENCES portfolio_positions(id) ON DELETE CASCADE;

ALTER TABLE paper_accounts
    DROP CONSTRAINT IF EXISTS fkiy4nnccf233nmajgol19yjya2,
    ADD CONSTRAINT fk_paper_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE paper_orders
    DROP CONSTRAINT IF EXISTS fkio4m0igac9bj7xetg4eom2e17,
    ADD CONSTRAINT fk_paper_orders_account
        FOREIGN KEY (account_id) REFERENCES paper_accounts(id) ON DELETE CASCADE;

ALTER TABLE paper_positions
    DROP CONSTRAINT IF EXISTS fkqeyta09uvi4cufgaqxptkgsrk,
    ADD CONSTRAINT fk_paper_positions_account
        FOREIGN KEY (account_id) REFERENCES paper_accounts(id) ON DELETE CASCADE;

ALTER TABLE portfolio_positions
    DROP CONSTRAINT IF EXISTS fkjaiaieo24ssj1ulgnp7bowc46,
    ADD CONSTRAINT fk_portfolio_positions_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE;

ALTER TABLE portfolios
    DROP CONSTRAINT IF EXISTS fk9xt36kgm9cxsf79r2me0d9f6u,
    ADD CONSTRAINT fk_portfolios_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE price_alerts
    DROP CONSTRAINT IF EXISTS fkaqq01qkath6ujf2ikwey10q8l,
    ADD CONSTRAINT fk_price_alerts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE refresh_tokens
    DROP CONSTRAINT IF EXISTS fk1lih5y2npsf8u5o3vhdb9y0os,
    ADD CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE screener_presets
    DROP CONSTRAINT IF EXISTS fkgqvfv09rm6mab0slafm1yvckc,
    ADD CONSTRAINT fk_screener_presets_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE totp_secrets
    DROP CONSTRAINT IF EXISTS fk3y28un1iib4airw82n94j8jxk,
    ADD CONSTRAINT fk_totp_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_settings
    DROP CONSTRAINT IF EXISTS fk8v82nj88rmai0nyck19f873dw,
    ADD CONSTRAINT fk_user_settings_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE watchlist_item_tag
    DROP CONSTRAINT IF EXISTS fkkl6a14809jyr362efr0n52ct6,
    ADD CONSTRAINT fk_watchlist_item_tag_tag
        FOREIGN KEY (tag_id) REFERENCES watchlist_tag(id) ON DELETE CASCADE;

ALTER TABLE watchlist_item_tag
    DROP CONSTRAINT IF EXISTS fkq96mffjb7nrlollb3gffmdfto,
    ADD CONSTRAINT fk_watchlist_item_tag_item
        FOREIGN KEY (watchlist_item_id) REFERENCES watchlist(id) ON DELETE CASCADE;

ALTER TABLE watchlist_tag
    DROP CONSTRAINT IF EXISTS fk4l3v6lfj2ae1g6wt2shxf1ne5,
    ADD CONSTRAINT fk_watchlist_tag_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE portfolios
    ADD CONSTRAINT uk_portfolios_user_name UNIQUE (user_id, name);

ALTER TABLE portfolio_positions
    ADD CONSTRAINT uk_portfolio_positions UNIQUE (portfolio_id, ticker, market);

ALTER TABLE screener_presets
    ADD CONSTRAINT uk_screener_presets_user_name UNIQUE (user_id, name);

ALTER TABLE paper_accounts
    ADD CONSTRAINT uk_paper_accounts_user_market UNIQUE (user_id, market);

ALTER TABLE paper_positions
    ADD CONSTRAINT uk_paper_positions_account_ticker UNIQUE (account_id, ticker);

CREATE INDEX IF NOT EXISTS idx_analysis_ticker_market
    ON analysis_runs (ticker, market, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_backtest_strategy_created
    ON backtest_runs (strategy, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_backup_codes_user_used
    ON backup_codes (user_id, used);

CREATE INDEX IF NOT EXISTS idx_cache_user_market_test_updated
    ON market_cache (user_id, market, test_mode, insights_updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_user_updated
    ON chat_conversations (user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_created
    ON chat_messages (conversation_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_dividends_position_exdate
    ON dividends (portfolio_position_id, ex_date DESC);

CREATE INDEX IF NOT EXISTS idx_expires_at
    ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_paper_orders_account_created
    ON paper_orders (account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_portfolios_user_created
    ON portfolios (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_portfolio_positions_portfolio_created
    ON portfolio_positions (portfolio_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_portfolio_positions_ticker
    ON portfolio_positions (ticker, market);

CREATE INDEX IF NOT EXISTS idx_price_alerts_ticker_enabled
    ON price_alerts (ticker, market, enabled);

CREATE INDEX IF NOT EXISTS idx_price_alerts_user_enabled
    ON price_alerts (user_id, enabled, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_screener_presets_public_created
    ON screener_presets (is_public, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_usage_ticker
    ON api_usage_log (ticker, created_at DESC)
    WHERE ticker IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_usage_user_test_created
    ON api_usage_log (user_id, test_mode, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_id
    ON refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_watchlist_item_tag_both
    ON watchlist_item_tag (watchlist_item_id, tag_id);

CREATE INDEX IF NOT EXISTS idx_watchlist_user_test_created
    ON watchlist (user_id, test_mode, created_at DESC);
