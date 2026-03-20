// Service Worker registration and utilities

function isServiceWorkerEnabled() {
  const raw =
    import.meta.env.VITE_ENABLE_SERVICE_WORKER ??
    import.meta.env.REACT_APP_ENABLE_SERVICE_WORKER ??
    "";
  return /^(1|true|yes|on)$/i.test(String(raw).trim());
}

async function clearManagedCaches() {
  if (!("caches" in window)) {
    return;
  }

  const cacheNames = await caches.keys();
  await Promise.all(
    cacheNames
      .filter((name) => name.startsWith("stock-ai-"))
      .map((name) => caches.delete(name))
  );
}

async function unregisterAllServiceWorkers() {
  if (!("serviceWorker" in navigator)) {
    return;
  }

  const registrations = await navigator.serviceWorker.getRegistrations();
  await Promise.all(registrations.map((registration) => registration.unregister()));
  await clearManagedCaches();
}

export function register() {
  if ('serviceWorker' in navigator) {
    if (!isServiceWorkerEnabled()) {
      unregisterAllServiceWorkers().catch(() => {
        // Ignore cleanup failures for legacy registrations.
      });
      return;
    }

    window.addEventListener('load', () => {
      navigator.serviceWorker
        .register('/service-worker.js')
        .then((registration) => {
          console.log('[SW] Registration successful:', registration.scope);
          registration.update().catch(() => {
            // Ignore transient update check failures.
          });

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
    unregisterAllServiceWorkers().catch(() => {
      // Ignore cleanup failures.
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
  // Security hardening: do not persist authenticated API payloads in SW cache.
  void ticker;
  void market;
  void data;
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
