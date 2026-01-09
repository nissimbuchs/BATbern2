#!/usr/bin/env ts-node
/**
 * Generate SRT subtitle file from timing configuration and narration text
 *
 * This script reads:
 * - timing-config.ts: Cumulative timing for each narration segment
 * - NARRATION-MAPPING.md: Full German text for each narration
 *
 * And generates:
 * - subtitles-de.srt: Standard SRT subtitle file for the screencast video
 *
 * Usage:
 *   cd web-frontend/e2e/workflows/documentation/screencast/scripts
 *   npx ts-node generate-subtitles.ts
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

interface TimingSegment {
  marker: string;
  delayAfter: number; // milliseconds
  description: string;
}

interface SubtitleEntry {
  index: number;
  startTime: string; // HH:MM:SS,mmm
  endTime: string; // HH:MM:SS,mmm
  text: string;
}

/**
 * Convert milliseconds to SRT timestamp format (HH:MM:SS,mmm)
 */
function millisecondsToSrtTime(ms: number): string {
  const hours = Math.floor(ms / 3600000);
  const minutes = Math.floor((ms % 3600000) / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  const milliseconds = ms % 1000;

  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')},${String(milliseconds).padStart(3, '0')}`;
}

/**
 * Clean narration text for subtitles
 * - Remove emotion markers like [excited], [playful]
 * - Remove pause markers like [pause], [short pause]
 * - Clean up extra whitespace
 * - Split long text into multiple subtitle segments (max ~42 chars per line)
 */
function cleanNarrationText(text: string): string[] {
  // Remove all emotion and pause markers
  const cleaned = text
    .replace(/\[excited\]/gi, '')
    .replace(/\[playful\]/gi, '')
    .replace(/\[chuckling\]/gi, '')
    .replace(/\[cheerful\]/gi, '')
    .replace(/\[professional\]/gi, '')
    .replace(/\[casual\]/gi, '')
    .replace(/\[helpful\]/gi, '')
    .replace(/\[confident\]/gi, '')
    .replace(/\[satisfied\]/gi, '')
    .replace(/\[dramatic\]/gi, '')
    .replace(/\[enthusiastic\]/gi, '')
    .replace(/\[informative\]/gi, '')
    .replace(/\[methodical\]/gi, '')
    .replace(/\[instructional\]/gi, '')
    .replace(/\[clear\]/gi, '')
    .replace(/\[positive\]/gi, '')
    .replace(/\[important\]/gi, '')
    .replace(/\[strategic\]/gi, '')
    .replace(/\[curious\]/gi, '')
    .replace(/\[calm\]/gi, '')
    .replace(/\[triumphant\]/gi, '')
    .replace(/\[whispers\]/gi, '')
    .replace(/\[laughing\]/gi, '')
    .replace(/\[pause\]/gi, '')
    .replace(/\[short pause\]/gi, '')
    .replace(/\s+/g, ' ') // Normalize whitespace
    .trim();

  // Split into sentences (roughly) for subtitle timing
  const sentences = cleaned.split(/(?<=[.!?])\s+/);

  // Group sentences into subtitle chunks (aim for 2-3 sentences or ~80-120 chars)
  const chunks: string[] = [];
  let currentChunk = '';

  for (const sentence of sentences) {
    if (currentChunk.length === 0) {
      currentChunk = sentence;
    } else if (currentChunk.length + sentence.length + 1 <= 120) {
      currentChunk += ' ' + sentence;
    } else {
      chunks.push(currentChunk);
      currentChunk = sentence;
    }
  }

  if (currentChunk.length > 0) {
    chunks.push(currentChunk);
  }

  return chunks;
}

/**
 * Extract narration text from NARRATION-MAPPING.md
 */
function parseNarrationMapping(filePath: string): Map<string, string> {
  const content = fs.readFileSync(filePath, 'utf-8');
  const narrationMap = new Map<string, string>();

  // Match ### NARRATION_XX sections with triple-backtick content
  // Handles both "### NARRATION_XX (Line YY)" and "### NARRATION_XX (Line YY - Details)"
  const narrationRegex = /### (NARRATION_\d+) \([^)]+\)\s*\n\s*```\s*\n([\s\S]*?)\n\s*```/g;

  let match;
  while ((match = narrationRegex.exec(content)) !== null) {
    const marker = match[1];
    const text = match[2].trim();
    narrationMap.set(marker, text);
  }

  return narrationMap;
}

/**
 * Generate SRT subtitle file
 */
function generateSrtFile(): void {
  console.log('🎬 Generating SRT subtitle file...\n');

  // Read timing configuration
  const timingConfigPath = path.join(__dirname, '../timing-config.ts');
  console.log(`📁 Reading timing config: ${timingConfigPath}`);

  // Simple parsing of the TypeScript file (assumes known structure)
  const timingContent = fs.readFileSync(timingConfigPath, 'utf-8');
  const timingSegments: TimingSegment[] = [];

  // Extract marker, delayAfter, and description from each object literal
  const segmentRegex =
    /{\s*marker:\s*'(NARRATION_\d+)',\s*delayAfter:\s*(\d+),.*?description:\s*'([^']+)'/gs;
  let match;

  while ((match = segmentRegex.exec(timingContent)) !== null) {
    timingSegments.push({
      marker: match[1],
      delayAfter: parseInt(match[2]),
      description: match[3],
    });
  }

  console.log(`   ✓ Loaded ${timingSegments.length} timing segments\n`);

  // Read narration mapping
  const narrationMappingPath = path.join(__dirname, '../../NARRATION-MAPPING.md');
  console.log(`📄 Reading narration text: ${narrationMappingPath}`);

  const narrationMap = parseNarrationMapping(narrationMappingPath);
  console.log(`   ✓ Loaded ${narrationMap.size} narration texts\n`);

  // Generate subtitle entries
  console.log('📝 Generating subtitle entries...\n');

  const subtitles: SubtitleEntry[] = [];
  let cumulativeTime = 0;
  let subtitleIndex = 1;

  for (const segment of timingSegments) {
    const narrationText = narrationMap.get(segment.marker);

    if (!narrationText) {
      console.warn(`⚠️  No narration text found for ${segment.marker}`);
      continue;
    }

    // Clean and split text into chunks
    const textChunks = cleanNarrationText(narrationText);

    // Calculate time per chunk (evenly distribute segment duration)
    const segmentDuration = segment.delayAfter;
    const chunkDuration = Math.floor(segmentDuration / textChunks.length);

    for (let i = 0; i < textChunks.length; i++) {
      const startTime = cumulativeTime + i * chunkDuration;
      const endTime =
        i === textChunks.length - 1 ? cumulativeTime + segmentDuration : startTime + chunkDuration;

      subtitles.push({
        index: subtitleIndex++,
        startTime: millisecondsToSrtTime(startTime),
        endTime: millisecondsToSrtTime(endTime),
        text: textChunks[i],
      });

      console.log(
        `   ${segment.marker} [${i + 1}/${textChunks.length}]: ${textChunks[i].substring(0, 60)}...`
      );
    }

    cumulativeTime += segmentDuration;
  }

  // Generate SRT content
  console.log(`\n📊 Total subtitles: ${subtitles.length}`);
  console.log(`⏱️  Total duration: ${millisecondsToSrtTime(cumulativeTime)}\n`);

  let srtContent = '';
  for (const subtitle of subtitles) {
    srtContent += `${subtitle.index}\n`;
    srtContent += `${subtitle.startTime} --> ${subtitle.endTime}\n`;
    srtContent += `${subtitle.text}\n\n`;
  }

  // Write SRT file
  const outputPath = path.join(__dirname, '../subtitles-de.srt');
  fs.writeFileSync(outputPath, srtContent, 'utf-8');

  console.log(`✅ SRT file generated: ${outputPath}`);
  console.log(`   Total entries: ${subtitles.length}`);
  console.log(`   Duration: ${millisecondsToSrtTime(cumulativeTime)}`);
  console.log(`   Encoding: UTF-8 (supports German umlauts: ä, ö, ü, ß)\n`);
}

// Run the generator
generateSrtFile();
