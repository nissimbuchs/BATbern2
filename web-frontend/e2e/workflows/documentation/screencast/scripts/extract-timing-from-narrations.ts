#!/usr/bin/env ts-node

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

interface AlignmentWord {
  word: string;
  start: number;
  end: number;
}

interface AlignmentData {
  words: AlignmentWord[];
}

interface NarrationSegment {
  marker: string;
  text: string;
  startTime?: number;
  endTime?: number;
}

// Extract NARRATION comments from Playwright script
function extractNarrations(scriptPath: string): NarrationSegment[] {
  const script = fs.readFileSync(scriptPath, 'utf-8');
  const narrations: NarrationSegment[] = [];

  // Match NARRATION_XX comments with their text
  const narrationRegex = /\/\*\s*\n\s*\*\s*NARRATION_(\d+):\s*(.+?)(?:\n\s*\*\/)/gs;
  const matches = script.matchAll(narrationRegex);

  for (const match of matches) {
    const narrationNumber = match[1];
    const text = match[2]
      .replace(/\[[\w\s]+\]/g, '') // Remove emotion tags like [excited], [playful]
      .replace(/\s+/g, ' ') // Normalize whitespace
      .trim();

    narrations.push({
      marker: `NARRATION_${narrationNumber}`,
      text,
    });
  }

  return narrations;
}

// Clean text for matching (remove punctuation, normalize)
function cleanTextForMatching(text: string): string {
  return text
    .toLowerCase()
    .replace(/[.,!?;:"'()-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

// Find the ending timestamp of a narration text in the alignment data
function findNarrationEndTime(
  narrationText: string,
  alignmentData: AlignmentData,
  startSearchIndex: number = 0
): { endTime: number; nextStartIndex: number } | null {
  const cleanNarration = cleanTextForMatching(narrationText);
  const narrationWords = cleanNarration.split(' ').filter((w) => w.length > 0);

  if (narrationWords.length === 0) {
    return null;
  }

  // Build a string from alignment words (excluding emotion tags)
  const alignmentText: string[] = [];
  const alignmentIndices: number[] = [];

  for (let i = startSearchIndex; i < alignmentData.words.length; i++) {
    const word = alignmentData.words[i].word.trim();
    if (word && !word.match(/^\[.*\]$/) && word !== ' ') {
      alignmentText.push(cleanTextForMatching(word));
      alignmentIndices.push(i);
    }
  }

  // Find the narration text in alignment text using sliding window
  const searchText = alignmentText.join(' ');

  // Try to find first few words (at least 5 or 30% of words)
  const minWordsToMatch = Math.max(5, Math.floor(narrationWords.length * 0.3));
  const firstWords = narrationWords.slice(0, minWordsToMatch).join(' ');

  const matchStartIndex = searchText.indexOf(firstWords);

  if (matchStartIndex === -1) {
    console.warn(
      `⚠️  Could not find narration text starting with: "${firstWords.substring(0, 50)}..."`
    );
    return null;
  }

  // Count words up to match start
  const wordsBeforeMatch = searchText
    .substring(0, matchStartIndex)
    .split(' ')
    .filter((w) => w.length > 0).length;

  // Find the last word index of this narration
  const narrationEndIndex = wordsBeforeMatch + narrationWords.length - 1;

  if (narrationEndIndex >= alignmentIndices.length) {
    console.warn(`⚠️  Narration extends beyond alignment data`);
    return {
      endTime: alignmentData.words[alignmentData.words.length - 1].end,
      nextStartIndex: alignmentData.words.length,
    };
  }

  const alignmentWordIndex = alignmentIndices[narrationEndIndex];
  const endTime = alignmentData.words[alignmentWordIndex].end;

  // Find next start index (skip to after this narration)
  const nextStartIndex = alignmentWordIndex + 1;

  return { endTime, nextStartIndex };
}

// Map NARRATION markers in Playwright script to their positions
function mapNarrationMarkers(scriptPath: string): Map<string, number> {
  const script = fs.readFileSync(scriptPath, 'utf-8');
  const markerMap = new Map<string, number>();

  // Find all logNarration calls to get the order
  const logRegex = /logNarration\('(NARRATION_\d+:\d+)',/g;
  let match;
  let order = 0;

  while ((match = logRegex.exec(script)) !== null) {
    markerMap.set(match[1], order++);
  }

  return markerMap;
}

// Main function
function main() {
  const scriptPath = path.join(__dirname, '..', '..', 'screencast-event-workflow.spec.ts');
  const alignmentPath = path.join(__dirname, '..', 'alignment-data.json');
  const outputPath = path.join(__dirname, '..', 'timing-config.ts');

  console.log('🔍 Extracting narrations from Playwright script...\n');

  // Extract narrations from script
  const narrations = extractNarrations(scriptPath);
  console.log(`Found ${narrations.length} NARRATION comments\n`);

  // Load alignment data
  const alignmentData: AlignmentData = JSON.parse(fs.readFileSync(alignmentPath, 'utf-8'));
  console.log(`Loaded alignment data: ${alignmentData.words.length} words\n`);

  // Map narrations to timing
  let currentSearchIndex = 0;
  const timingSegments: { marker: string; endTime: number }[] = [];

  console.log('⏱️  Matching narrations to alignment data...\n');

  for (let i = 0; i < narrations.length; i++) {
    const narration = narrations[i];
    console.log(`${i + 1}. ${narration.marker}`);
    console.log(`   Text: "${narration.text.substring(0, 60)}..."`);

    const result = findNarrationEndTime(narration.text, alignmentData, currentSearchIndex);

    if (result) {
      narration.endTime = result.endTime;
      currentSearchIndex = result.nextStartIndex;
      timingSegments.push({
        marker: narration.marker,
        endTime: result.endTime,
      });
      console.log(`   ✅ End time: ${result.endTime.toFixed(2)}s\n`);
    } else {
      console.log(`   ❌ Could not match\n`);
    }
  }

  // Now map to actual NARRATION markers used in the script (NARRATION_00:00, etc.)
  console.log('\n📊 Generating timing config...\n');

  const markerMap = mapNarrationMarkers(scriptPath);
  const markers = Array.from(markerMap.keys()).sort();

  // Calculate delays between markers
  const timingConfig: { [key: string]: number } = {};
  let previousEndTime = 0;

  for (let i = 0; i < markers.length; i++) {
    const marker = markers[i];

    // Find corresponding narration (e.g., NARRATION_00:00 -> NARRATION_01)
    const narrationMatch = marker.match(/NARRATION_(\d+):/);
    if (!narrationMatch) continue;

    const narrationNumber = parseInt(narrationMatch[1]);
    const narrationIndex = narrationNumber - 1; // NARRATION_01 is index 0

    if (narrationIndex >= 0 && narrationIndex < timingSegments.length) {
      const segment = timingSegments[narrationIndex];
      const endTime = segment.endTime;
      const delay = endTime - previousEndTime;

      timingConfig[marker] = Math.max(0, delay);
      previousEndTime = endTime;

      console.log(`   ${marker}: ${delay.toFixed(1)}s (ends at ${endTime.toFixed(1)}s)`);
    }
  }

  // Generate TypeScript timing config file
  const configContent = `// Generated timing configuration from alignment data
// Generated: ${new Date().toISOString()}
// Total markers: ${markers.length}

export const timingConfig: { [key: string]: number } = {
${markers.map((m) => `  '${m}': ${(timingConfig[m] || 0).toFixed(1)},`).join('\n')}
};

// Total duration: ${previousEndTime.toFixed(1)}s (${(previousEndTime / 60).toFixed(1)} minutes)
`;

  fs.writeFileSync(outputPath, configContent);

  console.log(`\n✅ Timing config generated: ${outputPath}`);
  console.log(`\n📊 Summary:`);
  console.log(`   Total markers: ${markers.length}`);
  console.log(
    `   Total duration: ${previousEndTime.toFixed(1)}s (${(previousEndTime / 60).toFixed(1)} minutes)`
  );
  console.log(`   Average per marker: ${(previousEndTime / markers.length).toFixed(1)}s`);
}

main();
