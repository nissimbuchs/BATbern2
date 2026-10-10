/**
 * UpcomingEventsSection Component Tests
 *
 * History: 2026-06-11 the section rendered unpublished events because the generic GET /events
 * list returned every event and the component filtered client-side. Since the Public Events read
 * model (2026-10-07) the server selects published upcoming events (scope=upcoming) and shapes
 * their sessions/speakers; the component only skips the event already featured in the hero.
 */

import { describe, test, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { UpcomingEventsSection } from '../UpcomingEventsSection';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventDetailUI } from '@/types/event.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// useMyRegistration is called per-card; stub it to "no registration".
vi.mock('@/hooks/useMyRegistration', () => ({
  useMyRegistration: () => ({ data: undefined }),
}));

// A TOPIC-phase card mounts SpeakerSelfNominatePanel, which calls useAuth; stub it.
vi.mock('@/hooks/useAuth/useAuth', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}));

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: { getPublicEvents: vi.fn() },
}));

const FUTURE_DATE = '2099-07-10T00:00:00Z';

function makeEvent(eventCode: string): EventDetailUI {
  return {
    eventCode,
    title: `${eventCode} title`,
    date: FUTURE_DATE,
    currentPublishedPhase: 'SPEAKERS',
    topic: { name: 'Architecture' },
    sessions: [],
    speakers: [{ username: 'jane.doe', firstName: 'Jane', lastName: 'Doe' }],
  } as unknown as EventDetailUI;
}

describe('UpcomingEventsSection', () => {
  beforeEach(() => {
    vi.mocked(eventApiClient.getPublicEvents).mockReset();
  });

  test('should request the upcoming scope from the public read model', async () => {
    vi.mocked(eventApiClient.getPublicEvents).mockResolvedValue({
      data: [makeEvent('BATbern73')],
      pagination: {
        page: 1,
        limit: 5,
        totalItems: 1,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    } as never);

    render(<UpcomingEventsSection currentEventCode="BATbern00" />);

    expect(await screen.findByTestId('event-card-BATbern73')).toBeInTheDocument();
    expect(eventApiClient.getPublicEvents).toHaveBeenCalledWith(
      expect.objectContaining({ scope: 'upcoming' })
    );
  });

  test('should skip the event already featured in the hero', async () => {
    vi.mocked(eventApiClient.getPublicEvents).mockResolvedValue({
      data: [makeEvent('BATbern60'), makeEvent('BATbern61')],
      pagination: {
        page: 1,
        limit: 5,
        totalItems: 2,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    } as never);

    render(<UpcomingEventsSection currentEventCode="BATbern60" />);

    expect(await screen.findByTestId('event-card-BATbern61')).toBeInTheDocument();
    expect(screen.queryByTestId('event-card-BATbern60')).not.toBeInTheDocument();
  });

  test('should render nothing when no upcoming events are published', async () => {
    vi.mocked(eventApiClient.getPublicEvents).mockResolvedValue({
      data: [],
      pagination: {
        page: 1,
        limit: 5,
        totalItems: 0,
        totalPages: 0,
        hasNext: false,
        hasPrev: false,
      },
    } as never);

    const { container } = render(<UpcomingEventsSection currentEventCode="BATbern00" />);

    await waitFor(() => expect(eventApiClient.getPublicEvents).toHaveBeenCalled());
    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });
});
