#!/usr/bin/env node

/**
 * Generate Staging Sessions JSON
 *
 * Transforms sessions.json to replace pdf filenames with staging CDN URLs
 * for use with batch session import on staging environment.
 *
 * Usage:
 *   node scripts/staging/generate-staging-sessions-json.js
 *
 * Reads:  apps/BATspa-old/src/api/sessions.json
 * Writes: apps/BATspa-old/src/api/sessions-with-staging-urls.json
 *
 * For each session with pdf field:
 *   - Sets materialUrl to https://cdn.staging.batbern.ch/import-data/session-materials/<filename>
 *   - Preserves pdf field for reference
 *
 * Sessions without pdf field are kept unchanged.
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '../../');
const INPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/sessions.json');
const OUTPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/sessions-with-staging-urls.json');
const CDN_BASE_URL = 'https://cdn.staging.batbern.ch/import-data/session-materials';

/**
 * Main processing function
 */
function generateStagingSessionsJson() {
  console.log('='.repeat(60));
  console.log('BATbern Staging Sessions JSON Generator');
  console.log('='.repeat(60));
  console.log('');

  // Read input file
  console.log(`Reading: ${INPUT_FILE}`);
  if (!fs.existsSync(INPUT_FILE)) {
    console.error(`ERROR: Input file not found: ${INPUT_FILE}`);
    process.exit(1);
  }

  const content = fs.readFileSync(INPUT_FILE, 'utf8');
  let sessions;

  try {
    sessions = JSON.parse(content);
  } catch (error) {
    console.error(`ERROR: Failed to parse JSON: ${error.message}`);
    process.exit(1);
  }

  if (!Array.isArray(sessions)) {
    console.error('ERROR: Input file must contain an array of sessions');
    process.exit(1);
  }

  console.log(`Loaded ${sessions.length} sessions\n`);

  // Statistics
  const stats = {
    total: sessions.length,
    hasPdf: 0,
    updated: 0,
    noPdf: 0,
  };

  const transformationLog = [];

  // Process each session
  const updatedSessions = sessions.map((session, index) => {
    const updated = { ...session };

    // Check if has pdf field
    if (!session.pdf || session.pdf.trim() === '') {
      stats.noPdf++;
      return updated;
    }

    stats.hasPdf++;

    // Add materialUrl field
    const pdfFilename = session.pdf.trim();
    updated.materialUrl = `${CDN_BASE_URL}/${pdfFilename}`;
    stats.updated++;

    console.log(`  [${index}] BAT${session.bat} - ${session.title}: ${pdfFilename}`);
    console.log(`      → ${updated.materialUrl}`);

    transformationLog.push({
      index,
      bat: session.bat,
      title: session.title,
      pdf: pdfFilename,
      url: updated.materialUrl,
    });

    return updated;
  });

  // Write output file
  console.log('');
  console.log(`Writing: ${OUTPUT_FILE}`);

  try {
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(updatedSessions, null, 2), 'utf8');
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
  console.log(`Total sessions:           ${stats.total}`);
  console.log(`Have PDF materials:       ${stats.hasPdf}`);
  console.log(`  ✓ Transformed to CDN:   ${stats.updated}`);
  console.log(`No PDF materials:         ${stats.noPdf}`);
  console.log('='.repeat(60));
  console.log('');

  // Show sample CDN URLs
  if (stats.updated > 0) {
    console.log('Sample CDN URLs:');
    const sampleUrls = transformationLog
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
  console.log('  1. Run: ./scripts/staging/upload-session-materials-fast.sh');
  console.log('  2. Verify materials are accessible via CDN');
  console.log('  3. Open https://staging.batbern.ch');
  console.log('  4. Navigate to Sessions → Batch Import');
  console.log(`  5. Upload: ${path.basename(OUTPUT_FILE)}`);
  console.log('');
}

// Run the script
try {
  generateStagingSessionsJson();
} catch (error) {
  console.error('FATAL ERROR:', error.message);
  console.error(error.stack);
  process.exit(1);
}
