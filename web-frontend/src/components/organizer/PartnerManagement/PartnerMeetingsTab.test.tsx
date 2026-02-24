import { describe, it, expect, vi } from 'vitest';
import React from 'react';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PartnerMeetingsTab } from './PartnerMeetingsTab';

const renderWithProviders = (ui: React.ReactElement) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
};
import type { PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';

// Mock the hook
vi.mock('@/hooks/usePartnerMeetings', () => ({
  usePartnerMeetings: vi.fn(),
}));

const { usePartnerMeetings } = await import('@/hooks/usePartnerMeetings');

// ─── Test fixtures ─────────────────────────────────────────────────────────────

const makeMeeting = (overrides: Partial<PartnerMeetingDTO> = {}): PartnerMeetingDTO => ({
  id: 'meeting-1',
  eventCode: 'BATbern57',
  meetingType: 'SPRING',
  meetingDate: '2025-03-15',
  startTime: '12:00:00',
  endTime: '14:00:00',
  location: 'Bern Office',
  agenda: 'Q1 Partnership Review',
  notes: null,
  inviteSentAt: null,
  createdBy: 'organizer',
  createdAt: '2025-01-01T10:00:00Z',
  updatedAt: '2025-01-01T10:00:00Z',
  ...overrides,
});

describe('PartnerMeetingsTab', () => {
  const mockCompanyName = 'GoogleZH';

  // Test 5.1: should_renderMeetingsTab_when_tabActivated
  it('should render meetings tab when tab activated', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    renderWithProviders(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByRole('heading', { name: /^meetings$/i, level: 6 })).toBeInTheDocument();
  });

  // Test 5.2: should_displayMeetingsList_when_meetingsLoaded
  it('should display meetings list when meetings loaded', () => {
    const mockMeetings: PartnerMeetingDTO[] = [
      makeMeeting({ id: 'meeting-1', agenda: 'Q1 Partnership Review', location: 'Bern Office' }),
      makeMeeting({
        id: 'meeting-2',
        meetingType: 'AUTUMN',
        agenda: 'Strategic Planning Session',
        location: 'Virtual',
      }),
    ];

    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: mockMeetings,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    renderWithProviders(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText('Q1 Partnership Review')).toBeInTheDocument();
    expect(screen.getByText('Strategic Planning Session')).toBeInTheDocument();
    expect(screen.getByText(/bern office/i)).toBeInTheDocument();
    expect(screen.getByText(/virtual/i)).toBeInTheDocument();
  });

  // Test 5.3: should_displayInviteStatus_when_inviteSent
  // Updated for Story 8.3: rsvpStatus removed; inviteSentAt is the new invite-sent indicator
  it('should display Invite Sent chip when invite was sent', () => {
    const mockMeetings: PartnerMeetingDTO[] = [
      makeMeeting({ id: 'meeting-1', inviteSentAt: '2025-03-01T10:00:00Z' }),
    ];

    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: mockMeetings,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    renderWithProviders(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText(/invite sent/i)).toBeInTheDocument();
  });

  // Test 5.7: should_formatMeetingDate_when_displayed
  it('should format meeting date when displayed', () => {
    const mockMeetings: PartnerMeetingDTO[] = [
      makeMeeting({ id: 'meeting-1', meetingDate: '2025-03-15' }),
    ];

    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: mockMeetings,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    renderWithProviders(<PartnerMeetingsTab companyName={mockCompanyName} />);

    // Should display formatted date (e.g., "Mar 15, 2025")
    expect(screen.getByText(/mar 15, 2025/i)).toBeInTheDocument();
  });

  // Test 5.8: should_displayEmptyState_when_noMeetings
  it('should display empty state when no meetings', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    renderWithProviders(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText(/no meetings scheduled/i)).toBeInTheDocument();
  });

  // Test 5.9: should_displayLoadingState_when_fetchingMeetings
  it('should display loading state when fetching meetings', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
      refetch: vi.fn(),
    } as any);

    renderWithProviders(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  // Test 5.10: should_displayErrorState_when_fetchFails
  it('should display error state when fetch fails', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch meetings'),
      refetch: vi.fn(),
    } as any);

    renderWithProviders(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText(/failed to load meetings/i)).toBeInTheDocument();
  });
});
