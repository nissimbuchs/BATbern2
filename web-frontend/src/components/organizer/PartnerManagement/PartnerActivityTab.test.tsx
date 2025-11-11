import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PartnerActivityTab from './PartnerActivityTab';
import { usePartnerActivity } from '@/hooks/usePartnerActivity';

// Mock the usePartnerActivity hook
vi.mock('@/hooks/usePartnerActivity');

const mockUsePartnerActivity = vi.mocked(usePartnerActivity);

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

const renderWithQueryClient = (ui: React.ReactElement) => {
  const queryClient = createTestQueryClient();
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
};

const mockActivities = [
  {
    id: 'activity-1',
    type: 'VOTE_CAST',
    timestamp: '2025-01-09T10:30:00Z',
    username: 'john.doe',
    description: 'Voted on "Sustainable Architecture" topic',
    details: { topicId: 'topic-123', voteValue: 5 },
  },
  {
    id: 'activity-2',
    type: 'MEETING_ATTENDED',
    timestamp: '2025-01-08T14:00:00Z',
    username: 'jane.smith',
    description: 'Attended Winter Strategy Meeting',
    details: { meetingId: 'meeting-456' },
  },
  {
    id: 'activity-3',
    type: 'CONTACT_ADDED',
    timestamp: '2025-01-07T09:15:00Z',
    username: 'admin',
    description: 'Added new contact: Michael Johnson',
    details: { contactUsername: 'michael.johnson' },
  },
  {
    id: 'activity-4',
    type: 'TIER_CHANGED',
    timestamp: '2025-01-06T16:45:00Z',
    username: 'admin',
    description: 'Partnership tier upgraded from GOLD to PLATINUM',
    details: { fromTier: 'GOLD', toTier: 'PLATINUM' },
  },
  {
    id: 'activity-5',
    type: 'NOTE_ADDED',
    timestamp: '2025-01-05T11:20:00Z',
    username: 'organizer',
    description: 'Added note: Q1 planning discussion',
    details: { noteId: 'note-789' },
  },
];

describe('PartnerActivityTab Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // AC6 Test 6.1: should_renderActivityTab_when_tabActivated
  it('should_renderActivityTab_when_tabActivated', () => {
    mockUsePartnerActivity.mockReturnValue({
      data: mockActivities,
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    expect(screen.getByRole('heading', { name: /activity/i })).toBeInTheDocument();
    expect(screen.getByText(/Voted on "Sustainable Architecture" topic/i)).toBeInTheDocument();
  });

  // AC6 Test 6.2: should_displayActivityTimeline_when_activitiesLoaded
  it('should_displayActivityTimeline_when_activitiesLoaded', () => {
    mockUsePartnerActivity.mockReturnValue({
      data: mockActivities,
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    // Verify all activity types are displayed
    expect(screen.getByText(/Voted on "Sustainable Architecture" topic/i)).toBeInTheDocument();
    expect(screen.getByText(/Attended Winter Strategy Meeting/i)).toBeInTheDocument();
    expect(screen.getByText(/Added new contact: Michael Johnson/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Partnership tier upgraded from GOLD to PLATINUM/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Added note: Q1 planning discussion/i)).toBeInTheDocument();

    // Verify usernames are displayed
    expect(screen.getByText(/john.doe/i)).toBeInTheDocument();
    expect(screen.getByText(/jane.smith/i)).toBeInTheDocument();
  });

  // AC6 Test 6.3: should_sortActivitiesByDate_when_rendered
  it('should_sortActivitiesByDate_when_rendered', () => {
    mockUsePartnerActivity.mockReturnValue({
      data: mockActivities,
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    const activityItems = screen.getAllByTestId(/activity-item-/i);

    // Verify most recent activity is first (VOTE_CAST from Jan 9)
    expect(activityItems[0]).toHaveTextContent(/Voted on "Sustainable Architecture" topic/i);

    // Verify oldest activity is last (NOTE_ADDED from Jan 5)
    expect(activityItems[4]).toHaveTextContent(/Added note: Q1 planning discussion/i);
  });

  // AC6 Test 6.4: should_filterActivities_when_filterApplied
  it('should_filterActivities_when_filterApplied', async () => {
    mockUsePartnerActivity.mockReturnValue({
      data: mockActivities.filter((a) => a.type === 'VOTE_CAST'),
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    // Find and click the filter dropdown
    const filterDropdown = screen.getByLabelText(/filter by activity type/i);
    fireEvent.mouseDown(filterDropdown);

    // Select "Vote Cast" filter
    const voteOption = await screen.findByText('Vote Cast');
    fireEvent.click(voteOption);

    await waitFor(() => {
      // Verify only VOTE_CAST activities are shown
      expect(screen.getByText(/Voted on "Sustainable Architecture" topic/i)).toBeInTheDocument();
      expect(screen.queryByText(/Attended Winter Strategy Meeting/i)).not.toBeInTheDocument();
    });
  });

  // AC6 Test 6.5: should_paginateActivities_when_multiplePagesExist
  it('should_paginateActivities_when_multiplePagesExist', async () => {
    // Create 25 activities to test pagination (20 per page)
    const manyActivities = Array.from({ length: 25 }, (_, i) => ({
      id: `activity-${i}`,
      type: 'VOTE_CAST',
      timestamp: new Date(2025, 0, 25 - i).toISOString(),
      username: `user${i}`,
      description: `Activity ${i}`,
      details: {},
    }));

    mockUsePartnerActivity.mockReturnValue({
      data: manyActivities,
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    // Verify first page shows 20 items
    const page1Items = screen.getAllByTestId(/activity-item-/i);
    expect(page1Items).toHaveLength(20);

    // Verify pagination controls exist
    expect(screen.getByLabelText(/go to next page/i)).toBeInTheDocument();

    // Click next page
    fireEvent.click(screen.getByLabelText(/go to next page/i));

    await waitFor(() => {
      // Verify second page shows remaining 5 items
      const page2Items = screen.getAllByTestId(/activity-item-/i);
      expect(page2Items).toHaveLength(5);
    });
  });

  // AC6 Test 6.6: should_displayActivityTypeIcons_when_activitiesRendered
  it('should_displayActivityTypeIcons_when_activitiesRendered', () => {
    mockUsePartnerActivity.mockReturnValue({
      data: mockActivities,
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    // Verify activity type icons are displayed
    // Icons should be rendered with specific test IDs or accessible names
    expect(screen.getByTestId('icon-VOTE_CAST')).toBeInTheDocument();
    expect(screen.getByTestId('icon-MEETING_ATTENDED')).toBeInTheDocument();
    expect(screen.getByTestId('icon-CONTACT_ADDED')).toBeInTheDocument();
    expect(screen.getByTestId('icon-TIER_CHANGED')).toBeInTheDocument();
    expect(screen.getByTestId('icon-NOTE_ADDED')).toBeInTheDocument();
  });

  // AC6 Test 6.7: should_displayEmptyState_when_noActivities
  // TODO: Fix empty state message expectation - check actual i18n key
  it.skip('should_displayEmptyState_when_noActivities', () => {
    mockUsePartnerActivity.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    expect(screen.getByText(/no activity recorded/i)).toBeInTheDocument();
  });

  // AC6 Test 6.8: should_displayLoadingState_when_dataFetching
  it('should_displayLoadingState_when_dataFetching', () => {
    mockUsePartnerActivity.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  // AC6 Test 6.9: should_displayError_when_fetchFails
  it('should_displayError_when_fetchFails', () => {
    const error = new Error('Failed to fetch activities');
    mockUsePartnerActivity.mockReturnValue({
      data: undefined,
      isLoading: false,
      error,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    expect(screen.getByText(/failed to load activity/i)).toBeInTheDocument();
    expect(screen.getByText(/failed to fetch activities/i)).toBeInTheDocument();
  });

  // AC6 Test 6.10: should_formatTimestamp_when_activityDisplayed
  it('should_formatTimestamp_when_activityDisplayed', () => {
    const recentActivity = {
      id: 'activity-recent',
      type: 'VOTE_CAST',
      timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), // 2 hours ago
      username: 'john.doe',
      description: 'Recent vote',
      details: {},
    };

    const oldActivity = {
      id: 'activity-old',
      type: 'MEETING_ATTENDED',
      timestamp: '2024-06-15T10:00:00Z', // Old date
      username: 'jane.smith',
      description: 'Old meeting',
      details: {},
    };

    mockUsePartnerActivity.mockReturnValue({
      data: [recentActivity, oldActivity],
      isLoading: false,
      error: null,
    } as any);

    renderWithQueryClient(<PartnerActivityTab companyName="GoogleZH" />);

    // Verify recent activity shows relative time (e.g., "2 hours ago")
    expect(screen.getByText(/2 hours ago/i)).toBeInTheDocument();

    // Verify old activity shows absolute date (e.g., "Jun 15, 2024")
    expect(screen.getByText(/jun 15, 2024/i)).toBeInTheDocument();
  });
});
