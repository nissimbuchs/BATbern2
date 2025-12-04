/**
 * Tests for Event Mapper (AC2)
 * TDD Approach: Write tests first (RED), then implement (GREEN), then refactor
 */

import { mapEvent, parseGermanDate, mapEventType } from '../mappers/event-mapper';
import { LegacyTopic } from '../types/legacy-types';
import { EventType, EventStatus, EventWorkflowState } from '../types/target-types';

describe('Event Mapper (AC2)', () => {
  describe('Test 2.1: should_mapBatNumberToEventCode_when_eventMapped', () => {
    it('should generate eventCode from BAT number', () => {
      const legacyEvent: LegacyTopic = {
        bat: 56,
        topic: 'Cloud Security',
        datum: '24. Juni 05, 16:00h - 18:30h',
        eventType: 'Abend-BAT'
      };

      const event = mapEvent(legacyEvent);

      expect(event.eventCode).toBe('BATbern56');
      expect(event.eventNumber).toBe(56);
    });

    it('should handle single digit BAT numbers', () => {
      const legacyEvent: LegacyTopic = {
        bat: 5,
        topic: 'Early Event',
        datum: '15. März 2005, 16:00h',
        eventType: 'Abend-BAT'
      };

      const event = mapEvent(legacyEvent);

      expect(event.eventCode).toBe('BATbern5');
      expect(event.eventNumber).toBe(5);
    });
  });

  describe('Test 2.2: should_parseGermanDateFormats_when_datumMapped', () => {
    it('should parse format: "DD. Month YY, HH:mmh - HH:mmh"', () => {
      const dateString = '24. Juni 05, 16:00h - 18:30h';

      const parsed = parseGermanDate(dateString);

      expect(parsed).toBeDefined();
      expect(parsed.getFullYear()).toBe(2005);
      expect(parsed.getMonth()).toBe(5); // June = 5 (0-indexed)
      expect(parsed.getDate()).toBe(24);
      expect(parsed.getHours()).toBe(16);
      expect(parsed.getMinutes()).toBe(0);
    });

    it('should parse format: "Day, DD. Month YYYY, HH:mm - HH:mm Uhr"', () => {
      const dateString = 'Donnerstag, 2. Mai 2024, 16:00 - 18:30 Uhr';

      const parsed = parseGermanDate(dateString);

      expect(parsed).toBeDefined();
      expect(parsed.getFullYear()).toBe(2024);
      expect(parsed.getMonth()).toBe(4); // May = 4
      expect(parsed.getDate()).toBe(2);
      expect(parsed.getHours()).toBe(16);
    });

    it('should parse format: "YYYY-MM-DD"', () => {
      const dateString = '2024-05-02';

      const parsed = parseGermanDate(dateString);

      expect(parsed).toBeDefined();
      expect(parsed.getFullYear()).toBe(2024);
      expect(parsed.getMonth()).toBe(4); // May = 4
      expect(parsed.getDate()).toBe(2);
    });

    it('should handle different German month names', () => {
      const months = [
        { str: '5. Januar 2020, 16:00h', month: 0 },
        { str: '5. Februar 2020, 16:00h', month: 1 },
        { str: '5. März 2020, 16:00h', month: 2 },
        { str: '5. April 2020, 16:00h', month: 3 },
        { str: '5. Mai 2020, 16:00h', month: 4 },
        { str: '5. Juni 2020, 16:00h', month: 5 },
        { str: '5. Juli 2020, 16:00h', month: 6 },
        { str: '5. August 2020, 16:00h', month: 7 },
        { str: '5. September 2020, 16:00h', month: 8 },
        { str: '5. Oktober 2020, 16:00h', month: 9 },
        { str: '5. November 2020, 16:00h', month: 10 },
        { str: '5. Dezember 2020, 16:00h', month: 11 }
      ];

      months.forEach(({ str, month }) => {
        const parsed = parseGermanDate(str);
        expect(parsed.getMonth()).toBe(month);
      });
    });
  });

  describe('Test 2.3: should_mapEventTypeEnum_when_eventTypeProvided', () => {
    it('should map "Abend-BAT" to EventType.EVENING', () => {
      const eventType = mapEventType('Abend-BAT');
      expect(eventType).toBe(EventType.EVENING);
    });

    it('should map "Ganztag-BAT" to EventType.FULL_DAY', () => {
      const eventType = mapEventType('Ganztag-BAT');
      expect(eventType).toBe(EventType.FULL_DAY);
    });

    it('should map "Halb-BAT" to EventType.AFTERNOON', () => {
      const eventType = mapEventType('Halb-BAT');
      expect(eventType).toBe(EventType.AFTERNOON);
    });

    it('should default to EventType.EVENING for unknown types', () => {
      const eventType = mapEventType('Unknown-BAT');
      expect(eventType).toBe(EventType.EVENING);
    });
  });

  describe('Test 2.4: should_setArchivedStatus_when_historicalEventMapped', () => {
    it('should set status to ARCHIVED for historical events', () => {
      const legacyEvent: LegacyTopic = {
        bat: 56,
        topic: 'Cloud Security',
        datum: '24. Juni 05, 16:00h - 18:30h',
        eventType: 'Abend-BAT'
      };

      const event = mapEvent(legacyEvent);

      expect(event.status).toBe(EventStatus.ARCHIVED);
    });

    it('should set workflowState to PUBLISHED for historical events', () => {
      const legacyEvent: LegacyTopic = {
        bat: 56,
        topic: 'Cloud Security',
        datum: '24. Juni 05, 16:00h - 18:30h',
        eventType: 'Abend-BAT'
      };

      const event = mapEvent(legacyEvent);

      expect(event.workflowState).toBe(EventWorkflowState.PUBLISHED);
    });
  });

  describe('Schema Compliance', () => {
    it('should respect VARCHAR(255) constraint for title', () => {
      const legacyEvent: LegacyTopic = {
        bat: 56,
        topic: 'A'.repeat(300), // Very long title
        datum: '24. Juni 05, 16:00h',
        eventType: 'Abend-BAT'
      };

      const event = mapEvent(legacyEvent);

      expect(event.title.length).toBeLessThanOrEqual(255);
    });

    it('should respect VARCHAR(50) constraint for eventCode', () => {
      const legacyEvent: LegacyTopic = {
        bat: 999,
        topic: 'Test Event',
        datum: '24. Juni 05, 16:00h',
        eventType: 'Abend-BAT'
      };

      const event = mapEvent(legacyEvent);

      expect(event.eventCode.length).toBeLessThanOrEqual(50);
    });

    it('should generate UUID for event id', () => {
      const legacyEvent: LegacyTopic = {
        bat: 56,
        topic: 'Cloud Security',
        datum: '24. Juni 05, 16:00h',
        eventType: 'Abend-BAT'
      };

      const event = mapEvent(legacyEvent);

      expect(event.id).toBeDefined();
      // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
      expect(event.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    });
  });
});
