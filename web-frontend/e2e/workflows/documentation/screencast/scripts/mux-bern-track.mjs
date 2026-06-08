/**
 * Mux the Bernese (Bärndütsch) test narration into the German screencast as a SECOND audio track.
 * Reads bern-segments.json (marker → startMs → per-segment WAV produced by build-bern-audio.mjs),
 * delays each clip to its DE-timeline offset, mixes them into one track, then adds it as a 2nd
 * AAC audio stream (German narration stays default; the 10 subtitle tracks are preserved).
 *
 * Idempotent: maps only 0:v + 0:a:0 from the current MP4 (drops any prior Bern track) and re-adds.
 * Usage: node mux-bern-track.mjs
 */
import { spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const SC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const VID = path.resolve(SC, '../../../../../docs/user-guide/assets/videos/workflow');
const MP4 = path.join(VID, 'event-workflow-schulung-de.mp4');

const bern = JSON.parse(fs.readFileSync(path.join(SC, 'bern-segments.json'), 'utf8')).segments;
const m4a = path.join(SC, 'narration-bern-de.m4a');

// 1) Assemble: each clip delayed to its DE-timeline start offset, mixed into one track.
const inputs = [];
const filters = [];
bern.forEach((s, i) => {
  inputs.push('-i', s.wav);
  filters.push(`[${i}:a]adelay=${s.startMs}:all=1[a${i}]`);
});
const filter =
  `${filters.join(';')};` +
  bern.map((_, i) => `[a${i}]`).join('') +
  `amix=inputs=${bern.length}:normalize=0[out]`;

let r = spawnSync(
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
    '96k',
    m4a,
  ],
  { encoding: 'utf8' }
);
if (r.status !== 0) {
  console.error('assemble failed:\n', r.stderr.slice(-800));
  process.exit(1);
}
console.log('assembled Bern track:', path.basename(m4a));

// 2) Mux as a 2nd audio track (drop any existing Bern track, keep video/German-audio/subs).
const out = path.join(VID, 'event-workflow-schulung-de.multi.mp4');
r = spawnSync(
  'ffmpeg',
  [
    '-y',
    '-v',
    'error',
    '-i',
    MP4,
    '-i',
    m4a,
    '-map',
    '0:v:0',
    '-map',
    '0:a:0',
    '-map',
    '1:a:0',
    '-map',
    '0:s',
    '-c:v',
    'copy',
    '-c:a',
    'copy',
    '-c:s',
    'copy',
    '-metadata:s:a:0',
    'language=deu',
    '-metadata:s:a:0',
    'title=Deutsch (Hochdeutsch)',
    '-metadata:s:a:1',
    'language=gsw',
    '-metadata:s:a:1',
    'title=Bärndütsch (Test · FHNW)',
    '-disposition:a:0',
    'default',
    '-disposition:a:1',
    '0',
    '-movflags',
    '+faststart',
    out,
  ],
  { encoding: 'utf8' }
);
if (r.status !== 0) {
  console.error('mux failed:\n', r.stderr.slice(-800));
  process.exit(1);
}
spawnSync('mv', [out, MP4]);
console.log('muxed Bern 2nd audio track into', path.basename(MP4));
