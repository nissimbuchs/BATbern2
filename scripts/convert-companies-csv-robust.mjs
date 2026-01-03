#!/usr/bin/env node

/**
 * Robust CSV to JSON converter with logo fetching
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const CSV_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.csv');
const OUTPUT_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.json');
const PROGRESS_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.progress.json');

const CONCURRENCY = 15;
const TIMEOUT = 3000; // Shorter timeout
const SAVE_INTERVAL = 20; // Save progress every N companies

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

async function findLogoWithTimeout(domain) {
  const timeoutPromise = new Promise((_, reject) =>
    setTimeout(() => reject(new Error('timeout')), TIMEOUT)
  );

  try {
    return await Promise.race([findLogo(domain), timeoutPromise]);
  } catch (error) {
    return null; // Return null on any error
  }
}

async function findLogo(domain) {
  if (!domain) return null;

  const baseUrl = domain.startsWith('http') ? domain : `https://${domain}`;

  // Quick patterns first
  const patterns = ['/logo.svg', '/logo.png', '/images/logo.svg'];

  for (const pattern of patterns) {
    try {
      const url = `${baseUrl}${pattern}`;
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 1500);

      const response = await fetch(url, {
        method: 'HEAD',
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (response.ok) return url;
    } catch {
      continue;
    }
  }

  // Try homepage with strict timeout
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2500);

    const response = await fetch(baseUrl, {
      signal: controller.signal,
      headers: { 'User-Agent': 'Mozilla/5.0' },
    });

    clearTimeout(timeoutId);

    if (!response.ok) return null;

    const html = await response.text();

    // og:image
    const ogMatch = html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']/i);
    if (ogMatch) {
      let url = ogMatch[1];
      if (url.startsWith('//')) url = `https:${url}`;
      else if (!url.startsWith('http')) url = `${baseUrl}${url}`;
      return url;
    }

    // Logo img tag
    const logoMatch = html.match(/<img[^>]*(class|id)=["'][^"']*logo[^"']*["'][^>]*src=["']([^"']+)["']/i);
    if (logoMatch) {
      let url = logoMatch[2];
      if (url.startsWith('//')) url = `https:${url}`;
      else if (!url.startsWith('http')) url = `${baseUrl}${url}`;
      return url;
    }
  } catch {
    // Timeout or error
  }

  return null;
}

async function processCompany(company) {
  const { domain, displayName, companyName, speakerCount } = company;

  let logoUrl = null;
  try {
    logoUrl = await findLogoWithTimeout(domain);
  } catch {
    logoUrl = null;
  }

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

async function processBatch(companies, concurrency = CONCURRENCY, startFrom = 0, existingResults = []) {
  const results = [...existingResults];
  const total = companies.length;

  for (let i = startFrom; i < total; i += concurrency) {
    const batch = companies.slice(i, Math.min(i + concurrency, total));

    // Process with individual error handling
    const batchPromises = batch.map(async (company) => {
      try {
        return await processCompany(company);
      } catch (error) {
        // If processing fails, return minimal company data
        return {
          id: company.companyName,
          displayName: company.displayName,
          url: company.domain ? `https://${company.domain}` : null,
          industry: guessIndustry(company.displayName, company.domain),
          speakerCount: company.speakerCount,
          logo: null,
          logoFilePath: null,
          has_logo: false,
          status: 'pending_logo',
        };
      }
    });

    const batchResults = await Promise.all(batchPromises);
    results.push(...batchResults);

    const progress = Math.min(i + concurrency, total);
    const percent = ((progress / total) * 100).toFixed(1);
    const logosFound = results.filter(r => r.logoUrl).length;
    console.log(`Progress: ${progress}/${total} (${percent}%) - Logos: ${logosFound}`);

    // Save progress periodically
    if (results.length % SAVE_INTERVAL === 0 || progress >= total) {
      saveProgress(results, progress);
    }
  }

  return results;
}

function saveProgress(results, processedCount) {
  try {
    fs.writeFileSync(PROGRESS_PATH, JSON.stringify({
      processedCount,
      results,
      timestamp: new Date().toISOString(),
    }, null, 2), 'utf-8');
  } catch (error) {
    console.error('Warning: Could not save progress:', error.message);
  }
}

function loadProgress() {
  try {
    if (fs.existsSync(PROGRESS_PATH)) {
      const data = JSON.parse(fs.readFileSync(PROGRESS_PATH, 'utf-8'));
      console.log(`Found previous progress: ${data.processedCount} companies processed`);
      console.log(`  Last saved: ${data.timestamp}`);
      return data;
    }
  } catch (error) {
    console.log('No valid progress file found, starting from beginning');
  }
  return null;
}

function cleanupProgress() {
  try {
    if (fs.existsSync(PROGRESS_PATH)) {
      fs.unlinkSync(PROGRESS_PATH);
    }
  } catch {
    // Ignore cleanup errors
  }
}

async function main() {
  console.log('Reading CSV...');
  const csvContent = fs.readFileSync(CSV_PATH, 'utf-8');

  console.log('Parsing...');
  const companies = parseCSV(csvContent);
  console.log(`Found ${companies.length} companies\n`);

  // Check for previous progress
  const progress = loadProgress();
  let startFrom = 0;
  let existingResults = [];

  if (progress && progress.processedCount < companies.length) {
    console.log(`Resuming from company ${progress.processedCount + 1}/${companies.length}\n`);
    startFrom = progress.processedCount;
    existingResults = progress.results;
  } else {
    console.log('Starting fresh\n');
  }

  console.log('Fetching logos...\n');
  const results = await processBatch(companies, CONCURRENCY, startFrom, existingResults);

  results.sort((a, b) => b.speakerCount - a.speakerCount);

  console.log(`\nWriting to: ${OUTPUT_PATH}`);
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(results, null, 2), 'utf-8');

  const withLogos = results.filter(r => r.logoUrl).length;

  console.log('\n✓ Complete!');
  console.log(`  Total: ${results.length}`);
  console.log(`  Logos found: ${withLogos} (${((withLogos / results.length) * 100).toFixed(1)}%)`);
  console.log(`  Logos missing: ${results.length - withLogos}`);
  console.log(`\nOutput: ${OUTPUT_PATH}`);

  // Cleanup progress file
  cleanupProgress();
  console.log('\nProgress file cleaned up.');
}

main().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});
