/**
 * Unit tests for computeTabBadges (Epic 14, Story 14.A.3 / FR6).
 */

import { describe, it, expect } from 'vitest';
import { computeTabBadges } from './tabBadges';
import type { EventTaskResponse } from '@/services/taskService';
import type { PublishingStatusResponse } from '@/types/event.types';

const NOW = new Date('2026-06-13T12:00:00Z').getTime();

const task = (over: Partial<EventTaskResponse>): EventTaskResponse => ({
  id: 'id',
  eventId: 'e',
  eventCode: 'BAT99',
  templateId: null,
  taskName: 'Generic task',
  triggerState: 'CREATED',
  dueDate: null,
  assignedOrganizerUsername: null,
  status: 'pending',
  notes: null,
  completedDate: null,
  completedByUsername: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  ...over,
});

const status = (over: Partial<PublishingStatusResponse>): PublishingStatusResponse => ({
  currentPhase: null,
  publishedPhases: [],
  topic: { isValid: false, errors: [] },
  speakers: { isValid: false, errors: [] },
  sessions: { isValid: false, errors: [] } as PublishingStatusResponse['sessions'],
  ...over,
});

describe('computeTabBadges — Speakers count', () => {
  it('counts sessions without a slot plus speakers pending review', () => {
    const badges = computeTabBadges({
      sessions: [
        { startTime: '2026-06-13T09:00:00Z' },
        { startTime: null },
        { startTime: undefined },
      ],
      pendingMaterialsCount: 2,
      now: NOW,
    });
    expect(badges.speakers).toBe(4); // 2 unslotted + 2 pending review
  });

  it('is 0 when all sessions are slotted and none pending review', () => {
    const badges = computeTabBadges({
      sessions: [{ startTime: '2026-06-13T09:00:00Z' }],
      pendingMaterialsCount: 0,
      now: NOW,
    });
    expect(badges.speakers).toBe(0);
  });

  it('is 0 with no data', () => {
    expect(computeTabBadges({ now: NOW }).speakers).toBe(0);
  });
});

describe('computeTabBadges — Publishing ready', () => {
  it('is true when the next unpublished phase is valid', () => {
    const badges = computeTabBadges({
      now: NOW,
      publishingStatus: status({ publishedPhases: [], topic: { isValid: true, errors: [] } }),
    });
    expect(badges.publishingReady).toBe(true);
  });

  it('is false when the next unpublished phase is invalid', () => {
    const badges = computeTabBadges({
      now: NOW,
      publishingStatus: status({ publishedPhases: [], topic: { isValid: false, errors: ['x'] } }),
    });
    expect(badges.publishingReady).toBe(false);
  });

  it('advances to the next phase once earlier phases are published', () => {
    const badges = computeTabBadges({
      now: NOW,
      publishingStatus: status({
        publishedPhases: ['topic'],
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
      }),
    });
    expect(badges.publishingReady).toBe(true); // speakers is next and valid
  });

  it('is false when every phase is already published', () => {
    const badges = computeTabBadges({
      now: NOW,
      publishingStatus: status({ publishedPhases: ['topic', 'speakers', 'agenda'] }),
    });
    expect(badges.publishingReady).toBe(false);
  });

  it('is false with no publishing status', () => {
    expect(computeTabBadges({ now: NOW }).publishingReady).toBe(false);
  });
});

describe('computeTabBadges — Communications overdue dot', () => {
  it('is true for an overdue, non-completed comms task', () => {
    const badges = computeTabBadges({
      now: NOW,
      tasks: [task({ taskName: 'Newsletter: Final Agenda', dueDate: '2026-06-10T00:00:00Z' })],
    });
    expect(badges.commsOverdue).toBe(true);
  });

  it('ignores overdue NON-comms tasks', () => {
    const badges = computeTabBadges({
      now: NOW,
      tasks: [task({ taskName: 'Book the venue', dueDate: '2026-06-10T00:00:00Z' })],
    });
    expect(badges.commsOverdue).toBe(false);
  });

  it('ignores a completed comms task even if past due', () => {
    const badges = computeTabBadges({
      now: NOW,
      tasks: [
        task({
          taskName: 'Newsletter reminder',
          dueDate: '2026-06-10T00:00:00Z',
          status: 'completed',
        }),
      ],
    });
    expect(badges.commsOverdue).toBe(false);
  });

  it('ignores a comms task that is not yet due', () => {
    const badges = computeTabBadges({
      now: NOW,
      tasks: [task({ taskName: 'Newsletter', dueDate: '2026-06-20T00:00:00Z' })],
    });
    expect(badges.commsOverdue).toBe(false);
  });

  it('is false with no tasks', () => {
    expect(computeTabBadges({ now: NOW }).commsOverdue).toBe(false);
  });
});
