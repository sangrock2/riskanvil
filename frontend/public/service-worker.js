const CACHE_NAME = 'stock-ai-v1';
const STATIC_CACHE = 'stock-ai-static-v1';
const API_CACHE = 'stock-ai-api-v1';

// Static assets to cache on install
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
];

// API patterns to cache
const API_CACHE_PATTERNS = [
  '/api/market/quote',
  '/api/market/prices',
  '/api/market/insights',
  '/api/watchlist',
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC_CACHE).then((cache) => {
      console.log('[SW] Caching static assets');
      return cache.addAll(STATIC_ASSETS);
    })
  );
  self.skipWaiting();
});

// Activate event - clean old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== STATIC_CACHE && name !== API_CACHE)
          .map((name) => caches.delete(name))
      );
    })
  );
  self.clients.claim();
});

// Fetch event - serve from cache or network
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Skip non-GET requests
  if (event.request.method !== 'GET') {
    return;
  }

  // Handle API requests
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(handleApiRequest(event.request));
    return;
  }

  // Handle static assets
  event.respondWith(handleStaticRequest(event.request));
});

// Handle API requests with cache-first strategy for reads
async function handleApiRequest(request) {
  const url = new URL(request.url);

  // Check if this API should be cached
  const shouldCache = API_CACHE_PATTERNS.some((pattern) =>
    url.pathname.includes(pattern)
  );

  if (!shouldCache) {
    return fetch(request);
  }

  try {
    // Try network first
    const networkResponse = await fetch(request);

    if (networkResponse.ok) {
      // Clone and cache the response
      const cache = await caches.open(API_CACHE);
      const cacheKey = getCacheKey(request);
      cache.put(cacheKey, networkResponse.clone());
    }

    return networkResponse;
  } catch (error) {
    // Network failed, try cache
    const cache = await caches.open(API_CACHE);
    const cacheKey = getCacheKey(request);
    const cachedResponse = await cache.match(cacheKey);

    if (cachedResponse) {
      console.log('[SW] Serving API from cache:', url.pathname);
      // Add header to indicate cached response
      const headers = new Headers(cachedResponse.headers);
      headers.set('X-From-Cache', 'true');
      return new Response(cachedResponse.body, {
        status: cachedResponse.status,
        statusText: cachedResponse.statusText,
        headers,
      });
    }

    // No cache available
    throw error;
  }
}

// Handle static requests with cache-first strategy
async function handleStaticRequest(request) {
  const cache = await caches.open(STATIC_CACHE);
  const cachedResponse = await cache.match(request);

  if (cachedResponse) {
    // Return cached version and update in background
    fetchAndCache(request, cache);
    return cachedResponse;
  }

  // Not in cache, fetch from network
  try {
    const networkResponse = await fetch(request);

    if (networkResponse.ok) {
      cache.put(request, networkResponse.clone());
    }

    return networkResponse;
  } catch (error) {
    // Return offline page if available
    const offlineResponse = await cache.match('/offline.html');
    if (offlineResponse) {
      return offlineResponse;
    }

    throw error;
  }
}

// Background fetch and cache update
async function fetchAndCache(request, cache) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      cache.put(request, response);
    }
  } catch (error) {
    // Ignore background fetch errors
  }
}

// Generate cache key for API requests
function getCacheKey(request) {
  const url = new URL(request.url);
  // Include search params in cache key
  return new Request(url.pathname + url.search);
}

// Listen for messages from main thread
self.addEventListener('message', (event) => {
  if (event.data.type === 'CLEAR_CACHE') {
    caches.keys().then((names) => {
      Promise.all(names.map((name) => caches.delete(name)));
    });
  }

  if (event.data.type === 'CACHE_INSIGHTS') {
    const { ticker, market, data } = event.data;
    caches.open(API_CACHE).then((cache) => {
      const key = `/api/market/insights?ticker=${ticker}&market=${market}`;
      cache.put(key, new Response(JSON.stringify(data)));
    });
  }
});

// Periodic sync for background updates (if supported)
self.addEventListener('periodicsync', (event) => {
  if (event.tag === 'update-watchlist') {
    event.waitUntil(updateWatchlistCache());
  }
});

async function updateWatchlistCache() {
  try {
    const response = await fetch('/api/watchlist');
    if (response.ok) {
      const cache = await caches.open(API_CACHE);
      cache.put('/api/watchlist', response);
    }
  } catch (error) {
    console.log('[SW] Background sync failed:', error);
  }
}
