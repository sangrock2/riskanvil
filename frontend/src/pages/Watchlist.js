import { useMemo, useState, useCallback, memo } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "../hooks/useTranslation";
import { useQueryClient } from "@tanstack/react-query";
import { useToast } from "../components/ui/Toast";
import { SkeletonTable } from "../components/ui/Loading";
import { validateNotes } from "../utils/validators";
import { toUserErrorMessage } from "../utils/errorMessage";
import { useRealtimeQuote } from "../hooks/useRealtimeQuote";
import { useWatchlist, useWatchlistTags, useWatchlistMutations } from "../hooks/queries";
import styles from "../css/Watchlist.module.css";

// Memoized watchlist item row to prevent unnecessary re-renders
const WatchlistItemRow = memo(function WatchlistItemRow({
    item,
    tags,
    testMode,
    isEditingNotes,
    isEditingTags,
    notesValue,
    updatingKey,
    onOpenDetail,
    onUpdateNotes,
    onSetEditingNotes,
    onSetEditingTags,
    onUpdateItemTags,
    onUpdateInsights,
    onRemove
}) {
    const { t } = useTranslation();
    const key = `${item.ticker}:${item.market}`;
    const s = item.summary;
    const realtimeQuote = useRealtimeQuote(item.market === "US" ? item.ticker : null);
    const displayPrice = realtimeQuote?.price ?? s?.price;

    return (
        <div className={styles.item}>
            <div className={styles.itemContent}>
                <div className={styles.left} onClick={() => onOpenDetail(item)}>
                    <div className={styles.mainLine}>
                        <b>{item.ticker}</b>
                        <span className={styles.small}>({item.market})</span>
                    </div>

                    <div className={styles.meta}>
                        <span>{t('watchlist.action')}: <b>{s?.action ?? t('messages.noData')}</b></span>
                        <span>{t('watchlist.score')}: <b className={s?.score >= 60 ? styles.scoreGood : s?.score >= 40 ? styles.scoreNeutral : styles.scoreBad}>{s?.score ?? t('messages.noData')}</b></span>
                        <span>{t('watchlist.price')}: <b>{displayPrice != null ? `$${Number(displayPrice).toFixed(2)}` : t('messages.noData')}{realtimeQuote ? " *" : ""}</b></span>
                    </div>

                    {item.tags && item.tags.length > 0 && (
                        <div className={styles.itemTags}>
                            {item.tags.map(tag => (
                                <span
                                    key={tag.id}
                                    className={styles.itemTag}
                                    style={{ backgroundColor: tag.color }}
                                >
                                    {tag.name}
                                </span>
                            ))}
                        </div>
                    )}

                    {item.notes && !isEditingNotes && (
                        <div className={styles.itemNotes}>
                            📝 {item.notes}
                        </div>
                    )}
                </div>

                {isEditingNotes && (
                    <div className={styles.notesEditor}>
                        <textarea
                            className={styles.notesTextarea}
                            value={notesValue}
                            onChange={(e) => onSetEditingNotes(item.id, e.target.value)}
                            placeholder={t('watchlist.notesPlaceholder')}
                            maxLength={500}
                        />
                        <div className={styles.notesActions}>
                            <button className={styles.btnSmall} onClick={() => onUpdateNotes(item.id)}>
                                {t('common.save')}
                            </button>
                            <button
                                className={styles.btnSmall}
                                onClick={() => onSetEditingNotes(item.id, null)}
                            >
                                {t('common.cancel')}
                            </button>
                        </div>
                    </div>
                )}

                {isEditingTags && (
                    <div className={styles.tagEditor}>
                        <div className={styles.tagCheckboxes}>
                            {tags.map(tag => {
                                const isChecked = item.tags?.some(t => t.id === tag.id);
                                return (
                                    <label key={tag.id} className={styles.tagCheckbox}>
                                        <input
                                            type="checkbox"
                                            checked={isChecked}
                                            onChange={(e) => {
                                                const currentTagIds = item.tags?.map(t => t.id) || [];
                                                const newTagIds = e.target.checked
                                                    ? [...currentTagIds, tag.id]
                                                    : currentTagIds.filter(id => id !== tag.id);
                                                onUpdateItemTags(item.id, newTagIds);
                                            }}
                                        />
                                        <span
                                            className={styles.tagBadgeSmall}
                                            style={{ backgroundColor: tag.color }}
                                        >
                                            {tag.name}
                                        </span>
                                    </label>
                                );
                            })}
                        </div>
                        <button
                            className={styles.btnSmall}
                            onClick={() => onSetEditingTags(null)}
                        >
                            {t('common.close')}
                        </button>
                    </div>
                )}
            </div>

            <div className={styles.actions}>
                <button
                    className={styles.btnSmall}
                    onClick={() => onSetEditingTags(isEditingTags ? null : item.id)}
                >
                    🏷️ {t('watchlist.tags')}
                </button>

                <button
                    className={styles.btnSmall}
                    onClick={() => onSetEditingNotes(item.id, isEditingNotes ? null : (item.notes || ""))}
                >
                    📝 {t('watchlist.notes')}
                </button>

                <button
                    className={styles.btn}
                    onClick={() => onUpdateInsights(item)}
                    disabled={updatingKey === key}
                    title={t('watchlist.updateTooltip')}
                >
                    {updatingKey === key ? t('watchlist.updating') : t('watchlist.update')}
                </button>

                <button className={styles.dangerBtn} onClick={() => onRemove(item)}>
                    {t('watchlist.remove')}
                </button>
            </div>
        </div>
    );
}, (prevProps, nextProps) => {
    // Custom comparison to prevent re-renders when unrelated state changes
    return (
        prevProps.item.id === nextProps.item.id &&
        prevProps.item.ticker === nextProps.item.ticker &&
        prevProps.item.market === nextProps.item.market &&
        prevProps.item.notes === nextProps.item.notes &&
        JSON.stringify(prevProps.item.summary) === JSON.stringify(nextProps.item.summary) &&
        JSON.stringify(prevProps.item.tags) === JSON.stringify(nextProps.item.tags) &&
        prevProps.isEditingNotes === nextProps.isEditingNotes &&
        prevProps.isEditingTags === nextProps.isEditingTags &&
        prevProps.notesValue === nextProps.notesValue &&
        prevProps.updatingKey === nextProps.updatingKey &&
        prevProps.tags.length === nextProps.tags.length
    );
});

export default function Watchlist() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [ticker, setTicker] = useState("");
    const [market, setMarket] = useState("US");
    const [err, setErr] = useState("");
    const [updatingKey, setUpdatingKey] = useState("");

    // Tag management
    const [showTagManager, setShowTagManager] = useState(false);
    const [newTagName, setNewTagName] = useState("");
    const [newTagColor, setNewTagColor] = useState("#3b82f6");

    // Filtering & Sorting
    const [selectedTagFilter, setSelectedTagFilter] = useState(null);
    const [sortBy, setSortBy] = useState("createdAt");
    const [sortOrder, setSortOrder] = useState("desc");
    const [searchQuery, setSearchQuery] = useState("");

    // Notes editing
    const [editingNotes, setEditingNotes] = useState({});

    // Tag assignment
    const [editingItemTags, setEditingItemTags] = useState(null);

    const params = useMemo(() => new URLSearchParams(window.location.search), []);
    const testMode = params.get("test") === "true";
    const toast = useToast();
    const qc = useQueryClient();

    const backUrl = `/analyze?test=${testMode}`;

    // React Query hooks
    const { data: items = [], isLoading: loading, error: queryError, refetch: refetchItems } = useWatchlist(testMode);
    const { data: tags = [] } = useWatchlistTags();
    const mutations = useWatchlistMutations(testMode);

    // Show query error
    const displayErr = err || (queryError ? toUserErrorMessage(queryError, t, "watchlist.loadError") : "");

    async function add() {
        const trimmedTicker = ticker.trim();
        if (!trimmedTicker) return;

        setErr("");

        try {
            await mutations.addItem.mutateAsync({ ticker: trimmedTicker, market });
            setTicker("");
            toast.success(t('watchlist.addSuccess', { ticker: trimmedTicker }));
        } catch (e) {
            const message = toUserErrorMessage(e, t, "watchlist.addError", "watchlistAdd");
            setErr(message);
            toast.error(message);
        }
    }

    async function remove(it) {
        if (!window.confirm(t('watchlist.removeConfirm', { ticker: it.ticker }))) return;

        setErr("");

        try {
            await mutations.removeItem.mutateAsync({ ticker: it.ticker, market: it.market });
            toast.success(t('watchlist.removeSuccess', { ticker: it.ticker }));
        } catch (e) {
            const message = toUserErrorMessage(e, t, "watchlist.removeError");
            setErr(message);
            toast.error(message);
        }
    }

    async function updateInsights(it) {
        setErr("");
        const key = `${it.ticker}:${it.market}`;
        setUpdatingKey(key);

        try {
            await mutations.updateInsights.mutateAsync({ ticker: it.ticker, market: it.market });
            toast.success(t('watchlist.updateSuccess', { ticker: it.ticker }));
        } catch (e) {
            const message = toUserErrorMessage(e, t, "watchlist.updateError");
            setErr(message);
            toast.error(message);
        } finally {
            setUpdatingKey("");
        }
    }

    async function createTag() {
        const name = newTagName.trim();
        if (!name) return;

        try {
            await mutations.createTag.mutateAsync({ name, color: newTagColor });
            setNewTagName("");
            setNewTagColor("#3b82f6");
            toast.success(t('watchlist.tagCreateSuccess', { name }));
        } catch (e) {
            toast.error(toUserErrorMessage(e, t, "watchlist.tagCreateError"));
        }
    }

    async function deleteTag(tagId) {
        if (!window.confirm(t('watchlist.tagDeleteConfirm'))) return;

        try {
            await mutations.deleteTag.mutateAsync(tagId);
            toast.success(t('watchlist.tagDeleteSuccess'));
        } catch (e) {
            toast.error(toUserErrorMessage(e, t, "watchlist.tagDeleteError"));
        }
    }

    async function updateItemTags(itemId, tagIds) {
        try {
            await mutations.updateItemTags.mutateAsync({ itemId, tagIds });
            setEditingItemTags(null);
            toast.success(t('watchlist.tagsUpdateSuccess'));
        } catch (e) {
            toast.error(toUserErrorMessage(e, t, "watchlist.tagsUpdateError"));
        }
    }

    async function updateNotes(itemId) {
        const notes = editingNotes[itemId];
        if (notes === undefined) return;

        // Validate notes length (max 500 characters)
        const validation = validateNotes(notes, t);
        if (!validation.valid) {
            toast.error(validation.message);
            return;
        }

        try {
            await mutations.updateNotes.mutateAsync({ itemId, notes });
            setEditingNotes(prev => {
                const next = { ...prev };
                delete next[itemId];
                return next;
            });
            toast.success(t('watchlist.notesUpdateSuccess'));
        } catch (e) {
            toast.error(toUserErrorMessage(e, t, "watchlist.notesUpdateError"));
        }
    }

    function openDetail(it) {
        const url = `/insight-detail?ticker=${encodeURIComponent(it.ticker)}&market=${encodeURIComponent(it.market)}&test=${testMode}`;
        navigate(url);
    }

    // Handlers for WatchlistItemRow
    const handleSetEditingNotes = useCallback((itemId, value) => {
        if (value === null) {
            setEditingNotes(prev => {
                const next = { ...prev };
                delete next[itemId];
                return next;
            });
        } else {
            setEditingNotes(prev => ({ ...prev, [itemId]: value }));
        }
    }, []);

    const handleSetEditingTags = useCallback((itemId) => {
        setEditingItemTags(itemId);
    }, []);

    // Filtered and sorted items
    const displayedItems = useMemo(() => {
        let filtered = items;

        // Search filter
        if (searchQuery.trim()) {
            const q = searchQuery.toLowerCase();
            filtered = filtered.filter(it =>
                it.ticker.toLowerCase().includes(q) ||
                it.notes?.toLowerCase().includes(q)
            );
        }

        // Tag filter
        if (selectedTagFilter) {
            filtered = filtered.filter(it =>
                it.tags?.some(tag => tag.id === selectedTagFilter)
            );
        }

        // Sort
        filtered = [...filtered].sort((a, b) => {
            let aVal, bVal;

            switch (sortBy) {
                case "ticker":
                    aVal = a.ticker;
                    bVal = b.ticker;
                    break;
                case "score":
                    aVal = a.summary?.score ?? 0;
                    bVal = b.summary?.score ?? 0;
                    break;
                case "price":
                    aVal = a.summary?.price ?? 0;
                    bVal = b.summary?.price ?? 0;
                    break;
                case "createdAt":
                default:
                    aVal = new Date(a.createdAt || 0);
                    bVal = new Date(b.createdAt || 0);
            }

            if (aVal < bVal) return sortOrder === "asc" ? -1 : 1;
            if (aVal > bVal) return sortOrder === "asc" ? 1 : -1;
            return 0;
        });

        return filtered;
    }, [items, searchQuery, selectedTagFilter, sortBy, sortOrder]);

    return (
        <div className={styles.container}>
            <div className={styles.topbar}>
                <div>
                    <div className={styles.title}>{t('watchlist.title')}</div>
                    <div className={styles.sub}>{testMode ? t('common.testMode') : ""}</div>
                </div>

                <div className={styles.topActions}>
                    <button className={styles.btn} onClick={() => setShowTagManager(!showTagManager)}>
                        {showTagManager ? t('watchlist.hideTags') : t('watchlist.manageTags')}
                    </button>

                    <a className={styles.linkBtn} href={backUrl}>
                        {t('watchlist.backToAnalyze')}
                    </a>

                    <button className={styles.btn} onClick={() => refetchItems()} disabled={loading}>
                        {loading ? t('common.loading') : t('common.reload')}
                    </button>
                </div>
            </div>

            {displayErr && <div className={styles.error}>{displayErr}</div>}

            {/* Tag Manager */}
            {showTagManager && (
                <div className={styles.card}>
                    <h3 className={styles.h3}>{t('watchlist.tagManagement')}</h3>

                    <div className={styles.row}>
                        <input
                            className={styles.input}
                            value={newTagName}
                            onChange={(e) => setNewTagName(e.target.value)}
                            placeholder={t('watchlist.tagNamePlaceholder')}
                            maxLength={50}
                        />
                        <input
                            type="color"
                            className={styles.colorInput}
                            value={newTagColor}
                            onChange={(e) => setNewTagColor(e.target.value)}
                        />
                        <button className={styles.btn} onClick={createTag}>
                            {t('watchlist.createTag')}
                        </button>
                    </div>

                    <div className={styles.tagList}>
                        {tags.map(tag => (
                            <div key={tag.id} className={styles.tagItem}>
                                <div
                                    className={styles.tagBadge}
                                    style={{ backgroundColor: tag.color }}
                                >
                                    {tag.name}
                                </div>
                                <span className={styles.tagCount}>({tag.itemCount})</span>
                                <button
                                    className={styles.tagDeleteBtn}
                                    onClick={() => deleteTag(tag.id)}
                                    title={t('watchlist.deleteTag')}
                                >
                                    ×
                                </button>
                            </div>
                        ))}
                        {tags.length === 0 && (
                            <div className={styles.small}>{t('watchlist.noTags')}</div>
                        )}
                    </div>
                </div>
            )}

            {/* Add Item */}
            <div className={styles.card}>
                <div className={styles.row}>
                    <input
                        className={styles.input}
                        value={ticker}
                        onChange={(e) => setTicker(e.target.value)}
                        placeholder={t('watchlist.addTickerPlaceholder')}
                        onKeyPress={(e) => e.key === "Enter" && add()}
                    />

                    <select
                        className={styles.input}
                        value={market}
                        onChange={(e) => setMarket(e.target.value)}
                    >
                        <option value="US">{t('common.market.us')}</option>
                        <option value="KR">{t('common.market.kr')}</option>
                    </select>

                    <button className={styles.btn} onClick={add}>
                        {t('common.add')}
                    </button>
                </div>
            </div>

            {/* Filters & Sorting */}
            <div className={styles.card}>
                <div className={styles.filterRow}>
                    <input
                        className={styles.searchInput}
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder={t('watchlist.searchPlaceholder')}
                    />

                    <select
                        className={styles.filterSelect}
                        value={selectedTagFilter || ""}
                        onChange={(e) => setSelectedTagFilter(e.target.value ? Number(e.target.value) : null)}
                    >
                        <option value="">{t('watchlist.allTags')}</option>
                        {tags.map(tag => (
                            <option key={tag.id} value={tag.id}>{tag.name}</option>
                        ))}
                    </select>

                    <select
                        className={styles.filterSelect}
                        value={sortBy}
                        onChange={(e) => setSortBy(e.target.value)}
                    >
                        <option value="createdAt">{t('watchlist.sortDate')}</option>
                        <option value="ticker">{t('watchlist.sortTicker')}</option>
                        <option value="score">{t('watchlist.sortScore')}</option>
                        <option value="price">{t('watchlist.sortPrice')}</option>
                    </select>

                    <button
                        className={styles.orderBtn}
                        onClick={() => setSortOrder(prev => prev === "asc" ? "desc" : "asc")}
                        title={sortOrder === "asc" ? t('watchlist.ascending') : t('watchlist.descending')}
                    >
                        {sortOrder === "asc" ? "↑" : "↓"}
                    </button>
                </div>

                <div className={styles.small}>
                    {t('watchlist.showingItems', { displayed: displayedItems.length, total: items.length })}
                </div>
            </div>

            {/* Items List */}
            <div className={styles.card}>
                <div className={styles.h3}>{t('watchlist.items')}</div>

                {loading ? (
                    <SkeletonTable rows={10} columns={6} />
                ) : displayedItems.length === 0 ? (
                    <div className={styles.small}>{t('watchlist.noItems')}</div>
                ) : (
                    <div className={styles.list}>
                        {displayedItems.map((it) => (
                            <WatchlistItemRow
                                key={it.id}
                                item={it}
                                tags={tags}
                                testMode={testMode}
                                isEditingNotes={editingNotes.hasOwnProperty(it.id)}
                                isEditingTags={editingItemTags === it.id}
                                notesValue={editingNotes[it.id] || ""}
                                updatingKey={updatingKey}
                                onOpenDetail={openDetail}
                                onUpdateNotes={updateNotes}
                                onSetEditingNotes={handleSetEditingNotes}
                                onSetEditingTags={handleSetEditingTags}
                                onUpdateItemTags={updateItemTags}
                                onUpdateInsights={updateInsights}
                                onRemove={remove}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
