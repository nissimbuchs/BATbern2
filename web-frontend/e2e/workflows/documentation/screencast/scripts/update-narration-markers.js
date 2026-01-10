#!/usr/bin/env node
/**
 * Update narration markers in Playwright script.
 *
 * This script replaces console.log narration markers with timing helper function calls.
 *
 * Usage:
 *   node update-narration-markers.js
 */

const fs = require('fs');
const path = require('path');

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

// Remove standalone "Narration pause" wait timeouts  // These will be replaced by waitForNarration calls
const pausePattern = /\s*await page\.waitForTimeout\(\d+\);?\s*\/\/ Narration pause\n?/g;
content = content.replace(pausePattern, '\n');

// Write back
fs.writeFileSync(scriptPath, content, 'utf-8');

console.log(`✅ Replaced ${replacements} narration markers`);
console.log(`✅ Removed standalone narration pause waits`);
console.log('\nNext step: Manually add waitForNarration() calls after each marker');
console.log('Or run the script with --add-waits flag to auto-add them');
