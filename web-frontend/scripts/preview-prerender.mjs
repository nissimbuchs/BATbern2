/**
 * Local preview that emulates the production CloudFront routing for the prerendered
 * (SSG) site, so `/`, `/privacy`, etc. behave exactly as they will in prod.
 *
 * Plain `vite preview` only serves files by their literal path (e.g. `/privacy/`),
 * because the `/ -> /home/index.html` and `/privacy -> /privacy/index.html` rewrites are
 * done by the CloudFront RouterFunction (infrastructure/lib/stacks/frontend-stack.ts).
 * This script reproduces that function as preview middleware.
 *
 * Usage:  npm run build:prerender && npm run preview:prerender
 *         (or: node scripts/preview-prerender.mjs)
 */

import { preview } from 'vite';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const PORT = 4173;

// Keep in sync with the CloudFront RouterFunction + scripts/prerender.mjs.
const PRERENDERED = ['/privacy', '/about', '/support'];

const server = await preview({
  root: projectRoot,
  preview: { port: PORT, strictPort: true },
  plugins: [
    {
      name: 'cloudfront-router-emulation',
      configurePreviewServer(s) {
        // Registered before Vite's static handler so the rewrite happens first —
        // mirrors the VIEWER_REQUEST CloudFront function.
        s.middlewares.use((req, _res, next) => {
          const [path, query] = (req.url || '/').split('?');
          const bare = path.replace(/\/$/, ''); // '/privacy/' -> '/privacy', '/' -> ''
          let uri = path;

          if (path === '/' || path === '/index.html') {
            uri = '/home/index.html'; // homepage loading shell
          } else if (PRERENDERED.includes(bare)) {
            uri = `${bare}/index.html`; // route-specific prerendered HTML
          } else if (!path.includes('.')) {
            uri = '/index.html'; // neutral SPA fallback (admin/archive/events/...)
          } else if (path.endsWith('/')) {
            uri = `${path}index.html`;
          }

          req.url = uri + (query ? `?${query}` : '');
          next();
        });
      },
    },
  ],
});

server.printUrls();
console.log(
  '\n[preview:prerender] CloudFront routing emulated. Try:\n' +
    `  http://localhost:${PORT}/          → prerendered homepage shell (nav + logo spinner)\n` +
    `  http://localhost:${PORT}/privacy   → prerendered static page\n` +
    `  http://localhost:${PORT}/about , /support\n` +
    `  http://localhost:${PORT}/archive   → neutral CSR shell (not prerendered)\n` +
    '  View source to confirm real content is present BEFORE any JS runs.\n' +
    '  (Ctrl-C to stop.)'
);
