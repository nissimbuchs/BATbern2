import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * Test Suite: Progressive Web App (PWA) Service Worker
 *
 * Tests PWA functionality including:
 * - Service worker registration
 * - Offline page caching
 * - Asset caching strategy
 * - Service worker updates
 *
 * Acceptance Criteria: AC12 (Progressive Web App)
 */

describe('Service Worker Registration', () => {
  let originalNavigator: Navigator;

  beforeEach(() => {
    originalNavigator = global.navigator;
  });

  afterEach(() => {
    global.navigator = originalNavigator;
    vi.clearAllMocks();
  });

  it('should_registerServiceWorker_when_appLoads', async () => {
    // Test 12.1: Service worker registration on app load
    const mockRegister = vi.fn().mockResolvedValue({
      active: { state: 'activated' },
      waiting: null,
      installing: null,
    });

    Object.defineProperty(global, 'navigator', {
      value: {
        serviceWorker: {
          register: mockRegister,
        },
      },
      writable: true,
      configurable: true,
    });

    const { registerServiceWorker } = await import('./serviceWorker');
    await registerServiceWorker();

    expect(mockRegister).toHaveBeenCalledWith('/sw.js', { scope: '/' });
  });

  it('should_notRegisterServiceWorker_when_notSupported', async () => {
    // Service worker not supported in environment
    Object.defineProperty(global, 'navigator', {
      value: {},
      writable: true,
      configurable: true,
    });

    const { registerServiceWorker } = await import('./serviceWorker');
    const result = await registerServiceWorker();

    expect(result).toBeUndefined();
  });

  it('should_handleRegistrationError_when_registerFails', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const mockRegister = vi.fn().mockRejectedValue(new Error('Registration failed'));

    Object.defineProperty(global, 'navigator', {
      value: {
        serviceWorker: {
          register: mockRegister,
        },
      },
      writable: true,
      configurable: true,
    });

    const { registerServiceWorker } = await import('./serviceWorker');
    await registerServiceWorker();

    expect(consoleErrorSpy).toHaveBeenCalledWith(
      'Service Worker registration failed:',
      expect.any(Error)
    );

    consoleErrorSpy.mockRestore();
  });

  it('should_updateServiceWorker_when_newVersionAvailable', async () => {
    const mockUpdate = vi.fn().mockResolvedValue(undefined);
    const mockRegistration = {
      active: { state: 'activated' },
      waiting: null,
      installing: null,
      update: mockUpdate,
    };

    const mockRegister = vi.fn().mockResolvedValue(mockRegistration);

    Object.defineProperty(global, 'navigator', {
      value: {
        serviceWorker: {
          register: mockRegister,
        },
      },
      writable: true,
      configurable: true,
    });

    const { registerServiceWorker, checkForUpdates } = await import('./serviceWorker');
    await registerServiceWorker();
    await checkForUpdates();

    expect(mockUpdate).toHaveBeenCalled();
  });
});

describe('Offline Page Caching', () => {
  it('should_cacheAssets_when_serviceWorkerInstalled', async () => {
    // Test 12.2: Asset caching on service worker install
    const mockCaches = {
      open: vi.fn().mockResolvedValue({
        addAll: vi.fn().mockResolvedValue(undefined),
      }),
    };

    global.caches = mockCaches as unknown as CacheStorage;

    const { getCacheName, cacheAssets } = await import('./serviceWorker');
    const cacheName = getCacheName();
    await cacheAssets(cacheName);

    expect(mockCaches.open).toHaveBeenCalledWith(cacheName);
  });

  it('should_serveCachedAssets_when_offline', async () => {
    const mockRequest = new Request('http://localhost/index.html');
    const mockResponse = new Response('cached content');

    const mockCaches = {
      match: vi.fn().mockResolvedValue(mockResponse),
    };

    global.caches = mockCaches as unknown as CacheStorage;

    const { getCachedResponse } = await import('./serviceWorker');
    const response = await getCachedResponse(mockRequest);

    expect(mockCaches.match).toHaveBeenCalledWith(mockRequest);
    expect(response).toBe(mockResponse);
  });

  it('should_fetchFromNetwork_when_assetNotCached', async () => {
    const mockRequest = new Request('http://localhost/new-page.html');
    const mockNetworkResponse = new Response('network content');

    const mockCaches = {
      match: vi.fn().mockResolvedValue(undefined),
    };

    global.caches = mockCaches as unknown as CacheStorage;
    global.fetch = vi.fn().mockResolvedValue(mockNetworkResponse);

    const { getCachedResponse } = await import('./serviceWorker');
    const response = await getCachedResponse(mockRequest);

    expect(mockCaches.match).toHaveBeenCalledWith(mockRequest);
    expect(global.fetch).toHaveBeenCalledWith(mockRequest);
    expect(response).toBe(mockNetworkResponse);
  });

  it('should_updateCache_when_newAssetFetched', async () => {
    const mockRequest = new Request('http://localhost/api/data');
    const mockResponse = new Response('fresh data');
    const mockCache = {
      put: vi.fn().mockResolvedValue(undefined),
    };

    const mockCaches = {
      open: vi.fn().mockResolvedValue(mockCache),
      match: vi.fn().mockResolvedValue(undefined),
    };

    global.caches = mockCaches as unknown as CacheStorage;
    global.fetch = vi.fn().mockResolvedValue(mockResponse.clone());

    const { getCacheName, fetchAndCache } = await import('./serviceWorker');
    const cacheName = getCacheName();
    await fetchAndCache(mockRequest, cacheName);

    expect(mockCaches.open).toHaveBeenCalledWith(cacheName);
    expect(global.fetch).toHaveBeenCalledWith(mockRequest);
    expect(mockCache.put).toHaveBeenCalledWith(mockRequest, expect.any(Response));
  });
});

describe('Service Worker Lifecycle', () => {
  it('should_skipWaiting_when_newServiceWorkerInstalled', async () => {
    const mockSkipWaiting = vi.fn().mockResolvedValue(undefined);

    Object.defineProperty(global, 'self', {
      value: {
        skipWaiting: mockSkipWaiting,
      },
      writable: true,
      configurable: true,
    });

    const { skipWaiting } = await import('./serviceWorker');
    await skipWaiting();

    expect(mockSkipWaiting).toHaveBeenCalled();
  });

  it('should_claimClients_when_serviceWorkerActivated', async () => {
    const mockClaimClients = vi.fn().mockResolvedValue(undefined);

    // Mock self.clients (ServiceWorkerGlobalScope)
    Object.defineProperty(global.self, 'clients', {
      value: {
        claim: mockClaimClients,
      },
      writable: true,
      configurable: true,
    });

    const { claimClients } = await import('./serviceWorker');
    await claimClients();

    expect(mockClaimClients).toHaveBeenCalled();
  });

  it('should_deleteOldCaches_when_serviceWorkerActivated', async () => {
    const oldCacheName = 'batbern-v1';
    const newCacheName = 'batbern-v2';

    const mockCaches = {
      keys: vi.fn().mockResolvedValue([oldCacheName, newCacheName]),
      delete: vi.fn().mockResolvedValue(true),
    };

    global.caches = mockCaches as unknown as CacheStorage;

    const { deleteOldCaches } = await import('./serviceWorker');
    await deleteOldCaches(newCacheName);

    expect(mockCaches.keys).toHaveBeenCalled();
    expect(mockCaches.delete).toHaveBeenCalledWith(oldCacheName);
    expect(mockCaches.delete).not.toHaveBeenCalledWith(newCacheName);
  });
});

describe('Offline Fallback', () => {
  it('should_serveOfflinePage_when_networkUnavailable', async () => {
    // Create mock request with navigate mode
    const mockRequest = {
      url: 'http://localhost/events',
      mode: 'navigate',
      method: 'GET',
      headers: new Headers(),
    } as Request;

    const offlineResponse = new Response('<html>Offline Page</html>');

    const mockCaches = {
      match: vi
        .fn()
        .mockResolvedValueOnce(undefined) // No cache for /events
        .mockResolvedValueOnce(offlineResponse), // Return offline.html from cache
    };

    global.caches = mockCaches as unknown as CacheStorage;
    global.fetch = vi.fn().mockRejectedValue(new Error('Network error'));

    const { handleFetch } = await import('./serviceWorker');
    const response = await handleFetch(mockRequest);

    expect(response).toBe(offlineResponse);
  });

  it('should_cacheOfflinePage_when_serviceWorkerInstalls', async () => {
    const mockCache = {
      addAll: vi.fn().mockResolvedValue(undefined),
    };

    const mockCaches = {
      open: vi.fn().mockResolvedValue(mockCache),
    };

    global.caches = mockCaches as unknown as CacheStorage;

    const { getCacheName, cacheAssets } = await import('./serviceWorker');
    const cacheName = getCacheName();
    await cacheAssets(cacheName);

    expect(mockCache.addAll).toHaveBeenCalledWith(expect.arrayContaining(['/offline.html']));
  });
});
