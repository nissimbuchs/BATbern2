#!/usr/bin/env node

/**
 * Updates companies.json to replace logoFilePath with localhost URLs
 *
 * Usage:
 *   node scripts/dev/update-logo-urls.js
 *
 * Reads:  apps/BATspa-old/src/api/companies.json
 * Writes: apps/BATspa-old/src/api/companies-with-local-urls.json
 *
 * For each company with logoFilePath:
 *   - Extracts filename from path
 *   - Sets logoUrl to http://localhost:8888/<filename>
 *   - Preserves logoFilePath for reference
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '../../');
const INPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/companies.json');
const OUTPUT_FILE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/api/companies-with-local-urls.json');
const LOGO_SERVER_URL = 'http://localhost:8888';
const ARCHIV_BASE = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/archiv');
const PARTNERS_DIR = path.join(PROJECT_ROOT, 'apps/BATspa-old/src/assets/partners');

// Logo directories to check if files exist
const LOGO_DIRS = [];

// Add partners directory first (higher quality logos)
if (fs.existsSync(PARTNERS_DIR)) {
  LOGO_DIRS.push(PARTNERS_DIR);
}

// Add all archiv directories (1-57)
for (let i = 1; i <= 57; i++) {
  const dir = path.join(ARCHIV_BASE, String(i));
  if (fs.existsSync(dir)) {
    LOGO_DIRS.push(dir);
  }
}

/**
 * Check if logo file exists in any logo directory
 */
function findLogoFile(filename) {
  for (const dir of LOGO_DIRS) {
    const filePath = path.join(dir, filename);
    if (fs.existsSync(filePath)) {
      return filePath;
    }
  }
  return null;
}

/**
 * Extract filename from logoFilePath
 */
function extractFilename(logoFilePath) {
  if (!logoFilePath) return null;
  return path.basename(logoFilePath);
}

/**
 * Main processing function
 */
function updateLogoUrls() {
  console.log('='.repeat(60));
  console.log('BATbern Logo URL Update Script');
  console.log('='.repeat(60));
  console.log('');

  // Read input file
  console.log(`Reading: ${INPUT_FILE}`);
  if (!fs.existsSync(INPUT_FILE)) {
    console.error(`ERROR: Input file not found: ${INPUT_FILE}`);
    process.exit(1);
  }

  const content = fs.readFileSync(INPUT_FILE, 'utf8');
  const companies = JSON.parse(content);

  console.log(`Loaded ${companies.length} companies\n`);

  // Statistics
  const stats = {
    total: companies.length,
    hasLogoFilePath: 0,
    fileExists: 0,
    fileMissing: 0,
    hasLogoUrl: 0,
    updated: 0,
  };

  // Process each company
  const updatedCompanies = companies.map((company, index) => {
    const updated = { ...company };

    // Skip if already has logoUrl (external URL)
    if (company.logoUrl) {
      stats.hasLogoUrl++;
      return updated;
    }

    // Check if has logoFilePath
    if (!company.logoFilePath) {
      return updated;
    }

    stats.hasLogoFilePath++;

    // Extract filename
    const filename = extractFilename(company.logoFilePath);
    if (!filename) {
      console.warn(`  [${index}] ${company.id}: Could not extract filename from ${company.logoFilePath}`);
      return updated;
    }

    // Check if file exists
    const filePath = findLogoFile(filename);
    if (!filePath) {
      stats.fileMissing++;
      console.warn(`  [${index}] ${company.id}: File not found: ${filename}`);
      return updated;
    }

    stats.fileExists++;

    // Update with localhost URL
    updated.logoUrl = `${LOGO_SERVER_URL}/${filename}`;
    stats.updated++;

    console.log(`  [${index}] ${company.id}: ${filename} → ${updated.logoUrl}`);

    return updated;
  });

  // Write output file
  console.log('');
  console.log(`Writing: ${OUTPUT_FILE}`);
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(updatedCompanies, null, 2), 'utf8');

  // Summary
  console.log('');
  console.log('='.repeat(60));
  console.log('Summary');
  console.log('='.repeat(60));
  console.log(`Total companies:           ${stats.total}`);
  console.log(`Already have logoUrl:      ${stats.hasLogoUrl} (external URLs)`);
  console.log(`Have logoFilePath:         ${stats.hasLogoFilePath} (local files)`);
  console.log(`  ✓ File exists:           ${stats.fileExists}`);
  console.log(`  ✗ File missing:          ${stats.fileMissing}`);
  console.log(`Updated with localhost:    ${stats.updated}`);
  console.log('='.repeat(60));
  console.log('');
  console.log('Next steps:');
  console.log('  1. Start logo server: node scripts/dev/serve-logos.js');
  console.log('  2. Use batch import with: companies-with-local-urls.json');
  console.log('');
}

// Run the script
try {
  updateLogoUrls();
} catch (error) {
  console.error('ERROR:', error.message);
  console.error(error.stack);
  process.exit(1);
}
