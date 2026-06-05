/**
 * PartnerMeetingsPage Tests
 * Story 8.3: Partner Meeting Coordination — AC5, 7, 8
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PartnerMeetingsPage from './PartnerMeetingsPage';

// Mock the hook so tests don't make real HTTP calls
vi.mock('@/hooks/usePartnerMeetings', () => ({
  usePartnerMeetings: vi.fn(),
}));

// Mock the child components (tested separately)
vi.mock('./CreateMeetingDialog', () => ({
  default: ({ open, onClose }: { open: boolean; onClose: () => void }) =>
    open ? (
      <div data-testid="create-meeting-dialog">
        <button onClick={onClose}>Close</button>
      </div>
    ) : null,
}));

vi.mock('./EditMeetingDialog', () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="edit-meeting-dialog" /> : null,
}));

vi.mock('./MeetingDetailPanel', () => ({
  default: ({ meeting }: { meeting: { id: string } }) => (
    <div data-testid={`meeting-detail-panel-${meeting.id}`}>Detail Panel</div>
  ),
}));

/**
 * Deterministic matchMedia mock keyed on a simulated viewport width — parses the
 * max/min-width out of each MUI breakpoint query so useBreakpoints().isMobile
 * (down('md') === max-width:899.95px) flips at 375px vs 1280px.
 * Mirrors PartnerCreateEditModal.test.tsx.
 */
const installMatchMediaForWidth = (viewportWidth: number) => {
  window.matchMedia = vi.fn((query: string) => {
    const maxMatch = /max-width:\s*([\d.]+)px/.exec(query);
    const minMatch = /min-width:\s*([\d.]+)px/.exec(query);
    let matches = false;
    if (maxMatch) matches = viewportWidth <= parseFloat(maxMatch[1]);
    else if (minMatch) matches = viewportWidth >= parseFloat(minMatch[1]);
    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    };
  }) as unknown as typeof window.matchMedia;
};

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      const map: Record<string, string> = {
        'meetings.title': 'Partner Meetings',
        'meetings.create': 'Create Meeting',
        'meetings.noMeetings': 'No partner meetings found',
        'meetings.error': 'Could not load meetings',
        'meetings.loading': 'Loading meetings...',
        'meetings.columns.event': 'Event',
        'meetings.columns.type': 'Type',
        'meetings.columns.date': 'Date',
        'meetings.columns.location': 'Location',
        'meetings.columns.inviteStatus': 'Invite',
        'meetings.columns.actions': 'Actions',
        'meetings.inviteSent': 'Invite Sent',
        'meetings.inviteNotSent': 'Invite Not Sent',
        'meetings.fields.type.spring': 'Spring Meeting',
        'meetings.fields.type.autumn': 'Autumn Meeting',
      };
      if (opts) {
        return map[key]?.replace(/\{\{(\w+)\}\}/g, (_, k: string) => String(opts[k] ?? '')) ?? key;
      }
      return map[key] ?? key;
    },
    i18n: { language: 'en' },
  }),
}));

const { usePartnerMeetings } = await import('@/hooks/usePartnerMeetings');

const MOCK_MEETINGS = [
  {
    id: 'meeting-uuid-1',
    eventCode: 'BATbern57',
    meetingType: 'SPRING' as const,
    meetingDate: '2026-05-14',
    startTime: '12:00:00',
    endTime: '14:00:00',
    location: 'Bern Congress Centre',
    agenda: 'Q2 Partnership Review',
    notes: null,
    inviteSentAt: null,
    createdBy: 'organizer',
    createdAt: '2026-02-22T10:00:00Z',
    updatedAt: '2026-02-22T10:00:00Z',
  },
  {
    id: 'meeting-uuid-2',
    eventCode: 'BATbern56',
    meetingType: 'AUTUMN' as const,
    meetingDate: '2025-11-06',
    startTime: '12:00:00',
    endTime: '14:00:00',
    location: 'Hotel Bern',
    agenda: 'Autumn Partner Discussion',
    notes: 'Great session.',
    inviteSentAt: '2025-10-01T08:00:00Z',
    createdBy: 'organizer',
    createdAt: '2025-09-15T10:00:00Z',
    updatedAt: '2025-10-01T08:00:00Z',
  },
];

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <PartnerMeetingsPage />
    </QueryClientProvider>
  );
}

describe('PartnerMeetingsPage', () => {
  const originalMatchMedia = window.matchMedia;
  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  // Test: loading state
  it('should_showLoadingState_when_dataIsLoading', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as any);

    renderPage();
    expect(screen.getByTestId('partner-meetings-loading')).toBeInTheDocument();
  });

  // Test: error state
  it('should_showErrorMessage_when_loadFails', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as any);

    renderPage();
    expect(screen.getByTestId('partner-meetings-error')).toBeInTheDocument();
    expect(screen.getByText('Could not load meetings')).toBeInTheDocument();
  });

  // Test: empty state
  it('should_showEmptyMessage_when_noMeetings', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    expect(screen.getByTestId('no-meetings-message')).toBeInTheDocument();
    expect(screen.getByText('No partner meetings found')).toBeInTheDocument();
  });

  // Test: renders meeting list
  it('should_renderMeetingList_when_meetingsLoaded', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    expect(screen.getByTestId('partner-meetings-table')).toBeInTheDocument();
    expect(screen.getByTestId('meeting-row-meeting-uuid-1')).toBeInTheDocument();
    expect(screen.getByTestId('meeting-row-meeting-uuid-2')).toBeInTheDocument();
    expect(screen.getByText('BATbern57')).toBeInTheDocument();
    expect(screen.getByText('BATbern56')).toBeInTheDocument();
  });

  // Test: table sits in a horizontally scrollable TableContainer (mobile responsiveness)
  it('should_wrapTableInScrollableContainer_when_meetingsLoaded', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    const table = screen.getByTestId('partner-meetings-table');
    // The Table must be wrapped in a MUI TableContainer that allows horizontal scroll.
    const container = table.closest('.MuiTableContainer-root') as HTMLElement;
    expect(container).toBeTruthy();

    const cssClass = Array.from(container.classList).find((c) => c.startsWith('css-'));
    let css = '';
    document.querySelectorAll('style').forEach((s) => {
      const text = s.textContent ?? '';
      if (cssClass && text.includes(`.${cssClass}`)) css += text + '\n';
    });
    expect(css).toMatch(/overflow-x:\s*auto/);
  });

  // Test: invite sent chip shows for meetings with inviteSentAt
  it('should_showInviteSentChip_when_inviteWasSent', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    // meeting-uuid-2 has inviteSentAt
    expect(screen.getByTestId('invite-sent-meeting-uuid-2')).toBeInTheDocument();
    // meeting-uuid-1 has no invite sent
    expect(screen.getByTestId('invite-not-sent-meeting-uuid-1')).toBeInTheDocument();
  });

  // Test: create meeting button opens dialog
  it('should_openCreateDialog_when_createButtonClicked', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    expect(screen.queryByTestId('create-meeting-dialog')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('create-meeting-btn'));
    expect(screen.getByTestId('create-meeting-dialog')).toBeInTheDocument();
  });

  // Test: clicking row expands detail panel
  it('should_expandDetailPanel_when_rowClicked', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    expect(screen.queryByTestId('meeting-detail-panel-meeting-uuid-1')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('meeting-row-meeting-uuid-1'));
    expect(screen.getByTestId('meeting-detail-panel-meeting-uuid-1')).toBeInTheDocument();
  });

  // Test: clicking same row again collapses it
  it('should_collapseDetailPanel_when_rowClickedAgain', async () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    fireEvent.click(screen.getByTestId('meeting-row-meeting-uuid-1'));
    expect(screen.getByTestId('meeting-detail-panel-meeting-uuid-1')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('meeting-row-meeting-uuid-1'));
    // Wait for Collapse unmountOnExit to remove the DOM node after transition
    await waitFor(() => {
      expect(screen.queryByTestId('meeting-detail-panel-meeting-uuid-1')).not.toBeInTheDocument();
    });
  });

  // Test: mobile renders meetings as cards (no table) at a phone viewport
  it('should_renderMeetingCards_when_mobileViewport', () => {
    installMatchMediaForWidth(375);
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    expect(screen.getByTestId('partner-meetings-cards')).toBeInTheDocument();
    expect(screen.queryByTestId('partner-meetings-table')).not.toBeInTheDocument();
    expect(screen.getByTestId('meeting-card-meeting-uuid-1')).toBeInTheDocument();
    expect(screen.getByTestId('meeting-card-meeting-uuid-2')).toBeInTheDocument();
  });

  // Test: tapping a mobile card reveals its detail panel and Edit/Delete actions
  it('should_expandCardDetail_when_mobileCardToggled', () => {
    installMatchMediaForWidth(375);
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    // Edit/Delete actions are present on the card
    expect(screen.getByTestId('edit-meeting-meeting-uuid-1')).toBeInTheDocument();
    expect(screen.getByTestId('delete-meeting-meeting-uuid-1')).toBeInTheDocument();

    expect(screen.queryByTestId('meeting-detail-panel-meeting-uuid-1')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('meeting-card-toggle-meeting-uuid-1'));
    expect(screen.getByTestId('meeting-detail-panel-meeting-uuid-1')).toBeInTheDocument();
  });

  // Test: desktop renders the table (not cards) at a wide viewport
  it('should_renderTable_when_desktopViewport', () => {
    installMatchMediaForWidth(1280);
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: MOCK_MEETINGS,
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    expect(screen.getByTestId('partner-meetings-table')).toBeInTheDocument();
    expect(screen.queryByTestId('partner-meetings-cards')).not.toBeInTheDocument();
  });

  // Test: page title visible (i18n AC7)
  it('should_renderPageTitle_in_english', () => {
    vi.mocked(usePartnerMeetings).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any);

    renderPage();
    expect(screen.getByText('Partner Meetings')).toBeInTheDocument();
  });
});
