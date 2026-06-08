#!/usr/bin/env node
/**
 * Assemble the final narration audio track + subtitles from a recorded screencast run.
 *
 * Inputs (all produced earlier in the pipeline):
 *   ../narration-manifest-{lang}.json   narration text (for subtitles)
 *   ../narration-timings-{lang}.json    per-segment MP3 paths + measured durations
 *   ../narration-timeline-{lang}.json   ACTUAL start offsets recorded during the test run
 *
 * Outputs:
 *   ../narration-{lang}.m4a             every segment placed at its recorded offset
 *   ../subtitles-{lang}.srt             chunked subtitles (audio tags stripped)
 *
 * The audio track is sample-accurate to the recorded video: mux with
 *   ffmpeg -i video.webm -i narration-de.m4a -i subtitles-de.srt -c:v libx264 -c:a aac \
 *          -c:s mov_text -map 0:v -map 1:a -map 2:s out.mp4
 *
 * Usage: node assemble-narration.mjs [--lang de]
 * Requires ffmpeg on PATH.
 */

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCREENCAST_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const langIdx = args.indexOf('--lang');
const lang = langIdx !== -1 && args[langIdx + 1] ? args[langIdx + 1] : 'de';

const readJson = (name) => {
  const file = path.join(SCREENCAST_DIR, name);
  if (!fs.existsSync(file)) {
    console.error(`ERROR: missing ${file}`);
    if (name.includes('timeline')) {
      console.error('       Run the screencast first: npm run test:e2e:screencast');
    } else if (name.includes('timings')) {
      console.error('       Generate narration first: npm run screencast:narration');
    }
    process.exit(1);
  }
  return JSON.parse(fs.readFileSync(file, 'utf8'));
};

const manifest = readJson(`narration-manifest-${lang}.json`);
const timings = readJson(`narration-timings-${lang}.json`);
const timeline = readJson(`narration-timeline-${lang}.json`);

const timingByMarker = new Map(timings.segments.map((s) => [s.marker, s]));
const textByMarker = new Map(manifest.segments.map((s) => [s.marker, s.text]));

// Process in chronological order regardless of logging order — non-monotonic input would
// otherwise produce out-of-order SRT cues and a wrong total-duration report.
const orderedSegments = [...timeline.segments].sort((a, b) => a.startMs - b.startMs);

// ── validate ────────────────────────────────────────────────────────────────
if (orderedSegments.length === 0) {
  console.error('ERROR: timeline contains no segments — nothing to assemble.');
  process.exit(1);
}
const missing = orderedSegments.filter((t) => {
  const timing = timingByMarker.get(t.marker);
  return (
    !timing || !(timing.durationMs > 0) || !fs.existsSync(path.join(SCREENCAST_DIR, timing.file))
  );
});
if (missing.length > 0) {
  console.error(
    `ERROR: ${missing.length} timeline marker(s) have no usable audio (missing file or ` +
      `zero duration): ${missing.map((m) => m.marker).join(', ')}`
  );
  console.error('       Regenerate with: npm run screencast:narration');
  process.exit(1);
}
for (const entry of orderedSegments) {
  if (!textByMarker.has(entry.marker)) {
    console.warn(`⚠️  ${entry.marker}: no manifest text — its subtitle will be skipped.`);
  }
}
if (orderedSegments.length < manifest.segments.length) {
  console.warn(
    `⚠️  Partial timeline: ${orderedSegments.length}/${manifest.segments.length} segments ` +
      '(aborted recording?) — output covers only the recorded part.'
  );
}
if (timings.fake) {
  console.warn('⚠️  Assembling from FAKE (silent) narration audio — dry-run output only.');
}

// ── audio: place each segment at its recorded offset ────────────────────────
const inputs = [];
const delayFilters = [];
orderedSegments.forEach((entry, i) => {
  const timing = timingByMarker.get(entry.marker);
  inputs.push('-i', path.join(SCREENCAST_DIR, timing.file));
  // adelay needs one value per channel; mono cache files → a single value works, but
  // "all=1" guards against stereo segments from future voices.
  delayFilters.push(`[${i}:a]adelay=${entry.startMs}:all=1[a${i}]`);
});
const mixInputs = orderedSegments.map((_, i) => `[a${i}]`).join('');
const filter =
  `${delayFilters.join(';')};` +
  `${mixInputs}amix=inputs=${orderedSegments.length}:duration=longest:normalize=0[out]`;

const audioOut = path.join(SCREENCAST_DIR, `narration-${lang}.m4a`);
try {
  execFileSync(
    'ffmpeg',
    [
      '-y',
      '-v',
      'error',
      ...inputs,
      '-filter_complex',
      filter,
      '-map',
      '[out]',
      '-c:a',
      'aac',
      '-b:a',
      '128k',
      audioOut,
    ],
    { stdio: 'inherit' }
  );
} catch (err) {
  if (err.code === 'ENOENT') {
    console.error('ERROR: ffmpeg not found on PATH — install it first: brew install ffmpeg');
    process.exit(1);
  }
  throw err;
}

// ── subtitles: chunk each segment's text across its duration ────────────────
const stripTags = (text) =>
  text
    .replace(/\[[^\]]*\]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

/** Split into subtitle chunks of <= 120 chars at sentence, then comma/space boundaries. */
function chunkText(text, maxLen = 120) {
  const sentences = text.match(/[^.!?]+[.!?]*\s*/g) ?? [text];
  const chunks = [];
  let current = '';
  for (const sentence of sentences) {
    if ((current + sentence).trim().length <= maxLen) {
      current += sentence;
    } else {
      if (current.trim()) chunks.push(current.trim());
      if (sentence.trim().length <= maxLen) {
        current = sentence;
      } else {
        // Oversized sentence: hard-wrap at word boundaries.
        let piece = '';
        for (const word of sentence.trim().split(' ')) {
          if ((piece + ' ' + word).trim().length > maxLen) {
            chunks.push(piece.trim());
            piece = word;
          } else {
            piece = (piece + ' ' + word).trim();
          }
        }
        current = piece + ' ';
      }
    }
  }
  if (current.trim()) chunks.push(current.trim());
  return chunks;
}

const srtTime = (ms) => {
  const h = String(Math.floor(ms / 3600000)).padStart(2, '0');
  const m = String(Math.floor((ms % 3600000) / 60000)).padStart(2, '0');
  const s = String(Math.floor((ms % 60000) / 1000)).padStart(2, '0');
  const milli = String(Math.floor(ms % 1000)).padStart(3, '0');
  return `${h}:${m}:${s},${milli}`;
};

let srt = '';
let cueIndex = 1;
for (const entry of orderedSegments) {
  const text = stripTags(textByMarker.get(entry.marker) ?? '');
  if (!text) continue;
  const chunks = chunkText(text);
  const totalChars = chunks.reduce((sum, c) => sum + c.length, 0);
  const segmentEnd = entry.startMs + entry.durationMs;
  let cursor = entry.startMs;
  chunks.forEach((chunk, idx) => {
    // Proportional share of the segment; the last chunk ends exactly at the segment end so
    // rounding drift can never push a cue past the audio it captions.
    const chunkMs = Math.round((chunk.length / totalChars) * entry.durationMs);
    const end = idx === chunks.length - 1 ? segmentEnd : Math.min(cursor + chunkMs, segmentEnd);
    srt += `${cueIndex}\n${srtTime(cursor)} --> ${srtTime(end)}\n${chunk}\n\n`;
    cursor = end;
    cueIndex++;
  });
}
const srtOut = path.join(SCREENCAST_DIR, `subtitles-${lang}.srt`);
fs.writeFileSync(srtOut, srt);

const lastEntry = orderedSegments[orderedSegments.length - 1];
const totalSec = (lastEntry.startMs + lastEntry.durationMs) / 1000;
console.log(
  `Done:\n  ${path.relative(process.cwd(), audioOut)} (${totalSec.toFixed(1)}s)\n` +
    `  ${path.relative(process.cwd(), srtOut)} (${cueIndex - 1} cues from ` +
    `${timeline.segments.length} segments)`
);
