#!/usr/bin/env node

/**
 * Simple HTTP server to serve local logo files for batch import
 *
 * Usage:
 *   node scripts/dev/serve-logos.js
 *
 * Serves files from all archiv directories (1-57)
 *
 * Available at: http://localhost:8888/<filename>
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8888;
const PROJECT_ROOT = path.resolve(__dirname, '../../');
const ARCHIV_BASE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/archiv');

// Generate all archiv directory paths (1-57)
const LOGO_DIRS = [];
for (let i = 1; i <= 57; i++) {
  const dir = path.join(ARCHIV_BASE, String(i));
  if (fs.existsSync(dir)) {
    LOGO_DIRS.push(dir);
  }
}

// MIME type mapping
const MIME_TYPES = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
};

/**
 * Find file in logo directories
 */
function findLogoFile(filename) {
  for (const dir of LOGO_DIRS) {
    const filePath = path.join(dir, filename);
    if (fs.existsSync(filePath)) {
      return filePath;
    }
  }
  return null;
}

/**
 * Get MIME type from file extension
 */
function getMimeType(filename) {
  const ext = path.extname(filename).toLowerCase();
  return MIME_TYPES[ext] || 'application/octet-stream';
}

/**
 * Request handler
 */
const server = http.createServer((req, res) => {
  // Enable CORS for all requests
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  // Handle OPTIONS preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Only handle GET requests
  if (req.method !== 'GET') {
    res.writeHead(405, { 'Content-Type': 'text/plain' });
    res.end('Method Not Allowed');
    return;
  }

  // Extract filename from URL (remove leading slash)
  const filename = req.url.substring(1);

  // Handle root path
  if (!filename) {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(`
      <!DOCTYPE html>
      <html>
        <head><title>Logo Server</title></head>
        <body>
          <h1>BATbern Logo Server</h1>
          <p>Server is running on port ${PORT}</p>
          <p>Serving files from:</p>
          <ul>
            ${LOGO_DIRS.map(dir => `<li><code>${dir}</code></li>`).join('')}
          </ul>
          <p>Usage: <code>http://localhost:${PORT}/&lt;filename&gt;</code></p>
        </body>
      </html>
    `);
    return;
  }

  console.log(`[${new Date().toISOString()}] GET /${filename}`);

  // Find the file
  const filePath = findLogoFile(filename);

  if (!filePath) {
    console.log(`  → 404 Not Found`);
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('File not found');
    return;
  }

  // Read and serve the file
  fs.readFile(filePath, (err, data) => {
    if (err) {
      console.log(`  → 500 Error reading file:`, err.message);
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end('Error reading file');
      return;
    }

    const mimeType = getMimeType(filename);
    console.log(`  → 200 OK (${data.length} bytes, ${mimeType})`);

    res.writeHead(200, {
      'Content-Type': mimeType,
      'Content-Length': data.length,
      'Cache-Control': 'no-cache',
    });
    res.end(data);
  });
});

// Start server
server.listen(PORT, () => {
  console.log('='.repeat(60));
  console.log('BATbern Logo Server');
  console.log('='.repeat(60));
  console.log(`Server running at http://localhost:${PORT}/`);
  console.log('');
  console.log('Serving logos from:');
  LOGO_DIRS.forEach(dir => {
    const exists = fs.existsSync(dir);
    console.log(`  ${exists ? '✓' : '✗'} ${dir}`);
  });
  console.log('');
  console.log('Press Ctrl+C to stop');
  console.log('='.repeat(60));
});

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n\nShutting down server...');
  server.close(() => {
    console.log('Server stopped');
    process.exit(0);
  });
});
