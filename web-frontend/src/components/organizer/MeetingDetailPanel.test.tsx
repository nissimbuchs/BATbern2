/**
 * MeetingDetailPanel tests (Story 16.9 AC5): the panel lists the mails of its meeting, using the
 * event's "What went out" list scoped to the meeting.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MeetingDetailPanel from './MeetingDetailPanel';
import type { PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';

const { mailSentList } = vi.hoisted(() => ({ mailSentList: vi.fn() }));

vi.mock('./EventPage/communications/MailSentList', () => ({
  MailSentList: (props: Record<string, unknown>) => {
    mailSentList(props);
    return <div data-testid="mail-sent-list-stub" />;
  },
}));
vi.mock('./PartnerMeetingInviteAction', () => ({ default: () => null }));
vi.mock('./PartnerMeetingRsvpPanel', () => ({ default: () => null }));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}));

const meeting = {
  id: '3f2a9c1e-7b4d-4e2a-9c1e-7b4d4e2a9c1e',
  eventCode: 'BATbern60',
  meetingType: 'SPRING',
  meetingDate: '2026-05-14',
  startTime: '12:00:00',
  endTime: '14:00:00',
  inviteSentAt: '2026-10-28T10:00:00Z',
} as unknown as PartnerMeetingDTO;

describe('MeetingDetailPanel', () => {
  it('should list the mails of this meeting when the panel is open', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MeetingDetailPanel meeting={meeting} />
      </QueryClientProvider>
    );

    expect(screen.getByText('meetings.mails.title')).toBeInTheDocument();
    expect(screen.getByTestId('mail-sent-list-stub')).toBeInTheDocument();
    expect(mailSentList).toHaveBeenCalledWith(
      expect.objectContaining({
        eventCode: 'BATbern60',
        audience: 'PARTNERS',
        reference: 'partner-meeting:3f2a9c1e-7b4d-4e2a-9c1e-7b4d4e2a9c1e',
      })
    );
  });
});
