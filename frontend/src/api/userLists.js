const KEY_RECENT = "recentTickers";
const KEY_FAV = "favoriteTickers";

function readList(key) {
    try {
        const raw = localStorage.getItem(key);
        const arr = raw ? JSON.parse(raw) : [];
        return Array.isArray(arr) ? arr.filter(Boolean) : [];
    } catch {
        return [];
    }
}

function writeList(key, arr) {
    localStorage.setItem(key, JSON.stringify(arr));
}

export function getRecentTickers(limit = 12) {
    return readList(KEY_RECENT).slice(0, limit);
}

export function pushRecentTicker(ticker, limit = 20) {
    const t = (ticker || "").trim().toUpperCase();
    if (!t) return;

    const prev = readList(KEY_RECENT);
    const next = [t, ...prev.filter(x => x !== t)].slice(0, limit);
    writeList(KEY_RECENT, next);
}

export function getFavorites(limit = 50) {
    return readList(KEY_FAV).slice(0, limit);
}

export function isFavorite(ticker) {
    const t = (ticker || "").trim().toUpperCase();
    if (!t) return false;
    return readList(KEY_FAV).includes(t);
}

export function toggleFavorite(ticker) {
    const t = (ticker || "").trim().toUpperCase();
    if (!t) return { favorites: readList(KEY_FAV), isFav: false };

    const prev = readList(KEY_FAV);
    const isFav = prev.includes(t);
    const next = isFav ? prev.filter(x => x !== t) : [t, ...prev];
    writeList(KEY_FAV, next);
    return { favorites: next, isFav: !isFav };
}