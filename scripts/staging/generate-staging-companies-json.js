#!/usr/bin/env node

/**
 * Generate Staging Companies JSON
 *
 * Transforms companies.json to replace logoFilePath with staging CDN URLs
 * for use with batch company import on staging environment.
 *
 * Usage:
 *   node scripts/staging/generate-staging-companies-json.js
 *
 * Reads:  apps/BATspa-old/src/api/companies.json
 * Writes: apps/BATspa-old/src/api/companies-with-staging-urls.json
 *
 * For each company with logoFilePath:
 *   - Extracts filename from path
 *   - Sets logoUrl to https://cdn.staging.batbern.ch/import-data/company-logos/<filename>
 *   - Preserves logoFilePath for reference
 *
 * Companies with existing logoUrl (external URLs) are kept unchanged.
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '../../');
const INPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/companies.json');
const OUTPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/companies-with-staging-urls.json');
const CDN_BASE_URL = 'https://cdn.staging.batbern.ch/import-data/company-logos';

/**
 * Extract filename from logoFilePath
 */
function extractFilename(logoFilePath) {
  if (!logoFilePath) return null;
  const filename = path.basename(logoFilePath);
  // Validate filename is not empty or just a dot
  if (!filename || filename === '.' || filename === '..') {
    return null;
  }
  return filename;
}

/**
 * Main processing function
 */
function generateStagingCompaniesJson() {
  console.log('='.repeat(60));
  console.log('BATbern Staging Companies JSON Generator');
  console.log('='.repeat(60));
  console.log('');

  // Read input file
  console.log(`Reading: ${INPUT_FILE}`);
  if (!fs.existsSync(INPUT_FILE)) {
    console.error(`ERROR: Input file not found: ${INPUT_FILE}`);
    process.exit(1);
  }

  const content = fs.readFileSync(INPUT_FILE, 'utf8');
  let companies;

  try {
    companies = JSON.parse(content);
  } catch (error) {
    console.error(`ERROR: Failed to parse JSON: ${error.message}`);
    process.exit(1);
  }

  if (!Array.isArray(companies)) {
    console.error('ERROR: Input file must contain an array of companies');
    process.exit(1);
  }

  console.log(`Loaded ${companies.length} companies\n`);

  // Statistics
  const stats = {
    total: companies.length,
    hasLogoFilePath: 0,
    hasLogoUrl: 0,
    updated: 0,
    skipped: 0,
    errors: 0,
  };

  const transformationLog = [];

  // Process each company
  const updatedCompanies = companies.map((company, index) => {
    const updated = { ...company };

    // Skip if already has logoUrl (external URL) - keep unchanged
    if (company.logoUrl) {
      stats.hasLogoUrl++;
      transformationLog.push({
        index,
        id: company.id,
        action: 'kept-external',
        url: company.logoUrl,
      });
      return updated;
    }

    // Check if has logoFilePath
    if (!company.logoFilePath) {
      stats.skipped++;
      return updated;
    }

    stats.hasLogoFilePath++;

    // Extract filename
    const filename = extractFilename(company.logoFilePath);
    if (!filename) {
      stats.errors++;
      console.warn(`  [${index}] ${company.id}: ERROR - Could not extract filename from: ${company.logoFilePath}`);
      transformationLog.push({
        index,
        id: company.id,
        action: 'error',
        error: 'Invalid logoFilePath',
      });
      return updated;
    }

    // Update with CDN URL
    updated.logoUrl = `${CDN_BASE_URL}/${filename}`;
    stats.updated++;

    console.log(`  [${index}] ${company.id}: ${filename} → ${updated.logoUrl}`);
    transformationLog.push({
      index,
      id: company.id,
      action: 'transformed',
      filename,
      url: updated.logoUrl,
    });

    return updated;
  });

  // Write output file
  console.log('');
  console.log(`Writing: ${OUTPUT_FILE}`);

  try {
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(updatedCompanies, null, 2), 'utf8');
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
  console.log(`Total companies:           ${stats.total}`);
  console.log(`Already have logoUrl:      ${stats.hasLogoUrl} (kept unchanged)`);
  console.log(`Have logoFilePath:         ${stats.hasLogoFilePath}`);
  console.log(`  ✓ Transformed to CDN:    ${stats.updated}`);
  console.log(`  ✗ Errors:                ${stats.errors}`);
  console.log(`No logo data:              ${stats.skipped}`);
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
  console.log('  1. Verify logos are accessible via CDN');
  console.log('  2. Open https://staging.batbern.ch');
  console.log('  3. Navigate to Companies → Batch Import');
  console.log(`  4. Upload: ${path.basename(OUTPUT_FILE)}`);
  console.log('');

  // Exit with error if there were transformation errors
  if (stats.errors > 0) {
    console.warn(`WARNING: ${stats.errors} companies had errors during transformation`);
    process.exit(1);
  }
}

// Run the script
try {
  generateStagingCompaniesJson();
} catch (error) {
  console.error('FATAL ERROR:', error.message);
  console.error(error.stack);
  process.exit(1);
}
