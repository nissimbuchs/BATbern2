/**
 * TEST/EXPERIMENT: generate a Bernese Swiss German (Bärndütsch) narration track for the German
 * screencast using the FHNW research demo (https://stt4sg.fhnw.ch/tts — Gradio /call API).
 * Per segment: translate_interface(High-German, "Bern") → dialect text, then synthesize.
 *
 * PROSODY FIX: the VITS model only pauses ~0.18s at punctuation — too short, so it sounds like
 * it reads over sentence ends and commas. We therefore SPLIT each segment's dialect text into
 * clauses (on . ! ? … and , ; :), synthesize each clause separately, and CONCATENATE them with
 * real inserted silence (sentence-end ≈ 550ms, comma ≈ 300ms). That gives natural pacing.
 *
 * Reuses the recorded DE narration timeline (no re-recording). Sequential + cached/resumable
 * (per-clause WAVs hashed) to stay gentle on a free research service.
 *
 * Output: audio-cache/bern/{marker}.wav (gitignored) + bern-segments.json + bern-dialect-text.json
 * NOTE: research-demo quality (16 kHz; rough dialect translation). For evaluation only.
 */
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { spawnSync } from 'child_process';
import { fileURLToPath } from 'url';

const DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ROOT = 'https://stt4sg.fhnw.ch/tts';
const DIALECT = 'Bern';
const CACHE = path.join(DIR, 'audio-cache', 'bern');
const CHUNKS = path.join(CACHE, 'chunks');
fs.mkdirSync(CHUNKS, { recursive: true });

// Silence gap (ms) by the clause's trailing punctuation.
const GAP = { '.': 550, '!': 550, '?': 550, '…': 450, ',': 100, ';': 250, ':': 250, '': 140 };
const SR = 16000; // demo output: 16 kHz mono pcm_s16le

const stripTags = (t) =>
  t
    .replace(/\[[^\]]*\]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sha8 = (s) => crypto.createHash('sha1').update(s).digest('hex').slice(0, 8);

/** Split into clauses, each tagged with the trailing punctuation that governs the gap after it. */
function splitClauses(text) {
  const out = [];
  const re = /\s*([^.!?…,;:]+)([.!?…,;:]*)/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const body = m[1].trim();
    if (!body) continue;
    const punct = (m[2].match(/[.!?…,;:]/g) || []).pop() || '';
    out.push({ text: (body + (m[2] || '')).trim(), gap: GAP[punct] ?? 140 });
  }
  return out.length ? out : [{ text, gap: 140 }];
}

async function gradioCall(apiName, data) {
  const post = await fetch(`${ROOT}/call/${apiName}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ data }),
  });
  const { event_id } = await post.json();
  if (!event_id) throw new Error(`no event_id from ${apiName}`);
  const stream = await (await fetch(`${ROOT}/call/${apiName}/${event_id}`)).text();
  let lastData = null,
    complete = false;
  for (const line of stream.split('\n')) {
    if (line.startsWith('event: complete')) complete = true;
    else if (complete && line.startsWith('data:')) {
      lastData = line.slice(5).trim();
      complete = false;
    }
  }
  if (!lastData) throw new Error(`no complete data from ${apiName}: ${stream.slice(0, 160)}`);
  return JSON.parse(lastData);
}

/** Synthesize one clause → cached 16 kHz mono WAV path. */
async function synthClause(text) {
  const wav = path.join(CHUNKS, `${sha8(DIALECT + '|' + text)}.wav`);
  if (fs.existsSync(wav) && fs.statSync(wav).size > 1000) return wav;
  const speech = await gradioCall('speech_interface', [text, DIALECT]);
  const audio = Array.isArray(speech) ? speech[0] : speech;
  const url = audio?.url || (audio?.path ? `${ROOT}/file=${audio.path}` : null);
  if (!url) throw new Error('no audio url');
  fs.writeFileSync(wav, Buffer.from(await (await fetch(url)).arrayBuffer()));
  await sleep(600); // be gentle on the research demo
  return wav;
}

const silenceCache = {};
function silence(ms) {
  if (silenceCache[ms]) return silenceCache[ms];
  const f = path.join(CHUNKS, `sil_${ms}.wav`);
  if (!fs.existsSync(f)) {
    spawnSync('ffmpeg', [
      '-y',
      '-v',
      'error',
      '-f',
      'lavfi',
      '-i',
      `anullsrc=r=${SR}:cl=mono`,
      '-t',
      (ms / 1000).toFixed(3),
      '-c:a',
      'pcm_s16le',
      f,
    ]);
  }
  return (silenceCache[ms] = f);
}

/** Concatenate clause WAVs + silence gaps into one segment WAV (same format → stream copy). */
function stitch(parts, outWav) {
  const list = path.join(CHUNKS, 'concat.txt');
  fs.writeFileSync(list, parts.map((p) => `file '${p}'`).join('\n') + '\n');
  const r = spawnSync('ffmpeg', [
    '-y',
    '-v',
    'error',
    '-f',
    'concat',
    '-safe',
    '0',
    '-i',
    list,
    '-c',
    'copy',
    outWav,
  ]);
  if (r.status !== 0) throw new Error('stitch failed: ' + r.stderr);
}

const manifest = JSON.parse(fs.readFileSync(path.join(DIR, 'narration-manifest-de.json'), 'utf8'));
const timeline = JSON.parse(fs.readFileSync(path.join(DIR, 'narration-timeline-de.json'), 'utf8'));
const textByMarker = Object.fromEntries(
  manifest.segments.map((s) => [s.marker, stripTags(s.text)])
);
const ordered = [...timeline.segments].sort((a, b) => a.startMs - b.startMs);

const dialectTextPath = path.join(DIR, 'bern-dialect-text.json');
const dialectText = fs.existsSync(dialectTextPath)
  ? JSON.parse(fs.readFileSync(dialectTextPath, 'utf8'))
  : {};

const out = [];
let segDone = 0;
for (const seg of ordered) {
  const de = textByMarker[seg.marker];
  if (!de) continue;
  try {
    if (!dialectText[seg.marker]) {
      const [dt] = await gradioCall('translate_interface', [de, DIALECT]);
      dialectText[seg.marker] = dt;
      fs.writeFileSync(dialectTextPath, JSON.stringify(dialectText, null, 2) + '\n');
      await sleep(600);
    }
    const clauses = splitClauses(dialectText[seg.marker]);
    const parts = [];
    for (const c of clauses) {
      parts.push(await synthClause(c.text));
      parts.push(silence(c.gap));
    }
    const segWav = path.join(CACHE, `${seg.marker}.wav`);
    stitch(parts, segWav);
    out.push({ marker: seg.marker, startMs: seg.startMs, wav: segWav });
    segDone++;
    console.log(`  ♪ ${seg.marker}: ${clauses.length} clauses → ${path.basename(segWav)}`);
  } catch (e) {
    console.error(`  ✗ ${seg.marker}: ${e.message}`);
  }
}

fs.writeFileSync(
  path.join(DIR, 'bern-segments.json'),
  JSON.stringify({ dialect: DIALECT, source: ROOT, segments: out }, null, 2) + '\n'
);
console.log(`Done: ${segDone}/${ordered.length} segments → bern-segments.json`);
