import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useCockpitCards } from '../useCockpitCards';

vi.mock('@/hooks/useEventTasks', () => ({
  useEventTasks: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
}));
vi.mock('@/hooks/useSpeakerPool', () => ({
  useSpeakerPool: () => ({ data: [] }),
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
});
