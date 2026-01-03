#!/usr/bin/env node

/**
 * Convert companies CSV to JSON format with logo fetching
 *
 * This script:
 * 1. Reads companies_participants.csv
 * 2. Converts to the JSON format expected by CompanyBatchImportModal
 * 3. Attempts to fetch company logos from their domains
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Input and output paths
const CSV_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.csv');
const OUTPUT_PATH = path.join(__dirname, '../apps/BATspa-old/src/api/companies_participants.json');

/**
 * Parse CSV line to object
 */
function parseCsvLine(line, index) {
  // Skip header and empty lines
  if (index === 0 || !line.trim()) return null;

  const parts = line.split(',');
  if (parts.length < 5) return null;

  const domain = parts[0]?.trim();
  const displayName = parts[1]?.trim();
  const companyName = parts[2]?.trim();
  const col1 = parseInt(parts[3]?.trim() || '0', 10);
  const col2 = parseInt(parts[4]?.trim() || '0', 10);

  // Skip if no company name
  if (!companyName) return null;

  return {
    domain,
    displayName,
    companyName,
    speakerCount: col1 + col2,
  };
}

/**
 * Attempt to construct logo URL from domain
 */
async function findLogoUrl(domain) {
  if (!domain) return null;

  // Common logo paths to try
  const logoPatterns = [
    '/logo.svg',
    '/logo.png',
    '/images/logo.svg',
    '/images/logo.png',
    '/assets/logo.svg',
    '/assets/logo.png',
    '/static/logo.svg',
    '/static/logo.png',
    '/_assets/logo.svg',
    '/_assets/logo.png',
  ];

  const protocol = 'https://';
  const baseUrl = domain.startsWith('http') ? domain : `${protocol}${domain}`;

  // Try fetching each pattern
  for (const pattern of logoPatterns) {
    const url = `${baseUrl}${pattern}`;
    try {
      const response = await fetch(url, {
        method: 'HEAD',
        signal: AbortSignal.timeout(3000) // 3 second timeout
      });
      if (response.ok) {
        console.log(`✓ Found logo: ${url}`);
        return url;
      }
    } catch (error) {
      // Continue to next pattern
    }
  }

  // Try fetching the homepage to look for og:image or favicon
  try {
    const response = await fetch(baseUrl, {
      signal: AbortSignal.timeout(5000),
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; LogoFetcher/1.0)'
      }
    });

    if (response.ok) {
      const html = await response.text();

      // Look for og:image
      const ogImageMatch = html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']/i);
      if (ogImageMatch) {
        const logoUrl = ogImageMatch[1].startsWith('http')
          ? ogImageMatch[1]
          : `${baseUrl}${ogImageMatch[1]}`;
        console.log(`✓ Found og:image: ${logoUrl}`);
        return logoUrl;
      }

      // Look for common logo class/id patterns
      const logoImgMatch = html.match(/<img[^>]*(class|id)=["'][^"']*logo[^"']*["'][^>]*src=["']([^"']+)["']/i);
      if (logoImgMatch) {
        const logoUrl = logoImgMatch[2].startsWith('http')
          ? logoImgMatch[2]
          : logoImgMatch[2].startsWith('//')
          ? `https:${logoImgMatch[2]}`
          : `${baseUrl}${logoImgMatch[2]}`;
        console.log(`✓ Found logo image: ${logoUrl}`);
        return logoUrl;
      }

      // Look for SVG logo in the HTML
      const svgLogoMatch = html.match(/<svg[^>]*(class|id)=["'][^"']*logo[^"']*["'][^>]*>/i);
      if (svgLogoMatch) {
        console.log(`  SVG logo found in HTML for ${domain} (inline, cannot extract URL)`);
      }
    }
  } catch (error) {
    // Couldn't fetch homepage
  }

  console.log(`✗ No logo found for: ${domain || 'unknown domain'}`);
  return null;
}

/**
 * Determine industry from company name/domain (basic heuristics)
 */
function guessIndustry(displayName, domain) {
  const name = (displayName || '').toLowerCase();
  const domainLower = (domain || '').toLowerCase();
  const combined = `${name} ${domainLower}`;

  if (combined.match(/bank|finanz|credit|raiffeisen|kantonalbank/)) return 'Financial Services';
  if (combined.match(/versicherung|insurance|mobiliar|helvetia|axa/)) return 'Insurance';
  if (combined.match(/sbb|bls|bahn|transport|mobility/)) return 'Transportation';
  if (combined.match(/post\b|postfinance/)) return 'Logistics';
  if (combined.match(/swisscom|telecom|mobile|internet/)) return 'Telecommunications';
  if (combined.match(/energie|energy|strom|bkw|ewz|alpiq/)) return 'Energy';
  if (combined.match(/pharma|medizin|hospital|klinik|insel|spital/)) return 'Healthcare';
  if (combined.match(/universit|hochschule|eth|epfl|bfh|zhaw|fachhochschule/)) return 'Higher Education';
  if (combined.match(/\b(admin\.ch|bundesamt|kanton\b|bund\b|eidg\.|staat)/)) return 'Government';
  if (combined.match(/aws|microsoft|ibm|oracle|google|cloud/)) return 'Cloud Provider';
  if (combined.match(/consulting|beratung|advisory|solutions|services|software|informatik|digital|it\b/)) return 'IT Consulting';

  return 'IT Consulting'; // Default
}

/**
 * Main conversion function
 */
async function convertCsvToJson() {
  console.log('Reading CSV file...');
  const csvContent = fs.readFileSync(CSV_PATH, 'utf-8');
  const lines = csvContent.split('\n');

  console.log(`Found ${lines.length - 1} lines in CSV\n`);

  const companies = [];
  const parsedLines = lines
    .map((line, index) => parseCsvLine(line, index))
    .filter(Boolean);

  console.log(`Parsed ${parsedLines.length} valid companies\n`);
  console.log('Fetching logos (this may take a while)...\n');

  for (let i = 0; i < parsedLines.length; i++) {
    const line = parsedLines[i];
    const { domain, displayName, companyName, speakerCount } = line;

    console.log(`[${i + 1}/${parsedLines.length}] Processing: ${displayName || companyName}`);

    // Attempt to find logo
    const logoUrl = await findLogoUrl(domain);

    // Construct URL from domain
    const website = domain
      ? (domain.startsWith('http') ? domain : `https://${domain}`)
      : null;

    // Guess industry
    const industry = guessIndustry(displayName, domain);

    companies.push({
      id: companyName,
      displayName: displayName || companyName,
      url: website,
      industry,
      speakerCount,
      logo: null,
      logoFilePath: null,
      has_logo: !!logoUrl,
      status: logoUrl ? 'complete' : 'pending_logo',
      logoUrl: logoUrl || undefined,
    });

    // Small delay to avoid overwhelming servers
    if (i < parsedLines.length - 1) {
      await new Promise(resolve => setTimeout(resolve, 200));
    }
  }

  console.log(`\n✓ Processed ${companies.length} companies`);
  console.log(`  - ${companies.filter(c => c.logoUrl).length} logos found`);
  console.log(`  - ${companies.filter(c => !c.logoUrl).length} logos missing`);

  // Sort by speakerCount descending
  companies.sort((a, b) => b.speakerCount - a.speakerCount);

  // Write output
  console.log(`\nWriting to: ${OUTPUT_PATH}`);
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(companies, null, 2), 'utf-8');

  console.log('\n✓ Done!');
  console.log(`\nOutput saved to: ${OUTPUT_PATH}`);
  console.log(`Total companies: ${companies.length}`);
}

// Run conversion
convertCsvToJson().catch(error => {
  console.error('Error:', error);
  process.exit(1);
});
