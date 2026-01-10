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

// Clean text for matching
function cleanText(text: string): string {
  return text
    .replace(/\[[\w\s]+\]/g, '') // Remove emotion tags
    .replace(/[.,!?;:"'()-]/g, ' ') // Remove punctuation
    .replace(/\s+/g, ' ') // Normalize whitespace
    .trim()
    .toLowerCase();
}

// Find end time of a paragraph in alignment data
function findParagraphEndTime(
  paragraph: string,
  alignmentData: AlignmentData,
  startIndex: number
): { endTime: number; nextIndex: number } | null {
  const cleanPara = cleanText(paragraph);
  const paraWords = cleanPara.split(' ').filter((w) => w.length > 2);

  if (paraWords.length === 0) return null;

  // Build searchable text from alignment
  const words: string[] = [];
  const indices: number[] = [];

  for (let i = startIndex; i < alignmentData.words.length; i++) {
    const word = alignmentData.words[i].word.trim();
    if (word && !word.match(/^\[.*\]$/) && word !== ' ' && word.length > 0) {
      words.push(cleanText(word));
      indices.push(i);
    }
  }

  const searchText = words.join(' ');

  // Try to find first 3-5 words
  const minMatch = Math.min(5, Math.max(3, Math.floor(paraWords.length * 0.2)));
  const startWords = paraWords.slice(0, minMatch).join(' ');

  let matchIndex = searchText.indexOf(startWords);
  if (matchIndex === -1) {
    // Try with first 3 words only
    const fallbackWords = paraWords.slice(0, 3).join(' ');
    matchIndex = searchText.indexOf(fallbackWords);

    if (matchIndex === -1) {
      console.warn(`   ⚠️  Could not find: "${startWords}"`);
      return null;
    }
  }

  // Find how many words before match
  const wordsBeforeMatch = searchText
    .substring(0, matchIndex)
    .split(' ')
    .filter((w) => w.length > 0).length;

  // Calculate end position (add all paragraph words)
  const endWordIndex = wordsBeforeMatch + paraWords.length - 1;

  if (endWordIndex >= indices.length) {
    const lastIndex = alignmentData.words.length - 1;
    return {
      endTime: alignmentData.words[lastIndex].end,
      nextIndex: lastIndex + 1,
    };
  }

  const alignmentIndex = indices[endWordIndex];
  const endTime = alignmentData.words[alignmentIndex].end;
  const nextIndex = alignmentIndex + 1;

  return { endTime, nextIndex };
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
  const ttsScriptPath = path.join(__dirname, '..', 'script-for-tts-de-funny-with-emotions.txt');
  const alignmentPath = path.join(__dirname, '..', 'alignment-data.json');
  const playwrightPath = path.join(__dirname, '..', '..', 'screencast-event-workflow.spec.ts');
  const outputPath = path.join(__dirname, '..', 'timing-config.ts');

  console.log('🎬 Generating accurate timing from TTS script and alignment data\n');

  // Read TTS script and split into paragraphs
  const ttsScript = fs.readFileSync(ttsScriptPath, 'utf-8');
  const paragraphs = ttsScript
    .split('\n\n')
    .map((p) => p.trim())
    .filter((p) => p.length > 0);

  console.log(`Found ${paragraphs.length} paragraphs in TTS script\n`);

  // Load alignment data
  const alignmentData: AlignmentData = JSON.parse(fs.readFileSync(alignmentPath, 'utf-8'));
  console.log(`Loaded ${alignmentData.words.length} words from alignment data\n`);

  // Extract marker order from Playwright script
  const markers = extractMarkerOrder(playwrightPath);
  console.log(`Found ${markers.length} logNarration() calls in Playwright script\n`);

  if (markers.length !== paragraphs.length) {
    console.warn(
      `⚠️  WARNING: Marker count (${markers.length}) != Paragraph count (${paragraphs.length})\n`
    );
  }

  // Match paragraphs to alignment data
  console.log('⏱️  Matching paragraphs to timing...\n');

  let currentSearchIndex = 0;
  const timingMap: { [key: string]: number } = {};
  let previousEndTime = 0;

  for (let i = 0; i < Math.min(markers.length, paragraphs.length); i++) {
    const marker = markers[i];
    const paragraph = paragraphs[i];

    console.log(`${i + 1}. ${marker}`);
    console.log(`   Text: "${paragraph.substring(0, 60).replace(/\n/g, ' ')}..."`);

    const result = findParagraphEndTime(paragraph, alignmentData, currentSearchIndex);

    if (result) {
      const delay = result.endTime - previousEndTime;
      timingMap[marker] = Math.max(0, delay);
      previousEndTime = result.endTime;
      currentSearchIndex = result.nextIndex;

      console.log(`   ✅ Delay: ${delay.toFixed(1)}s (ends at ${result.endTime.toFixed(1)}s)\n`);
    } else {
      // Use fallback timing
      timingMap[marker] = 0;
      console.log(`   ❌ Match failed - setting delay to 0s\n`);
    }
  }

  // Generate config file
  const configContent = `// Generated timing configuration from TTS script alignment
// Generated: ${new Date().toISOString()}
// Source: script-for-tts-de-funny-with-emotions.txt
// Total markers: ${markers.length}

export const timingConfig: { [key: string]: number } = {
${markers.map((m) => `  '${m}': ${(timingMap[m] || 0).toFixed(1)},`).join('\n')}
};

// Total duration: ${previousEndTime.toFixed(1)}s (${(previousEndTime / 60).toFixed(2)} minutes)
`;

  fs.writeFileSync(outputPath, configContent);

  console.log(`✅ Timing config generated: ${outputPath}`);
  console.log(`\n📊 Summary:`);
  console.log(`   Total markers: ${markers.length}`);
  console.log(
    `   Total duration: ${previousEndTime.toFixed(1)}s (${(previousEndTime / 60).toFixed(2)} min)`
  );
  console.log(`   Average delay: ${(previousEndTime / markers.length).toFixed(1)}s`);
  console.log(`   Target audio: 740.7s\n`);
}

main();
