import { useState, useEffect, useRef, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { usePreferences } from "../context/PreferencesContext";
import { debounce } from "../utils/debounce";
import { apiFetch } from "../api/http";
import styles from "../css/SearchAutocomplete.module.css";

export default function SearchAutocomplete({ className }) {
  const navigate = useNavigate();
  const { preferences, addSearchHistory } = usePreferences();
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const listboxId = "global-search-listbox";
  const inputRef = useRef(null);
  const dropdownRef = useRef(null);

  const searchHistory = preferences.searchHistory || [];

  // Click outside to close dropdown
  useEffect(() => {
    function handleClickOutside(event) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target) &&
        inputRef.current &&
        !inputRef.current.contains(event.target)
      ) {
        setShowDropdown(false);
      }
    }

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Fetch suggestions from API - using useMemo to avoid stale closure
  const fetchSuggestions = useMemo(
    () => debounce(async (searchQuery) => {
      if (!searchQuery || searchQuery.length < 2) {
        setSuggestions([]);
        setLoading(false);
        return;
      }

      setLoading(true);
      try {
        // API endpoint for search suggestions
        const results = await apiFetch(
          `/api/market/search?keywords=${encodeURIComponent(searchQuery)}&market=US`,
          { retry: 1 }
        );

        // Parse response if it's a JSON string
        const parsed = typeof results === 'string' ? JSON.parse(results) : results;
        const items = parsed?.results || parsed || [];

        setSuggestions(Array.isArray(items) ? items.slice(0, 8) : []);
      } catch (error) {
        console.error("Search suggestions error:", error);
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, 300),
    [] // setSuggestions and setLoading are stable, no need to include in deps
  );

  useEffect(() => {
    if (query.trim()) {
      fetchSuggestions(query);
    } else {
      setSuggestions([]);
    }
  }, [query, fetchSuggestions]);

  useEffect(() => {
    setSelectedIndex(-1);
  }, [query, showDropdown]);

  function handleSubmit(e) {
    e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;

    addSearchHistory(trimmed);
    navigate(`/analyze?q=${encodeURIComponent(trimmed)}`);
    setQuery("");
    setShowDropdown(false);
  }

  function selectSuggestion(item) {
    const searchTerm = item.ticker || item.symbol || item;
    addSearchHistory(searchTerm);
    navigate(`/analyze?q=${encodeURIComponent(searchTerm)}`);
    setQuery("");
    setShowDropdown(false);
  }

  function selectHistory(term) {
    setQuery(term);
    navigate(`/analyze?q=${encodeURIComponent(term)}`);
    setShowDropdown(false);
  }

  function handleKeyDown(e) {
    if (!showDropdown) return;

    const items = suggestions.length > 0 ? suggestions : searchHistory;
    const maxIndex = items.length - 1;
    if (maxIndex < 0) return;

    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIndex((prev) => (prev < maxIndex ? prev + 1 : 0));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIndex((prev) => (prev > 0 ? prev - 1 : maxIndex));
    } else if (e.key === "Enter" && selectedIndex >= 0) {
      e.preventDefault();
      if (suggestions.length > 0) {
        selectSuggestion(suggestions[selectedIndex]);
      } else if (searchHistory.length > 0) {
        selectHistory(searchHistory[selectedIndex]);
      }
    } else if (e.key === "Escape") {
      setShowDropdown(false);
      setSelectedIndex(-1);
    }
  }

  function clearQuery() {
    setQuery("");
    setSuggestions([]);
    setSelectedIndex(-1);
    setShowDropdown(false);
    inputRef.current?.focus();
  }

  const showHistory = !query && searchHistory.length > 0;
  const showSuggestions = query && suggestions.length > 0;
  const hasDropdownContent = showHistory || showSuggestions || loading;
  const showClearButton = query.length > 0;

  return (
    <div className={`${styles.container} ${className || ""}`}>
      <form onSubmit={handleSubmit} className={styles.form} role="search">
        <input
          ref={inputRef}
          type="text"
          className={styles.input}
          placeholder="종목 검색 (티커 또는 회사명)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => setShowDropdown(true)}
          onKeyDown={handleKeyDown}
          autoComplete="off"
          role="combobox"
          aria-autocomplete="list"
          aria-haspopup="listbox"
          aria-controls={listboxId}
          aria-expanded={showDropdown && hasDropdownContent}
          aria-activedescendant={selectedIndex >= 0 ? `${listboxId}-item-${selectedIndex}` : undefined}
        />
        {showClearButton && (
          <button
            type="button"
            className={styles.clearBtn}
            onClick={clearQuery}
            aria-label="입력 초기화"
            title="입력 초기화"
          >
            ×
          </button>
        )}
        <button type="submit" className={styles.submitBtn} aria-label="검색">
          🔍
        </button>
      </form>

      {showDropdown && hasDropdownContent && (
        <div ref={dropdownRef} className={styles.dropdown} role="listbox" id={listboxId}>
          {loading && (
            <div className={styles.loading}>
              <span className={styles.spinner} />
              <span>검색 중...</span>
            </div>
          )}

          {!loading && showHistory && (
            <>
              <div className={styles.dropdownHeader}>최근 검색</div>
              {searchHistory.map((term, index) => (
                <button
                  key={term}
                  className={`${styles.dropdownItem} ${
                    selectedIndex === index ? styles.selected : ""
                  }`}
                  onClick={() => selectHistory(term)}
                  role="option"
                  aria-selected={selectedIndex === index}
                  id={`${listboxId}-item-${index}`}
                >
                  <span className={styles.icon}>🕐</span>
                  <span className={styles.itemText}>{term}</span>
                </button>
              ))}
            </>
          )}

          {!loading && showSuggestions && (
            <>
              <div className={styles.dropdownHeader}>검색 결과</div>
              {suggestions.map((item, index) => (
                <button
                  key={item.ticker || item.symbol || index}
                  className={`${styles.dropdownItem} ${
                    selectedIndex === index ? styles.selected : ""
                  }`}
                  onClick={() => selectSuggestion(item)}
                  role="option"
                  aria-selected={selectedIndex === index}
                  id={`${listboxId}-item-${index}`}
                >
                  <div className={styles.suggestionContent}>
                    <div className={styles.suggestionMain}>
                      <span className={styles.ticker}>
                        {item.ticker || item.symbol}
                      </span>
                      {item.name && (
                        <span className={styles.name}>{item.name}</span>
                      )}
                    </div>
                    {item.market && (
                      <span className={styles.market}>{item.market}</span>
                    )}
                  </div>
                </button>
              ))}
            </>
          )}

          {!loading && query && suggestions.length === 0 && (
            <div className={styles.empty}>검색 결과가 없습니다</div>
          )}
        </div>
      )}
    </div>
  );
}
