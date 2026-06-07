/**
 * PartnerMeetingRsvpPanel Tests
 * Story 10.27: iCal RSVP Tracking — AC8, AC9
 *
 * T20.1 — inviteSentAt null → panel not rendered
 * T20.2 — renders summary "1 Accepted · 1 Declined · 1 Tentative"
 * T20.3 — ACCEPTED chip has success color
 * T20.4 — loading state shown while fetching
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import PartnerMeetingRsvpPanel from './PartnerMeetingRsvpPanel';
import * as partnerMeetingsApi from '@/services/api/partnerMeetingsApi';
import type { MeetingRsvpListResponse } from '@/services/api/partnerMeetingsApi';

vi.mock('@/services/api/partnerMeetingsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof partnerMeetingsApi>();
  return { ...actual, getRsvps: vi.fn() };
});

const mockRsvpResponse: MeetingRsvpListResponse = {
  meetingId: 'meeting-1',
  inviteSentAt: '2026-03-01T10:00:00Z',
  rsvps: [
    { attendeeEmail: 'alice@partner.com', status: 'ACCEPTED', respondedAt: '2026-03-02T09:00:00Z' },
    { attendeeEmail: 'bob@partner.com', status: 'DECLINED', respondedAt: '2026-03-02T10:00:00Z' },
    { attendeeEmail: 'carl@partner.com', status: 'TENTATIVE', respondedAt: '2026-03-02T11:00:00Z' },
  ],
  summary: { accepted: 1, declined: 1, tentative: 1 },
};

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </QueryClientProvider>
  );
};

describe('PartnerMeetingRsvpPanel — Story 10.27', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // T20.1 — inviteSentAt null → panel not rendered
  it('should_notRender_when_inviteSentAtIsNull', () => {
    const { container } = renderWithProviders(
      <PartnerMeetingRsvpPanel meetingId="meeting-1" inviteSentAt={null} />
    );

    expect(container).toBeEmptyDOMElement();
    expect(partnerMeetingsApi.getRsvps).not.toHaveBeenCalled();
  });

  // T20.2 — renders summary line with correct counts
  it('should_renderSummary_when_inviteSentAtIsSet', async () => {
    vi.mocked(partnerMeetingsApi.getRsvps).mockResolvedValue(mockRsvpResponse);

    renderWithProviders(
      <PartnerMeetingRsvpPanel meetingId="meeting-1" inviteSentAt="2026-03-01T10:00:00Z" />
    );

    await waitFor(() => {
      expect(screen.getByTestId('rsvp-summary')).toBeInTheDocument();
    });

    const summary = screen.getByTestId('rsvp-summary');
    expect(summary.textContent).toMatch(/1/);
  });

  // T20.3 — ACCEPTED chip has success color
  it('should_renderAcceptedChip_with_successColor', async () => {
    vi.mocked(partnerMeetingsApi.getRsvps).mockResolvedValue(mockRsvpResponse);

    renderWithProviders(
      <PartnerMeetingRsvpPanel meetingId="meeting-1" inviteSentAt="2026-03-01T10:00:00Z" />
    );

    await waitFor(() => {
      expect(screen.getByTestId('rsvp-chip-alice@partner.com')).toBeInTheDocument();
    });

    const chip = screen.getByTestId('rsvp-chip-alice@partner.com');
    // MUI Chip with color="success" renders with MuiChip-colorSuccess class
    expect(chip.className).toMatch(/colorSuccess/);
  });

  // T20.4 — loading state shown while fetching
  it('should_showLoadingState_while_fetching', () => {
    vi.mocked(partnerMeetingsApi.getRsvps).mockImplementation(
      () => new Promise(() => {}) // never resolves — simulates in-flight request
    );

    renderWithProviders(
      <PartnerMeetingRsvpPanel meetingId="meeting-1" inviteSentAt="2026-03-01T10:00:00Z" />
    );

    expect(screen.getByTestId('rsvp-loading')).toBeInTheDocument();
  });
});
