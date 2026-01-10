#!/usr/bin/env node
/**
 * Add waitForNarration() calls after each logNarration() call.
 *
 * This script adds the timing wait calls to synchronize with the narration.
 *
 * Usage:
 *   node add-wait-calls.mjs
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const scriptPath = path.join(__dirname, '../../screencast-event-workflow.spec.ts');

console.log('⏱️  Adding waitForNarration() calls...\n');

// Read the file
let content = fs.readFileSync(scriptPath, 'utf-8');

// Pattern to match: logNarration('NARRATION_XX:XX', 'message');
// We want to add: await waitForNarration('NARRATION_XX:XX', page);
// But only if it's not already there

const lines = content.split('\n');
const newLines = [];
let addedWaits = 0;

for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  newLines.push(line);

  // Check if this line is a logNarration call
  const match = line.match(/logNarration\('(NARRATION_\d+:\d+)',/);
  if (match) {
    const marker = match[1];

    // Check if the next line is already a waitForNarration call for this marker
    const nextLine = lines[i + 1];
    if (nextLine && nextLine.includes(`waitForNarration('${marker}', page)`)) {
      // Already has wait call, skip
      continue;
    }

    // Add wait call with same indentation as logNarration
    const indentation = line.match(/^\s*/)[0];
    newLines.push(`${indentation}await waitForNarration('${marker}', page);`);
    addedWaits++;
  }
}

// Write back
const newContent = newLines.join('\n');
fs.writeFileSync(scriptPath, newContent, 'utf-8');

console.log(`✅ Added ${addedWaits} waitForNarration() calls`);
console.log('\nNext step: Run verification test to check timing configuration');
