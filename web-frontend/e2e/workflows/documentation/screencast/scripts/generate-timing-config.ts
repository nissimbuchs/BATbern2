#!/usr/bin/env ts-node
/**
 * Generate timing configuration for Playwright screencast.
 *
 * This script reads ElevenLabs alignment data and maps it to narration markers
 * in the Playwright test script, calculating appropriate delays for synchronization.
 *
 * Usage:
 *   cd web-frontend/e2e/workflows/documentation/screencast/scripts
 *   npx ts-node generate-timing-config.ts
 */

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
  audio_duration: number;
  words: AlignmentWord[];
  characters: unknown[];
  paragraphs: unknown[];
}

interface NarrationSegment {
  marker: string; // "NARRATION_00:00"
  targetTime: number; // Expected timestamp in video (seconds)
  narrationDuration: number; // Actual narration length (seconds)
  delayAfter: number; // Wait after marker (milliseconds)
  description: string; // First 60 chars of narration text
}

/**
 * Mapping of narration markers to paragraph ranges in the script.
 * This maps the markers from the Playwright script to the funny narration script paragraphs.
 *
 * Note: Paragraphs are 0-indexed. The script has ~72 lines (36 paragraphs separated by blank lines).
 */
const markerToParagraphs: Record<string, { start: number; end: number; keywords: string[] }> = {
  'NARRATION_00:00': { start: 0, end: 0, keywords: ['Willkommen', 'BATbern'] },
  'NARRATION_00:30': { start: 1, end: 1, keywords: ['beginnen', 'Dashboard'] },
  'NARRATION_01:00': { start: 1, end: 1, keywords: ['Navigation', 'Dashboard'] },
  'NARRATION_02:00': { start: 2, end: 2, keywords: ['erstellen', 'brandneues Event'] },
  'NARRATION_04:00': {
    start: 3,
    end: 10,
    keywords: ['Event-Nummer', 'Titel', 'Beschreibung', 'Speichern'],
  },
  'NARRATION_06:00': { start: 11, end: 14, keywords: ['Aufgaben', 'Teammitglieder'] },
  'NARRATION_07:00': { start: 15, end: 17, keywords: ['Themenauswahl', 'Heat Map'] },
  'NARRATION_10:00': {
    start: 18,
    end: 19,
    keywords: ['Referenten', 'Brainstorming', 'Kandidaten'],
  },
  'NARRATION_11:00': { start: 20, end: 21, keywords: ['Kanban', 'Kontaktierung'] },
  'NARRATION_13:00': { start: 22, end: 22, keywords: ['kontaktieren', 'systematisch'] },
  'NARRATION_15:00': { start: 23, end: 23, keywords: ['READY', 'Drag-and-Drop'] },
  'NARRATION_20:00': { start: 24, end: 24, keywords: ['ACCEPTED', 'verschoben'] },
  'NARRATION_20:30': { start: 25, end: 25, keywords: ['Thema veröffentlichen'] },
  'NARRATION_21:00': { start: 26, end: 26, keywords: ['Präsentations-Inhalte', 'einreichen'] },
  'NARRATION_25:00': { start: 27, end: 27, keywords: ['Qualitätsprüfung', 'Inhaltseinreichung'] },
  'NARRATION_25:30': { start: 28, end: 28, keywords: ['Referenten veröffentlichen'] },
  'NARRATION_26:00': { start: 29, end: 29, keywords: ['genehmigen', 'Präsentation'] },
  'NARRATION_30:00': { start: 30, end: 30, keywords: ['Slot-Zuweisung', 'Sessions'] },
  'NARRATION_30:30': { start: 30, end: 30, keywords: ['Sessions-Ansicht'] },
  'NARRATION_31:00': { start: 30, end: 30, keywords: ['Slot-Zuweisungen verwalten'] },
  'NARRATION_32:00': { start: 31, end: 31, keywords: ['Auto-Assign', 'automatische Zuweisung'] },
  'NARRATION_33:00': { start: 32, end: 33, keywords: ['Agenda', 'veröffentlichen'] },
  'NARRATION_38:00': { start: 34, end: 34, keywords: ['archivieren', 'Historie'] },
  'NARRATION_38:30': { start: 34, end: 34, keywords: ['archivieren', 'Event'] },
  'NARRATION_39:00': { start: 35, end: 35, keywords: ['ARCHIVIERT', 'Status'] },
  'NARRATION_40:00': { start: 36, end: 36, keywords: ['Workflow-Validierung', 'überschreiben'] },
  'NARRATION_43:00': { start: 37, end: 37, keywords: ['abgeschlossen', 'Dank'] },
};

function generateTimingConfig(): void {
  console.log('🎬 Generating timing configuration...\n');

  // Read alignment data
  const alignmentPath = path.join(__dirname, '../alignment-data.json');
  console.log(`📁 Reading alignment data: ${alignmentPath}`);

  if (!fs.existsSync(alignmentPath)) {
    console.error(`❌ Error: alignment-data.json not found!`);
    console.error(`   Please run: python extract-alignment.py first`);
    process.exit(1);
  }

  const alignment: AlignmentData = JSON.parse(fs.readFileSync(alignmentPath, 'utf-8'));
  console.log(`   ✓ Loaded ${alignment.words.length} words with timestamps`);

  // Read script paragraphs (funny version with emotion tags)
  const scriptPath = path.join(__dirname, '../script-for-tts-de-funny-with-emotions.txt');
  console.log(`📄 Reading narration script: ${scriptPath}`);
  const scriptText = fs.readFileSync(scriptPath, 'utf-8');
  const paragraphs = scriptText.split('\n\n').filter((p) => p.trim());
  console.log(`   ✓ Loaded ${paragraphs.length} paragraphs`);

  const segments: NarrationSegment[] = [];
  let cumulativeTime = 0;
  let previousEndTime = 0;

  console.log(`\n📊 Processing ${Object.keys(markerToParagraphs).length} narration markers...\n`);

  for (const [marker, range] of Object.entries(markerToParagraphs)) {
    // Get text for this marker (remove emotion tags for matching)
    const markerParagraphs = paragraphs.slice(range.start, range.end + 1);
    const markerText = markerParagraphs
      .join(' ')
      .replace(/\[.*?\]/g, ' ')
      .trim();

    // Find first and last word of this segment in alignment data
    const words = markerText.split(/\s+/).filter((w) => w.length > 3); // Filter out short words
    const firstWord = words[0] || 'Willkommen';
    const lastWord = words[words.length - 1] || firstWord;

    // Find word timestamps
    let startTime = previousEndTime;
    let endTime = alignment.audio_duration;

    // Find start time by matching first word (skip emotion tags and spaces)
    for (let i = 0; i < alignment.words.length; i++) {
      const word = alignment.words[i].word;
      if (!word || word.trim().length === 0 || word.startsWith('[')) continue;

      if (
        word
          .toLowerCase()
          .includes(firstWord.toLowerCase().substring(0, Math.min(4, firstWord.length)))
      ) {
        startTime = alignment.words[i].start;
        break;
      }
    }

    // Find end time by matching last word (skip emotion tags and spaces)
    for (let i = 0; i < alignment.words.length; i++) {
      const word = alignment.words[i].word;
      if (!word || word.trim().length === 0 || word.startsWith('[')) continue;

      if (
        alignment.words[i].start > startTime &&
        word
          .toLowerCase()
          .includes(lastWord.toLowerCase().substring(0, Math.min(4, lastWord.length)))
      ) {
        endTime = alignment.words[i].end;
        break;
      }
    }

    const narrationDuration = endTime - startTime;

    // Calculate delay: narration duration + UI buffer (2.5s)
    const uiBuffer = 2500; // milliseconds

    // Add extra buffer for phase transitions
    const isPhaseTransition =
      marker.includes('Phase') || marker === 'NARRATION_00:00' || marker === 'NARRATION_43:00';
    const phaseBuffer = isPhaseTransition ? 2000 : 0;

    const delayAfter = Math.round(narrationDuration * 1000 + uiBuffer + phaseBuffer);

    // Create description (first 60 chars, no emotion tags)
    const cleanText = markerText.replace(/\[.*?\]/g, '');
    const description = cleanText.substring(0, 60) + (cleanText.length > 60 ? '...' : '');

    segments.push({
      marker,
      targetTime: parseTimestamp(marker),
      narrationDuration: Math.max(narrationDuration, 1.0), // Minimum 1 second
      delayAfter: Math.max(delayAfter, 3000), // Minimum 3 seconds
      description,
    });

    previousEndTime = endTime;
    cumulativeTime += delayAfter / 1000;

    console.log(`   ${marker}: ${(delayAfter / 1000).toFixed(1)}s - "${description}"`);
  }

  // Verify total timing
  console.log(`\n=== TIMING SUMMARY ===`);
  console.log(`Total calculated delays: ${cumulativeTime.toFixed(1)}s`);
  console.log(`Target audio duration: ${alignment.audio_duration}s`);
  const difference = Math.abs(alignment.audio_duration - cumulativeTime);
  console.log(
    `Difference: ${difference.toFixed(1)}s (${((difference / alignment.audio_duration) * 100).toFixed(1)}%)`
  );

  if (difference > alignment.audio_duration * 0.05) {
    console.log(`\n⚠️  WARNING: Timing difference exceeds 5% tolerance`);
    console.log(`   You may need to manually adjust delays in timing-config.ts`);
  } else {
    console.log(`\n✅ Timing within acceptable range`);
  }

  // Generate TypeScript config file
  const configContent = `// Auto-generated timing configuration
// Generated: ${new Date().toISOString()}
// Audio duration: ${alignment.audio_duration}s
// Total delays: ${cumulativeTime.toFixed(1)}s

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

  console.log(`\n💾 Generated timing config: ${outputPath}`);
  console.log(`   ${segments.length} markers configured`);
  console.log(`\nNext steps:`);
  console.log(`  1. Review timing-config.ts and adjust delays if needed`);
  console.log(`  2. Run: npm run test:e2e -- verify-timing.spec.ts`);
}

function parseTimestamp(marker: string): number {
  const match = marker.match(/NARRATION_(\d+):(\d+)/);
  if (!match) return 0;
  const minutes = parseInt(match[1], 10);
  const seconds = parseInt(match[2], 10);
  return minutes * 60 + seconds;
}

// Run the generator
try {
  generateTimingConfig();
} catch (error) {
  console.error('\n❌ Error generating timing config:');
  console.error(error);
  process.exit(1);
}
