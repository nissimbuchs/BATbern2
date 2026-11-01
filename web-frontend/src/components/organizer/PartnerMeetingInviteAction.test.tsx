/**
 * PartnerMeetingInviteAction tests (Story 16.4 AC6): the invite extracted from MeetingDetailPanel
 * keeps its confirmation step and reports a sent invite.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PartnerMeetingInviteAction from './PartnerMeetingInviteAction';
import type { PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';

const { sendInvite } = vi.hoisted(() => ({ sendInvite: vi.fn() }));

vi.mock('@/services/api/partnerMeetingsApi', () => ({
  sendInvite: (...args: unknown[]) => sendInvite(...args),
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const meeting = {
  id: 'meeting-1',
  eventCode: 'BATbern60',
  meetingType: 'AUTUMN',
  meetingDate: '2026-11-06',
  startTime: '12:00',
  endTime: '14:00',
  inviteSentAt: null,
  createdBy: 'organizer1',
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
} as PartnerMeetingDTO;

function renderAction(onSent = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <PartnerMeetingInviteAction meeting={meeting} onSent={onSent} />
    </QueryClientProvider>
  );
  return { onSent };
}

describe('PartnerMeetingInviteAction', () => {
  // Block body on purpose: a function returned from beforeEach is run by Vitest as teardown,
  // and mockResolvedValue() returns the mock itself, which would then be called once more.
  beforeEach(() => {
    sendInvite.mockReset().mockResolvedValue({});
  });

  it('sends nothing until the confirmation, then sends and calls onSent', async () => {
    const { onSent } = renderAction();
    expect(screen.getByTestId('invite-not-sent-chip-meeting-1')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('send-invite-btn-meeting-1'));
    expect(sendInvite).not.toHaveBeenCalled();

    fireEvent.click(screen.getByTestId('confirm-send-invite-btn'));
    await waitFor(() => expect(sendInvite).toHaveBeenCalledWith('meeting-1'));
    expect(onSent).toHaveBeenCalledTimes(1);
    expect(await screen.findByTestId('invite-success-alert-meeting-1')).toBeInTheDocument();
  });

  it('shows an error and does not report success when the invite could not be sent', async () => {
    sendInvite.mockRejectedValue({ response: { status: 502 } });
    const { onSent } = renderAction();

    fireEvent.click(screen.getByTestId('send-invite-btn-meeting-1'));
    fireEvent.click(screen.getByTestId('confirm-send-invite-btn'));

    expect(await screen.findByTestId('invite-error-alert-meeting-1')).toHaveTextContent(
      'meetings.inviteFailed'
    );
    expect(screen.queryByTestId('invite-success-alert-meeting-1')).not.toBeInTheDocument();
    expect(onSent).not.toHaveBeenCalled();
  });
});
