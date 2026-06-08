/**
 * TEST/EXPERIMENT: generate a Bernese Swiss German (Bärndütsch) narration track for the German
 * screencast using the FHNW research demo (https://stt4sg.fhnw.ch/tts — Gradio /call API).
 * Two steps per segment: translate_interface(High-German, "Bern") → dialect text, then
 * speech_interface(dialect text, "Bern") → 16 kHz WAV. Reuses the recorded DE narration timeline
 * (no re-recording). Sequential + cached (resumable) to be gentle on a free research service.
 *
 * Output: audio-cache/bern/{marker}.wav (gitignored) + bern-segments.json (marker→startMs→wav).
 * NOTE: research-demo quality (16 kHz; rough dialect translation). For evaluation only.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ROOT = 'https://stt4sg.fhnw.ch/tts';
const DIALECT = 'Bern';
const CACHE = path.join(DIR, 'audio-cache', 'bern');
fs.mkdirSync(CACHE, { recursive: true });

const stripTags = (t) =>
  t
    .replace(/\[[^\]]*\]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Call a Gradio /call endpoint and return the parsed `complete` data array. */
async function gradioCall(apiName, data) {
  const post = await fetch(`${ROOT}/call/${apiName}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ data }),
  });
  const { event_id } = await post.json();
  if (!event_id) throw new Error(`no event_id from ${apiName}`);
  const stream = await (await fetch(`${ROOT}/call/${apiName}/${event_id}`)).text();
  // Grab the data line following the final "event: complete".
  const lines = stream.split('\n');
  let lastData = null;
  let complete = false;
  for (const line of lines) {
    if (line.startsWith('event: complete')) complete = true;
    else if (complete && line.startsWith('data:')) {
      lastData = line.slice(5).trim();
      complete = false;
    }
  }
  if (!lastData) throw new Error(`no complete data from ${apiName}: ${stream.slice(0, 200)}`);
  return JSON.parse(lastData);
}

const manifest = JSON.parse(fs.readFileSync(path.join(DIR, 'narration-manifest-de.json'), 'utf8'));
const timeline = JSON.parse(fs.readFileSync(path.join(DIR, 'narration-timeline-de.json'), 'utf8'));
const textByMarker = Object.fromEntries(
  manifest.segments.map((s) => [s.marker, stripTags(s.text)])
);
const ordered = [...timeline.segments].sort((a, b) => a.startMs - b.startMs);

const out = [];
let generated = 0;
for (const seg of ordered) {
  const de = textByMarker[seg.marker];
  if (!de) continue;
  const wav = path.join(CACHE, `${seg.marker}.wav`);
  if (fs.existsSync(wav) && fs.statSync(wav).size > 1000) {
    console.log(`  ✓ ${seg.marker} (cached)`);
    out.push({ marker: seg.marker, startMs: seg.startMs, wav });
    continue;
  }
  try {
    const [dialectText] = await gradioCall('translate_interface', [de, DIALECT]);
    const speech = await gradioCall('speech_interface', [dialectText, DIALECT]);
    const audio = Array.isArray(speech) ? speech[0] : speech;
    const url = audio?.url || (audio?.path ? `${ROOT}/file=${audio.path}` : null);
    if (!url) throw new Error('no audio url');
    const buf = Buffer.from(await (await fetch(url)).arrayBuffer());
    fs.writeFileSync(wav, buf);
    generated++;
    console.log(
      `  ♪ ${seg.marker} → ${(buf.length / 1024).toFixed(0)}KB  «${dialectText.slice(0, 50)}»`
    );
    out.push({ marker: seg.marker, startMs: seg.startMs, wav });
    await sleep(800); // be gentle on the research demo
  } catch (e) {
    console.error(`  ✗ ${seg.marker}: ${e.message}`);
  }
}

fs.writeFileSync(
  path.join(DIR, 'bern-segments.json'),
  JSON.stringify({ dialect: DIALECT, source: ROOT, segments: out }, null, 2) + '\n'
);
console.log(
  `Done: ${generated} generated, ${out.length}/${ordered.length} total → bern-segments.json`
);
