#!/usr/bin/env node

/**
 * Fast parallel CSV to JSON converter with logo fetching
 *
 * Features:
 * - Parallel processing with concurrency limit
 * - Progress tracking
 * - Logo URL discovery from multiple sources
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const CSV_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.csv');
const OUTPUT_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.json');

const CONCURRENCY = 10; // Process 10 companies at a time
const TIMEOUT = 5000; // 5 second timeout per request

/**
 * Parse CSV to array of company data
 */
function parseCSV(csvContent) {
  const lines = csvContent.split('\n');
  const companies = [];

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    const parts = line.split(',');
    if (parts.length < 5) continue;

    const domain = parts[0]?.trim();
    const displayName = parts[1]?.trim();
    const companyName = parts[2]?.trim();
    const col1 = parseInt(parts[3]?.trim() || '0', 10);
    const col2 = parseInt(parts[4]?.trim() || '0', 10);

    if (!companyName) continue;

    companies.push({
      domain,
      displayName: displayName || companyName,
      companyName,
      speakerCount: col1 + col2,
    });
  }

  return companies;
}

/**
 * Determine industry from company name/domain
 */
function guessIndustry(displayName, domain) {
  const combined = `${(displayName || '').toLowerCase()} ${(domain || '').toLowerCase()}`;

  if (combined.match(/bank|finanz|credit|raiffeisen|kantonalbank|julius.*b[aä]r/)) return 'Financial Services';
  if (combined.match(/versicherung|insurance|mobiliar|helvetia|axa|baloise|css|helsana|atupri/)) return 'Insurance';
  if (combined.match(/sbb|bls|bahn|transport|mobility|bernmobil/)) return 'Transportation';
  if (combined.match(/post\b|postfinance|logistics/)) return 'Logistics';
  if (combined.match(/swisscom|telecom|mobile|internet|aastra/)) return 'Telecommunications';
  if (combined.match(/energie|energy|strom|bkw|ewz|alpiq|axpo/)) return 'Energy';
  if (combined.match(/pharma|medizin|hospital|klinik|insel|spital|behring|health/)) return 'Healthcare';
  if (combined.match(/universit[aä]t|hochschule|eth|epfl|bfh|zhaw|fachhochschule|hslu|phbern/)) return 'Higher Education';
  if (combined.match(/\b(admin\.ch|bundesamt|kanton\b|bund\b|eidg\.|staat|bafu|bit\b|isb)/)) return 'Government';
  if (combined.match(/aws|amazon.*web|microsoft|ibm|oracle|google.*cloud|cloud/)) return 'Cloud Provider';
  if (combined.match(/migros|coop|bell\b/)) return 'Retail';
  if (combined.match(/media|verlag|ringier|srf|srgssr/)) return 'Media';

  return 'IT Consulting';
}

/**
 * Try to fetch logo from domain
 */
async function findLogo(domain) {
  if (!domain) return null;

  const baseUrl = domain.startsWith('http') ? domain : `https://${domain}`;

  // Common logo patterns
  const patterns = [
    '/logo.svg',
    '/logo.png',
    '/images/logo.svg',
    '/images/logo.png',
    '/assets/logo.svg',
    '/_assets/logo.svg',
  ];

  // Try common logo paths first (faster)
  for (const pattern of patterns) {
    try {
      const url = `${baseUrl}${pattern}`;
      const response = await fetch(url, {
        method: 'HEAD',
        signal: AbortSignal.timeout(2000),
      });
      if (response.ok) {
        return url;
      }
    } catch {
      continue;
    }
  }

  // Try fetching homepage
  try {
    const response = await fetch(baseUrl, {
      signal: AbortSignal.timeout(TIMEOUT),
      headers: { 'User-Agent': 'Mozilla/5.0' },
    });

    if (!response.ok) return null;

    const html = await response.text();

    // Look for og:image
    const ogMatch = html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']/i);
    if (ogMatch) {
      let url = ogMatch[1];
      if (url.startsWith('//')) url = `https:${url}`;
      else if (!url.startsWith('http')) url = `${baseUrl}${url}`;
      return url;
    }

    // Look for logo image tag
    const logoMatch = html.match(/<img[^>]*(class|id)=["'][^"']*logo[^"']*["'][^>]*src=["']([^"']+)["']/i);
    if (logoMatch) {
      let url = logoMatch[2];
      if (url.startsWith('//')) url = `https:${url}`;
      else if (!url.startsWith('http')) url = `${baseUrl}${url}`;
      return url;
    }

    // Look for icon/favicon
    const faviconMatch = html.match(/<link[^>]*rel=["'](?:icon|shortcut icon)["'][^>]*href=["']([^"']+)["']/i);
    if (faviconMatch) {
      let url = faviconMatch[1];
      if (url.endsWith('.svg') || url.endsWith('.png')) {
        if (url.startsWith('//')) url = `https:${url}`;
        else if (!url.startsWith('http')) url = `${baseUrl}${url}`;
        return url;
      }
    }
  } catch {
    // Failed to fetch
  }

  return null;
}

/**
 * Process a single company
 */
async function processCompany(company) {
  const { domain, displayName, companyName, speakerCount } = company;

  const logoUrl = await findLogo(domain);
  const website = domain ? (domain.startsWith('http') ? domain : `https://${domain}`) : null;
  const industry = guessIndustry(displayName, domain);

  return {
    id: companyName,
    displayName,
    url: website,
    industry,
    speakerCount,
    logo: null,
    logoFilePath: null,
    has_logo: !!logoUrl,
    status: logoUrl ? 'complete' : 'pending_logo',
    ...(logoUrl && { logoUrl }),
  };
}

/**
 * Process companies in parallel batches
 */
async function processBatch(companies, concurrency = CONCURRENCY) {
  const results = [];
  const total = companies.length;

  for (let i = 0; i < total; i += concurrency) {
    const batch = companies.slice(i, Math.min(i + concurrency, total));
    const batchResults = await Promise.all(batch.map(processCompany));
    results.push(...batchResults);

    const progress = Math.min(i + concurrency, total);
    const percent = ((progress / total) * 100).toFixed(1);
    const logosFound = results.filter(r => r.logoUrl).length;
    console.log(`Progress: ${progress}/${total} (${percent}%) - Logos found: ${logosFound}`);
  }

  return results;
}

/**
 * Main function
 */
async function main() {
  console.log('Reading CSV file...');
  const csvContent = fs.readFileSync(CSV_PATH, 'utf-8');

  console.log('Parsing CSV...');
  const companies = parseCSV(csvContent);
  console.log(`Found ${companies.length} companies\n`);

  console.log('Fetching logos in parallel...\n');
  const results = await processBatch(companies);

  // Sort by speakerCount descending
  results.sort((a, b) => b.speakerCount - a.speakerCount);

  // Write output
  console.log(`\nWriting to: ${OUTPUT_PATH}`);
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(results, null, 2), 'utf-8');

  // Summary
  const withLogos = results.filter(r => r.logoUrl).length;
  const withoutLogos = results.length - withLogos;

  console.log('\n✓ Done!');
  console.log(`\nSummary:`);
  console.log(`  Total companies: ${results.length}`);
  console.log(`  Logos found: ${withLogos} (${((withLogos / results.length) * 100).toFixed(1)}%)`);
  console.log(`  Logos missing: ${withoutLogos}`);
  console.log(`\nOutput: ${OUTPUT_PATH}`);
}

main().catch(error => {
  console.error('Error:', error);
  process.exit(1);
});
