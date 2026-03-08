-- Postgres hotfix for insights/report cache tables used by backend market APIs.
-- Run once on Render Postgres if these tables are missing.

CREATE TABLE IF NOT EXISTS market_cache (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    ticker VARCHAR(32) NOT NULL,
    market VARCHAR(8) NOT NULL,
    test_mode BOOLEAN NOT NULL DEFAULT FALSE,
    days INTEGER NOT NULL,
    news_limit INTEGER NOT NULL,
    insights_json TEXT,
    insights_updated_at TIMESTAMP,
    report_text TEXT,
    report_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_market_cache_key UNIQUE (user_id, ticker, market, test_mode, days, news_limit)
);

CREATE TABLE IF NOT EXISTS market_report_history (
    id BIGSERIAL PRIMARY KEY,
    cache_id BIGINT NOT NULL REFERENCES market_cache(id) ON DELETE CASCADE,
    report_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hist_cache_created
    ON market_report_history(cache_id, created_at);

CREATE INDEX IF NOT EXISTS idx_market_cache_user_market_test_updated
    ON market_cache(user_id, market, test_mode, insights_updated_at DESC);
