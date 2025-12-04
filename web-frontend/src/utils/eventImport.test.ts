/**
 * Event Import Utilities - Unit Tests
 *
 * Tests for event import parsing, validation, and transformation functions.
 */

import { describe, it, expect } from 'vitest';
import {
  transformNameToUsername,
  buildOrganizerMap,
  determineVenue,
  calculateRegistrationDeadline,
  transformEventForApi,
  parseEventsJson,
  parseSessionsJson,
  createImportCandidates,
  isDuplicateEventNumber,
} from './eventImport';
import type { LegacyEvent, LegacySession, OrganizerMap } from '@/types/eventImport.types';

describe('transformNameToUsername', () => {
  it('should_transformSimpleName_when_firstAndLastNameProvided', () => {
    expect(transformNameToUsername('Thomas Goetz')).toBe('thomas.goetz');
  });

  it('should_transformNameWithCompany_when_companyAfterComma', () => {
    expect(transformNameToUsername('Nissim J. Buchs, RTC AG')).toBe('nissim.buchs');
  });

  it('should_ignoreMiddleInitials_when_present', () => {
    expect(transformNameToUsername('John Q. Public')).toBe('john.public');
  });

  it('should_handleSingleName_when_onlyOnePartProvided', () => {
    expect(transformNameToUsername('Madonna')).toBe('madonna');
  });

  it('should_handleEmptyString_when_noNameProvided', () => {
    expect(transformNameToUsername('')).toBe('');
  });

  it('should_handleMultipleSpaces_when_nameHasExtraWhitespace', () => {
    expect(transformNameToUsername('  Sara   Kim  ')).toBe('sara.kim');
  });
});

describe('buildOrganizerMap', () => {
  it('should_extractModerator_when_moderationSessionExists', () => {
    const sessions: LegacySession[] = [
      { bat: 11, title: 'Moderation', authoren: 'Thomas Goetz' },
      { bat: 11, title: 'Some Presentation', abstract: 'Test abstract' },
    ];

    const result = buildOrganizerMap(sessions);

    expect(result[11]).toBe('thomas.goetz');
  });

  it('should_handleMultipleEvents_when_multipleModerationSessions', () => {
    const sessions: LegacySession[] = [
      { bat: 11, title: 'Moderation', authoren: 'Thomas Goetz' },
      { bat: 14, title: 'Moderation', authoren: 'Sara Kim' },
      { bat: 15, title: 'Moderation', authoren: 'Nissim Buchs, RTC AG' },
    ];

    const result = buildOrganizerMap(sessions);

    expect(result[11]).toBe('thomas.goetz');
    expect(result[14]).toBe('sara.kim');
    expect(result[15]).toBe('nissim.buchs');
  });

  it('should_skipEvent_when_noModerationSession', () => {
    const sessions: LegacySession[] = [
      { bat: 1, title: 'Programmheft', authoren: '' },
      { bat: 1, title: 'Some Presentation', abstract: 'Test' },
    ];

    const result = buildOrganizerMap(sessions);

    expect(result[1]).toBeUndefined();
  });

  it('should_skipEvent_when_moderationSessionHasEmptyAuthoren', () => {
    const sessions: LegacySession[] = [{ bat: 1, title: 'Moderation', authoren: '' }];

    const result = buildOrganizerMap(sessions);

    expect(result[1]).toBeUndefined();
  });

  it('should_handleEmptyArray_when_noSessionsProvided', () => {
    const result = buildOrganizerMap([]);

    expect(result).toEqual({});
  });
});

describe('determineVenue', () => {
  it('should_returnKursaal_when_eventNumberIs1to9', () => {
    expect(determineVenue(1)).toEqual({
      name: 'Kursaal Bern',
      address: 'Kornhausstrasse 3, 3013 Bern',
      capacity: 50,
    });

    expect(determineVenue(9)).toEqual({
      name: 'Kursaal Bern',
      address: 'Kornhausstrasse 3, 3013 Bern',
      capacity: 50,
    });
  });

  it('should_returnPaulKlee_when_eventNumberIs10OrGreater', () => {
    expect(determineVenue(10)).toEqual({
      name: 'Zentrum Paul Klee, Bern',
      address: 'Monument im Fruchtland 3, 3006 Bern',
      capacity: 200,
    });

    expect(determineVenue(50)).toEqual({
      name: 'Zentrum Paul Klee, Bern',
      address: 'Monument im Fruchtland 3, 3006 Bern',
      capacity: 200,
    });
  });
});

describe('calculateRegistrationDeadline', () => {
  it('should_subtractOneDay_when_eventDateProvided', () => {
    const result = calculateRegistrationDeadline('2005-06-24T16:00:00+02:00');
    const resultDate = new Date(result);

    expect(resultDate.getUTCDate()).toBe(23); // One day before (UTC)
    expect(resultDate.getUTCHours()).toBe(23); // 23:59:59 UTC
    expect(resultDate.getUTCMinutes()).toBe(59);
    expect(resultDate.getUTCSeconds()).toBe(59);
  });

  it('should_handleMonthBoundary_when_eventDateIsFirstOfMonth', () => {
    const result = calculateRegistrationDeadline('2005-07-01T10:00:00Z');
    const resultDate = new Date(result);

    expect(resultDate.getUTCMonth()).toBe(5); // June (0-indexed)
    expect(resultDate.getUTCDate()).toBe(30);
  });
});

describe('transformEventForApi', () => {
  const mockEvent: LegacyEvent = {
    bat: 11,
    topic: ' GUI Frameworks ',
    datum: '24. Juni 05, 16:00h - 18:30h',
    parsedDate: '2005-06-24T16:00:00+02:00',
    description: 'Test event description about GUI frameworks',
    eventType: 'Abend-BAT',
    moderator: 'Thomas Goetz',
  };

  it('should_transformAllFields_when_validEventProvided', () => {
    const result = transformEventForApi(mockEvent);

    expect(result.title).toBe('GUI Frameworks'); // Trimmed
    expect(result.eventNumber).toBe(11);
    expect(result.date).toBe('2005-06-24T16:00:00+02:00');
    expect(result.status).toBe('archived');
    expect(result.organizerUsername).toBe('thomas.goetz');
    expect(result.currentAttendeeCount).toBe(0);
    expect(result.description).toBe('Test event description about GUI frameworks');
  });

  it('should_applyCorrectVenue_when_eventNumberIs7OrGreater', () => {
    const result = transformEventForApi(mockEvent);

    expect(result.venueName).toBe('Zentrum Paul Klee, Bern');
    expect(result.venueAddress).toBe('Monument im Fruchtland 3, 3006 Bern');
    expect(result.venueCapacity).toBe(200);
  });

  it('should_calculateDeadline_when_eventDateProvided', () => {
    const result = transformEventForApi(mockEvent);
    const deadlineDate = new Date(result.registrationDeadline);

    expect(deadlineDate.getUTCDate()).toBe(23); // One day before event (UTC)
  });

  it('should_transformModeratorToUsername_when_moderatorProvided', () => {
    const result = transformEventForApi(mockEvent);

    expect(result.organizerUsername).toBe('thomas.goetz');
  });

  it('should_storeMetadata_when_legacyFieldsPresent', () => {
    const result = transformEventForApi(mockEvent);
    const metadata = JSON.parse(result.metadata!);

    expect(metadata.legacyEventType).toBe('Abend-BAT');
    expect(metadata.legacyDatum).toBe('24. Juni 05, 16:00h - 18:30h');
    expect(metadata.legacyBatNumber).toBe(11);
  });
});

describe('parseEventsJson', () => {
  it('should_parseValidEvents_when_allRequiredFieldsPresent', () => {
    const json = JSON.stringify([
      {
        bat: 1,
        topic: 'GUI Frameworks',
        datum: '24. Juni 05, 16:00h - 18:30h',
        parsedDate: '2005-06-24T16:00:00+02:00',
        description: 'Test description',
        eventType: 'Abend-BAT',
        moderator: 'Thomas Goetz',
      },
    ]);

    const result = parseEventsJson(json);

    expect(result).toHaveLength(1);
    expect(result[0].bat).toBe(1);
    expect(result[0].topic).toBe('GUI Frameworks');
  });

  it('should_throwError_when_jsonIsInvalid', () => {
    expect(() => parseEventsJson('{ invalid json')).toThrow('Invalid JSON');
  });

  it('should_throwError_when_jsonIsNotArray', () => {
    expect(() => parseEventsJson('{"bat": 1}')).toThrow('JSON must be an array of events');
  });

  it('should_throwError_when_eventMissingBatField', () => {
    const json = JSON.stringify([{ topic: 'Test' }]);

    expect(() => parseEventsJson(json)).toThrow("missing required 'bat' field");
  });

  it('should_throwError_when_eventMissingParsedDate', () => {
    const json = JSON.stringify([
      {
        bat: 1,
        topic: 'Test',
        datum: '24. Juni 05',
        description: 'Test',
        eventType: 'Abend-BAT',
      },
    ]);

    expect(() => parseEventsJson(json)).toThrow(
      "missing required 'parsedDate' field (Phase 0 preprocessing required)"
    );
  });

  it('should_throwError_when_eventMissingDescription', () => {
    const json = JSON.stringify([
      {
        bat: 1,
        topic: 'Test',
        datum: '24. Juni 05',
        parsedDate: '2005-06-24T16:00:00+02:00',
        eventType: 'Abend-BAT',
        moderator: 'Thomas Goetz',
      },
    ]);

    expect(() => parseEventsJson(json)).toThrow(
      "missing required 'description' field (Phase 0 preprocessing required)"
    );
  });

  it('should_throwError_when_eventMissingModerator', () => {
    const json = JSON.stringify([
      {
        bat: 1,
        topic: 'Test',
        datum: '24. Juni 05',
        parsedDate: '2005-06-24T16:00:00+02:00',
        description: 'Test',
        eventType: 'Abend-BAT',
      },
    ]);

    expect(() => parseEventsJson(json)).toThrow(
      "missing required 'moderator' field (Phase 0 preprocessing required)"
    );
  });

  it('should_throwError_when_parsedDateIsInvalid', () => {
    const json = JSON.stringify([
      {
        bat: 1,
        topic: 'Test',
        datum: '24. Juni 05',
        parsedDate: 'not-a-date',
        description: 'Test',
        eventType: 'Abend-BAT',
        moderator: 'Thomas Goetz',
      },
    ]);

    expect(() => parseEventsJson(json)).toThrow("invalid 'parsedDate' format");
  });
});

describe('parseSessionsJson', () => {
  it('should_parseValidSessions_when_allRequiredFieldsPresent', () => {
    const json = JSON.stringify([
      { bat: 1, title: 'Moderation', authoren: 'Thomas Goetz' },
      { bat: 1, title: 'Presentation', abstract: 'Test abstract' },
    ]);

    const result = parseSessionsJson(json);

    expect(result).toHaveLength(2);
    expect(result[0].bat).toBe(1);
    expect(result[0].title).toBe('Moderation');
  });

  it('should_throwError_when_jsonIsInvalid', () => {
    expect(() => parseSessionsJson('{ invalid')).toThrow('Invalid JSON');
  });

  it('should_throwError_when_jsonIsNotArray', () => {
    expect(() => parseSessionsJson('{"bat": 1}')).toThrow('JSON must be an array of sessions');
  });

  it('should_throwError_when_sessionMissingBatField', () => {
    const json = JSON.stringify([{ title: 'Test' }]);

    expect(() => parseSessionsJson(json)).toThrow("missing required 'bat' field");
  });

  it('should_throwError_when_sessionMissingTitleField', () => {
    const json = JSON.stringify([{ bat: 1 }]);

    expect(() => parseSessionsJson(json)).toThrow("missing required 'title' field");
  });
});

describe('createImportCandidates', () => {
  const mockEvents: LegacyEvent[] = [
    {
      bat: 11,
      topic: 'GUI Frameworks',
      datum: '24. Juni 05, 16:00h - 18:30h',
      parsedDate: '2005-06-24T16:00:00+02:00',
      description: 'Test description',
      eventType: 'Abend-BAT',
      moderator: 'Thomas Goetz',
    },
  ];

  it('should_createCandidates_when_eventsProvided', () => {
    const result = createImportCandidates(mockEvents);

    expect(result).toHaveLength(1);
    expect(result[0].source).toEqual(mockEvents[0]);
    expect(result[0].apiPayload.organizerUsername).toBe('thomas.goetz');
    expect(result[0].importStatus).toBe('pending');
  });

  it('should_transformModeratorToUsername_when_moderatorProvided', () => {
    const eventsWithDifferentModerator: LegacyEvent[] = [
      {
        ...mockEvents[0],
        moderator: 'Nissim J. Buchs, RTC AG',
      },
    ];

    const result = createImportCandidates(eventsWithDifferentModerator);

    expect(result[0].apiPayload.organizerUsername).toBe('nissim.buchs');
  });

  it('should_handleEmptyArray_when_noEventsProvided', () => {
    const result = createImportCandidates([]);

    expect(result).toHaveLength(0);
  });
});

describe('isDuplicateEventNumber', () => {
  it('should_returnTrue_when_eventNumberExists', () => {
    const existingNumbers = new Set([1, 2, 3, 11, 14]);

    expect(isDuplicateEventNumber(11, existingNumbers)).toBe(true);
  });

  it('should_returnFalse_when_eventNumberDoesNotExist', () => {
    const existingNumbers = new Set([1, 2, 3]);

    expect(isDuplicateEventNumber(11, existingNumbers)).toBe(false);
  });

  it('should_returnFalse_when_setIsEmpty', () => {
    const existingNumbers = new Set<number>();

    expect(isDuplicateEventNumber(11, existingNumbers)).toBe(false);
  });
});
