/// <reference lib="webworker" />

/**
 * Service Worker Registration and Management
 *
 * Handles PWA service worker lifecycle:
 * - Registration on app load
 * - Caching strategies for offline support
 * - Service worker updates
 * - Cache management
 *
 * Used by: main.tsx (app initialization)
 */

const CACHE_NAME = 'batbern-v1';
const OFFLINE_URL = '/offline.html';

let swRegistration: ServiceWorkerRegistration | null = null;

/**
 * Get the current cache name
 * @returns Current cache name with version
 */
export function getCacheName(): string {
  return CACHE_NAME;
}

/**
 * Register the service worker
 * @returns Service worker registration or undefined if not supported
 */
export async function registerServiceWorker(): Promise<ServiceWorkerRegistration | undefined> {
  if (!('serviceWorker' in navigator)) {
    console.warn('Service Worker not supported in this browser');
    return undefined;
  }

  try {
    swRegistration = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
    console.log('Service Worker registered successfully:', swRegistration);
    return swRegistration;
  } catch (error) {
    console.error('Service Worker registration failed:', error);
    return undefined;
  }
}

/**
 * Check for service worker updates
 */
export async function checkForUpdates(): Promise<void> {
  if (swRegistration) {
    await swRegistration.update();
  }
}

/**
 * Cache assets for offline use
 * @param cacheName - Name of the cache
 */
export async function cacheAssets(cacheName: string): Promise<void> {
  const urlsToCache = ['/', '/index.html', '/offline.html', '/manifest.json'];

  const cache = await caches.open(cacheName);
  await cache.addAll(urlsToCache);
}

/**
 * Get cached response for a request
 * @param request - The request to check
 * @returns Cached response or fetched response
 */
export async function getCachedResponse(request: Request): Promise<Response> {
  const cachedResponse = await caches.match(request);

  if (cachedResponse) {
    return cachedResponse;
  }

  // If not in cache, fetch from network
  return fetch(request);
}

/**
 * Fetch from network and update cache
 * @param request - The request to fetch
 * @param cacheName - Name of the cache
 * @returns Fetched response
 */
export async function fetchAndCache(request: Request, cacheName: string): Promise<Response> {
  const response = await fetch(request);
  const cache = await caches.open(cacheName);
  await cache.put(request, response.clone());
  return response;
}

/**
 * Skip waiting and activate new service worker immediately
 */
export async function skipWaiting(): Promise<void> {
  if ('skipWaiting' in self) {
    await (self as ServiceWorkerGlobalScope & { skipWaiting: () => Promise<void> }).skipWaiting();
  }
}

/**
 * Claim clients immediately after activation
 */
export async function claimClients(): Promise<void> {
  // Access clients through self (ServiceWorkerGlobalScope)
  const sw = self as ServiceWorkerGlobalScope & { clients: { claim: () => Promise<void> } };
  if (sw.clients && sw.clients.claim) {
    await sw.clients.claim();
  }
}

/**
 * Delete old caches during activation
 * @param currentCacheName - The current cache name to keep
 */
export async function deleteOldCaches(currentCacheName: string): Promise<void> {
  const cacheNames = await caches.keys();
  await Promise.all(
    cacheNames
      .filter((cacheName) => cacheName !== currentCacheName)
      .map((cacheName) => caches.delete(cacheName))
  );
}

/**
 * Handle fetch events with caching strategies
 * @param request - The request to handle
 * @returns Response from cache or network
 */
export async function handleFetch(request: Request): Promise<Response> {
  try {
    // Try cache first
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }

    // Try network
    const networkResponse = await fetch(request);
    return networkResponse;
  } catch (error) {
    // If both fail, return offline page for navigation requests
    if (request.mode === 'navigate') {
      const offlineResponse = await caches.match(OFFLINE_URL);
      if (offlineResponse) {
        return offlineResponse;
      }
    }

    throw error;
  }
}
