/**
 * Company Import Utilities
 *
 * Functions for parsing, validating, and transforming legacy company JSON
 * into API-compatible format for batch import.
 */

import type { components } from '@/types/generated/company-api.types';
import type { SourceCompany, ImportCandidate } from '@/types/companyImport.types';

type CreateCompanyRequest = components['schemas']['CreateCompanyRequest'];

/**
 * Transforms a source company ID to an API-compatible name.
 * API requires alphanumeric only, so we strip all non-alphanumeric characters.
 *
 * @example
 * transformIdToApiName("isc-ejpd") // => "iscejpd"
 * transformIdToApiName("zühlke") // => "zhlke"
 * transformIdToApiName("google-cloud") // => "googlecloud"
 */
export function transformIdToApiName(id: string): string {
  // Remove all non-alphanumeric characters and convert to lowercase
  return id.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
}

/**
 * Validates a URL string
 */
export function isValidUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  try {
    new URL(url);
    return true;
  } catch {
    return false;
  }
}

/**
 * Gets the best available logo URL for a source company.
 * Priority: logoUrl (external) > null (local files can't be used directly)
 */
export function getLogoUrl(company: SourceCompany): string | null {
  // If there's an external logoUrl, use it
  if (company.logoUrl && isValidUrl(company.logoUrl)) {
    return company.logoUrl;
  }
  // Local file paths can't be directly used in browser
  return null;
}

/**
 * Transforms a source company object to API CreateCompanyRequest format
 */
export function transformCompanyForApi(source: SourceCompany): CreateCompanyRequest {
  const name = transformIdToApiName(source.id);

  // Use displayName, but clean it if it looks like a filename (e.g., "googleCloud.png")
  let displayName = source.displayName;
  if (displayName && /\.(png|jpg|jpeg|svg)$/i.test(displayName)) {
    // It's a filename, try to make it readable
    displayName = displayName
      .replace(/\.(png|jpg|jpeg|svg)$/i, '')
      .replace(/([a-z])([A-Z])/g, '$1 $2') // Add space before capitals
      .trim();
  }

  return {
    name,
    displayName: displayName || undefined,
    website: isValidUrl(source.url) ? source.url! : undefined,
    // swissUID, industry, description not available in source data
  };
}

/**
 * Parses JSON content and validates it as an array of source companies
 *
 * @throws Error if JSON is invalid or doesn't contain company array
 */
export function parseCompanyJson(jsonContent: string): SourceCompany[] {
  let parsed: unknown;

  try {
    parsed = JSON.parse(jsonContent);
  } catch (error) {
    throw new Error(`Invalid JSON: ${error instanceof Error ? error.message : 'Parse error'}`);
  }

  if (!Array.isArray(parsed)) {
    throw new Error('JSON must be an array of companies');
  }

  // Validate each item has required fields
  const companies: SourceCompany[] = [];
  for (let i = 0; i < parsed.length; i++) {
    const item = parsed[i];
    if (typeof item !== 'object' || item === null) {
      throw new Error(`Item at index ${i} is not an object`);
    }

    if (typeof item.id !== 'string' || !item.id) {
      throw new Error(`Item at index ${i} is missing required 'id' field`);
    }

    if (typeof item.displayName !== 'string') {
      throw new Error(`Item at index ${i} is missing required 'displayName' field`);
    }

    // Cast to SourceCompany - we've validated the minimum required fields
    companies.push(item as SourceCompany);
  }

  return companies;
}

/**
 * Creates import candidates from source companies
 */
export function createImportCandidates(sources: SourceCompany[]): ImportCandidate[] {
  return sources.map((source) => ({
    source,
    apiPayload: transformCompanyForApi(source),
    logoUrl: getLogoUrl(source),
    importStatus: 'pending' as const,
  }));
}

/**
 * Checks if a company name already exists in the provided list
 */
export function isDuplicateName(name: string, existingNames: string[]): boolean {
  return existingNames.some((existing) => existing.toLowerCase() === name.toLowerCase());
}
