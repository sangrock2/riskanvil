package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.dto.WatchlistAddRequest;
import com.sw103302.backend.dto.WatchlistItemResponse;
import com.sw103302.backend.entity.MarketCache;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.entity.WatchlistItem;
import com.sw103302.backend.repository.MarketCacheRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.WatchlistRepository;
import com.sw103302.backend.repository.WatchlistTagRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private MarketCacheRepository marketCacheRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WatchlistTagRepository tagRepository;

    private ObjectMapper objectMapper;
    private WatchlistService watchlistService;
    private MockedStatic<SecurityUtil> securityUtilMock;

    private User testUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        watchlistService = new WatchlistService(watchlistRepository, marketCacheRepository, userRepository, tagRepository, objectMapper);
        securityUtilMock = mockStatic(SecurityUtil.class);

        testUser = new User("user@example.com", "hash", "ROLE_USER");
        ReflectionTestUtils.setField(testUser, "id", 1L);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void list_shouldReturnUserWatchlist() {
        // Given
        WatchlistItem item1 = new WatchlistItem(testUser, "AAPL", "US", false);
        ReflectionTestUtils.setField(item1, "id", 1L);
        ReflectionTestUtils.setField(item1, "createdAt", LocalDateTime.now());

        WatchlistItem item2 = new WatchlistItem(testUser, "GOOGL", "US", false);
        ReflectionTestUtils.setField(item2, "id", 2L);
        ReflectionTestUtils.setField(item2, "createdAt", LocalDateTime.now());

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUserIdWithTags(1L, false))
                .thenReturn(List.of(item1, item2));
        when(marketCacheRepository.findByUser_IdAndTickerInAndMarketAndTestMode(
                anyLong(), anySet(), anyString(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());

        // When
        List<WatchlistItemResponse> result = watchlistService.list(false);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).ticker()).isEqualTo("AAPL");
        assertThat(result.get(1).ticker()).isEqualTo("GOOGL");
    }

    @Test
    void list_withCachedInsights_shouldIncludeSummary() {
        // Given
        WatchlistItem item = new WatchlistItem(testUser, "AAPL", "US", false);
        ReflectionTestUtils.setField(item, "id", 1L);
        ReflectionTestUtils.setField(item, "createdAt", LocalDateTime.now());

        MarketCache cache = mock(MarketCache.class);
        when(cache.getTicker()).thenReturn("AAPL");
        when(cache.getInsightsJson()).thenReturn("""
            {
              "recommendation": {"action": "BUY", "score": 85},
              "quote": {"price": 150.0}
            }
            """);
        when(cache.getInsightsUpdatedAt()).thenReturn(LocalDateTime.now());

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUserIdWithTags(1L, false))
                .thenReturn(List.of(item));
        when(marketCacheRepository.findByUser_IdAndTickerInAndMarketAndTestMode(
                eq(1L), anySet(), eq("US"), eq(false), eq(90), eq(20)))
                .thenReturn(List.of(cache));

        // When
        List<WatchlistItemResponse> result = watchlistService.list(false);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).summary()).isNotNull();
        assertThat(result.get(0).summary().action()).isEqualTo("BUY");
        assertThat(result.get(0).summary().score()).isEqualTo(85);
        assertThat(result.get(0).summary().price()).isEqualTo(150.0);
    }

    @Test
    void list_withUnauthenticatedUser_shouldThrowException() {
        // Given
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(null);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenThrow(new IllegalStateException("User is not authenticated"));

        // When & Then
        assertThatThrownBy(() -> watchlistService.list(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void add_shouldAddNewItem() {
        // Given
        WatchlistAddRequest request = new WatchlistAddRequest("AAPL", "US");

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUser_IdAndTickerAndMarketAndTestMode(1L, "AAPL", "US", false))
                .thenReturn(Optional.empty());

        // When
        watchlistService.add(request, false);

        // Then
        ArgumentCaptor<WatchlistItem> itemCaptor = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(watchlistRepository).save(itemCaptor.capture());
        WatchlistItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getTicker()).isEqualTo("AAPL");
        assertThat(savedItem.getMarket()).isEqualTo("US");
        assertThat(savedItem.isTestMode()).isFalse();
    }

    @Test
    void add_withNullMarket_shouldDefaultToUS() {
        // Given
        WatchlistAddRequest request = new WatchlistAddRequest("AAPL", null);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUser_IdAndTickerAndMarketAndTestMode(anyLong(), anyString(), anyString(), anyBoolean()))
                .thenReturn(Optional.empty());

        // When
        watchlistService.add(request, false);

        // Then
        ArgumentCaptor<WatchlistItem> itemCaptor = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(watchlistRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getMarket()).isEqualTo("US");
    }

    @Test
    void add_withExistingItem_shouldThrowException() {
        // Given
        WatchlistAddRequest request = new WatchlistAddRequest("AAPL", "US");
        WatchlistItem existingItem = new WatchlistItem(testUser, "AAPL", "US", false);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUser_IdAndTickerAndMarketAndTestMode(1L, "AAPL", "US", false))
                .thenReturn(Optional.of(existingItem));

        // When & Then
        assertThatThrownBy(() -> watchlistService.add(request, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("already exists");

        verify(watchlistRepository, never()).save(any());
    }

    @Test
    void add_withUnauthenticatedUser_shouldThrowException() {
        // Given
        WatchlistAddRequest request = new WatchlistAddRequest("AAPL", "US");
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(null);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenThrow(new IllegalStateException("User is not authenticated"));

        // When & Then
        assertThatThrownBy(() -> watchlistService.add(request, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void remove_shouldDeleteItem() {
        // Given
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        // When
        watchlistService.remove("AAPL", "US", false);

        // Then
        verify(watchlistRepository).deleteByUser_IdAndTickerAndMarketAndTestMode(1L, "AAPL", "US", false);
    }

    @Test
    void remove_shouldTrimTickerAndMarket() {
        // Given
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        // When
        watchlistService.remove("  AAPL  ", "  US  ", false);

        // Then
        verify(watchlistRepository).deleteByUser_IdAndTickerAndMarketAndTestMode(1L, "AAPL", "US", false);
    }

    @Test
    void remove_withUnauthenticatedUser_shouldThrowException() {
        // Given
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(null);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenThrow(new IllegalStateException("User is not authenticated"));

        // When & Then
        assertThatThrownBy(() -> watchlistService.remove("AAPL", "US", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void list_withInvalidInsightsJson_shouldReturnNullSummaryFields() {
        // Given
        WatchlistItem item = new WatchlistItem(testUser, "AAPL", "US", false);
        ReflectionTestUtils.setField(item, "id", 1L);
        ReflectionTestUtils.setField(item, "createdAt", LocalDateTime.now());

        MarketCache cache = mock(MarketCache.class);
        when(cache.getTicker()).thenReturn("AAPL");
        when(cache.getInsightsJson()).thenReturn("invalid json {{{");
        when(cache.getInsightsUpdatedAt()).thenReturn(LocalDateTime.now());

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUserIdWithTags(1L, false))
                .thenReturn(List.of(item));
        when(marketCacheRepository.findByUser_IdAndTickerInAndMarketAndTestMode(
                eq(1L), anySet(), eq("US"), eq(false), eq(90), eq(20)))
                .thenReturn(List.of(cache));

        // When
        List<WatchlistItemResponse> result = watchlistService.list(false);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).summary()).isNotNull();
        assertThat(result.get(0).summary().action()).isNull();
        assertThat(result.get(0).summary().score()).isNull();
        assertThat(result.get(0).summary().price()).isNull();
    }

    @Test
    void list_withSameTickerAcrossMarkets_shouldUseMarketScopedCache() {
        // Given
        WatchlistItem usItem = new WatchlistItem(testUser, "AAPL", "US", false);
        ReflectionTestUtils.setField(usItem, "id", 1L);
        ReflectionTestUtils.setField(usItem, "createdAt", LocalDateTime.now());

        WatchlistItem krItem = new WatchlistItem(testUser, "AAPL", "KR", false);
        ReflectionTestUtils.setField(krItem, "id", 2L);
        ReflectionTestUtils.setField(krItem, "createdAt", LocalDateTime.now());

        MarketCache usCache = mock(MarketCache.class);
        when(usCache.getTicker()).thenReturn("AAPL");
        when(usCache.getMarket()).thenReturn("US");
        when(usCache.getInsightsJson()).thenReturn("""
            {
              "recommendation": {"action": "BUY", "score": 80},
              "quote": {"price": 180.0}
            }
            """);
        when(usCache.getInsightsUpdatedAt()).thenReturn(LocalDateTime.now());

        MarketCache krCache = mock(MarketCache.class);
        when(krCache.getTicker()).thenReturn("AAPL");
        when(krCache.getMarket()).thenReturn("KR");
        when(krCache.getInsightsJson()).thenReturn("""
            {
              "recommendation": {"action": "SELL", "score": 20},
              "quote": {"price": 250000.0}
            }
            """);
        when(krCache.getInsightsUpdatedAt()).thenReturn(LocalDateTime.now());

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUserIdWithTags(1L, false))
                .thenReturn(List.of(usItem, krItem));
        when(marketCacheRepository.findByUser_IdAndTickerInAndMarketAndTestMode(
                eq(1L), anySet(), eq("US"), eq(false), eq(90), eq(20)))
                .thenReturn(List.of(usCache));
        when(marketCacheRepository.findByUser_IdAndTickerInAndMarketAndTestMode(
                eq(1L), anySet(), eq("KR"), eq(false), eq(90), eq(20)))
                .thenReturn(List.of(krCache));

        // When
        List<WatchlistItemResponse> result = watchlistService.list(false);

        // Then
        assertThat(result).hasSize(2);
        Map<String, WatchlistItemResponse> byMarket = result.stream()
                .collect(java.util.stream.Collectors.toMap(WatchlistItemResponse::market, r -> r));
        assertThat(byMarket.get("US").summary()).isNotNull();
        assertThat(byMarket.get("US").summary().action()).isEqualTo("BUY");
        assertThat(byMarket.get("US").summary().score()).isEqualTo(80);
        assertThat(byMarket.get("KR").summary()).isNotNull();
        assertThat(byMarket.get("KR").summary().action()).isEqualTo("SELL");
        assertThat(byMarket.get("KR").summary().score()).isEqualTo(20);
    }

    @Test
    void list_inTestMode_shouldFilterByTestMode() {
        // Given
        WatchlistItem item = new WatchlistItem(testUser, "TEST", "US", true);
        ReflectionTestUtils.setField(item, "id", 1L);
        ReflectionTestUtils.setField(item, "createdAt", LocalDateTime.now());

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.findByUserIdWithTags(1L, true))
                .thenReturn(List.of(item));
        when(marketCacheRepository.findByUser_IdAndTickerInAndMarketAndTestMode(
                anyLong(), anySet(), anyString(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());

        // When
        List<WatchlistItemResponse> result = watchlistService.list(true);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("TEST");
        assertThat(result.get(0).testMode()).isTrue();
    }
}
