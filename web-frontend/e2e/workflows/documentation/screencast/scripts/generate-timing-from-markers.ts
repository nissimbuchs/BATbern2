#!/usr/bin/env ts-node

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Parse marker time code to seconds
function parseMarkerTime(marker: string): number | null {
  // NARRATION_MM:SS -> seconds
  const match = marker.match(/NARRATION_(\d+):(\d+)/);
  if (!match) return null;

  const minutes = parseInt(match[1]);
  const seconds = parseInt(match[2]);
  return minutes * 60 + seconds;
}

// Extract logNarration markers in order
function extractMarkerOrder(scriptPath: string): string[] {
  const script = fs.readFileSync(scriptPath, 'utf-8');
  const markers: string[] = [];
  const regex = /logNarration\('([^']+)',/g;
  let match;

  while ((match = regex.exec(script)) !== null) {
    markers.push(match[1]);
  }

  return markers;
}

function main() {
  const playwrightPath = path.join(__dirname, '..', '..', 'screencast-event-workflow.spec.ts');
  const outputPath = path.join(__dirname, '..', 'timing-config.ts');

  console.log('🎬 Generating timing from marker time codes\n');

  // Extract marker order from Playwright script
  const markers = extractMarkerOrder(playwrightPath);
  console.log(`Found ${markers.length} logNarration() calls\n`);

  // Calculate delays based on marker time codes
  const timingMap: { [key: string]: number } = {};
  let previousTime = 0;

  console.log('⏱️  Calculating delays from marker time codes...\n');

  for (const marker of markers) {
    const markerTime = parseMarkerTime(marker);

    if (markerTime !== null) {
      const delay = markerTime - previousTime;
      timingMap[marker] = Math.max(0, delay);
      console.log(`   ${marker}: ${delay.toFixed(1)}s delay (at ${markerTime}s)`);
      previousTime = markerTime;
    } else {
      console.warn(`   ${marker}: Could not parse time code`);
      timingMap[marker] = 0;
    }
  }

  const totalDuration = previousTime;

  // Generate config file
  const configContent = `// Generated timing configuration from marker time codes
// Generated: ${new Date().toISOString()}
// Marker time codes represent intended synchronization points in audio
// Total markers: ${markers.length}

export const timingConfig: { [key: string]: number } = {
${markers.map((m) => `  '${m}': ${(timingMap[m] || 0).toFixed(1)},`).join('\n')}
};

// Total duration: ${totalDuration.toFixed(1)}s (${(totalDuration / 60).toFixed(2)} minutes)
// Target audio: 740.7s (12.35 minutes)
`;

  fs.writeFileSync(outputPath, configContent);

  console.log(`\n✅ Timing config generated: ${outputPath}`);
  console.log(`\n📊 Summary:`);
  console.log(`   Total markers: ${markers.length}`);
  console.log(
    `   Total duration: ${totalDuration.toFixed(1)}s (${(totalDuration / 60).toFixed(2)} min)`
  );
  console.log(`   Target audio: 740.7s (12.35 min)`);
  console.log(`   Difference: ${(740.7 - totalDuration).toFixed(1)}s\n`);
}

main();
