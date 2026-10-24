import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useCockpitCards } from '../useCockpitCards';

vi.mock('@/hooks/useEventTasks', () => ({
  useEventTasks: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
}));
vi.mock('@/hooks/useSpeakerPool', () => ({
  useSpeakerPool: () => ({
    data: [
      { status: 'ACCEPTED' },
      { status: 'CONTENT_SUBMITTED' },
      { status: 'CONTENT_SUBMITTED' },
      { status: 'QUALITY_REVIEWED' },
      { status: 'DECLINED' },
      { status: 'INVITED' },
    ],
  }),
}));
// Event-type template says min 3; the per-event agenda override says min 5.
vi.mock('@/hooks/useEventTypes', () => ({
  useEventType: () => ({ data: { minSlots: 3, maxSlots: 3 } }),
}));
vi.mock('@/hooks/useAgendaConfig/useAgendaConfig', () => ({
  useAgendaConfig: () => ({ data: { source: 'EVENT_OVERRIDE', minSlots: 5, maxSlots: 6 } }),
}));

describe('useCockpitCards', () => {
  it('should use the per-event agenda override minSlots for the fill-pool card when one exists', () => {
    const event = {
      eventCode: 'BATbern60',
      eventType: 'EVENING',
      workflowState: 'SPEAKER_IDENTIFICATION',
      confirmedSpeakersCount: 4,
    };

    const { result } = renderHook(() => useCockpitCards(event as never, 'BATbern60'));

    const fillPool = result.current.cards.find((c) => c.id === 'fill-pool');
    expect(fillPool).toBeDefined();
    expect(fillPool?.labelVars).toEqual({ confirmed: 4, min: 5 });
  });

  it('should count not-yet-reviewed speakers from the pool status once speakers are public', () => {
    const event = {
      eventCode: 'BATbern60',
      workflowState: 'AGENDA_PUBLISHED',
      currentPublishedPhase: 'AGENDA',
    };
    const { result } = renderHook(() => useCockpitCards(event as never, 'BATbern60'));
    const card = result.current.cards.find((c) => c.id === 'speakers-not-reviewed');
    expect(card?.labelVars).toEqual({ content: 1, review: 2 });
  });

  it('should treat the published phase case-insensitively and ignore the topic phase', () => {
    const lower = {
      eventCode: 'X',
      workflowState: 'SLOT_ASSIGNMENT',
      currentPublishedPhase: 'speakers',
    };
    const topic = {
      eventCode: 'X',
      workflowState: 'SLOT_ASSIGNMENT',
      currentPublishedPhase: 'TOPIC',
    };
    const a = renderHook(() => useCockpitCards(lower as never, 'X'));
    const b = renderHook(() => useCockpitCards(topic as never, 'X'));
    expect(a.result.current.cards.some((c) => c.id === 'speakers-not-reviewed')).toBe(true);
    expect(b.result.current.cards.some((c) => c.id === 'speakers-not-reviewed')).toBe(false);
  });

  it('should expose showRehearsal for an agenda-published event before its day', () => {
    const event = {
      eventCode: 'BATbern60',
      workflowState: 'AGENDA_PUBLISHED',
      date: '2099-11-06T00:00:00Z',
    };
    const { result } = renderHook(() => useCockpitCards(event as never, 'BATbern60'));
    expect(result.current.showRehearsal).toBe(true);
  });
});
