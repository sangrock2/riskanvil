-- V8: Add missing indexes for query optimization

-- api_usage_log: Most queries filter by (user_id, test_mode, created_at)
-- Current index (user_id, created_at) is inefficient for test_mode filtering
CREATE INDEX idx_usage_user_test_created
    ON api_usage_log(user_id, test_mode, created_at);

-- watchlist: List query uses (user_id, test_mode) with ORDER BY created_at
CREATE INDEX idx_watchlist_user_test_created
    ON watchlist(user_id, test_mode, created_at DESC);

-- market_cache: List query filters by (user_id, market, test_mode)
-- and orders by insights_updated_at
CREATE INDEX idx_cache_user_market_test_updated
    ON market_cache(user_id, market, test_mode, insights_updated_at DESC);
