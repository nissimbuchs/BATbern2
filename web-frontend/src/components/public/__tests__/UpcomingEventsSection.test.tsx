/**
 * UpcomingEventsSection Component Tests
 *
 * Regression (2026-06-11, Epic 7 testing): the section filtered only by date +
 * currentEventCode and rendered events that are NOT published (e.g. a CREATED event with
 * currentPublishedPhase NONE/null). The public homepage must only surface events the
 * organizer has actively published — mirror the published-phase whitelist EventCard uses.
 */

import { describe, test, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { UpcomingEventsSection } from '../UpcomingEventsSection';
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

// A published card mounts SpeakerSelfNominatePanel, which calls useAuth; stub it
// (this suite exercises the section's publication filter, not the nomination panel).
vi.mock('@/hooks/useAuth/useAuth', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}));

const useEventsMock = vi.fn();
vi.mock('@/hooks/useEvents', () => ({
  useEvents: () => useEventsMock(),
}));

// One year out, so both events are unambiguously "upcoming".
const FUTURE_DATE = '2099-07-10T00:00:00Z';

function makeEvent(
  eventCode: string,
  currentPublishedPhase: EventDetailUI['currentPublishedPhase'] | 'NONE'
): EventDetailUI {
  return {
    eventCode,
    title: `${eventCode} title`,
    date: FUTURE_DATE,
    currentPublishedPhase: currentPublishedPhase as EventDetailUI['currentPublishedPhase'],
    topic: { name: 'Architecture' },
    sessions: [],
  } as unknown as EventDetailUI;
}

describe('UpcomingEventsSection — publication filter', () => {
  test('renders published events and hides unpublished (CREATED / NONE phase) ones', () => {
    useEventsMock.mockReturnValue({
      data: {
        data: [
          makeEvent('BATbern73', 'SPEAKERS'), // published → shown
          makeEvent('BATbern55', 'NONE'), // created, not published → hidden
        ],
      },
      isLoading: false,
    });

    render(<UpcomingEventsSection currentEventCode="BATbern00" />);

    expect(screen.getByTestId('event-card-BATbern73')).toBeInTheDocument();
    expect(screen.queryByTestId('event-card-BATbern55')).not.toBeInTheDocument();
  });

  test('hides the whole section when no published upcoming events remain', () => {
    useEventsMock.mockReturnValue({
      data: { data: [makeEvent('BATbern55', 'NONE')] },
      isLoading: false,
    });

    const { container } = render(<UpcomingEventsSection currentEventCode="BATbern00" />);

    expect(container).toBeEmptyDOMElement();
  });
});
