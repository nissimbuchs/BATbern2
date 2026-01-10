#!/usr/bin/env node
/**
 * Update narration markers in Playwright script.
 *
 * This script replaces console.log narration markers with timing helper function calls.
 *
 * Usage:
 *   node update-narration-markers.mjs
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const scriptPath = path.join(__dirname, '../../screencast-event-workflow.spec.ts');

console.log('🔧 Updating narration markers in Playwright script...\n');

// Read the file
let content = fs.readFileSync(scriptPath, 'utf-8');

// Pattern to match: console.log('[NARRATION_XX:XX] message');
const markerPattern = /console\.log\('\[NARRATION_(\d+:\d+)\]\s+([^']+)'\);/g;

let replacements = 0;

// Replace all narration markers
content = content.replace(markerPattern, (match, timestamp, message) => {
  replacements++;
  const marker = `NARRATION_${timestamp}`;
  return `logNarration('${marker}', '${message}');`;
});

// Remove standalone "Narration pause" wait timeouts
// These will be replaced by waitForNarration calls
const pausePattern = /\s*await page\.waitForTimeout\(\d+\);?\s*\/\/ Narration pause\n?/g;
content = content.replace(pausePattern, '\n');

// Write back
fs.writeFileSync(scriptPath, content, 'utf-8');

console.log(`✅ Replaced ${replacements} narration markers`);
console.log(`✅ Removed standalone narration pause waits`);
console.log('\nℹ️  Note: You still need to manually add waitForNarration() calls');
console.log('   after each logNarration() call where appropriate');
