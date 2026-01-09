#!/usr/bin/env ts-node
/**
 * Generate simple timing configuration with equal distribution.
 *
 * This creates a timing config by dividing the audio duration evenly across markers,
 * which can then be manually adjusted based on actual narration needs.
 *
 * Usage:
 *   npx ts-node generate-simple-timing.ts
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

interface NarrationSegment {
  marker: string;
  targetTime: number;
  narrationDuration: number;
  delayAfter: number;
  description: string;
}

// All markers from the Playwright script
const markers = [
  'NARRATION_00:00',
  'NARRATION_00:30',
  'NARRATION_01:00',
  'NARRATION_02:00',
  'NARRATION_04:00',
  'NARRATION_06:00',
  'NARRATION_07:00',
  'NARRATION_10:00',
  'NARRATION_11:00',
  'NARRATION_13:00',
  'NARRATION_15:00',
  'NARRATION_20:00',
  'NARRATION_20:30',
  'NARRATION_21:00',
  'NARRATION_25:00',
  'NARRATION_25:30',
  'NARRATION_26:00',
  'NARRATION_30:00',
  'NARRATION_30:30',
  'NARRATION_31:00',
  'NARRATION_32:00',
  'NARRATION_33:00',
  'NARRATION_38:00',
  'NARRATION_38:30',
  'NARRATION_39:00',
  'NARRATION_40:00',
  'NARRATION_43:00',
];

const AUDIO_DURATION = 740.7; // seconds
const UI_BUFFER = 2500; // milliseconds

console.log('🎬 Generating simple timing configuration...\n');
console.log(`Audio duration: ${AUDIO_DURATION}s`);
console.log(`Number of markers: ${markers.length}`);
console.log(`Average time per marker: ${(AUDIO_DURATION / markers.length).toFixed(1)}s\n`);

const segments: NarrationSegment[] = [];
const avgDurationPerMarker = AUDIO_DURATION / markers.length;

for (const marker of markers) {
  const targetTime = parseTimestamp(marker);

  // Base narration duration (equal distribution)
  const narrationDuration = avgDurationPerMarker;

  // Add UI buffer
  const delayAfter = Math.round(narrationDuration * 1000 + UI_BUFFER);

  segments.push({
    marker,
    targetTime,
    narrationDuration,
    delayAfter,
    description: `Segment at ${marker}`,
  });

  console.log(`   ${marker}: ${(delayAfter / 1000).toFixed(1)}s`);
}

const totalDelay = segments.reduce((sum, s) => sum + s.delayAfter, 0) / 1000;

console.log(`\n=== TIMING SUMMARY ===`);
console.log(`Total delays: ${totalDelay.toFixed(1)}s`);
console.log(`Target: ${AUDIO_DURATION}s`);
console.log(`Difference: ${Math.abs(AUDIO_DURATION - totalDelay).toFixed(1)}s`);

// Generate config file
const configContent = `// Auto-generated timing configuration (simple equal distribution)
// Generated: ${new Date().toISOString()}
// Audio duration: ${AUDIO_DURATION}s
// Total delays: ${totalDelay.toFixed(1)}s
//
// ⚠️  This uses equal distribution. You should manually adjust delays
// based on actual narration segment lengths.

export interface NarrationSegment {
  marker: string;
  targetTime: number;
  narrationDuration: number;
  delayAfter: number;
  description: string;
}

export const timingConfig: NarrationSegment[] = ${JSON.stringify(segments, null, 2)};

export function getDelayForMarker(marker: string): number {
  return timingConfig.find(s => s.marker === marker)?.delayAfter || 0;
}
`;

const outputPath = path.join(__dirname, '../timing-config.ts');
fs.writeFileSync(outputPath, configContent, 'utf-8');

console.log(`\n✅ Generated timing config: ${outputPath}`);
console.log(`\nNext steps:`);
console.log(`  1. Manually adjust delays in timing-config.ts based on actual narration`);
console.log(`  2. Run: npm run test:e2e -- verify-timing.spec.ts`);

function parseTimestamp(marker: string): number {
  const match = marker.match(/NARRATION_(\d+):(\d+)/);
  if (!match) return 0;
  const minutes = parseInt(match[1], 10);
  const seconds = parseInt(match[2], 10);
  return minutes * 60 + seconds;
}
