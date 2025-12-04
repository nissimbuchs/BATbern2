/**
 * Company Mapper - Maps legacy company data to Company Management Service schema
 * Follows Story 1.16.2 (Meaningful IDs) and ADR-004 (Reference Patterns)
 */

import * as fs from 'fs-extra';
import * as path from 'path';
import { CompanyData, CompaniesData } from '../types/legacy-types';
import { Company } from '../types/target-types';
import { randomUUID } from 'crypto';

/**
 * Load companies from companies.json
 * @param jsonPath - Path to companies.json file
 * @returns Array of CompanyData (excluding duplicates)
 */
export async function loadCompaniesFromJson(jsonPath: string): Promise<CompanyData[]> {
  const data = await fs.readJson(jsonPath) as CompaniesData;
  return data.companies;
}

/**
 * Map legacy company data to target Company schema
 * @param legacyCompany - Company data from companies.json
 * @returns Mapped Company entity or null if duplicate
 */
export function mapCompany(legacyCompany: CompanyData): Company | null {
  // Skip duplicate companies (they reference another company)
  if (legacyCompany.status === 'duplicate') {
    return null;
  }

  // Normalize company name to max 12 chars (per user_profiles.company_id constraint)
  const companyName = normalizeCompanyName(legacyCompany.id);

  // Generate file ID for logo if present
  const fileId = legacyCompany.logo || legacyCompany.logoUrl ? randomUUID() : undefined;

  // Generate S3 key and CloudFront URL for logo
  let logoS3Key: string | undefined;
  let logoUrl: string | undefined;

  if (legacyCompany.logo && legacyCompany.logoFilePath) {
    // Local logo file
    const extension = path.extname(legacyCompany.logo);
    logoS3Key = generateCompanyLogoS3Key(companyName, legacyCompany.logo, fileId!);
    logoUrl = generateCloudFrontUrl(logoS3Key);
  } else if (legacyCompany.logoUrl) {
    // Online logo URL - needs download during migration
    const extension = path.extname(new URL(legacyCompany.logoUrl).pathname) || '.svg';
    const filename = `${companyName}${extension}`;
    logoS3Key = generateCompanyLogoS3Key(companyName, filename, fileId!);
    logoUrl = generateCloudFrontUrl(logoS3Key);
  }

  const company: Company = {
    name: companyName,
    displayName: legacyCompany.displayName,
    website: legacyCompany.url,
    logoUrl,
    logoS3Key,
    logoFileId: fileId,
    isVerified: false // All migrated companies are unverified initially
  };

  return company;
}

/**
 * Normalize company name to meet VARCHAR(12) constraint for user_profiles.company_id FK
 * @param companyId - Original company identifier
 * @returns Normalized name (max 12 chars, alphanumeric)
 */
function normalizeCompanyName(companyId: string): string {
  // Remove special characters and truncate to 12 chars
  let normalized = companyId
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '') // Remove non-alphanumeric
    .substring(0, 12);           // Truncate to 12 chars

  // Ensure it matches the constraint pattern: ^[a-zA-Z0-9]{1,12}$
  if (!normalized || normalized.length === 0) {
    throw new Error(`Cannot normalize company name: ${companyId}`);
  }

  return normalized;
}

/**
 * Generate S3 key for company logo following GenericLogoService pattern
 * Pattern: logos/{year}/companies/{companyName}/logo-{fileId}.{ext}
 * Reference: CompanyService.java line 369
 *
 * @param companyName - Company identifier
 * @param filename - Original filename
 * @param fileId - Unique file identifier (UUID)
 * @returns S3 key
 */
export function generateCompanyLogoS3Key(
  companyName: string,
  filename: string,
  fileId: string
): string {
  const year = new Date().getFullYear(); // Migration year (2025)
  const extension = path.extname(filename);

  return `logos/${year}/companies/${companyName}/logo-${fileId}${extension}`;
}

/**
 * Generate CloudFront URL from S3 key
 * @param s3Key - S3 object key
 * @returns CloudFront CDN URL
 */
function generateCloudFrontUrl(s3Key: string): string {
  return `https://cdn.batbern.ch/${s3Key}`;
}

/**
 * Map all companies from companies.json, excluding duplicates
 * @param jsonPath - Path to companies.json
 * @returns Array of mapped Company entities
 */
export async function mapAllCompanies(jsonPath: string): Promise<Company[]> {
  const legacyCompanies = await loadCompaniesFromJson(jsonPath);

  const companies = legacyCompanies
    .map(mapCompany)
    .filter((c): c is Company => c !== null); // Filter out nulls (duplicates)

  return companies;
}
