package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.dto.TagResponse;
import com.sw103302.backend.dto.WatchlistAddRequest;
import com.sw103302.backend.dto.WatchlistItemResponse;
import com.sw103302.backend.dto.WatchlistUpdateTagsRequest;
import com.sw103302.backend.entity.MarketCache;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.entity.WatchlistItem;
import com.sw103302.backend.entity.WatchlistTag;
import com.sw103302.backend.repository.MarketCacheRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.WatchlistRepository;
import com.sw103302.backend.repository.WatchlistTagRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing user watchlists
 *
 * <p>Provides CRUD operations for watchlist items with:
 * <ul>
 *   <li>Market data caching integration</li>
 *   <li>Tag management</li>
 *   <li>Notes support (max 500 characters)</li>
 *   <li>Test mode filtering</li>
 *   <li>N+1 query optimization with EntityGraph and batch loading</li>
 * </ul>
 *
 * @author Stock-AI Team
 * @version 1.1
 * @since 0.4.0
 */
@Service
public class WatchlistService {
    private final WatchlistRepository watchlistRepository;
    private final MarketCacheRepository marketCacheRepository;
    private final UserRepository userRepository;
    private final WatchlistTagRepository tagRepository;
    private final ObjectMapper om;

    // insights 기본 파라미터(네 인사이트 페이지 기준)
    private static final int DEFAULT_DAYS = 90;
    private static final int DEFAULT_NEWS_LIMIT = 20;

    public WatchlistService(WatchlistRepository watchlistRepository, MarketCacheRepository marketCacheRepository, UserRepository userRepository, WatchlistTagRepository tagRepository, ObjectMapper om) {
        this.watchlistRepository = watchlistRepository;
        this.marketCacheRepository = marketCacheRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.om = om;
    }

    /**
     * Retrieves all watchlist items for a user with market data and tags
     *
     * <p>Performance optimizations:
     * <ul>
     *   <li>Uses EntityGraph to eagerly fetch tags (prevents N+1 queries)</li>
     *   <li>Batch loads MarketCache data for all items grouped by market</li>
     *   <li>Supports mixed US/KR markets in single watchlist</li>
     * </ul>
     *
     * <p>Query complexity: O(3) instead of O(1+2N) where N is number of items
     *
     * @param test Test mode flag - when true, returns test data only
     * @return List of watchlist items with current prices, tags, and AI insights summary
     * @throws IllegalStateException if user is not authenticated or not found
     *
     * @see WatchlistItemResponse for response structure
     * @see MarketCache for cached market data details
     */
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> list(boolean test) {
        User user = currentUser();

        // 1. EntityGraph로 tags와 user를 eager fetch (N+1 방지)
        List<WatchlistItem> items = watchlistRepository.findByUserIdWithTags(user.getId(), test);

        if (items.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. MarketCache를 배치 조회 (N+1 방지)
        // Group by market to support mixed US/KR watchlist items
        Map<String, Set<String>> tickersByMarket = items.stream()
                .collect(Collectors.groupingBy(
                        WatchlistItem::getMarket,
                        Collectors.mapping(WatchlistItem::getTicker, Collectors.toSet())
                ));

        Map<String, MarketCache> cacheMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : tickersByMarket.entrySet()) {
            String market = entry.getKey();
            Set<String> tickers = entry.getValue();

            List<MarketCache> caches = marketCacheRepository.findByUser_IdAndTickerInAndMarketAndTestMode(
                    user.getId(),
                    tickers,
                    market,
                    test,
                    DEFAULT_DAYS,
                    DEFAULT_NEWS_LIMIT
            );

            for (MarketCache cache : caches) {
                String cacheMarket = cache.getMarket() == null || cache.getMarket().isBlank()
                        ? market
                        : cache.getMarket();
                cacheMap.putIfAbsent(symbolKey(cache.getTicker(), cacheMarket), cache);
            }
        }

        // 3. 조회된 데이터로 response 생성
        List<WatchlistItemResponse> out = new ArrayList<>();
        for (WatchlistItem it : items) {
            MarketCache cache = cacheMap.get(symbolKey(it.getTicker(), it.getMarket()));

            WatchlistItemResponse.Summary summary = null;
            if (cache != null && cache.getInsightsJson() != null && !cache.getInsightsJson().isBlank()) {
                summary = extractSummary(cache.getInsightsJson(), cache.getInsightsUpdatedAt());
            }

            // Tags are already loaded via EntityGraph (no N+1)
            List<TagResponse> tags = it.getTags().stream()
                    .map(tag -> new TagResponse(
                            tag.getId(),
                            tag.getName(),
                            tag.getColor(),
                            tag.getCreatedAt(),
                            null // Don't include item count in watchlist response
                    ))
                    .collect(Collectors.toList());

            out.add(new WatchlistItemResponse(
                    it.getId(),
                    it.getTicker(),
                    it.getMarket(),
                    it.isTestMode(),
                    it.getCreatedAt(),
                    it.getNotes(),
                    tags,
                    summary
            ));
        }

        return out;
    }

    /**
     * Adds a new stock to the user's watchlist
     *
     * @param req Request containing ticker and market information
     * @param test Test mode flag
     * @throws IllegalStateException if the ticker already exists in watchlist
     * @throws IllegalStateException if user is not authenticated
     */
    @Transactional
    public void add(WatchlistAddRequest req, boolean test) {
        User user = currentUser();

        String ticker = req.ticker().trim();
        String market = (req.market() == null ? "US" : req.market().trim());

        watchlistRepository.findByUser_IdAndTickerAndMarketAndTestMode(user.getId(), ticker, market, test)
                .ifPresent(x -> { throw new IllegalStateException("already exists"); });

        watchlistRepository.save(new WatchlistItem(user, ticker, market, test));
    }

    /**
     * Removes a stock from the user's watchlist
     *
     * @param ticker Stock ticker symbol
     * @param market Market type (US or KR)
     * @param test Test mode flag
     * @throws IllegalStateException if user is not authenticated
     */
    @Transactional
    public void remove(String ticker, String market, boolean test) {
        User user = currentUser();
        watchlistRepository.deleteByUser_IdAndTickerAndMarketAndTestMode(user.getId(), ticker.trim(), market.trim(), test);
    }

    /**
     * Updates tags assigned to a watchlist item
     *
     * <p>Replaces all existing tags with the new tag set.
     * Validates that all tags belong to the authenticated user.
     *
     * @param itemId Watchlist item ID
     * @param req Request containing new tag IDs
     * @throws IllegalArgumentException if item not found or user unauthorized
     * @throws IllegalArgumentException if any tag is not found or unauthorized
     */
    @Transactional
    public void updateTags(Long itemId, WatchlistUpdateTagsRequest req) {
        User user = currentUser();

        WatchlistItem item = watchlistRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist item not found"));

        if (!item.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Unauthorized");
        }

        // Clear existing tags
        item.clearTags();

        // Add new tags
        if (req.tagIds() != null) {
            for (Long tagId : req.tagIds()) {
                WatchlistTag tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));

                if (!tag.getUser().getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Unauthorized for tag: " + tagId);
                }

                item.addTag(tag);
            }
        }

        watchlistRepository.save(item);
    }

    /**
     * Updates notes for a watchlist item
     *
     * <p>Notes are limited to 500 characters (validated on frontend).
     *
     * @param itemId Watchlist item ID
     * @param notes New notes text (can be null or empty to clear notes)
     * @throws IllegalArgumentException if item not found or user unauthorized
     */
    @Transactional
    public void updateNotes(Long itemId, String notes) {
        User user = currentUser();

        WatchlistItem item = watchlistRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist item not found"));

        if (!item.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Unauthorized");
        }

        item.setNotes(notes);
        watchlistRepository.save(item);
    }

    /**
     * Extracts summary information from cached insights JSON
     *
     * @param insightsJson Raw insights JSON string from MarketCache
     * @param updatedAt Timestamp when insights were last updated
     * @return Summary containing action, score, price, and update timestamp
     */
    private WatchlistItemResponse.Summary extractSummary(String insightsJson, LocalDateTime updatedAt) {
        try {
            JsonNode root = om.readTree(insightsJson);

            JsonNode rec = root.path("recommendation");
            String action = rec.path("action").asText(null);
            Integer score = rec.hasNonNull("score") ? rec.get("score").asInt() : null;

            JsonNode quote = root.path("quote");
            Double price = quote.hasNonNull("price") ? quote.get("price").asDouble() : null;

            return new WatchlistItemResponse.Summary(action, score, price, updatedAt);
        } catch (Exception e) {
            return new WatchlistItemResponse.Summary(null, null, null, updatedAt);
        }
    }

    /**
     * Gets the currently authenticated user
     *
     * @return Current user entity
     * @throws IllegalStateException if user is not authenticated or not found
     */
    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalStateException("user not found"));
    }

    /**
     * 시장/티커 복합키를 생성한다.
     * 같은 심볼이라도 US/KR 데이터가 섞이지 않도록 키를 분리한다.
     */
    private String symbolKey(String ticker, String market) {
        String normalizedTicker = ticker == null ? "" : ticker.trim().toUpperCase();
        String normalizedMarket = market == null ? "" : market.trim().toUpperCase();
        return normalizedMarket + ":" + normalizedTicker;
    }
}
