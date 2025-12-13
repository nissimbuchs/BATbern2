/**
 * Tests for Speaker Mapper (AC3)
 * TDD Approach: Write tests first (RED), then implement (GREEN), then refactor
 *
 * CRITICAL: Speaker mapping requires Company entities to exist first (Task 1)
 * User.companyId must reference an existing Company.name
 */

import {
  parseSpeakerName,
  generateUsername,
  mapSpeakerToUser,
  mapSpeakerToSpeaker,
  extractUniqueSpeakers,
  validateCompanyExists
} from '../mappers/speaker-mapper';
import { LegacySpeaker, LegacySession } from '../types/legacy-types';
import { User, Speaker, SpeakerAvailability, SpeakerWorkflowState } from '../types/target-types';
import { Company } from '../types/target-types';

describe('Speaker Mapper (AC3)', () => {
  // Mock company data from Task 1 (companies must exist before users)
  const mockCompanies: Company[] = [
    { name: 'mobiliar', displayName: 'Die Mobiliar', isVerified: false },
    { name: 'sbb', displayName: 'SBB CFF FFS', isVerified: false },
    { name: 'swisscom', displayName: 'Swisscom AG', isVerified: false }
  ];

  describe('Test 3.1: should_extractUniqueSpeakers_when_sessionsProcessed', () => {
    it('should extract unique speakers from sessions array', () => {
      const sessions: LegacySession[] = [
        {
          bat: 56,
          pdf: 'session1.pdf',
          title: 'Session 1',
          referenten: [
            { name: 'Thomas Goetz, Die Mobiliar', bio: 'Bio 1', company: 'mobiliar', portrait: 'thomas.jpg' }
          ]
        },
        {
          bat: 56,
          pdf: 'session2.pdf',
          title: 'Session 2',
          referenten: [
            { name: 'Thomas Goetz, Die Mobiliar', bio: 'Bio 1', company: 'mobiliar', portrait: 'thomas.jpg' },
            { name: 'Anna Mueller, SBB', bio: 'Bio 2', company: 'sbb', portrait: 'anna.jpg' }
          ]
        }
      ];

      const uniqueSpeakers = extractUniqueSpeakers(sessions);

      expect(uniqueSpeakers.length).toBe(2); // Thomas appears twice, should be deduplicated
      expect(uniqueSpeakers.map(s => s.name)).toContain('Thomas Goetz, Die Mobiliar');
      expect(uniqueSpeakers.map(s => s.name)).toContain('Anna Mueller, SBB');
    });

    it('should handle sessions without speakers', () => {
      const sessions: LegacySession[] = [
        {
          bat: 1,
          pdf: 'program.pdf',
          title: 'Program Brochure',
          authoren: 'Various authors'
          // No referenten array
        }
      ];

      const uniqueSpeakers = extractUniqueSpeakers(sessions);

      expect(uniqueSpeakers.length).toBe(0);
    });
  });

  describe('Test 3.2: should_parseSpeakerNameFormat_when_referentMapped', () => {
    it('should parse "FirstName LastName, Company" format', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Experienced architect',
        company: 'mobiliar',
        portrait: 'thomas.goetz.jpg'
      };

      const parsed = parseSpeakerName(legacySpeaker.name);

      expect(parsed.firstName).toBe('Thomas');
      expect(parsed.lastName).toBe('Goetz');
      expect(parsed.companyDisplayName).toBe('Die Mobiliar');
    });

    it('should handle multi-word first names', () => {
      const parsed = parseSpeakerName('Jean-Pierre Dubois, Swisscom');

      expect(parsed.firstName).toBe('Jean-Pierre');
      expect(parsed.lastName).toBe('Dubois');
    });

    it('should handle multi-word last names', () => {
      const parsed = parseSpeakerName('Anna von Mueller, SBB');

      expect(parsed.firstName).toBe('Anna');
      expect(parsed.lastName).toBe('von Mueller');
    });

    it('should handle names without company', () => {
      const parsed = parseSpeakerName('Thomas Goetz');

      expect(parsed.firstName).toBe('Thomas');
      expect(parsed.lastName).toBe('Goetz');
      expect(parsed.companyDisplayName).toBeUndefined();
    });
  });

  describe('Test 3.3: should_mapBioToUser_when_speakerMapped', () => {
    it('should map bio to User entity, not Speaker entity (ADR-004)', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Experienced software architect with 15 years in cloud computing.',
        company: 'mobiliar',
        portrait: 'thomas.goetz.jpg'
      };

      const user = mapSpeakerToUser(legacySpeaker, mockCompanies);

      expect(user.bio).toBe('Experienced software architect with 15 years in cloud computing.');
    });

    it('should not include bio in Speaker entity (ADR-004)', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Experienced architect',
        company: 'mobiliar',
        portrait: 'thomas.goetz.jpg'
      };

      const user = mapSpeakerToUser(legacySpeaker, mockCompanies);
      const speaker = mapSpeakerToSpeaker(user.id);

      // Speaker entity should NOT have bio field
      expect(speaker).not.toHaveProperty('bio');
      expect(speaker.userId).toBe(user.id);
    });
  });

  describe('Test 3.4: should_createSpeakerWithUserId_when_userCreated', () => {
    it('should create Speaker entity with userId FK (ADR-004)', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Experienced architect',
        company: 'mobiliar',
        portrait: 'thomas.goetz.jpg'
      };

      const user = mapSpeakerToUser(legacySpeaker, mockCompanies);
      const speaker = mapSpeakerToSpeaker(user.id);

      expect(speaker.userId).toBe(user.id);
      expect(speaker.userId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    });

    it('should set default availability to AVAILABLE', () => {
      const user = mapSpeakerToUser({
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.jpg'
      }, mockCompanies);

      const speaker = mapSpeakerToSpeaker(user.id);

      expect(speaker.availability).toBe(SpeakerAvailability.AVAILABLE);
    });

    it('should set default workflowState to OPEN', () => {
      const user = mapSpeakerToUser({
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.jpg'
      }, mockCompanies);

      const speaker = mapSpeakerToSpeaker(user.id);

      expect(speaker.workflowState).toBe(SpeakerWorkflowState.OPEN);
    });
  });

  describe('Test 3.5: should_validateCompanyExists_when_userCreated', () => {
    it('should validate company exists before creating user', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.jpg'
      };

      const isValid = validateCompanyExists('mobiliar', mockCompanies);

      expect(isValid).toBe(true);
    });

    it('should return false if company does not exist', () => {
      const isValid = validateCompanyExists('nonexistent', mockCompanies);

      expect(isValid).toBe(false);
    });

    it('should map to normalized company name', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.jpg'
      };

      const user = mapSpeakerToUser(legacySpeaker, mockCompanies);

      expect(user.companyId).toBe('mobiliar'); // Normalized company name
      expect(user.companyId.length).toBeLessThanOrEqual(12); // VARCHAR(12) constraint
    });
  });

  describe('Username Generation', () => {
    it('should generate username from first and last name', () => {
      const username = generateUsername('Thomas', 'Goetz');

      expect(username).toBe('thomas.goetz');
    });

    it('should handle special characters in names', () => {
      const username = generateUsername('Jean-Pierre', 'Müller');

      expect(username).toMatch(/^[a-z0-9.]+$/); // Only lowercase alphanumeric and dots
    });

    it('should handle multi-word last names', () => {
      const username = generateUsername('Anna', 'von Mueller');

      expect(username).toBe('anna.vonmueller');
    });

    it('should respect VARCHAR(100) constraint', () => {
      const username = generateUsername('A'.repeat(50), 'B'.repeat(60));

      expect(username.length).toBeLessThanOrEqual(100);
    });
  });

  describe('Portrait Mapping', () => {
    it('should generate S3 key for portrait', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.goetz.jpg'
      };

      const user = mapSpeakerToUser(legacySpeaker, mockCompanies);

      expect(user.profilePictureS3Key).toBeDefined();
      expect(user.profilePictureS3Key).toMatch(/^profile-pictures\/2025\/thomas\.goetz\/profile-.+\.jpg$/);
    });

    it('should generate CloudFront URL from S3 key', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.goetz.jpg'
      };

      const user = mapSpeakerToUser(legacySpeaker, mockCompanies);

      expect(user.profilePictureUrl).toBeDefined();
      expect(user.profilePictureUrl).toMatch(/^https:\/\/cdn\.batbern\.ch\//);
      expect(user.profilePictureUrl).toContain('profile-pictures/2025/thomas.goetz/');
    });
  });

  describe('Schema Compliance', () => {
    it('should respect VARCHAR(100) constraint for username', () => {
      const username = generateUsername('VeryLongFirstName', 'VeryLongLastNameThatExceedsLimit');

      expect(username.length).toBeLessThanOrEqual(100);
    });

    it('should respect VARCHAR(12) constraint for companyId', () => {
      const legacySpeaker: LegacySpeaker = {
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.jpg'
      };

      const user = mapSpeakerToUser(legacySpeaker, mockCompanies);

      expect(user.companyId.length).toBeLessThanOrEqual(12);
      expect(user.companyId).toMatch(/^[a-zA-Z0-9]{1,12}$/);
    });

    it('should generate UUID for user id', () => {
      const user = mapSpeakerToUser({
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.jpg'
      }, mockCompanies);

      expect(user.id).toBeDefined();
      expect(user.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    });

    it('should generate UUID for speaker id', () => {
      const user = mapSpeakerToUser({
        name: 'Thomas Goetz, Die Mobiliar',
        bio: 'Bio',
        company: 'mobiliar',
        portrait: 'thomas.jpg'
      }, mockCompanies);

      const speaker = mapSpeakerToSpeaker(user.id);

      expect(speaker.id).toBeDefined();
      expect(speaker.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    });
  });
});
