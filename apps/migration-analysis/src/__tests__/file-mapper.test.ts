/**
 * Tests for File Mapper (AC4)
 * TDD Approach: Write tests first (RED), then implement (GREEN), then refactor
 *
 * Maps all file types to S3 bucket structure:
 * - Company logos
 * - Profile pictures
 * - Presentation PDFs
 * - Event photos
 */

import {
  generateCompanyLogoMapping,
  generateProfilePictureMapping,
  generatePresentationMapping,
  generateEventPhotoMapping,
  generateCloudFrontUrl,
  generateMinIOUrl
} from '../mappers/file-mapper';

describe('File Mapper (AC4)', () => {
  describe('Test 4.1: should_generateCompanyLogoS3Key_when_logoProvided', () => {
    it('should generate S3 key for company logo', () => {
      const mapping = generateCompanyLogoMapping({
        companyName: 'mobiliar',
        filename: 'mobiliar.jpg',
        year: 2025
      });

      expect(mapping.s3Key).toMatch(/^logos\/2025\/companies\/mobiliar\/logo-.+\.jpg$/);
      expect(mapping.fileId).toBeDefined();
      expect(mapping.originalFilename).toBe('mobiliar.jpg');
    });

    it('should handle different file extensions', () => {
      const mappingPng = generateCompanyLogoMapping({
        companyName: 'sbb',
        filename: 'sbb.png',
        year: 2025
      });

      const mappingSvg = generateCompanyLogoMapping({
        companyName: 'puzzle',
        filename: 'puzzle.svg',
        year: 2025
      });

      expect(mappingPng.s3Key).toContain('.png');
      expect(mappingSvg.s3Key).toContain('.svg');
    });

    it('should generate unique file IDs', () => {
      const mapping1 = generateCompanyLogoMapping({
        companyName: 'company1',
        filename: 'logo.jpg',
        year: 2025
      });

      const mapping2 = generateCompanyLogoMapping({
        companyName: 'company2',
        filename: 'logo.jpg',
        year: 2025
      });

      expect(mapping1.fileId).not.toBe(mapping2.fileId);
    });
  });

  describe('Test 4.2: should_generateProfilePictureS3Key_when_speakerPhotoMapped', () => {
    it('should generate S3 key for profile picture', () => {
      const mapping = generateProfilePictureMapping({
        username: 'thomas.goetz',
        filename: 'thomas.goetz.jpg',
        year: 2025
      });

      expect(mapping.s3Key).toMatch(/^profile-pictures\/2025\/thomas\.goetz\/profile-.+\.jpg$/);
      expect(mapping.fileId).toBeDefined();
      expect(mapping.originalFilename).toBe('thomas.goetz.jpg');
    });

    it('should handle different file extensions for portraits', () => {
      const mappingPng = generateProfilePictureMapping({
        username: 'anna.mueller',
        filename: 'anna.png',
        year: 2025
      });

      expect(mappingPng.s3Key).toContain('.png');
    });

    it('should handle usernames with special characters', () => {
      const mapping = generateProfilePictureMapping({
        username: 'jeanpierre.muller',
        filename: 'portrait.jpg',
        year: 2025
      });

      expect(mapping.s3Key).toContain('jeanpierre.muller');
    });
  });

  describe('Test 4.3: should_generatePresentationS3Key_when_pdfMapped', () => {
    it('should generate S3 key for presentation PDF', () => {
      const mapping = generatePresentationMapping({
        eventNumber: 56,
        filename: 'Cloud_Security.pdf'
      });

      expect(mapping.s3Key).toBe('presentations/56/Cloud_Security.pdf');
      expect(mapping.fileId).toBeDefined();
      expect(mapping.originalFilename).toBe('Cloud_Security.pdf');
    });

    it('should handle different event numbers', () => {
      const mapping1 = generatePresentationMapping({
        eventNumber: 1,
        filename: 'First_Event.pdf'
      });

      const mapping60 = generatePresentationMapping({
        eventNumber: 60,
        filename: 'Latest_Event.pdf'
      });

      expect(mapping1.s3Key).toBe('presentations/1/First_Event.pdf');
      expect(mapping60.s3Key).toBe('presentations/60/Latest_Event.pdf');
    });

    it('should preserve original filename including spaces', () => {
      const mapping = generatePresentationMapping({
        eventNumber: 56,
        filename: 'Cloud Security Best Practices.pdf'
      });

      expect(mapping.s3Key).toBe('presentations/56/Cloud Security Best Practices.pdf');
    });
  });

  describe('Test 4.4: should_generateEventPhotoS3Key_when_pictureMapped', () => {
    it('should generate S3 key for event photo', () => {
      const mapping = generateEventPhotoMapping({
        eventNumber: 56,
        filename: '01.jpg'
      });

      expect(mapping.s3Key).toBe('photos/events/56/01.jpg');
      expect(mapping.fileId).toBeDefined();
      expect(mapping.originalFilename).toBe('01.jpg');
    });

    it('should handle different photo filenames', () => {
      const mapping = generateEventPhotoMapping({
        eventNumber: 56,
        filename: 'event-photo-networking.jpg'
      });

      expect(mapping.s3Key).toBe('photos/events/56/event-photo-networking.jpg');
    });

    it('should handle different image formats', () => {
      const mappingJpg = generateEventPhotoMapping({
        eventNumber: 56,
        filename: 'photo.jpg'
      });

      const mappingPng = generateEventPhotoMapping({
        eventNumber: 56,
        filename: 'photo.png'
      });

      expect(mappingJpg.s3Key).toContain('.jpg');
      expect(mappingPng.s3Key).toContain('.png');
    });
  });

  describe('Test 4.5: should_generateCloudFrontUrl_when_s3KeyDefined', () => {
    it('should generate CloudFront URL for production/staging', () => {
      const s3Key = 'logos/2025/companies/mobiliar/logo-abc123.jpg';

      const url = generateCloudFrontUrl(s3Key);

      expect(url).toBe('https://cdn.batbern.ch/logos/2025/companies/mobiliar/logo-abc123.jpg');
    });

    it('should handle profile pictures', () => {
      const s3Key = 'profile-pictures/2025/thomas.goetz/profile-xyz789.jpg';

      const url = generateCloudFrontUrl(s3Key);

      expect(url).toBe('https://cdn.batbern.ch/profile-pictures/2025/thomas.goetz/profile-xyz789.jpg');
    });

    it('should handle presentations', () => {
      const s3Key = 'presentations/56/Cloud_Security.pdf';

      const url = generateCloudFrontUrl(s3Key);

      expect(url).toBe('https://cdn.batbern.ch/presentations/56/Cloud_Security.pdf');
    });

    it('should handle event photos', () => {
      const s3Key = 'photos/events/56/01.jpg';

      const url = generateCloudFrontUrl(s3Key);

      expect(url).toBe('https://cdn.batbern.ch/photos/events/56/01.jpg');
    });
  });

  describe('MinIO URL Generation (Local Development)', () => {
    it('should generate MinIO URL for local development', () => {
      const s3Key = 'logos/2025/companies/mobiliar/logo-abc123.jpg';
      const bucketName = 'batbern-development-company-logos';

      const url = generateMinIOUrl(s3Key, bucketName);

      expect(url).toBe('http://localhost:8450/batbern-development-company-logos/logos/2025/companies/mobiliar/logo-abc123.jpg');
    });

    it('should handle profile pictures in MinIO', () => {
      const s3Key = 'profile-pictures/2025/thomas.goetz/profile-xyz789.jpg';
      const bucketName = 'batbern-development-company-logos';

      const url = generateMinIOUrl(s3Key, bucketName);

      expect(url).toContain('localhost:8450');
      expect(url).toContain(bucketName);
    });
  });

  describe('S3 Bucket Structure Validation', () => {
    it('should follow GenericLogoService pattern for company logos', () => {
      const mapping = generateCompanyLogoMapping({
        companyName: 'mobiliar',
        filename: 'mobiliar.jpg',
        year: 2025
      });

      // Pattern: logos/{year}/companies/{companyName}/logo-{fileId}.{ext}
      expect(mapping.s3Key).toMatch(/^logos\/\d{4}\/companies\/[a-z0-9]+\/logo-[a-f0-9-]+\.[a-z]+$/);
    });

    it('should follow ProfilePictureService pattern for profile pictures', () => {
      const mapping = generateProfilePictureMapping({
        username: 'thomas.goetz',
        filename: 'thomas.jpg',
        year: 2025
      });

      // Pattern: profile-pictures/{year}/{username}/profile-{fileId}.{ext}
      expect(mapping.s3Key).toMatch(/^profile-pictures\/\d{4}\/[a-z0-9.]+\/profile-[a-f0-9-]+\.[a-z]+$/);
    });

    it('should follow custom pattern for presentations', () => {
      const mapping = generatePresentationMapping({
        eventNumber: 56,
        filename: 'presentation.pdf'
      });

      // Pattern: presentations/{eventNumber}/{filename}
      expect(mapping.s3Key).toMatch(/^presentations\/\d+\/.+\.pdf$/);
    });

    it('should follow custom pattern for event photos', () => {
      const mapping = generateEventPhotoMapping({
        eventNumber: 56,
        filename: '01.jpg'
      });

      // Pattern: photos/events/{eventNumber}/{filename}
      expect(mapping.s3Key).toMatch(/^photos\/events\/\d+\/.+\.[a-z]+$/);
    });
  });

  describe('File ID Generation', () => {
    it('should generate UUID v4 format for file IDs', () => {
      const mapping = generateCompanyLogoMapping({
        companyName: 'test',
        filename: 'test.jpg',
        year: 2025
      });

      // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
      expect(mapping.fileId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    });

    it('should generate unique file IDs for each mapping', () => {
      const fileIds = new Set<string>();

      for (let i = 0; i < 100; i++) {
        const mapping = generateCompanyLogoMapping({
          companyName: `company${i}`,
          filename: `logo${i}.jpg`,
          year: 2025
        });
        fileIds.add(mapping.fileId);
      }

      expect(fileIds.size).toBe(100); // All unique
    });
  });
});
