-- Performance optimization indexes for new features
-- These indexes support efficient batch queries and prevent N+1 problems

-- Portfolio positions batch loading by multiple portfolio IDs
-- Supports: findByPortfolio_IdInOrderByCreatedAtDesc
CREATE INDEX idx_portfolio_positions_portfolio_created
    ON portfolio_positions(portfolio_id, created_at DESC);

-- Chat messages by conversation with timestamp for pagination
CREATE INDEX idx_chat_messages_conversation_created
    ON chat_messages(conversation_id, created_at ASC);

-- Chat conversations by user with last update time for recent list
CREATE INDEX idx_chat_conversations_user_updated
    ON chat_conversations(user_id, updated_at DESC);

-- Backup codes lookup by user and used status
CREATE INDEX idx_backup_codes_user_used
    ON backup_codes(user_id, used);

-- Dividends by portfolio position with ex_date for calendar
CREATE INDEX idx_dividends_position_exdate
    ON dividends(portfolio_position_id, ex_date DESC);

-- Screener presets public list ordered by creation
CREATE INDEX idx_screener_presets_public_created
    ON screener_presets(is_public, created_at DESC);

-- Price alerts by ticker and market for active monitoring
CREATE INDEX idx_price_alerts_ticker_enabled
    ON price_alerts(ticker, market, enabled);
