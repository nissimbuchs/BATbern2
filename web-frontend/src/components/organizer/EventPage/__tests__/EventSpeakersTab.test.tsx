/**
 * EventSpeakersTab Component Tests (Story 5.6 · Epic 14 Phase C 14.C.1/14.C.4/14.C.5)
 *
 * Renders the REAL component with mocked child components + data hooks, exercising the
 * Pool/Agenda/Slots sub-view contract, the agenda "needs a slot" summary + jump, and the
 * in-tab Slots sub-view (focusSpeakerId carried from ?speakerId=).
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';

// --- Mock heavy child components to keep the render light + assert props ---
vi.mock('@/components/organizer/SpeakerStatus/SpeakerStatusLanes', () => ({
  SpeakerStatusLanes: () => <div data-testid="speaker-status-lanes">Kanban</div>,
}));
vi.mock('@/components/organizer/EventManagement/SpeakersSessionsTable', () => ({
  SpeakersSessionsTable: () => <div data-testid="sessions-table">Sessions</div>,
}));
vi.mock('@/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment', () => ({
  DragDropSlotAssignment: ({ focusSpeakerId }: { focusSpeakerId?: string }) => (
    <div data-testid="slot-assignment">focus:{focusSpeakerId ?? 'none'}</div>
  ),
}));
vi.mock('@/components/organizer/SpeakerDrawer', () => ({
  SpeakerDetailDrawer: () => <div data-testid="speaker-detail-drawer" />,
}));
vi.mock('@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel', () => ({
  SpeakerBrainstormingPanel: () => <div data-testid="brainstorming-panel" />,
}));
vi.mock('@/components/organizer/SpeakerOutreach/MarkContactedModal', () => ({
  default: () => <div data-testid="mark-contacted-modal" />,
}));

// --- Mock data hooks/services ---
const mockSpeakers = [
  { id: 's1', speakerName: 'Jane Doe', username: 'jane.doe', status: 'QUALITY_REVIEWED' },
];
vi.mock('@/hooks/useSpeakerPool', () => ({
  useSpeakerPool: () => ({ data: mockSpeakers, isLoading: false }),
  useSendInvitation: () => ({ mutateAsync: vi.fn() }),
  speakerPoolKeys: { list: (code: string) => ['speakerPool', code] },
}));
const mockEventData = {
  data: {
    date: '2026-09-01',
    sessions: [
      { sessionSlug: 'a', title: 'A', startTime: '2026-09-01T09:00:00Z', speakers: [] },
      { sessionSlug: 'b', title: 'B', startTime: null, speakers: [] },
    ],
  } as { date: string; sessions: unknown[] },
};
vi.mock('@/hooks/useEvents', () => ({
  useEvent: () => mockEventData,
}));
vi.mock('@/services/speakerStatusService', () => ({
  speakerStatusService: {
    getStatusSummary: vi.fn().mockResolvedValue({
      acceptedCount: 8,
      minSlotsRequired: 12,
      thresholdMet: false,
      acceptanceRate: 66.7,
      maxSlotsAllowed: 12,
    }),
  },
}));

import { EventSpeakersTab } from '../EventSpeakersTab';

const renderAt = (search = '') => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter initialEntries={[`/organizer/events/BAT54?tab=speakers${search}`]}>
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          <EventSpeakersTab eventCode="BAT54" />
        </I18nextProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
};

describe('EventSpeakersTab — Pool/Agenda/Slots sub-views (Epic 14 Phase C)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should_renderThreeWayToggle_when_loaded', async () => {
    renderAt();
    expect(await screen.findByTestId('pool-view-toggle')).toBeInTheDocument();
    expect(screen.getByTestId('agenda-view-toggle')).toBeInTheDocument();
    expect(screen.getByTestId('slots-view-toggle')).toBeInTheDocument();
  });

  it('should_defaultToPoolKanban_when_noViewParam', async () => {
    renderAt();
    expect(await screen.findByTestId('speaker-status-lanes')).toBeInTheDocument();
  });

  it('should_renderAgenda_when_viewAgenda', async () => {
    renderAt('&view=agenda');
    expect(await screen.findByTestId('sessions-table')).toBeInTheDocument();
  });

  it('should_renderSlots_when_viewSlots', async () => {
    renderAt('&view=slots');
    expect(await screen.findByTestId('slot-assignment')).toBeInTheDocument();
  });

  it('should_mapLegacyKanbanToPool_and_sessionsToAgenda', async () => {
    renderAt('&view=kanban');
    expect(await screen.findByTestId('speaker-status-lanes')).toBeInTheDocument();
    renderAt('&view=sessions');
    expect(await screen.findAllByTestId('sessions-table')).not.toHaveLength(0);
  });

  it('should_carryFocusSpeakerId_into_slots', async () => {
    renderAt('&view=slots&speakerId=jane.doe');
    const slot = await screen.findByTestId('slot-assignment');
    expect(slot).toHaveTextContent('focus:jane.doe');
  });

  it('should_showNeedsSlotSummary_when_agenda', async () => {
    renderAt('&view=agenda');
    // one of the two sessions has a null startTime → "1 of 2 ... need a slot"
    const summary = await screen.findByTestId('needs-slot-summary');
    expect(summary).toHaveTextContent(/1.*2/);
  });

  it('should_showNoSessionsYet_when_agendaWithZeroSessions', async () => {
    const original = mockEventData.data.sessions;
    mockEventData.data.sessions = [];
    try {
      renderAt('&view=agenda');
      const summary = await screen.findByTestId('needs-slot-summary');
      // Zero sessions must NOT read as "all slotted" — it's "no sessions yet".
      expect(summary).toHaveTextContent(/no sessions yet/i);
    } finally {
      mockEventData.data.sessions = original;
    }
  });

  it('should_jumpToSlots_when_arrangeSlotsClicked', async () => {
    const user = userEvent.setup();
    renderAt('&view=agenda');
    const jump = await screen.findByTestId('arrange-slots-button');
    await user.click(jump);
    await waitFor(() => expect(screen.getByTestId('slot-assignment')).toBeInTheDocument());
  });
});
