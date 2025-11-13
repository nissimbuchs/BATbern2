import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PartnerMeetingsTab } from './PartnerMeetingsTab';

// Mock the hook
vi.mock('@/hooks/usePartnerMeetings', () => ({
  usePartnerMeetings: vi.fn(),
}));

const { usePartnerMeetings } = await import('@/hooks/usePartnerMeetings');

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

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByRole('heading', { name: /^meetings$/i, level: 6 })).toBeInTheDocument();
  });

  // Test 5.2: should_displayMeetingsList_when_meetingsLoaded
  it('should display meetings list when meetings loaded', () => {
    const mockMeetings = [
      {
        id: 'meeting-1',
        meetingType: 'SEASONAL',
        scheduledDate: '2025-03-15T10:00:00Z',
        location: 'Bern Office',
        agenda: 'Q1 Partnership Review',
        rsvpStatus: 'CONFIRMED',
      },
      {
        id: 'meeting-2',
        meetingType: 'STRATEGIC',
        scheduledDate: '2025-06-20T14:00:00Z',
        location: 'Virtual',
        agenda: 'Strategic Planning Session',
        rsvpStatus: 'PENDING',
      },
    ];

    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: mockMeetings,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText('Q1 Partnership Review')).toBeInTheDocument();
    expect(screen.getByText('Strategic Planning Session')).toBeInTheDocument();
    expect(screen.getByText(/bern office/i)).toBeInTheDocument();
    expect(screen.getByText(/virtual/i)).toBeInTheDocument();
  });

  // Test 5.3: should_displayRSVPStatus_when_meetingHasRSVP
  it('should display RSVP status when meeting has RSVP', () => {
    const mockMeetings = [
      {
        id: 'meeting-1',
        meetingType: 'SEASONAL',
        scheduledDate: '2025-03-15T10:00:00Z',
        location: 'Bern Office',
        agenda: 'Q1 Partnership Review',
        rsvpStatus: 'CONFIRMED',
      },
      {
        id: 'meeting-2',
        meetingType: 'STRATEGIC',
        scheduledDate: '2025-06-20T14:00:00Z',
        location: 'Virtual',
        agenda: 'Strategic Planning Session',
        rsvpStatus: 'PENDING',
      },
    ];

    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: mockMeetings,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText(/confirmed/i)).toBeInTheDocument();
    expect(screen.getByText(/pending/i)).toBeInTheDocument();
  });

  // Test 5.4: should_showEpic8Message_when_fullFeaturesDeferred
  // TODO: Enable when Epic 8 placeholder messaging is implemented
  it.skip('should show Epic 8 message when full features deferred', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText(/full meeting coordination coming in epic 8/i)).toBeInTheDocument();
  });

  // Test 5.5: should_disableScheduleButton_when_epic8Deferred
  // TODO: Enable when schedule button is implemented
  it.skip('should disable schedule button when Epic 8 deferred', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

    const scheduleButton = screen.getByRole('button', { name: /schedule new meeting/i });
    expect(scheduleButton).toBeDisabled();
  });

  // Test 5.6: should_displayMeetingMaterials_when_available
  it('should display meeting materials when available', () => {
    const mockMeetings = [
      {
        id: 'meeting-1',
        meetingType: 'SEASONAL',
        scheduledDate: '2025-03-15T10:00:00Z',
        location: 'Bern Office',
        agenda: 'Q1 Partnership Review',
        rsvpStatus: 'CONFIRMED',
        materials: [
          { name: 'Agenda Document', url: 'https://example.com/agenda.pdf' },
          { name: 'Presentation Slides', url: 'https://example.com/slides.pdf' },
        ],
      },
    ];

    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: mockMeetings,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText('Agenda Document')).toBeInTheDocument();
    expect(screen.getByText('Presentation Slides')).toBeInTheDocument();
  });

  // Test 5.7: should_formatMeetingDate_when_displayed
  it('should format meeting date when displayed', () => {
    const mockMeetings = [
      {
        id: 'meeting-1',
        meetingType: 'SEASONAL',
        scheduledDate: '2025-03-15T10:00:00Z',
        location: 'Bern Office',
        agenda: 'Q1 Partnership Review',
        rsvpStatus: 'CONFIRMED',
      },
    ];

    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: mockMeetings,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

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

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

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

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

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

    render(<PartnerMeetingsTab companyName={mockCompanyName} />);

    expect(screen.getByText(/failed to load meetings/i)).toBeInTheDocument();
  });
});
