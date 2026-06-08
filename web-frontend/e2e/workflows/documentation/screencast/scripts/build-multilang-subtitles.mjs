/**
 * Build per-language SRT subtitle files for an already-recorded screencast — WITHOUT
 * re-recording. Each video's `narration-timeline-{video}.json` already holds the actual
 * per-segment start offsets + durations; we just drop translated text into those same
 * windows. Source text is `subtitle-translations.json` ({ lang: { marker: text } }).
 *
 * Outputs (gitignored build artifacts): subtitles-{video}-{lang}.srt for every lang, for
 * every video in VIDEOS. These are then muxed into the MP4 as parallel mov_text tracks.
 *
 * Usage: node build-multilang-subtitles.mjs            (all videos, all langs)
 *        node build-multilang-subtitles.mjs --video de (one video)
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const SCREENCAST_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const VIDEOS = (() => {
  const i = process.argv.indexOf('--video');
  return i !== -1 && process.argv[i + 1] ? [process.argv[i + 1]] : ['de', 'en'];
})();

const translations = JSON.parse(
  fs.readFileSync(path.join(SCREENCAST_DIR, 'subtitle-translations.json'), 'utf8')
);

const stripTags = (t) =>
  t
    .replace(/\[[^\]]*\]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

// CJK languages have no spaces and use 。！？ as sentence enders; wrap them by character
// count instead of word boundaries, with a shorter line budget.
const CJK = new Set(['ja', 'zh', 'ko']);

function chunkText(text, lang) {
  const maxLen = CJK.has(lang) ? 40 : 120;
  const sentences = text.match(/[^.!?。！？]+[.!?。！？]*\s*/g) ?? [text];
  const chunks = [];
  let current = '';
  const pushCurrent = () => {
    if (current.trim()) chunks.push(current.trim());
    current = '';
  };
  for (const sentence of sentences) {
    if ((current + sentence).trim().length <= maxLen) {
      current += sentence;
      continue;
    }
    pushCurrent();
    if (sentence.trim().length <= maxLen) {
      current = sentence;
    } else if (CJK.has(lang)) {
      // Hard-wrap CJK by character count.
      const s = sentence.trim();
      for (let i = 0; i < s.length; i += maxLen) chunks.push(s.slice(i, i + maxLen));
    } else {
      // Hard-wrap by word boundaries.
      let piece = '';
      for (const word of sentence.trim().split(' ')) {
        if ((piece + ' ' + word).trim().length > maxLen) {
          if (piece.trim()) chunks.push(piece.trim());
          piece = word;
        } else {
          piece = (piece + ' ' + word).trim();
        }
      }
      current = piece + ' ';
    }
  }
  pushCurrent();
  return chunks.length ? chunks : [text];
}

const srtTime = (ms) => {
  const h = String(Math.floor(ms / 3600000)).padStart(2, '0');
  const m = String(Math.floor((ms % 3600000) / 60000)).padStart(2, '0');
  const s = String(Math.floor((ms % 60000) / 1000)).padStart(2, '0');
  const milli = String(Math.floor(ms % 1000)).padStart(3, '0');
  return `${h}:${m}:${s},${milli}`;
};

for (const video of VIDEOS) {
  const timelinePath = path.join(SCREENCAST_DIR, `narration-timeline-${video}.json`);
  if (!fs.existsSync(timelinePath)) {
    console.warn(`⚠️  no timeline for video=${video} (${timelinePath}) — skipping`);
    continue;
  }
  const timeline = JSON.parse(fs.readFileSync(timelinePath, 'utf8'));
  const ordered = [...timeline.segments].sort((a, b) => a.startMs - b.startMs);

  for (const lang of Object.keys(translations)) {
    const textByMarker = translations[lang];
    let srt = '';
    let cueIndex = 1;
    for (const entry of ordered) {
      const text = stripTags(textByMarker[entry.marker] ?? '');
      if (!text) continue;
      const chunks = chunkText(text, lang);
      const totalChars = chunks.reduce((sum, c) => sum + c.length, 0) || 1;
      const segmentEnd = entry.startMs + entry.durationMs;
      let cursor = entry.startMs;
      chunks.forEach((chunk, idx) => {
        const chunkMs = Math.round((chunk.length / totalChars) * entry.durationMs);
        const end = idx === chunks.length - 1 ? segmentEnd : Math.min(cursor + chunkMs, segmentEnd);
        srt += `${cueIndex}\n${srtTime(cursor)} --> ${srtTime(end)}\n${chunk}\n\n`;
        cursor = end;
        cueIndex++;
      });
    }
    const out = path.join(SCREENCAST_DIR, `subtitles-${video}-${lang}.srt`);
    fs.writeFileSync(out, srt);
    console.log(`  ${path.basename(out)} (${cueIndex - 1} cues)`);
  }
}
console.log('Done.');
