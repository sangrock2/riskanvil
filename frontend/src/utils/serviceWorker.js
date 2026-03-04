// Service Worker registration and utilities

export function register() {
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker
        .register('/service-worker.js')
        .then((registration) => {
          console.log('[SW] Registration successful:', registration.scope);

          // Check for updates
          registration.addEventListener('updatefound', () => {
            const newWorker = registration.installing;
            if (newWorker) {
              newWorker.addEventListener('statechange', () => {
                if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                  // New version available
                  dispatchUpdateEvent();
                }
              });
            }
          });
        })
        .catch((error) => {
          console.log('[SW] Registration failed:', error);
        });
    });
  }
}

export function unregister() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.ready.then((registration) => {
      registration.unregister();
    });
  }
}

// Clear all caches
export function clearCache() {
  if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
    navigator.serviceWorker.controller.postMessage({ type: 'CLEAR_CACHE' });
  }
}

// Cache specific insights data
export function cacheInsights(ticker, market, data) {
  if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
    navigator.serviceWorker.controller.postMessage({
      type: 'CACHE_INSIGHTS',
      ticker,
      market,
      data,
    });
  }
}

// Check if service worker is active
export function isServiceWorkerActive() {
  return 'serviceWorker' in navigator && navigator.serviceWorker.controller !== null;
}

// Dispatch custom event for app updates
function dispatchUpdateEvent() {
  window.dispatchEvent(new CustomEvent('sw-update-available'));
}

// Check online status
export function isOnline() {
  return navigator.onLine;
}

// Add online/offline listeners
export function addNetworkListeners(onOnline, onOffline) {
  window.addEventListener('online', onOnline);
  window.addEventListener('offline', onOffline);

  return () => {
    window.removeEventListener('online', onOnline);
    window.removeEventListener('offline', onOffline);
  };
}
