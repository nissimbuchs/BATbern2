#!/usr/bin/env node

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JSON_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.json');

const data = JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8'));
const withoutLogos = data.filter(c => !c.logoUrl);

console.log('Companies without logos:', withoutLogos.length);
console.log('\nTop 50 by speaker count:\n');

withoutLogos
  .sort((a, b) => b.speakerCount - a.speakerCount)
  .slice(0, 50)
  .forEach((c, i) => {
    console.log(`${i + 1}. ${c.displayName} (${c.speakerCount} speakers)`);
    console.log(`   Domain: ${c.url || 'no URL'}`);
    console.log(`   Industry: ${c.industry}`);
    console.log('');
  });
