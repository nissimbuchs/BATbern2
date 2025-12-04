/**
 * Relationship Mapper - Defines foreign key relationships across microservices
 * Handles both same-service and cross-service relationships per ADR-004
 */

import { Event, User, Session, SessionUser, Speaker, Company } from '../types/target-types';

/**
 * Session → Event relationship (same service)
 */
export interface SessionEventRelationship {
  sessionId: string;           // Session.id
  eventId: string;             // Event.id (UUID, same service)
  eventCode: string;           // Event.eventCode (for reference)
  relationshipType: 'session_to_event';
}

/**
 * SessionUser → User relationship (cross-service)
 */
export interface SessionUserUserRelationship {
  sessionUserId: string;       // SessionUser.id
  sessionId: string;           // SessionUser.sessionId
  userId: string;              // User.id (UUID, cross-service)
  username: string;            // User.username (for reference)
  relationshipType: 'session_user_to_user';
}

/**
 * Speaker → User relationship (cross-service)
 */
export interface SpeakerUserRelationship {
  speakerId: string;           // Speaker.id
  userId: string;              // User.id (UUID, cross-service)
  username: string;            // User.username (for reference)
  relationshipType: 'speaker_to_user';
}

/**
 * User → Company relationship (cross-service, meaningful ID)
 */
export interface UserCompanyRelationship {
  userId: string;              // User.id
  username: string;            // User.username (for reference)
  companyId: string;           // Company.name (meaningful ID, NOT UUID)
  companyDisplayName: string;  // Company.displayName (for reference)
  relationshipType: 'user_to_company';
}

/**
 * Complete relationship map for migration
 */
export interface RelationshipMap {
  sessionToEvent: SessionEventRelationship[];
  sessionUserToUser: SessionUserUserRelationship[];
  sessionUserToSession: SessionUserUserRelationship[]; // Same as sessionUserToUser
  speakerToUser: SpeakerUserRelationship[];
  userToCompany: UserCompanyRelationship[];
}

/**
 * Find event by event number
 * @param eventNumber - BAT event number (1-60)
 * @param events - List of events
 * @returns Event if found
 */
export function findEventByEventNumber(eventNumber: number, events: Event[]): Event | undefined {
  return events.find(e => e.eventNumber === eventNumber);
}

/**
 * Find user by username
 * @param username - User's username
 * @param users - List of users
 * @returns User if found
 */
export function findUserByUsername(username: string, users: User[]): User | undefined {
  return users.find(u => u.username === username);
}

/**
 * Create Session → Event relationship
 * Relationship: Session.eventId → Event.id (UUID FK, same service)
 *
 * @param session - Session entity
 * @param events - List of events
 * @returns Session-Event relationship
 */
export function createSessionEventRelationship(
  session: Session,
  events: Event[]
): SessionEventRelationship {
  // Find the event by its UUID
  const event = events.find(e => e.id === session.eventId);

  if (!event) {
    throw new Error(`Event with id ${session.eventId} not found for session ${session.id}`);
  }

  return {
    sessionId: session.id,
    eventId: session.eventId,
    eventCode: event.eventCode,
    relationshipType: 'session_to_event'
  };
}

/**
 * Create SessionUser → User relationship
 * Relationship: SessionUser.userId → User.id (UUID FK, cross-service)
 * Follows ADR-004: User fields in User entity, not duplicated in SessionUser
 *
 * @param sessionUser - SessionUser junction entity
 * @param users - List of users
 * @returns SessionUser-User relationship
 */
export function createSessionUserRelationship(
  sessionUser: SessionUser,
  users: User[]
): SessionUserUserRelationship {
  // Find the user by UUID
  const user = users.find(u => u.id === sessionUser.userId);

  if (!user) {
    throw new Error(`User with id ${sessionUser.userId} not found for SessionUser ${sessionUser.id}`);
  }

  return {
    sessionUserId: sessionUser.id,
    sessionId: sessionUser.sessionId,
    userId: sessionUser.userId,
    username: user.username,
    relationshipType: 'session_user_to_user'
  };
}

/**
 * Create Speaker → User relationship
 * Relationship: Speaker.userId → User.id (UUID FK, cross-service)
 * Follows ADR-004: bio, profilePicture in User, not duplicated in Speaker
 *
 * @param speaker - Speaker entity
 * @param users - List of users
 * @returns Speaker-User relationship
 */
export function createSpeakerUserRelationship(
  speaker: Speaker,
  users: User[]
): SpeakerUserRelationship {
  // Find the user by UUID
  const user = users.find(u => u.id === speaker.userId);

  if (!user) {
    throw new Error(`User with id ${speaker.userId} not found for Speaker ${speaker.id}`);
  }

  return {
    speakerId: speaker.id,
    userId: speaker.userId,
    username: user.username,
    relationshipType: 'speaker_to_user'
  };
}

/**
 * Create User → Company relationship
 * Relationship: User.companyId → Company.name (meaningful ID, NOT UUID)
 * Follows Story 1.16.2: Meaningful IDs pattern
 *
 * @param user - User entity
 * @param companies - List of companies
 * @returns User-Company relationship
 */
export function createUserCompanyRelationship(
  user: User,
  companies: Company[]
): UserCompanyRelationship {
  // Find company by meaningful ID (name)
  const company = companies.find(c => c.name === user.companyId);

  if (!company) {
    throw new Error(`Company with name ${user.companyId} not found for User ${user.id}`);
  }

  return {
    userId: user.id,
    username: user.username,
    companyId: user.companyId,
    companyDisplayName: company.displayName,
    relationshipType: 'user_to_company'
  };
}

/**
 * Build complete relationship map for migration
 * @param data - All entities
 * @returns Complete relationship map
 */
export function buildRelationshipMap(data: {
  events: Event[];
  sessions: Session[];
  sessionUsers: SessionUser[];
  users: User[];
  speakers: Speaker[];
  companies: Company[];
}): RelationshipMap {
  const map: RelationshipMap = {
    sessionToEvent: [],
    sessionUserToUser: [],
    sessionUserToSession: [],
    speakerToUser: [],
    userToCompany: []
  };

  // Build Session → Event relationships
  for (const session of data.sessions) {
    try {
      const relationship = createSessionEventRelationship(session, data.events);
      map.sessionToEvent.push(relationship);
    } catch (error) {
      console.error(`Failed to create Session → Event relationship:`, error);
    }
  }

  // Build SessionUser → User relationships
  for (const sessionUser of data.sessionUsers) {
    try {
      const relationship = createSessionUserRelationship(sessionUser, data.users);
      map.sessionUserToUser.push(relationship);
      map.sessionUserToSession.push(relationship); // Same relationship
    } catch (error) {
      console.error(`Failed to create SessionUser → User relationship:`, error);
    }
  }

  // Build Speaker → User relationships
  for (const speaker of data.speakers) {
    try {
      const relationship = createSpeakerUserRelationship(speaker, data.users);
      map.speakerToUser.push(relationship);
    } catch (error) {
      console.error(`Failed to create Speaker → User relationship:`, error);
    }
  }

  // Build User → Company relationships
  for (const user of data.users) {
    try {
      const relationship = createUserCompanyRelationship(user, data.companies);
      map.userToCompany.push(relationship);
    } catch (error) {
      console.error(`Failed to create User → Company relationship:`, error);
    }
  }

  return map;
}
