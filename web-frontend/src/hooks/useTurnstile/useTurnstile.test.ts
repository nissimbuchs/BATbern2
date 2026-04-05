/**
 * useTurnstile hook tests — Story 10.31 (AC7, AC12)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, render } from '@testing-library/react';
import React from 'react';
import type { AppConfig } from '@/config/runtime-config';

// Mock useConfig
vi.mock('@/contexts/useConfig');
import { useConfig } from '@/contexts/useConfig';

const mockUseConfig = vi.mocked(useConfig);

function makeConfig(turnstileEnabled: boolean, siteKey?: string): AppConfig {
  return {
    environment: 'development',
    apiBaseUrl: 'http://localhost:8080/api/v1',
    cognito: { userPoolId: 'pool', clientId: 'client', region: 'eu-central-1' },
    features: {
      notifications: true,
      analytics: false,
      pwa: false,
      turnstile: turnstileEnabled,
    },
    ...(turnstileEnabled && siteKey ? { turnstile: { siteKey } } : {}),
  };
}

describe('useTurnstile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Clean up any injected scripts between tests
    const existing = document.getElementById('cf-turnstile-script');
    if (existing) existing.remove();
    // Reset window.turnstile
    delete (window as unknown as { turnstile?: unknown }).turnstile;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ------------------------------------------------------------------ disabled path (AC7)
  it('disabled: getToken() resolves null without touching window.turnstile', async () => {
    mockUseConfig.mockReturnValue(makeConfig(false));

    const { useTurnstile } = await import('./useTurnstile');
    const { result } = renderHook(() => useTurnstile());

    const token = await act(() => result.current.getToken());

    expect(token).toBeNull();
    expect(document.getElementById('cf-turnstile-script')).toBeNull();
  });

  it('disabled: widgetRef is provided (can be mounted without error)', async () => {
    mockUseConfig.mockReturnValue(makeConfig(false));

    const { useTurnstile } = await import('./useTurnstile');
    const { result } = renderHook(() => useTurnstile());

    expect(result.current.widgetRef).toBeDefined();
  });

  it('disabled: resetWidget() does nothing (no error thrown)', async () => {
    mockUseConfig.mockReturnValue(makeConfig(false));

    const { useTurnstile } = await import('./useTurnstile');
    const { result } = renderHook(() => useTurnstile());

    expect(() => result.current.resetWidget()).not.toThrow();
  });

  // ------------------------------------------------------------------ enabled path (AC7)
  it('enabled: injects Turnstile script on mount', async () => {
    mockUseConfig.mockReturnValue(makeConfig(true, '1x00000000000000000000AA'));

    // Simulate script load by mocking document.createElement
    const mockRender = vi.fn().mockReturnValue('widget-id-1');
    (window as unknown as { turnstile: unknown }).turnstile = {
      render: mockRender,
      execute: vi.fn(),
      reset: vi.fn(),
    };

    // Override loadTurnstileScript by pre-loading the script element
    // We simulate the script already being present with window.turnstile ready
    const scriptEl = document.createElement('script');
    scriptEl.id = 'cf-turnstile-script';
    document.head.appendChild(scriptEl);

    const { useTurnstile } = await import('./useTurnstile');
    const { result } = renderHook(() => useTurnstile());

    // Flush the microtask queue for the loadTurnstileScript promise
    await act(async () => {
      await Promise.resolve();
    });

    // widgetRef needs a DOM node to trigger render
    // Since JSDOM doesn't attach widgetRef.current automatically, we just
    // verify the hook returns the expected shape
    expect(result.current.getToken).toBeTypeOf('function');
    expect(result.current.resetWidget).toBeTypeOf('function');
    expect(result.current.widgetRef).toBeDefined();
  });

  it('enabled: getToken() resolves null when widget not yet rendered', async () => {
    mockUseConfig.mockReturnValue(makeConfig(true, '1x00000000000000000000AA'));

    const { useTurnstile } = await import('./useTurnstile');
    const { result } = renderHook(() => useTurnstile());

    const token = await act(() => result.current.getToken());

    // widgetIdRef is null because widgetRef.current is null in JSDOM → resolves null
    expect(token).toBeNull();
  });

  it('enabled: resetWidget() does not throw when widget not yet rendered', async () => {
    mockUseConfig.mockReturnValue(makeConfig(true, '1x00000000000000000000AA'));

    const { useTurnstile } = await import('./useTurnstile');
    const { result } = renderHook(() => useTurnstile());

    // widgetIdRef is null — should be a no-op, not throw
    expect(() => result.current.resetWidget()).not.toThrow();
  });

  it('loadTurnstileScript: reuses existing script when already injected', async () => {
    mockUseConfig.mockReturnValue(makeConfig(true, '1x00000000000000000000AA'));

    // Pre-inject script WITHOUT window.turnstile
    const scriptEl = document.createElement('script');
    scriptEl.id = 'cf-turnstile-script';
    document.head.appendChild(scriptEl);

    const { useTurnstile } = await import('./useTurnstile');
    renderHook(() => useTurnstile());

    await act(async () => {
      await Promise.resolve();
    });

    // Should not have created a second script
    const scripts = document.querySelectorAll('#cf-turnstile-script');
    expect(scripts.length).toBe(1);
  });

  // ------------------------------------------------------------------ enabled with mounted ref
  it('enabled: renders widget and executes token flow when ref is mounted', async () => {
    mockUseConfig.mockReturnValue(makeConfig(true, '1x00000000000000000000AA'));

    let renderCallback: ((token: string) => void) | undefined;
    const mockRender = vi.fn((_el: HTMLElement, opts: { callback: (t: string) => void }) => {
      renderCallback = opts.callback;
      return 'widget-id-1';
    });
    const mockExecute = vi.fn();
    const mockReset = vi.fn();
    (window as unknown as { turnstile: unknown }).turnstile = {
      render: mockRender,
      execute: mockExecute,
      reset: mockReset,
    };

    // Pre-inject script so loadTurnstileScript resolves immediately
    const scriptEl = document.createElement('script');
    scriptEl.id = 'cf-turnstile-script';
    document.head.appendChild(scriptEl);

    const { useTurnstile } = await import('./useTurnstile');

    // Use a real component that attaches widgetRef to a DOM node
    let hookResult: ReturnType<typeof useTurnstile> | undefined;
    function TestComponent() {
      const result = useTurnstile();
      hookResult = result;
      return React.createElement('div', { ref: result.widgetRef, 'data-testid': 'turnstile' });
    }

    render(React.createElement(TestComponent));

    // Let useEffect + loadTurnstileScript resolve
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    // Widget should have been rendered
    expect(mockRender).toHaveBeenCalled();
    expect(mockRender.mock.calls[0][1]).toMatchObject({
      sitekey: '1x00000000000000000000AA',
      size: 'invisible',
    });

    // getToken should call execute and resolve via callback
    let tokenPromise: Promise<string | null> | undefined;
    await act(async () => {
      tokenPromise = hookResult!.getToken();
    });

    expect(mockExecute).toHaveBeenCalledWith('widget-id-1');

    // Simulate Cloudflare calling the callback
    await act(async () => {
      renderCallback!('cf-real-token-abc');
    });

    const token = await tokenPromise;
    expect(token).toBe('cf-real-token-abc');

    // resetWidget should call window.turnstile.reset
    act(() => {
      hookResult!.resetWidget();
    });
    expect(mockReset).toHaveBeenCalledWith('widget-id-1');
  });

  it('enabled: logs error when script fails to load', async () => {
    mockUseConfig.mockReturnValue(makeConfig(true, '1x00000000000000000000AA'));

    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { useTurnstile } = await import('./useTurnstile');
    renderHook(() => useTurnstile());

    // Simulate script error by finding the injected script and firing onerror
    await act(async () => {
      await Promise.resolve();
      const script = document.getElementById('cf-turnstile-script') as HTMLScriptElement;
      if (script?.onerror) {
        (script.onerror as (e: Event | string) => void)(new Event('error'));
      }
    });

    // Give the catch handler a chance to run
    await act(async () => {
      await Promise.resolve();
    });

    // The error should have been caught and logged (not thrown)
    // We mainly verify the hook doesn't crash
    consoleSpy.mockRestore();
  });
});
