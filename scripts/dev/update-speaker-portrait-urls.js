#!/usr/bin/env node

/**
 * Update speakers.json with localhost portrait URLs for batch import
 *
 * This script reads speakers.json and adds portraitUrl fields pointing to localhost:8888
 * so that the batch import can fetch portrait images from the local server.
 *
 * Usage:
 *   node scripts/dev/update-speaker-portrait-urls.js
 *
 * Prerequisites:
 *   - Run `node scripts/dev/serve-logos.js` in a separate terminal first
 *
 * Output:
 *   - apps/BATspa-old/src/api/speakers-with-local-urls.json
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '../../');
const INPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/speakers.json');
const OUTPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/speakers-with-local-urls.json');
const ARCHIV_BASE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/archiv');
const PORTRAITS_DIR = path.join(PROJECT_ROOT, 'apps/BATspa-old/public/portraits');

const LOCALHOST_PORT = 8888;

/**
 * Find portrait file in archiv directories
 * Returns the filename if found, null otherwise
 */
function findPortraitFile(filename) {
  if (!filename) return null;

  // Check portraits directory first
  const portraitsPath = path.join(PORTRAITS_DIR, filename);
  if (fs.existsSync(portraitsPath)) {
    return filename;
  }

  // Check all archiv directories
  for (let i = 1; i <= 57; i++) {
    const archivPath = path.join(ARCHIV_BASE, String(i), filename);
    if (fs.existsSync(archivPath)) {
      return filename;
    }
  }

  return null;
}

/**
 * Main function
 */
function main() {
  console.log('='.repeat(60));
  console.log('BATbern Speaker Portrait URL Updater');
  console.log('='.repeat(60));
  console.log('');

  // Read input file
  console.log(`Reading: ${INPUT_FILE}`);
  if (!fs.existsSync(INPUT_FILE)) {
    console.error(`ERROR: Input file not found: ${INPUT_FILE}`);
    process.exit(1);
  }

  const inputContent = fs.readFileSync(INPUT_FILE, 'utf8');
  const speakers = JSON.parse(inputContent);
  console.log(`Loaded ${speakers.length} speakers`);
  console.log('');

  // Statistics
  let withPortrait = 0;
  let withPortraitUrl = 0;
  let fileExists = 0;
  let fileMissing = 0;
  let noPortrait = 0;

  // Process each speaker
  const updatedSpeakers = speakers.map((speaker, index) => {
    const updated = { ...speaker };

    // Already has portraitUrl (external URL)
    if (speaker.portraitUrl) {
      withPortraitUrl++;
      console.log(`  [${index}] ${speaker.id}: already has portraitUrl`);
      return updated;
    }

    // Has portrait filename
    if (speaker.portrait) {
      withPortrait++;
      const filename = findPortraitFile(speaker.portrait);

      if (filename) {
        fileExists++;
        updated.portraitUrl = `http://localhost:${LOCALHOST_PORT}/${filename}`;
        console.log(`  [${index}] ${speaker.id}: ${speaker.portrait} -> ${updated.portraitUrl}`);
      } else {
        fileMissing++;
        console.log(`  [${index}] ${speaker.id}: ${speaker.portrait} (FILE NOT FOUND)`);
      }
    } else {
      noPortrait++;
      console.log(`  [${index}] ${speaker.id}: no portrait`);
    }

    return updated;
  });

  console.log('');
  console.log('='.repeat(60));
  console.log('Summary');
  console.log('='.repeat(60));
  console.log(`Total speakers:           ${speakers.length}`);
  console.log(`Already have portraitUrl: ${withPortraitUrl} (external URLs)`);
  console.log(`Have portrait filename:   ${withPortrait} (local files)`);
  console.log(`  ✓ File exists:          ${fileExists}`);
  console.log(`  ✗ File missing:         ${fileMissing}`);
  console.log(`No portrait data:         ${noPortrait}`);
  console.log(`Updated with localhost:   ${fileExists}`);
  console.log('='.repeat(60));

  // Write output file
  console.log('');
  console.log(`Writing: ${OUTPUT_FILE}`);
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(updatedSpeakers, null, 2));
  console.log('Done!');
  console.log('');
  console.log('Next steps:');
  console.log('  1. Run: node scripts/dev/serve-logos.js');
  console.log('  2. Upload speakers-with-local-urls.json in the User Management batch import');
  console.log('');
}

main();
