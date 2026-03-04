-- V11: Add database indexes for query optimization

-- MarketCache composite index for efficient lookups
-- Optimizes the query: findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit
CREATE INDEX IF NOT EXISTS idx_cache_user_ticker_market_test_days_news
    ON market_cache(user_id, ticker, market, test_mode, days, news_limit);

-- WatchlistItemTag composite index for bidirectional queries
-- Optimizes both watchlist->tags and tags->watchlist lookups
CREATE INDEX IF NOT EXISTS idx_watchlist_item_tag_both
    ON watchlist_item_tag(watchlist_item_id, tag_id);

-- AnalysisRun index for ticker/market filtering with recent-first sorting
-- Optimizes queries that filter by ticker and market and sort by creation date
CREATE INDEX IF NOT EXISTS idx_analysis_ticker_market
    ON analysis_runs(ticker, market, created_at DESC);

-- BacktestRun index for strategy-based queries with recent-first sorting
-- Optimizes queries that filter by strategy and sort by creation date
CREATE INDEX IF NOT EXISTS idx_backtest_strategy_created
    ON backtest_runs(strategy, created_at DESC);

-- ApiUsageLog indexes for usage analytics queries
-- Optimizes aggregation queries by user, test mode, and time range
CREATE INDEX IF NOT EXISTS idx_usage_user_test_created
    ON api_usage_log(user_id, test_mode, created_at DESC);

-- Optimize ticker-based usage queries
CREATE INDEX IF NOT EXISTS idx_usage_ticker
    ON api_usage_log(ticker, created_at DESC) WHERE ticker IS NOT NULL;
