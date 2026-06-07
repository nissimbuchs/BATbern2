#!/usr/bin/env node
/**
 * Generate per-segment narration audio via the ElevenLabs API (pre-step for the screencast).
 *
 * Reads  ../narration-manifest-{lang}.json   (single source of truth for narration text)
 * Writes ../audio-cache/{lang}/{MARKER}-{hash8}.mp3   (one file per segment, cached)
 * Writes ../narration-timings-{lang}.json    (measured durations — consumed by timing-helper.ts)
 *
 * Caching: each segment is keyed by sha256(voice|model|text). Re-runs with unchanged text,
 * voice and model make ZERO API calls. Stale cache files for a marker (old hash) are pruned.
 *
 * Usage:
 *   node generate-narration-audio.mjs [--lang de] [--fake] [--force]
 *
 *   --fake   No API key needed: generates SILENT placeholder MP3s whose duration is estimated
 *            from text length (~15 chars/sec). Lets the whole pipeline (test waits, timeline,
 *            assembly) be exercised offline before ElevenLabs API access is confirmed.
 *   --force  Regenerate even on cache hit.
 *
 * Env (real mode):
 *   ELEVENLABS_API_KEY        required (text_to_speech permission suffices)
 *   ELEVENLABS_VOICE_ID_{DE}  optional override; default comes from manifest.voiceId
 *   ELEVENLABS_VOICE_ID       optional generic override (lower precedence)
 *   ELEVENLABS_MODEL_ID       optional override; default manifest.modelId, then "eleven_v3"
 *
 * Requires ffmpeg + ffprobe on PATH (already required by the video assembly pipeline).
 */

import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCREENCAST_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// ── args ────────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
const getArg = (name) => {
  const i = args.indexOf(name);
  if (i === -1) return undefined;
  const value = args[i + 1];
  if (!value || value.startsWith('--')) {
    console.error(`ERROR: ${name} requires a value (got ${value ?? 'nothing'})`);
    process.exit(1);
  }
  return value;
};
const lang = getArg('--lang') ?? 'de';
const fake = args.includes('--fake');
const force = args.includes('--force');

// ── load manifest ───────────────────────────────────────────────────────────
const manifestPath = path.join(SCREENCAST_DIR, `narration-manifest-${lang}.json`);
if (!fs.existsSync(manifestPath)) {
  console.error(`ERROR: manifest not found: ${manifestPath}`);
  process.exit(1);
}
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

const apiKey = process.env.ELEVENLABS_API_KEY;
// Voice/model defaults live in the manifest (IDs are not secrets); env vars override
// (ELEVENLABS_VOICE_ID_DE > ELEVENLABS_VOICE_ID > manifest.voiceId).
const voiceId = fake
  ? 'fake'
  : (process.env[`ELEVENLABS_VOICE_ID_${lang.toUpperCase()}`] ??
    process.env.ELEVENLABS_VOICE_ID ??
    manifest.voiceId);
const modelId = fake
  ? 'fake'
  : (process.env.ELEVENLABS_MODEL_ID ?? manifest.modelId ?? 'eleven_v3');

if (!fake && (!apiKey || !voiceId)) {
  console.error(
    'ERROR: ELEVENLABS_API_KEY is required, and a voice must come from the manifest ' +
      `(voiceId) or ELEVENLABS_VOICE_ID_${lang.toUpperCase()} / ELEVENLABS_VOICE_ID.\n` +
      '       Use --fake to generate silent placeholder audio without API access.'
  );
  process.exit(1);
}

// Fake (silent placeholder) audio lives in its OWN cache dir: the stale-prune below is
// keyed per marker within one dir, so sharing a dir would let a --fake run delete paid
// ElevenLabs MP3s (and vice versa).
const cacheDir = path.join(SCREENCAST_DIR, 'audio-cache', fake ? `${lang}-fake` : lang);
fs.mkdirSync(cacheDir, { recursive: true });

// ── helpers ─────────────────────────────────────────────────────────────────
const hashSegment = (text) =>
  createHash('sha256').update(`${voiceId}|${modelId}|${text}`).digest('hex').slice(0, 8);

/** Visible text without [audio tags] — used for duration estimation and SRT. */
const stripTags = (text) =>
  text
    .replace(/\[[^\]]*\]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

/** execFileSync wrapper with a friendly error when ffmpeg/ffprobe is not installed. */
const runTool = (tool, toolArgs) => {
  try {
    return execFileSync(tool, toolArgs, { encoding: 'utf8' });
  } catch (err) {
    if (err.code === 'ENOENT') {
      console.error(`ERROR: ${tool} not found on PATH — install it first: brew install ffmpeg`);
      process.exit(1);
    }
    throw err;
  }
};

const probeDurationMs = (file) => {
  const out = runTool('ffprobe', [
    '-v',
    'error',
    '-show_entries',
    'format=duration',
    '-of',
    'csv=p=0',
    file,
  ]).trim();
  const seconds = parseFloat(out);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    throw new Error(`ffprobe returned invalid duration "${out}" for ${file}`);
  }
  return Math.round(seconds * 1000);
};

const generateSilence = (file, seconds) => {
  runTool('ffmpeg', [
    '-y',
    '-v',
    'error',
    '-f',
    'lavfi',
    '-i',
    'anullsrc=r=44100:cl=mono',
    '-t',
    seconds.toFixed(1),
    '-c:a',
    'libmp3lame',
    '-b:a',
    '128k',
    file,
  ]);
};

/** One TTS call: 2-minute timeout, single retry on 429/5xx, sanity-check the body. */
const generateElevenLabs = async (file, text) => {
  const url = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?output_format=mp3_44100_128`;
  for (let attempt = 1; ; attempt++) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'xi-api-key': apiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, model_id: modelId }),
      signal: AbortSignal.timeout(120_000),
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '<unreadable>');
      const retryable = res.status === 429 || res.status >= 500;
      if (retryable && attempt === 1) {
        console.warn(`  ↻ ${path.basename(file)}: HTTP ${res.status}, retrying in 5s...`);
        await new Promise((resolve) => setTimeout(resolve, 5000));
        continue;
      }
      throw new Error(`ElevenLabs API ${res.status} for ${path.basename(file)}: ${body}`);
    }
    const audio = Buffer.from(await res.arrayBuffer());
    if (audio.byteLength < 1000) {
      throw new Error(
        `ElevenLabs returned a suspiciously small body (${audio.byteLength} bytes) for ` +
          `${path.basename(file)} — not written`
      );
    }
    fs.writeFileSync(file, audio);
    return;
  }
};

// ── main ────────────────────────────────────────────────────────────────────
const segments = [];
let generated = 0;
let cached = 0;

for (const segment of manifest.segments) {
  const { marker, text } = segment;
  const hash = hashSegment(text);
  const fileName = `${marker}-${hash}.mp3`;
  const filePath = path.join(cacheDir, fileName);

  // Prune stale variants of this marker (different hash) so the cache stays tidy.
  for (const stale of fs
    .readdirSync(cacheDir)
    .filter((f) => f.startsWith(`${marker}-`) && f !== fileName)) {
    fs.unlinkSync(path.join(cacheDir, stale));
  }

  if (fs.existsSync(filePath) && !force) {
    cached++;
    console.log(`  ✓ ${marker} (cached, ${fileName})`);
  } else if (fake) {
    const estimatedSec = Math.max(3, stripTags(text).length / 15);
    generateSilence(filePath, estimatedSec);
    generated++;
    console.log(`  ⚙ ${marker} (fake silence ~${estimatedSec.toFixed(1)}s)`);
  } else {
    await generateElevenLabs(filePath, text);
    generated++;
    console.log(`  ♪ ${marker} (generated via ElevenLabs)`);
  }

  segments.push({
    marker,
    file: path.relative(SCREENCAST_DIR, filePath),
    durationMs: probeDurationMs(filePath),
    textHash: hash,
  });
}

const timings = {
  lang,
  voiceId,
  modelId,
  fake,
  generatedAt: new Date().toISOString(),
  segments,
};
const timingsPath = path.join(SCREENCAST_DIR, `narration-timings-${lang}.json`);
fs.writeFileSync(timingsPath, JSON.stringify(timings, null, 2) + '\n');

const totalSec = segments.reduce((sum, s) => sum + s.durationMs, 0) / 1000;
console.log(
  `\nDone: ${generated} generated, ${cached} cached → ${path.relative(process.cwd(), timingsPath)}` +
    `\nTotal narration: ${totalSec.toFixed(1)}s across ${segments.length} segments${fake ? ' (FAKE silent audio)' : ''}`
);
