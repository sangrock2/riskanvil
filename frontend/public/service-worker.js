const STATIC_CACHE = 'stock-ai-static-v2';
const API_CACHE = 'stock-ai-api-v2';

// Static assets to cache on install
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
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

  // Only handle same-origin requests
  if (url.origin !== self.location.origin) {
    return;
  }

  // Handle API requests
  if (url.pathname.startsWith('/api/')) {
    // Security: do not persist authenticated API responses in Service Worker cache.
    event.respondWith(handleApiRequest(event.request));
    return;
  }

  // Handle SPA navigations with network-first + app-shell fallback
  if (isNavigationRequest(event.request)) {
    event.respondWith(handleNavigationRequest(event.request));
    return;
  }

  // Handle static assets
  event.respondWith(handleStaticRequest(event.request));
});

// Handle API requests as pass-through (no SW persistence)
async function handleApiRequest(request) {
  return fetch(request);
}

function isNavigationRequest(request) {
  if (request.mode === 'navigate') return true;
  const accept = request.headers.get('accept') || '';
  return accept.includes('text/html');
}

function isHtmlResponse(response) {
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('text/html');
}

// Handle SPA navigation requests
async function handleNavigationRequest(request) {
  const cache = await caches.open(STATIC_CACHE);
  try {
    const networkResponse = await fetch(request);
    if (!networkResponse.ok) {
      throw new Error(`Navigation request failed: ${networkResponse.status}`);
    }

    // Keep app shell fresh for offline/degraded fallback
    if (isHtmlResponse(networkResponse)) {
      cache.put('/index.html', networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    const cachedIndex = await cache.match('/index.html');
    if (cachedIndex) {
      return cachedIndex;
    }

    const cachedRoot = await cache.match('/');
    if (cachedRoot) {
      return cachedRoot;
    }

    return new Response('Service temporarily unavailable', {
      status: 503,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    });
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
    // For static assets, return cached shell if possible
    const fallback = await cache.match('/index.html');
    if (fallback) return fallback;
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

// Listen for messages from main thread
self.addEventListener('message', (event) => {
  if (event.data.type === 'CLEAR_CACHE') {
    caches.keys().then((names) => {
      Promise.all(names.map((name) => caches.delete(name)));
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
  // Security hardening: do not background-cache authenticated watchlist data.
}
