/**
 * Tests for Company Mapper (AC1)
 * TDD Approach: Write tests first (RED), then implement (GREEN), then refactor
 */

import { mapCompany, loadCompaniesFromJson, generateCompanyLogoS3Key } from '../mappers/company-mapper';
import { CompanyData } from '../types/legacy-types';
import * as path from 'path';

describe('Company Mapper (AC1)', () => {
  const companiesJsonPath = path.join(__dirname, '../../../../docs/migration/companies.json');

  describe('Test 1.1: should_loadCompaniesFromJson_when_companiesMapped', () => {
    it('should load 70 companies from companies.json', async () => {
      const companies = await loadCompaniesFromJson(companiesJsonPath);

      expect(companies).toBeDefined();
      expect(companies.length).toBe(70);
    });

    it('should exclude duplicate companies from the list', async () => {
      const companies = await loadCompaniesFromJson(companiesJsonPath);

      // Filter out duplicates (status: "duplicate")
      const nonDuplicates = companies.filter(c => c.status !== 'duplicate');

      // Should have fewer than 70 after removing duplicates
      expect(nonDuplicates.length).toBeLessThan(70);
    });
  });

  describe('Test 1.2: should_mapCompanyIdAsMeaningfulName_when_companyCreated', () => {
    it('should map company id to Company.name as meaningful ID', () => {
      const legacyCompany: CompanyData = {
        id: 'mobiliar',
        displayName: 'Die Mobiliar',
        url: 'https://www.mobiliar.ch',
        speakerCount: 15,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.name).toBe('mobiliar');
        expect(company.name).toMatch(/^[a-z0-9]{1,12}$/);
      }
    });

    it('should truncate company name if longer than 12 characters', () => {
      const legacyCompany: CompanyData = {
        id: 'verylongcompanyname',
        displayName: 'Very Long Company Name AG',
        speakerCount: 1,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.name.length).toBeLessThanOrEqual(12);
      }
    });
  });

  describe('Test 1.3: should_mapDisplayNameAndUrl_when_companyHasData', () => {
    it('should map displayName and url correctly', () => {
      const legacyCompany: CompanyData = {
        id: 'mobiliar',
        displayName: 'Die Mobiliar',
        url: 'https://www.mobiliar.ch',
        speakerCount: 15,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.displayName).toBe('Die Mobiliar');
        expect(company.website).toBe('https://www.mobiliar.ch');
      }
    });

    it('should handle missing url gracefully', () => {
      const legacyCompany: CompanyData = {
        id: 'testco',
        displayName: 'Test Company',
        speakerCount: 1,
        status: 'needs_url'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.website).toBeUndefined();
      }
    });
  });

  describe('Test 1.4: should_generateS3KeyFromLocalLogo_when_logoFileProvided', () => {
    it('should generate S3 key for local logo file', () => {
      const legacyCompany: CompanyData = {
        id: 'mobiliar',
        displayName: 'Die Mobiliar',
        logo: 'mobiliar.jpg',
        logoFilePath: '/path/to/mobiliar.jpg',
        speakerCount: 15,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.logoS3Key).toBeDefined();
        expect(company.logoS3Key).toMatch(/^logos\/2025\/companies\/mobiliar\/logo-.+\.jpg$/);
      }
    });

    it('should extract file extension correctly', () => {
      const legacyCompany: CompanyData = {
        id: 'sbb',
        displayName: 'SBB CFF FFS',
        logo: 'sbb.png',
        logoFilePath: '/path/to/sbb.png',
        speakerCount: 20,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.logoS3Key).toContain('.png');
      }
    });

    it('should generate unique file IDs for different companies', () => {
      const company1: CompanyData = {
        id: 'company1',
        displayName: 'Company 1',
        logo: 'logo.jpg',
        logoFilePath: '/path/to/logo1.jpg',
        speakerCount: 1,
        status: 'complete'
      };

      const company2: CompanyData = {
        id: 'company2',
        displayName: 'Company 2',
        logo: 'logo.jpg',
        logoFilePath: '/path/to/logo2.jpg',
        speakerCount: 1,
        status: 'complete'
      };

      const mapped1 = mapCompany(company1);
      const mapped2 = mapCompany(company2);

      expect(mapped1).not.toBeNull();
      expect(mapped2).not.toBeNull();
      if (mapped1 && mapped2) {
        expect(mapped1.logoFileId).toBeDefined();
        expect(mapped2.logoFileId).toBeDefined();
        expect(mapped1.logoFileId).not.toBe(mapped2.logoFileId);
      }
    });
  });

  describe('Test 1.5: should_handleOnlineLogoUrl_when_logoUrlProvided', () => {
    it('should note online logo URL for download strategy', () => {
      const legacyCompany: CompanyData = {
        id: 'puzzle',
        displayName: 'Puzzle ITC',
        logoUrl: 'https://www.puzzle.ch/assets/img/puzzle-logo.svg',
        url: 'https://www.puzzle.ch',
        speakerCount: 8,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.logoS3Key).toBeDefined();
        expect(company.logoS3Key).toContain('puzzle');
      }
    });
  });

  describe('Test 1.6: should_skipDuplicateCompanies_when_statusIsDuplicate', () => {
    it('should return null for duplicate companies', () => {
      const legacyCompany: CompanyData = {
        id: 'aws-logo',
        displayName: 'AWS (duplicate)',
        speakerCount: 1,
        status: 'duplicate',
        note: 'Duplicate of aws'
      };

      const company = mapCompany(legacyCompany);

      expect(company).toBeNull();
    });
  });

  describe('S3 Key Generation', () => {
    it('should follow GenericLogoService pattern for S3 keys', () => {
      const s3Key = generateCompanyLogoS3Key('mobiliar', 'mobiliar.jpg', 'test-file-id-123');

      expect(s3Key).toBe('logos/2025/companies/mobiliar/logo-test-file-id-123.jpg');
    });

    it('should handle different file extensions', () => {
      const s3KeyPng = generateCompanyLogoS3Key('sbb', 'sbb.png', 'file-id-1');
      const s3KeySvg = generateCompanyLogoS3Key('puzzle', 'puzzle.svg', 'file-id-2');

      expect(s3KeyPng).toContain('.png');
      expect(s3KeySvg).toContain('.svg');
    });
  });

  describe('CloudFront URL Generation', () => {
    it('should generate correct CloudFront URL from S3 key', () => {
      const legacyCompany: CompanyData = {
        id: 'mobiliar',
        displayName: 'Die Mobiliar',
        logo: 'mobiliar.jpg',
        logoFilePath: '/path/to/mobiliar.jpg',
        speakerCount: 15,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.logoUrl).toBeDefined();
        expect(company.logoUrl).toMatch(/^https:\/\/cdn\.batbern\.ch\//);
        expect(company.logoUrl).toContain('logos/2025/companies/mobiliar/');
      }
    });
  });

  describe('Schema Compliance', () => {
    it('should respect VARCHAR(255) constraint for Company.name', () => {
      const legacyCompany: CompanyData = {
        id: 'a'.repeat(300), // Very long name
        displayName: 'Test Company',
        speakerCount: 1,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.name.length).toBeLessThanOrEqual(255);
      }
    });

    it('should respect VARCHAR(12) constraint for user_profiles.company_id FK', () => {
      const legacyCompany: CompanyData = {
        id: 'testcompany',
        displayName: 'Test Company AG',
        speakerCount: 1,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.name.length).toBeLessThanOrEqual(12);
        expect(company.name).toMatch(/^[a-zA-Z0-9]{1,12}$/);
      }
    });

    it('should set isVerified to false for migrated companies', () => {
      const legacyCompany: CompanyData = {
        id: 'sbb',
        displayName: 'SBB CFF FFS',
        url: 'https://www.sbb.ch',
        speakerCount: 20,
        status: 'complete'
      };

      const company = mapCompany(legacyCompany);

      expect(company).not.toBeNull();
      if (company) {
        expect(company.isVerified).toBe(false);
      }
    });
  });
});
