/**
 * EventCommunicationsContainer tests (Epic 14, Phase E — Story 14.E.1/14.E.2)
 *
 * The container is a thin audience switch; each child has its own tests, so we
 * stub them and assert the 4-audience rail + that switching mounts the right one.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { EventCommunicationsContainer } from '../EventCommunicationsContainer';
import type { Event } from '@/types/event.types';

vi.mock('../EventNewsletterTab', () => ({
  EventNewsletterTab: () => <div data-testid="stub-newsletter" />,
}));
vi.mock('../EventRegistrantNoticesTab', () => ({
  EventRegistrantNoticesTab: () => <div data-testid="stub-registrant" />,
}));
vi.mock('../SpeakerBulkComms', () => ({
  SpeakerBulkComms: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="stub-speakers">{eventCode}</div>
  ),
}));
vi.mock('../EventVenueTab', () => ({
  EventVenueTab: () => <div data-testid="stub-venue" />,
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, def?: string) => def ?? key,
  }),
}));

const mockEvent = { eventCode: 'BAT54', title: 'Spring Conf' } as Event;

function renderContainer() {
  return render(<EventCommunicationsContainer event={mockEvent} eventCode="BAT54" />);
}

describe('EventCommunicationsContainer', () => {
  it('renders all four audiences in the switch', () => {
    renderContainer();
    expect(screen.getByTestId('comms-subtab-newsletter')).toBeInTheDocument();
    expect(screen.getByTestId('comms-subtab-registrant-notices')).toBeInTheDocument();
    expect(screen.getByTestId('comms-subtab-speakers')).toBeInTheDocument();
    expect(screen.getByTestId('comms-subtab-venue')).toBeInTheDocument();
  });

  it('defaults to the Newsletter audience', () => {
    renderContainer();
    expect(screen.getByTestId('stub-newsletter')).toBeInTheDocument();
    expect(screen.queryByTestId('stub-speakers')).not.toBeInTheDocument();
  });

  it('switches to the Speakers audience and mounts SpeakerBulkComms with the event code', () => {
    renderContainer();
    fireEvent.click(screen.getByTestId('comms-subtab-speakers'));
    const speakers = screen.getByTestId('stub-speakers');
    expect(speakers).toBeInTheDocument();
    expect(speakers).toHaveTextContent('BAT54');
    expect(screen.queryByTestId('stub-newsletter')).not.toBeInTheDocument();
  });

  it('switches to the Venue & Caterer audience', () => {
    renderContainer();
    fireEvent.click(screen.getByTestId('comms-subtab-venue'));
    expect(screen.getByTestId('stub-venue')).toBeInTheDocument();
  });
});
