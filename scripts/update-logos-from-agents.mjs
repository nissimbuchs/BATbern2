#!/usr/bin/env node

/**
 * Update company logos based on agent findings
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JSON_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.json');

// Logo mappings from agent findings
const LOGO_UPDATES = {
  // Swiss Government (all use same logo)
  'isc-ejpd': 'https://upload.wikimedia.org/wikipedia/commons/0/05/Logo_der_Schweizerischen_Eidgenossenschaft.svg',
  'gs-wbf': 'https://upload.wikimedia.org/wikipedia/commons/0/05/Logo_der_Schweizerischen_Eidgenossenschaft.svg',
  'astra': 'https://upload.wikimedia.org/wikipedia/commons/0/05/Logo_der_Schweizerischen_Eidgenossenschaft.svg',
  'bafu': 'https://upload.wikimedia.org/wikipedia/commons/0/05/Logo_der_Schweizerischen_Eidgenossenschaft.svg',
  'bar': 'https://upload.wikimedia.org/wikipedia/commons/0/05/Logo_der_Schweizerischen_Eidgenossenschaft.svg',
  'bazl': 'https://upload.wikimedia.org/wikipedia/commons/0/05/Logo_der_Schweizerischen_Eidgenossenschaft.svg',

  // Insurance/Finance
  'baloise': 'https://upload.wikimedia.org/wikipedia/commons/8/88/Baloise_Logo_2022.svg',

  // Transport/Public Services
  'bernmobil': 'https://upload.wikimedia.org/wikipedia/commons/8/8b/Bernmobil_Logo.svg',

  // Tech/Telecom
  'ascom': 'https://upload.wikimedia.org/wikipedia/commons/2/20/Logo_Ascom.svg',
  'bernafon': 'https://logotyp.us/file/bernafon.svg',

  // Cloud/Tech Giants
  'amazon': 'https://upload.wikimedia.org/wikipedia/commons/9/93/Amazon_Web_Services_Logo.svg', // AWS

  // Higher Education
  'bfh': 'https://seeklogo.com/images/B/bern-university-of-applied-sciences-bfh-logo-2FFC62ECD6-seeklogo.com.png',
};

function updateLogos() {
  console.log('Reading JSON file...');
  const data = JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8'));

  let updatedCount = 0;

  for (const company of data) {
    const logoUrl = LOGO_UPDATES[company.id];

    if (logoUrl && !company.logoUrl) {
      console.log(`✓ Adding logo for: ${company.displayName}`);
      company.logoUrl = logoUrl;
      company.has_logo = true;
      company.status = 'complete';
      updatedCount++;
    }
  }

  console.log(`\nUpdated ${updatedCount} companies with logos`);

  // Save updated JSON
  fs.writeFileSync(JSON_PATH, JSON.stringify(data, null, 2), 'utf-8');
  console.log(`\nSaved to: ${JSON_PATH}`);

  // Summary
  const withLogos = data.filter(c => c.logoUrl).length;
  console.log(`\nNew totals:`);
  console.log(`  Total companies: ${data.length}`);
  console.log(`  With logos: ${withLogos} (${((withLogos / data.length) * 100).toFixed(1)}%)`);
  console.log(`  Without logos: ${data.length - withLogos}`);
}

updateLogos();
