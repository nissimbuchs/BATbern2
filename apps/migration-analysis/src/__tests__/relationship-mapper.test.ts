/**
 * Tests for Relationship Mapper (AC5)
 * TDD Approach: Write tests first (RED), then implement (GREEN), then refactor
 *
 * Defines foreign key relationships across microservices
 */

import {
  createSessionEventRelationship,
  createSessionUserRelationship,
  createSpeakerUserRelationship,
  createUserCompanyRelationship,
  findEventByEventNumber,
  findUserByUsername,
  RelationshipMap
} from '../mappers/relationship-mapper';
import { Event, User, Session, SessionUser, Speaker, Company } from '../types/target-types';
import { SpeakerRole } from '../types/target-types';

describe('Relationship Mapper (AC5)', () => {
  // Mock data for testing
  const mockEvents: Event[] = [
    {
      id: 'event-uuid-56',
      eventCode: 'BATbern56',
      eventNumber: 56,
      title: 'Cloud Security',
      eventDate: new Date('2005-06-24'),
      eventType: 'evening' as any,
      status: 'archived' as any,
      workflowState: 'published' as any
    }
  ];

  const mockUsers: User[] = [
    {
      id: 'user-uuid-123',
      username: 'thomas.goetz',
      firstName: 'Thomas',
      lastName: 'Goetz',
      bio: 'Experienced architect',
      companyId: 'mobiliar'
    },
    {
      id: 'user-uuid-456',
      username: 'anna.mueller',
      firstName: 'Anna',
      lastName: 'Mueller',
      bio: 'Security expert',
      companyId: 'sbb'
    }
  ];

  const mockCompanies: Company[] = [
    { name: 'mobiliar', displayName: 'Die Mobiliar', isVerified: false },
    { name: 'sbb', displayName: 'SBB CFF FFS', isVerified: false }
  ];

  describe('Test 5.1: should_linkSessionToEvent_when_sessionMapped', () => {
    it('should create Session → Event relationship using eventId FK', () => {
      const session: Session = {
        id: 'session-uuid-1',
        eventId: 'event-uuid-56', // FK to Event.id
        title: 'Cloud Security Best Practices',
        description: 'Learn about cloud security',
        sessionType: 'presentation' as any
      };

      const relationship = createSessionEventRelationship(session, mockEvents);

      expect(relationship.sessionId).toBe('session-uuid-1');
      expect(relationship.eventId).toBe('event-uuid-56');
      expect(relationship.eventCode).toBe('BATbern56');
      expect(relationship.relationshipType).toBe('session_to_event');
    });

    it('should find event by event number', () => {
      const event = findEventByEventNumber(56, mockEvents);

      expect(event).toBeDefined();
      expect(event?.id).toBe('event-uuid-56');
      expect(event?.eventNumber).toBe(56);
    });

    it('should return undefined if event not found', () => {
      const event = findEventByEventNumber(999, mockEvents);

      expect(event).toBeUndefined();
    });
  });

  describe('Test 5.2: should_createSessionUserWithUserId_when_speakerAssigned', () => {
    it('should create SessionUser → User relationship using userId FK (ADR-004)', () => {
      const sessionUser: SessionUser = {
        id: 'session-user-uuid-1',
        sessionId: 'session-uuid-1',
        userId: 'user-uuid-123', // FK to User.id (cross-service)
        speakerRole: SpeakerRole.PRIMARY_SPEAKER,
        isConfirmed: true
      };

      const relationship = createSessionUserRelationship(sessionUser, mockUsers);

      expect(relationship.sessionUserId).toBe('session-user-uuid-1');
      expect(relationship.userId).toBe('user-uuid-123');
      expect(relationship.username).toBe('thomas.goetz');
      expect(relationship.relationshipType).toBe('session_user_to_user');
    });

    it('should link SessionUser → Session relationship', () => {
      const sessionUser: SessionUser = {
        id: 'session-user-uuid-1',
        sessionId: 'session-uuid-1', // FK to Session.id
        userId: 'user-uuid-123',
        speakerRole: SpeakerRole.PRIMARY_SPEAKER,
        isConfirmed: true
      };

      const relationship = createSessionUserRelationship(sessionUser, mockUsers);

      expect(relationship.sessionId).toBe('session-uuid-1');
      expect(relationship.sessionUserId).toBe('session-user-uuid-1');
    });
  });

  describe('Test 5.3: should_linkSpeakerToUser_when_speakerCreated', () => {
    it('should create Speaker → User relationship using userId FK (ADR-004)', () => {
      const speaker: Speaker = {
        id: 'speaker-uuid-1',
        userId: 'user-uuid-123', // FK to User.id (cross-service)
        availability: 'available' as any,
        workflowState: 'open' as any,
        expertiseAreas: [],
        speakingTopics: []
      };

      const relationship = createSpeakerUserRelationship(speaker, mockUsers);

      expect(relationship.speakerId).toBe('speaker-uuid-1');
      expect(relationship.userId).toBe('user-uuid-123');
      expect(relationship.username).toBe('thomas.goetz');
      expect(relationship.relationshipType).toBe('speaker_to_user');
    });
  });

  describe('Test 5.4: should_linkUserToCompany_when_companyAssigned', () => {
    it('should create User → Company relationship using companyId FK (meaningful ID)', () => {
      const user = mockUsers[0]; // thomas.goetz with companyId = 'mobiliar'

      const relationship = createUserCompanyRelationship(user, mockCompanies);

      expect(relationship.userId).toBe('user-uuid-123');
      expect(relationship.username).toBe('thomas.goetz');
      expect(relationship.companyId).toBe('mobiliar'); // Meaningful ID, not UUID
      expect(relationship.companyDisplayName).toBe('Die Mobiliar');
      expect(relationship.relationshipType).toBe('user_to_company');
    });

    it('should use meaningful ID for company FK (Story 1.16.2)', () => {
      const user = mockUsers[0];

      const relationship = createUserCompanyRelationship(user, mockCompanies);

      // Verify it's a meaningful ID, not a UUID
      expect(relationship.companyId).not.toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}/);
      expect(relationship.companyId).toBe('mobiliar');
      expect(relationship.companyId.length).toBeLessThanOrEqual(12);
    });
  });

  describe('User Lookup Functions', () => {
    it('should find user by username', () => {
      const user = findUserByUsername('thomas.goetz', mockUsers);

      expect(user).toBeDefined();
      expect(user?.id).toBe('user-uuid-123');
      expect(user?.username).toBe('thomas.goetz');
    });

    it('should return undefined if user not found', () => {
      const user = findUserByUsername('nonexistent.user', mockUsers);

      expect(user).toBeUndefined();
    });

    it('should be case-sensitive for username lookup', () => {
      const user = findUserByUsername('Thomas.Goetz', mockUsers); // Wrong case

      expect(user).toBeUndefined();
    });
  });

  describe('Relationship Map Generation', () => {
    it('should generate complete relationship map for migration', () => {
      const relationshipMap: RelationshipMap = {
        sessionToEvent: [],
        sessionUserToUser: [],
        sessionUserToSession: [],
        speakerToUser: [],
        userToCompany: []
      };

      // Verify structure
      expect(relationshipMap).toHaveProperty('sessionToEvent');
      expect(relationshipMap).toHaveProperty('sessionUserToUser');
      expect(relationshipMap).toHaveProperty('sessionUserToSession');
      expect(relationshipMap).toHaveProperty('speakerToUser');
      expect(relationshipMap).toHaveProperty('userToCompany');
    });
  });

  describe('Cross-Service Relationships (ADR-004)', () => {
    it('should identify cross-service relationships', () => {
      // SessionUser → User (Event Management → Company User Management)
      const sessionUser: SessionUser = {
        id: 'session-user-uuid-1',
        sessionId: 'session-uuid-1',
        userId: 'user-uuid-123', // Cross-service FK
        speakerRole: SpeakerRole.PRIMARY_SPEAKER,
        isConfirmed: true
      };

      const relationship = createSessionUserRelationship(sessionUser, mockUsers);

      expect(relationship.relationshipType).toBe('session_user_to_user');
      // This crosses service boundaries: Event Management → Company User Management
    });

    it('should identify same-service relationships', () => {
      const session: Session = {
        id: 'session-uuid-1',
        eventId: 'event-uuid-56', // Same service FK
        title: 'Session',
        sessionType: 'presentation' as any
      };

      const relationship = createSessionEventRelationship(session, mockEvents);

      expect(relationship.relationshipType).toBe('session_to_event');
      // This is within Event Management Service
    });
  });

  describe('FK Type Validation', () => {
    it('should use UUID for same-service relationships', () => {
      const session: Session = {
        id: 'session-uuid-1',
        eventId: 'event-uuid-56',
        title: 'Session',
        sessionType: 'presentation' as any
      };

      const relationship = createSessionEventRelationship(session, mockEvents);

      // Event ID is UUID (same service)
      expect(relationship.eventId).toMatch(/^[a-z-]+uuid-\d+$/);
    });

    it('should use meaningful ID for User → Company relationship', () => {
      const user = mockUsers[0];

      const relationship = createUserCompanyRelationship(user, mockCompanies);

      // Company ID is meaningful ID, not UUID
      expect(relationship.companyId).toBe('mobiliar');
      expect(relationship.companyId).not.toMatch(/uuid/);
    });
  });
});
