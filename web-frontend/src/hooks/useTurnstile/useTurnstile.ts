/**
 * useTurnstile hook — Story 10.31 (AC7)
 *
 * Loads the Cloudflare Turnstile invisible widget from CDN (no npm package).
 * When disabled (features.turnstile=false) getToken() returns null immediately.
 */

import { useRef, useEffect, useCallback } from 'react';
import { useConfig } from '@/contexts/useConfig';

declare global {
  interface Window {
    turnstile: {
      render: (el: HTMLElement, opts: object) => string;
      execute: (widgetId: string) => void;
      reset: (widgetId: string) => void;
    };
  }
}

export interface UseTurnstileReturn {
  getToken: () => Promise<string | null>;
  resetWidget: () => void;
  widgetRef: React.RefObject<HTMLDivElement | null>;
}

const TURNSTILE_SCRIPT_URL =
  'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
const TURNSTILE_SCRIPT_ID = 'cf-turnstile-script';

/**
 * Injects the Cloudflare Turnstile script once per page load.
 * Returns a Promise that resolves when the script is ready.
 */
function loadTurnstileScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (document.getElementById(TURNSTILE_SCRIPT_ID)) {
      // Already injected — resolve immediately if window.turnstile is present,
      // or wait for onload event via the existing script element.
      if (window.turnstile) {
        resolve();
      } else {
        const existing = document.getElementById(TURNSTILE_SCRIPT_ID) as HTMLScriptElement | null;
        if (existing) {
          existing.addEventListener('load', () => resolve());
          existing.addEventListener('error', reject);
        } else {
          resolve();
        }
      }
      return;
    }

    const script = document.createElement('script');
    script.id = TURNSTILE_SCRIPT_ID;
    script.src = TURNSTILE_SCRIPT_URL;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

export function useTurnstile(): UseTurnstileReturn {
  const config = useConfig();
  const enabled = config.features.turnstile;
  const siteKey = config.turnstile?.siteKey ?? '';

  const widgetRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<string | null>(null);
  // Pending resolve callbacks for getToken() calls during execute
  const pendingResolversRef = useRef<Array<(token: string) => void>>([]);

  useEffect(() => {
    if (!enabled || !widgetRef.current) return;

    let cancelled = false;

    loadTurnstileScript()
      .then(() => {
        if (cancelled || !widgetRef.current) return;

        widgetIdRef.current = window.turnstile.render(widgetRef.current, {
          sitekey: siteKey,
          size: 'invisible',
          callback: (token: string) => {
            // Resolve all pending getToken() promises
            const resolvers = pendingResolversRef.current.splice(0);
            resolvers.forEach((resolve) => resolve(token));
          },
        });
      })
      .catch((err) => {
        console.error('[useTurnstile] Failed to load Turnstile script:', err);
      });

    return () => {
      cancelled = true;
    };
  }, [enabled, siteKey]);

  const getToken = useCallback((): Promise<string | null> => {
    if (!enabled) {
      return Promise.resolve(null);
    }

    return new Promise<string | null>((resolve) => {
      if (!widgetIdRef.current) {
        // Widget not yet rendered — resolve null (fail-open on frontend)
        resolve(null);
        return;
      }

      pendingResolversRef.current.push(resolve as (token: string) => void);
      window.turnstile.execute(widgetIdRef.current);
    });
  }, [enabled]);

  const resetWidget = useCallback(() => {
    if (enabled && widgetIdRef.current) {
      window.turnstile.reset(widgetIdRef.current);
    }
  }, [enabled]);

  return { getToken, resetWidget, widgetRef };
}
