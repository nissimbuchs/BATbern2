#!/usr/bin/env node

/**
 * Generate Staging Speakers JSON
 *
 * Transforms speakers.json to replace portrait paths with staging CDN URLs
 * for use with batch speaker import on staging environment.
 *
 * Usage:
 *   node scripts/staging/generate-staging-speakers-json.js
 *
 * Reads:  apps/BATspa-old/src/api/speakers.json
 * Writes: apps/BATspa-old/src/api/speakers-with-staging-urls.json
 *
 * For each speaker with portrait:
 *   - Extracts filename from portrait path
 *   - Sets portraitUrl to https://cdn.staging.batbern.ch/import-data/speaker-portraits/<filename>
 *   - Preserves portrait field for reference
 *
 * Speakers with existing portraitUrl (external URLs) are kept unchanged.
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '../../');
const INPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/speakers.json');
const OUTPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/speakers-with-staging-urls.json');
const CDN_BASE_URL = 'https://cdn.staging.batbern.ch/import-data/speaker-portraits';

/**
 * Extract filename from portrait field
 */
function extractFilename(portrait) {
  if (!portrait) return null;
  const filename = path.basename(portrait);
  // Validate filename is not empty or just a dot
  if (!filename || filename === '.' || filename === '..') {
    return null;
  }
  return filename;
}

/**
 * Main processing function
 */
function generateStagingSpeakersJson() {
  console.log('='.repeat(60));
  console.log('BATbern Staging Speakers JSON Generator');
  console.log('='.repeat(60));
  console.log('');

  // Read input file
  console.log(`Reading: ${INPUT_FILE}`);
  if (!fs.existsSync(INPUT_FILE)) {
    console.error(`ERROR: Input file not found: ${INPUT_FILE}`);
    process.exit(1);
  }

  const content = fs.readFileSync(INPUT_FILE, 'utf8');
  let speakers;

  try {
    speakers = JSON.parse(content);
  } catch (error) {
    console.error(`ERROR: Failed to parse JSON: ${error.message}`);
    process.exit(1);
  }

  if (!Array.isArray(speakers)) {
    console.error('ERROR: Input file must contain an array of speakers');
    process.exit(1);
  }

  console.log(`Loaded ${speakers.length} speakers\n`);

  // Statistics
  const stats = {
    total: speakers.length,
    hasPortrait: 0,
    hasPortraitUrl: 0,
    updated: 0,
    skipped: 0,
    errors: 0,
  };

  const transformationLog = [];

  // Process each speaker
  const updatedSpeakers = speakers.map((speaker, index) => {
    const updated = { ...speaker };

    // Skip if already has portraitUrl (external URL) - keep unchanged
    if (speaker.portraitUrl) {
      stats.hasPortraitUrl++;
      transformationLog.push({
        index,
        id: speaker.id,
        action: 'kept-external',
        url: speaker.portraitUrl,
      });
      return updated;
    }

    // Check if has portrait
    if (!speaker.portrait) {
      stats.skipped++;
      return updated;
    }

    stats.hasPortrait++;

    // Extract filename
    const filename = extractFilename(speaker.portrait);
    if (!filename) {
      stats.errors++;
      console.warn(`  [${index}] ${speaker.id}: ERROR - Could not extract filename from: ${speaker.portrait}`);
      transformationLog.push({
        index,
        id: speaker.id,
        action: 'error',
        error: 'Invalid portrait path',
      });
      return updated;
    }

    // Update with CDN URL
    updated.portraitUrl = `${CDN_BASE_URL}/${filename}`;
    stats.updated++;

    console.log(`  [${index}] ${speaker.id}: ${filename} → ${updated.portraitUrl}`);
    transformationLog.push({
      index,
      id: speaker.id,
      action: 'transformed',
      filename,
      url: updated.portraitUrl,
    });

    return updated;
  });

  // Write output file
  console.log('');
  console.log(`Writing: ${OUTPUT_FILE}`);

  try {
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(updatedSpeakers, null, 2), 'utf8');
    console.log('✓ File written successfully');
  } catch (error) {
    console.error(`ERROR: Failed to write output file: ${error.message}`);
    process.exit(1);
  }

  // Summary
  console.log('');
  console.log('='.repeat(60));
  console.log('Summary');
  console.log('='.repeat(60));
  console.log(`Total speakers:            ${stats.total}`);
  console.log(`Already have portraitUrl:  ${stats.hasPortraitUrl} (kept unchanged)`);
  console.log(`Have portrait:             ${stats.hasPortrait}`);
  console.log(`  ✓ Transformed to CDN:    ${stats.updated}`);
  console.log(`  ✗ Errors:                ${stats.errors}`);
  console.log(`No portrait data:          ${stats.skipped}`);
  console.log('='.repeat(60));
  console.log('');

  // Show sample CDN URLs
  if (stats.updated > 0) {
    console.log('Sample CDN URLs:');
    const sampleUrls = transformationLog
      .filter(log => log.action === 'transformed')
      .slice(0, 5)
      .map(log => `  - ${log.url}`);
    console.log(sampleUrls.join('\n'));
    if (stats.updated > 5) {
      console.log(`  ... and ${stats.updated - 5} more`);
    }
    console.log('');
  }

  // Next steps
  console.log('Next steps:');
  console.log('  1. Upload portraits to S3: ./scripts/staging/upload-speaker-portraits-fast.sh');
  console.log('  2. Verify portraits are accessible via CDN');
  console.log('  3. Open https://staging.batbern.ch');
  console.log('  4. Navigate to Users → Batch Import Speakers');
  console.log(`  5. Upload: ${path.basename(OUTPUT_FILE)}`);
  console.log('');

  // Exit with error if there were transformation errors
  if (stats.errors > 0) {
    console.warn(`WARNING: ${stats.errors} speakers had errors during transformation`);
    process.exit(1);
  }
}

// Run the script
try {
  generateStagingSpeakersJson();
} catch (error) {
  console.error('FATAL ERROR:', error.message);
  console.error(error.stack);
  process.exit(1);
}
