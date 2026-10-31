/**
 * PartnerMeetingsCompose tests (Story 16.4 AC6): this event's partner meetings only, or a link to
 * the partner meetings page when there is none.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { PartnerMeetingsCompose } from '../PartnerMeetingsCompose';

const { getMeetings } = vi.hoisted(() => ({ getMeetings: vi.fn() }));

vi.mock('@/services/api/partnerMeetingsApi', () => ({
  getMeetings: (...args: unknown[]) => getMeetings(...args),
}));
vi.mock('@/components/organizer/PartnerMeetingInviteAction', () => ({
  default: ({ meeting }: { meeting: { id: string } }) => (
    <div data-testid={`invite-action-${meeting.id}`} />
  ),
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}));

const meeting = (id: string, eventCode: string) => ({
  id,
  eventCode,
  meetingType: 'AUTUMN',
  meetingDate: '2026-11-06',
  startTime: '12:00',
  endTime: '14:00',
  createdBy: 'organizer1',
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
});

function renderCompose() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PartnerMeetingsCompose eventCode="BATbern60" />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('PartnerMeetingsCompose', () => {
  beforeEach(() => getMeetings.mockReset());

  it("lists only this event's meetings with the invite action", async () => {
    getMeetings.mockResolvedValue([meeting('m1', 'BATbern60'), meeting('m2', 'BATbern59')]);
    renderCompose();
    expect(await screen.findByTestId('invite-action-m1')).toBeInTheDocument();
    expect(screen.queryByTestId('invite-action-m2')).not.toBeInTheDocument();
  });

  it('says so and links to the partner meetings page when the event has no meeting', async () => {
    getMeetings.mockResolvedValue([meeting('m2', 'BATbern59')]);
    renderCompose();
    expect(await screen.findByTestId('partner-compose-empty')).toHaveTextContent(
      'eventPage.mail.partners.noMeeting'
    );
    expect(screen.getByRole('link')).toHaveAttribute('href', '/organizer/partner-meetings');
  });
});
