/**
 * Build-time prerender (SSG) for the public, MUI-free routes.
 *
 * WHY a Playwright crawl (not vite-react-ssg / RR7 framework mode): the chosen tool is
 * version-immune — it operates on the already-built `dist` artifact and is indifferent to
 * our bleeding-edge Vite 8 / React Router 7 / helmet-async 2 stack. See
 * docs/plans/public-homepage-prerender.md §3 for the full decision record.
 *
 * HOW it works:
 *   1. Serve the built `dist/` via `vite preview` (SPA fallback → index.html).
 *   2. Launch headless Chromium; for each public route:
 *      - stub the backend (`/api/v1/config` + 404 everything else under `/api`) so the
 *        app boots with NO backend and bakes NO environment-specific data into the HTML
 *        ("build once, deploy everywhere" preserved),
 *      - navigate, wait for real content in #root,
 *      - snapshot the rendered DOM.
 *   3. AFTER crawling every route, write each snapshot to `dist/<route>/index.html`
 *      (crawl-all-then-write so a freshly-written file can't change a later route's
 *      SPA-fallback mid-run).
 *
 * RENDER MODEL — paint-and-replace: at runtime the browser paints this static HTML at
 * first paint (the FCP/LCP win), then the app's JS boots and React `createRoot().render()`
 * mounts fresh and replaces it. No hydration ⇒ no hydration-mismatch risk across our
 * 10-locale i18n. The prerendered shell is the German default locale.
 *
 * GRACEFUL DEGRADATION: if Chromium can't launch (e.g. a CI image without Playwright
 * browsers), we log a warning and exit 0 — the build still ships a working CSR SPA;
 * prerender is a pure enhancement, never a hard build dependency.
 */

import { preview } from 'vite';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';
import { mkdir, writeFile } from 'node:fs/promises';

const __dirname = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(__dirname, '..');
const distDir = join(projectRoot, 'dist');

const PORT = 4185;

// The public routes to prerender.
//   - Static routes snapshot once #root has real content (default).
//   - The homepage `/` is data-driven; to honour "build once" we capture its branded
//     LOADING shell (nav + animated logo spinner + footer) by keeping the current-event
//     request PENDING during the crawl (`hangApi`) and waiting for the loading testid.
//     At runtime the real client fetches normally and swaps in the live event.
const ROUTES = [
  { path: '/privacy', expect: 'Datenschutz' },
  { path: '/support' },
  { path: '/about' },
  {
    path: '/',
    expect: 'homepage-loading',
    // Write to home/index.html (NOT dist/index.html) so the build's neutral empty shell
    // stays the SPA fallback for non-prerendered routes (admin/archive/events) — no
    // homepage-shell flash on those. The CloudFront function maps `/` → /home/index.html.
    out: 'home/index.html',
    hangApi: /\/api\/v1\/events\/current/,
    waitForSelector: '[data-testid="homepage-loading"]',
    waitUntil: 'commit',
  },
];

// A neutral, backend-free config so the app can boot during the crawl. Deliberately
// minimal — turnstile/analytics off, no real Cognito — because NONE of these values may
// end up baked into the static HTML (the public shell does not render config-derived
// content). The real values arrive client-side via the normal runtime-config fetch.
const STUB_CONFIG = {
  environment: 'production',
  apiBaseUrl: 'https://api.batbern.ch/api/v1',
  cognito: { userPoolId: 'prerender', clientId: 'prerender', region: 'eu-central-1' },
  features: { notifications: false, analytics: false, pwa: true, turnstile: false },
};

/** A route is considered "rendered" once #root holds real, non-trivial content. */
function rootHasContent() {
  const root = document.getElementById('root');
  return !!root && root.children.length > 0 && (root.textContent || '').trim().length > 50;
}

async function loadChromium() {
  try {
    const { chromium } = await import('@playwright/test');
    return await chromium.launch();
  } catch (err) {
    console.warn(
      `[prerender] Chromium unavailable — skipping prerender, shipping CSR-only build.\n` +
        `           (${err instanceof Error ? err.message : String(err)})`
    );
    return null;
  }
}

async function main() {
  const server = await preview({
    root: projectRoot,
    preview: { port: PORT, strictPort: true },
  });
  const base = `http://localhost:${PORT}`;

  const browser = await loadChromium();
  if (!browser) {
    await server.httpServer.close();
    return; // graceful no-op
  }

  const snapshots = [];
  let hadError = false;

  try {
    for (const route of ROUTES) {
      const page = await browser.newPage();
      // Only uncaught JS exceptions are hard failures. Browser console errors during the
      // crawl are dominated by the deliberately-404'd `/api/**` network stubs (and prod
      // build strips app console.* via terser anyway), so they are advisory noise, not
      // a broken-prerender signal.
      const pageErrors = [];
      const consoleNoise = [];
      // Track the same-origin JS chunks the route actually loads. The route's lazy
      // component chunk (e.g. PrivacyPage) is NOT in Vite's injected preloads, so without
      // this it would only be fetched once React mounts → a content→spinner→content flash
      // after first paint. We inject <link rel="modulepreload"> for these so the chunk is
      // cached by mount time and React.lazy resolves without painting the Suspense fallback.
      const loadedJs = new Set();
      page.on('request', (req) => {
        if (req.resourceType() !== 'script') return;
        try {
          const u = new URL(req.url());
          if (`${u.protocol}//${u.host}` === base) loadedJs.add(u.pathname);
        } catch {
          /* ignore non-URL */
        }
      });
      page.on('pageerror', (e) => pageErrors.push(e.message));
      page.on('console', (msg) => {
        if (msg.type() === 'error' && !/Failed to load resource/i.test(msg.text())) {
          consoleNoise.push(msg.text());
        }
      });

      // Backend-free boot. Playwright invokes the LAST-registered matching handler first,
      // so register the generic catch-all FIRST and the specific overrides AFTER it.
      await page.route('**/api/**', (r) =>
        r.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
      );
      await page.route('**/api/v1/config', (r) =>
        r.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(STUB_CONFIG),
        })
      );
      if (route.hangApi) {
        // Never resolve → request stays pending → the query stays in its loading state,
        // so the snapshot captures the loading shell rather than an empty/error state.
        await page.route(route.hangApi, () => {});
      }

      await page.goto(base + route.path, { waitUntil: route.waitUntil ?? 'networkidle' });
      if (route.waitForSelector) {
        await page.waitForSelector(route.waitForSelector, { timeout: 20000 });
      } else {
        await page.waitForFunction(rootHasContent, null, { timeout: 20000 });
      }

      let html = await page.content();

      // Guardrail: a silently-broken prerender (empty #root, or missing expected content)
      // must fail the build — never ship a blank shell as if it were prerendered.
      if (/<div id="root">\s*<\/div>/.test(html)) {
        hadError = true;
        console.error(`[prerender] ${route.path}: #root is EMPTY in snapshot — broken prerender.`);
      }
      if (route.expect && !html.includes(route.expect)) {
        hadError = true;
        console.error(
          `[prerender] ${route.path}: expected marker "${route.expect}" missing from snapshot.`
        );
      }

      // Inject modulepreload for any same-origin JS chunk the route loaded that isn't
      // already referenced in the HTML (i.e. the lazy route chunk). Prevents the
      // post-first-paint Suspense-fallback flash on prerendered lazy routes.
      const toPreload = [...loadedJs].filter((p) => !html.includes(p));
      if (toPreload.length) {
        const links = toPreload
          .map((p) => `<link rel="modulepreload" href="${p}" crossorigin>`)
          .join('');
        html = html.replace('</head>', `${links}</head>`);
      }

      // out: explicit relative path (e.g. 'home/index.html'); else '<route>/index.html'.
      const out = route.out ?? `${route.path.replace(/^\//, '')}/index.html`;
      snapshots.push({ route: route.path, out, html });

      if (pageErrors.length) {
        hadError = true;
        console.error(
          `[prerender] ${route.path} threw uncaught errors:\n  - ${pageErrors.join('\n  - ')}`
        );
      } else {
        const note = consoleNoise.length ? ` (console warnings: ${consoleNoise.length})` : '';
        console.log(`[prerender] crawled ${route.path} (${html.length} bytes)${note}`);
      }

      await page.close();
    }
  } finally {
    await browser.close();
    await server.httpServer.close();
  }

  // Fail BEFORE writing anything if any snapshot was suspect — so a broken prerender never
  // lands in dist. The build's CSR dist (incl. the neutral dist/index.html) is left intact,
  // so callers that tolerate a non-zero exit (the deploy steps use `|| warn`) still ship a
  // working CSR site; strict callers (local dev / a dedicated CI check) see the failure.
  if (hadError) {
    console.error('[prerender] Completed with errors (see above) — NOT writing snapshots.');
    process.exit(1);
  }

  // Crawl-all-then-write: only now do we touch dist. Note we never overwrite the build's
  // neutral dist/index.html — it stays the SPA fallback for non-prerendered routes.
  for (const { out, html } of snapshots) {
    const outPath = join(distDir, out);
    await mkdir(dirname(outPath), { recursive: true });
    await writeFile(outPath, html, 'utf8');
    console.log(`[prerender] wrote ${outPath}`);
  }

  console.log(`[prerender] Done — prerendered ${snapshots.length} route(s).`);
}

main().catch((err) => {
  console.error('[prerender] Fatal:', err);
  process.exit(1);
});
