-- Add tags and notes to watchlist

-- Create watchlist_tag table
CREATE TABLE watchlist_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7),
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_watchlist_tag UNIQUE (user_id, name),
    CONSTRAINT fk_watchlist_tag_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_watchlist_tag_user (user_id)
);

-- Create watchlist_item_tag junction table
CREATE TABLE watchlist_item_tag (
    watchlist_item_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (watchlist_item_id, tag_id),
    CONSTRAINT fk_watchlist_item_tag_item FOREIGN KEY (watchlist_item_id) REFERENCES watchlist(id) ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_item_tag_tag FOREIGN KEY (tag_id) REFERENCES watchlist_tag(id) ON DELETE CASCADE,
    INDEX idx_watchlist_item_tag_item (watchlist_item_id),
    INDEX idx_watchlist_item_tag_tag (tag_id)
);

-- Add notes column to watchlist table
ALTER TABLE watchlist ADD COLUMN notes VARCHAR(500) AFTER created_at;
