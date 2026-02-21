#!/usr/bin/env node
/**
 * Captures the BATbernLoader spinner as an animated GIF or APNG using Playwright + ffmpeg.
 * Output format is determined by file extension: .gif → GIF, .apng/.png → APNG
 *
 * ⚠️  PowerPoint ignores GIF frame delay metadata — use .apng for correct speed in PPT.
 *
 * Usage (from repo root — no extra setup needed):
 *   node scripts/capture-spinner-gif.mjs [output.apng] [size] [speed] [fps] [scale]
 *
 * Defaults:
 *   output : batbern-spinner.apng  (written to current directory)
 *   size   : 400                   (px, square — logical size)
 *   speed  : normal                (slow | normal | fast)
 *   fps    : 15
 *   scale  : 2                     (deviceScaleFactor: 1=normal, 2=Retina 2×, 3=3×)
 *
 * Prerequisites:
 *   - npx playwright install chromium   (one-time)
 *   - ffmpeg on PATH
 *
 * Examples:
 *   node scripts/capture-spinner-gif.mjs
 *   node scripts/capture-spinner-gif.mjs spinner-big.gif 600 slow 25 3
 */

// Playwright lives in web-frontend/node_modules — resolve it from there
import { createRequire } from 'module';
const requireFromFrontend = createRequire(
  new URL('../web-frontend/package.json', import.meta.url)
);
const { chromium } = requireFromFrontend('playwright');
import { execSync } from 'child_process';
import { mkdirSync, rmSync } from 'fs';
import { join, resolve } from 'path';
import { tmpdir } from 'os';

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------
const OUTPUT_FILE = resolve(process.argv[2] ?? 'batbern-spinner.apng');
const IS_APNG     = /\.(apng|png)$/i.test(OUTPUT_FILE);
const IS_MP4      = /\.mp4$/i.test(OUTPUT_FILE);
const BG_COLOR    = process.argv[7] ?? 'white'; // background for MP4 (no transparency)
const SIZE        = Number(process.argv[3] ?? 400);
const SPEED       = process.argv[4] ?? 'normal';   // slow | normal | fast
const FPS         = Number(process.argv[5] ?? 15); // ≤15 for PowerPoint compatibility
const SCALE       = Number(process.argv[6] ?? 2);  // deviceScaleFactor (Retina multiplier)

const SPEED_TOTALS = { slow: 4800, normal: 3000, fast: 1800 }; // ms
const DURATION_MS  = SPEED_TOTALS[SPEED] ?? 3000;
const FRAMES       = Math.ceil((DURATION_MS / 1000) * FPS);

// ---------------------------------------------------------------------------
// Inline HTML — no dev server needed
// ---------------------------------------------------------------------------
function buildHtml(size, speed) {
  const total =
    speed === 'slow' ? '4.8s' : speed === 'fast' ? '1.8s' : '3.0s';

  return /* html */ `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body {
      width: ${size}px; height: ${size}px;
      background: transparent;
      display: flex; align-items: center; justify-content: center;
    }
  </style>
</head>
<body>
  <svg viewBox="0 -1 100 100" width="${size}" height="${size}"
       aria-hidden="true" xmlns="http://www.w3.org/2000/svg">
    <style>
      .bat-arrow { transform-box: fill-box; }
      .bat-arrow-1 {
        transform-origin: 50% 87%;
        animation: bat-spin-double ${total} linear infinite;
      }
      .bat-arrow-2 {
        transform-origin: 50% 16%;
        animation: bat-spin-single ${total} linear infinite;
      }
      @keyframes bat-spin-double {
        0%      { transform: rotate(0deg); }
        33.33%  { transform: rotate(0deg); animation-timing-function: ease-in-out; }
        100%    { transform: rotate(720deg); }
      }
      @keyframes bat-spin-single {
        0%      { transform: rotate(0deg); }
        33.33%  { transform: rotate(0deg); animation-timing-function: ease-in-out; }
        100%    { transform: rotate(360deg); }
      }
    </style>
    <g class="bat-arrow bat-arrow-1" fill="#3498DB">
      <path d="M35.822,21.061c8.877,0.261,16.278,3.112,22.344,9.105c1.02,1.007,1.862,1.383,3.196,0.678
        c1.135-0.6,2.4-0.948,3.584-1.46c1.17-0.506,1.687-0.421,1.453,1.086c-0.744,4.796-1.39,9.607-2.081,14.411
        c-0.306,2.128-0.647,4.251-0.936,6.381c-0.143,1.055-0.554,1.309-1.425,0.62c-5.598-4.425-11.193-8.855-16.804-13.262
        c-1.002-0.787-0.533-1.142,0.32-1.479c0.972-0.384,1.941-0.774,2.907-1.172c0.489-0.202,1.214-0.249,1.232-0.898
        c0.014-0.504-0.622-0.706-1.017-0.981c-7.132-4.97-17.108-5.073-24.534-0.159c-6.465,4.279-9.702,10.438-10.144,18.109
        c-0.18,3.131-1.942,5.125-4.643,5.087c-2.693-0.038-4.588-2.316-4.527-5.442c0.299-15.337,12.257-28.445,27.624-30.27
        C33.651,21.262,34.936,21.151,35.822,21.061z"/>
    </g>
    <g class="bat-arrow bat-arrow-2" fill="#1a6fa8">
      <path d="M63.149,76.87c-7.916-0.206-15.29-3.125-21.373-9.075c-1.033-1.01-1.879-1.349-3.197-0.648
        c-1.079,0.573-2.291,0.888-3.415,1.384c-1.282,0.565-1.851,0.323-1.622-1.184c0.665-4.373,1.302-8.749,1.945-13.125
        c0.33-2.248,0.654-4.498,0.97-6.748c0.296-2.105,0.518-2.219,2.137-0.944c5.268,4.146,10.511,8.324,15.794,12.452
        c1.139,0.89,1.436,1.475-0.233,1.994c-0.935,0.291-1.812,0.768-2.741,1.083c-1.481,0.503-1.182,1.077-0.141,1.764
        c5.296,3.493,11.052,4.59,17.262,3.319c9.38-1.92,17.277-10.642,17.434-20.875c0.05-3.239,1.767-5.249,4.389-5.391
        c2.683-0.145,4.711,1.851,4.785,4.709c0.337,13.023-8.736,25.494-21.634,29.705C70.331,76.326,67.061,76.839,63.149,76.87z"/>
    </g>
  </svg>
</body>
</html>`;
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------
const framesDir = join(tmpdir(), `batbern-spinner-frames-${Date.now()}`);
mkdirSync(framesDir, { recursive: true });

const PIXEL_SIZE = SIZE * SCALE;
console.log(`⚙  Size: ${SIZE}px  Scale: ${SCALE}×  Output: ${PIXEL_SIZE}×${PIXEL_SIZE}px  Speed: ${SPEED}  FPS: ${FPS}  Frames: ${FRAMES}`);
console.log(`📁 Frames dir: ${framesDir}`);

const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: { width: SIZE, height: SIZE },
  deviceScaleFactor: SCALE,
});

// Transparent background
await page.addInitScript(() => {
  document.documentElement.style.background = 'transparent';
});

await page.setContent(buildHtml(SIZE, SPEED), { waitUntil: 'domcontentloaded' });

// Wait for CSS animations to register, then pause them all at t=0
await page.waitForTimeout(200);
await page.evaluate(() => {
  document.getAnimations().forEach(a => { a.pause(); a.currentTime = 0; });
});

console.log(`📸 Capturing ${FRAMES} frames via Web Animations API seek…`);

for (let i = 0; i < FRAMES; i++) {
  // Seek every animation to the exact frame timestamp
  const frameTimeMs = (i / FRAMES) * DURATION_MS;
  await page.evaluate((t) => {
    document.getAnimations().forEach(a => { a.currentTime = t; });
  }, frameTimeMs);

  const framePath = join(framesDir, `frame-${String(i).padStart(4, '0')}.png`);
  await page.screenshot({ path: framePath, omitBackground: true });

  if ((i + 1) % 10 === 0) process.stdout.write(`  ${i + 1}/${FRAMES}\r`);
}

await browser.close();
console.log(`\n✅ Frames captured`);

// ---------------------------------------------------------------------------
// ffmpeg: PNG frames → palette → GIF
// ---------------------------------------------------------------------------
const palette = join(framesDir, 'palette.png');
const framePattern = join(framesDir, 'frame-%04d.png');

if (!IS_APNG && !IS_MP4) {
  console.log(`🎨 Building palette…`);
  execSync(
    `ffmpeg -y -framerate ${FPS} -i "${framePattern}" \
     -vf "palettegen=max_colors=256:reserve_transparent=1:transparency_color=ffffff" \
     "${palette}"`,
    { stdio: 'inherit' }
  );
}

if (IS_MP4) {
  console.log(`🎬 Assembling MP4 (background: ${BG_COLOR})…`);
  // flatten transparent PNGs onto BG_COLOR, encode H.264 (PPT-compatible)
  execSync(
    `ffmpeg -y -framerate ${FPS} -i "${framePattern}" \
     -vf "format=rgba,pad=ceil(iw/2)*2:ceil(ih/2)*2:0:0:color=${BG_COLOR},format=yuv420p" \
     -c:v libx264 -preset slow -crf 18 -r ${FPS} \
     -movflags +faststart "${OUTPUT_FILE}"`,
    { stdio: 'inherit' }
  );
  console.log(`\n💡 In PowerPoint: Insert → Media → Video → From file`);
  console.log(`   Then: Playback tab → Start: Automatically, ✓ Loop until Stopped, ✓ Hide While Not Playing`);
} else if (IS_APNG) {
  console.log(`🎬 Assembling APNG…`);
  execSync(
    `ffmpeg -y -framerate ${FPS} -i "${framePattern}" \
     -plays 0 -r ${FPS} "${OUTPUT_FILE}"`,
    { stdio: 'inherit' }
  );
} else {
  if (FPS > 15) {
    console.warn(`⚠️  FPS=${FPS} — PowerPoint may ignore short GIF frame delays.`);
  }
  console.log(`🎬 Assembling GIF…`);
  execSync(
    `ffmpeg -y -framerate ${FPS} -i "${framePattern}" -i "${palette}" \
     -lavfi "paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
     -r ${FPS} "${OUTPUT_FILE}"`,
    { stdio: 'inherit' }
  );
}

// Cleanup
rmSync(framesDir, { recursive: true, force: true });

console.log(`\n🎉 Done! → ${OUTPUT_FILE}`);
console.log(`   Insert into PowerPoint: Insert → Pictures → Picture from File`);
console.log(`   GIF loops automatically during slideshow.`);
