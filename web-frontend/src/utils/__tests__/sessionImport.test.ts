/**
 * Session Import Utilities Tests
 *
 * Tests for parsing, validating, and transforming legacy session JSON
 */

import { describe, it, expect } from 'vitest';
import {
  parseSessionsJson,
  createSessionImportCandidates,
  filterSessionsByEvent,
  groupSessionsByEvent,
  validateSessionEventReferences,
} from '../sessionImport';
import type { LegacySession } from '@/types/sessionImport.types';

describe('sessionImport', () => {
  describe('parseSessionsJson', () => {
    it('should_parseValidJson_when_arrayOfSessions', () => {
      const json = JSON.stringify([
        { bat: 142, title: 'Session 1', abstract: 'Test abstract' },
        { bat: 143, title: 'Session 2', abstract: 'Another abstract' },
      ]);

      const result = parseSessionsJson(json);

      expect(result).toHaveLength(2);
      expect(result[0].bat).toBe(142);
      expect(result[0].title).toBe('Session 1');
    });

    it('should_throwError_when_jsonIsNotArray', () => {
      const json = JSON.stringify({ bat: 142, title: 'Session 1' });

      expect(() => parseSessionsJson(json)).toThrow('sessions.json must be an array');
    });

    it('should_throwError_when_invalidJsonSyntax', () => {
      const invalidJson = '{ invalid json }';

      expect(() => parseSessionsJson(invalidJson)).toThrow('Invalid JSON format');
    });

    it('should_throwError_when_noValidSessions', () => {
      const json = JSON.stringify([
        { title: 'Missing bat field' },
        { bat: 'not a number', title: 'Invalid bat' },
      ]);

      expect(() => parseSessionsJson(json)).toThrow('No valid sessions found');
    });

    it('should_filterOutInvalidSessions_when_mixedArray', () => {
      const json = JSON.stringify([
        { bat: 142, title: 'Valid session' },
        { title: 'Missing bat' },
        null,
        { bat: 143, title: 'Another valid' },
      ]);

      const result = parseSessionsJson(json);

      expect(result).toHaveLength(2);
      expect(result[0].title).toBe('Valid session');
      expect(result[1].title).toBe('Another valid');
    });

    it('should_parseSessionsWithAllFields_when_provided', () => {
      const json = JSON.stringify([
        {
          bat: 142,
          title: 'Full Session',
          abstract: 'Detailed abstract',
          pdf: 'presentation.pdf',
          authoren: 'John Doe',
          referenten: [{ name: 'Jane Smith', bio: 'Speaker bio', company: 'ACME' }],
        },
      ]);

      const result = parseSessionsJson(json);

      expect(result).toHaveLength(1);
      expect(result[0].pdf).toBe('presentation.pdf');
      expect(result[0].authoren).toBe('John Doe');
      expect(result[0].referenten).toHaveLength(1);
      expect(result[0].referenten?.[0].name).toBe('Jane Smith');
    });
  });

  describe('createSessionImportCandidates', () => {
    const mockSessions: LegacySession[] = [
      {
        bat: 142,
        title: 'Test Session',
        abstract: 'Test abstract',
        pdf: 'test.pdf',
        referenten: [{ name: 'Speaker 1' }, { name: 'Speaker 2' }],
      },
      {
        bat: 143,
        title: 'Another Session',
        abstract: '',
      },
    ];

    it('should_createImportCandidates_when_sessionsProvided', () => {
      const candidates = createSessionImportCandidates(mockSessions);

      expect(candidates).toHaveLength(2);
    });

    it('should_setCorrectEventCode_when_batProvided', () => {
      const candidates = createSessionImportCandidates(mockSessions);

      expect(candidates[0].eventCode).toBe('BATbern142');
      expect(candidates[1].eventCode).toBe('BATbern143');
    });

    it('should_countSpeakers_when_referentenProvided', () => {
      const candidates = createSessionImportCandidates(mockSessions);

      expect(candidates[0].speakersCount).toBe(2);
      expect(candidates[1].speakersCount).toBe(0);
    });

    it('should_setImportStatusToPending_when_created', () => {
      const candidates = createSessionImportCandidates(mockSessions);

      expect(candidates[0].importStatus).toBe('pending');
      expect(candidates[1].importStatus).toBe('pending');
    });

    it('should_preserveSourceData_when_created', () => {
      const candidates = createSessionImportCandidates(mockSessions);

      expect(candidates[0].source).toBe(mockSessions[0]);
      expect(candidates[0].source.title).toBe('Test Session');
    });

    it('should_createCorrectApiPayload_when_created', () => {
      const candidates = createSessionImportCandidates(mockSessions);

      expect(candidates[0].apiPayload.title).toBe('Test Session');
      expect(candidates[0].apiPayload.abstract).toBe('Test abstract');
      expect(candidates[0].apiPayload.pdf).toBe('test.pdf');
      expect(candidates[0].apiPayload.bat).toBe(142);
    });

    it('should_handleEmptyAbstract_when_notProvided', () => {
      const sessions: LegacySession[] = [{ bat: 142, title: 'No Abstract', abstract: '' }];

      const candidates = createSessionImportCandidates(sessions);

      expect(candidates[0].apiPayload.abstract).toBe('');
    });
  });

  describe('filterSessionsByEvent', () => {
    const sessions: LegacySession[] = [
      { bat: 142, title: 'Session A', abstract: '' },
      { bat: 142, title: 'Session B', abstract: '' },
      { bat: 143, title: 'Session C', abstract: '' },
      { bat: 144, title: 'Session D', abstract: '' },
    ];

    it('should_returnMatchingSessions_when_eventNumberProvided', () => {
      const filtered = filterSessionsByEvent(sessions, 142);

      expect(filtered).toHaveLength(2);
      expect(filtered[0].title).toBe('Session A');
      expect(filtered[1].title).toBe('Session B');
    });

    it('should_returnEmptyArray_when_noMatchingEvent', () => {
      const filtered = filterSessionsByEvent(sessions, 999);

      expect(filtered).toHaveLength(0);
    });

    it('should_returnSingleSession_when_oneMatchingEvent', () => {
      const filtered = filterSessionsByEvent(sessions, 143);

      expect(filtered).toHaveLength(1);
      expect(filtered[0].title).toBe('Session C');
    });
  });

  describe('groupSessionsByEvent', () => {
    const sessions: LegacySession[] = [
      { bat: 142, title: 'Session A', abstract: '' },
      { bat: 142, title: 'Session B', abstract: '' },
      { bat: 143, title: 'Session C', abstract: '' },
    ];

    it('should_groupSessionsByEventNumber_when_sessionsProvided', () => {
      const grouped = groupSessionsByEvent(sessions);

      expect(grouped.size).toBe(2);
      expect(grouped.get(142)).toHaveLength(2);
      expect(grouped.get(143)).toHaveLength(1);
    });

    it('should_returnEmptyMap_when_noSessions', () => {
      const grouped = groupSessionsByEvent([]);

      expect(grouped.size).toBe(0);
    });

    it('should_preserveSessionOrder_when_grouping', () => {
      const grouped = groupSessionsByEvent(sessions);

      const event142Sessions = grouped.get(142);
      expect(event142Sessions?.[0].title).toBe('Session A');
      expect(event142Sessions?.[1].title).toBe('Session B');
    });
  });

  describe('validateSessionEventReferences', () => {
    const sessions: LegacySession[] = [
      { bat: 142, title: 'Valid Session 1', abstract: '' },
      { bat: 143, title: 'Valid Session 2', abstract: '' },
      { bat: 999, title: 'Invalid Session', abstract: '' },
    ];

    it('should_separateValidAndInvalidSessions_when_validating', () => {
      const validEventNumbers = new Set([142, 143, 144]);
      const result = validateSessionEventReferences(sessions, validEventNumbers);

      expect(result.valid).toHaveLength(2);
      expect(result.invalid).toHaveLength(1);
    });

    it('should_returnAllAsValid_when_allEventsExist', () => {
      const validEventNumbers = new Set([142, 143, 999]);
      const result = validateSessionEventReferences(sessions, validEventNumbers);

      expect(result.valid).toHaveLength(3);
      expect(result.invalid).toHaveLength(0);
    });

    it('should_returnAllAsInvalid_when_noEventsExist', () => {
      const validEventNumbers = new Set([100, 101]);
      const result = validateSessionEventReferences(sessions, validEventNumbers);

      expect(result.valid).toHaveLength(0);
      expect(result.invalid).toHaveLength(3);
    });

    it('should_handleEmptySessionArray_when_provided', () => {
      const validEventNumbers = new Set([142, 143]);
      const result = validateSessionEventReferences([], validEventNumbers);

      expect(result.valid).toHaveLength(0);
      expect(result.invalid).toHaveLength(0);
    });

    it('should_handleEmptyValidEventNumbers_when_provided', () => {
      const result = validateSessionEventReferences(sessions, new Set());

      expect(result.valid).toHaveLength(0);
      expect(result.invalid).toHaveLength(3);
    });
  });
});
