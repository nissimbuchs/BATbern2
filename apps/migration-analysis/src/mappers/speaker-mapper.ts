/**
 * Speaker Mapper - Maps legacy speaker data to User and Speaker entities
 * Follows ADR-004 (Reference Patterns): bio, profilePicture stored in User, NOT in Speaker
 *
 * CRITICAL: Requires Company entities from Task 1 to exist before creating Users
 * User.companyId must reference an existing Company.name
 */

import { randomUUID } from 'crypto';
import * as path from 'path';
import { LegacySpeaker, LegacySession } from '../types/legacy-types';
import { User, Speaker, Company, SpeakerAvailability, SpeakerWorkflowState } from '../types/target-types';

/**
 * Parsed speaker name result
 */
export interface ParsedSpeakerName {
  firstName: string;
  lastName: string;
  companyDisplayName?: string;
}

/**
 * Parse speaker name in format "FirstName LastName, Company"
 * Handles multi-word first names and last names
 *
 * @param name - Speaker name from legacy system
 * @returns Parsed name components
 */
export function parseSpeakerName(name: string): ParsedSpeakerName {
  // Split by comma to separate name from company
  const parts = name.split(',').map(p => p.trim());

  const fullName = parts[0];
  const companyDisplayName = parts[1];

  // Split full name by space
  const nameParts = fullName.split(' ').filter(p => p.length > 0);

  if (nameParts.length === 0) {
    throw new Error(`Invalid speaker name: ${name}`);
  }

  if (nameParts.length === 1) {
    // Only one name provided, use as last name
    return {
      firstName: '',
      lastName: nameParts[0],
      companyDisplayName
    };
  }

  // First part is first name, rest is last name
  const firstName = nameParts[0];
  const lastName = nameParts.slice(1).join(' ');

  return {
    firstName,
    lastName,
    companyDisplayName
  };
}

/**
 * Generate username from first and last name
 * Pattern: firstname.lastname (lowercase, alphanumeric + dot)
 * Constraint: VARCHAR(100)
 *
 * @param firstName - First name
 * @param lastName - Last name
 * @returns Generated username
 */
export function generateUsername(firstName: string, lastName: string): string {
  // Normalize to lowercase and remove special characters
  const normalizeText = (text: string): string => {
    return text
      .toLowerCase()
      .normalize('NFD') // Decompose accented characters
      .replace(/[\u0300-\u036f]/g, '') // Remove diacritics
      .replace(/[^a-z0-9]/g, ''); // Keep only alphanumeric
  };

  const first = normalizeText(firstName);
  const last = normalizeText(lastName);

  let username = first && last ? `${first}.${last}` : (first || last);

  // Truncate to 100 chars if needed
  if (username.length > 100) {
    username = username.substring(0, 100);
  }

  return username;
}

/**
 * Generate S3 key for profile picture following ProfilePictureService pattern
 * Pattern: profile-pictures/{year}/{username}/profile-{fileId}.{ext}
 * Reference: ProfilePictureService.java line 161
 *
 * @param username - User's username
 * @param filename - Original filename
 * @param fileId - Unique file identifier (UUID)
 * @returns S3 key
 */
function generateProfilePictureS3Key(username: string, filename: string, fileId: string): string {
  const year = new Date().getFullYear(); // Migration year (2025)
  const extension = path.extname(filename);

  return `profile-pictures/${year}/${username}/profile-${fileId}${extension}`;
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
 * Validate that company exists in the list of migrated companies
 * @param companyId - Company identifier
 * @param companies - List of migrated companies
 * @returns True if company exists
 */
export function validateCompanyExists(companyId: string, companies: Company[]): boolean {
  return companies.some(c => c.name === companyId);
}

/**
 * Map legacy speaker to User entity
 * User entity contains bio and profile picture per ADR-004
 *
 * @param legacySpeaker - Speaker data from sessions.json
 * @param companies - List of migrated companies (from Task 1)
 * @returns User entity
 */
export function mapSpeakerToUser(legacySpeaker: LegacySpeaker, companies: Company[]): User {
  // Parse name
  const parsed = parseSpeakerName(legacySpeaker.name);

  // Generate username
  const username = generateUsername(parsed.firstName, parsed.lastName);

  // Validate company exists
  const companyId = legacySpeaker.company;
  if (!validateCompanyExists(companyId, companies)) {
    console.warn(`Company "${companyId}" not found for speaker "${legacySpeaker.name}". Using as-is.`);
  }

  // Generate profile picture S3 key and URL
  const fileId = randomUUID();
  let profilePictureS3Key: string | undefined;
  let profilePictureUrl: string | undefined;
  let profilePictureFileId: string | undefined;

  if (legacySpeaker.portrait) {
    profilePictureS3Key = generateProfilePictureS3Key(username, legacySpeaker.portrait, fileId);
    profilePictureUrl = generateCloudFrontUrl(profilePictureS3Key);
    profilePictureFileId = fileId;
  }

  const user: User = {
    id: randomUUID(), // Internal database UUID
    username, // Public API identifier (per Story 1.16.2)
    firstName: parsed.firstName || undefined,
    lastName: parsed.lastName,
    bio: legacySpeaker.bio, // Stored in User per ADR-004
    companyId: companyId, // FK to Company.name (meaningful ID, not UUID)
    profilePictureUrl,
    profilePictureS3Key,
    profilePictureFileId
    // email intentionally omitted - not available in legacy data
  };

  return user;
}

/**
 * Map User entity to Speaker entity
 * Speaker entity only contains domain-specific fields per ADR-004
 * bio, profilePictureUrl, companyId are in User, NOT duplicated here
 *
 * @param userId - User ID (FK to user_profiles.id)
 * @returns Speaker entity
 */
export function mapSpeakerToSpeaker(userId: string): Speaker {
  const speaker: Speaker = {
    id: randomUUID(), // Speaker UUID
    userId, // FK to User.id (cross-service, ADR-004)
    availability: SpeakerAvailability.AVAILABLE, // Default for historical speakers
    workflowState: SpeakerWorkflowState.OPEN, // Default for historical speakers
    expertiseAreas: [], // Empty initially, can be backfilled
    speakingTopics: [] // Empty initially, can be backfilled
  };

  return speaker;
}

/**
 * Extract unique speakers from all sessions
 * Deduplicates based on speaker name
 *
 * @param sessions - All sessions from sessions.json
 * @returns Unique speakers across all sessions
 */
export function extractUniqueSpeakers(sessions: LegacySession[]): LegacySpeaker[] {
  const speakerMap = new Map<string, LegacySpeaker>();

  for (const session of sessions) {
    if (session.referenten && Array.isArray(session.referenten)) {
      for (const speaker of session.referenten) {
        // Use name as unique key (assume name uniquely identifies speaker)
        if (!speakerMap.has(speaker.name)) {
          speakerMap.set(speaker.name, speaker);
        }
      }
    }
  }

  return Array.from(speakerMap.values());
}

/**
 * Map all speakers from sessions, creating both User and Speaker entities
 *
 * @param sessions - All sessions from sessions.json
 * @param companies - List of migrated companies (from Task 1)
 * @returns Tuple of [User[], Speaker[]]
 */
export function mapAllSpeakers(
  sessions: LegacySession[],
  companies: Company[]
): [User[], Speaker[]] {
  const uniqueSpeakers = extractUniqueSpeakers(sessions);

  const users: User[] = [];
  const speakers: Speaker[] = [];

  for (const legacySpeaker of uniqueSpeakers) {
    const user = mapSpeakerToUser(legacySpeaker, companies);
    const speaker = mapSpeakerToSpeaker(user.id);

    users.push(user);
    speakers.push(speaker);
  }

  return [users, speakers];
}
