/**
 * EventSettingsTab Component Tests
 *
 * TDD Tests for the Event Moderator section in the settings tab.
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import EventSettingsTab from './EventSettingsTab';

// Mock hooks
const mockMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();

vi.mock('@/hooks/useEvents', () => ({
  useUpdateEvent: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  }),
  useDeleteEvent: () => ({
    mutateAsync: mockDeleteMutateAsync,
    isPending: false,
  }),
}));

// Mock OrganizerSelect
vi.mock('@/components/shared/OrganizerSelect/OrganizerSelect', () => ({
  OrganizerSelect: ({
    value,
    onChange,
    disabled,
    'data-testid': testId,
  }: {
    value: string;
    onChange: (v: string) => void;
    disabled?: boolean;
    'data-testid'?: string;
  }) => (
    <select
      data-testid={testId ?? 'moderator-select'}
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="john.doe">John Doe</option>
      <option value="jane.smith">Jane Smith</option>
    </select>
  ),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (_key: string, fallback?: string) => fallback ?? _key,
  }),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => vi.fn() };
});

const mockEvent = {
  eventCode: 'BAT-2024-01',
  eventNumber: 42,
  title: 'Test Event',
  subtitle: 'Test Subtitle',
  date: '2024-06-15T18:00:00Z',
  registrationDeadline: '2024-06-10T23:59:59Z',
  venueName: 'Test Venue',
  venueAddress: 'Test Address',
  venueCapacity: 200,
  organizerUsername: 'john.doe',
  currentWorkflowState: 'DRAFT' as const,
  workflowHistory: [],
  registrationCount: 0,
  attendanceCount: 0,
  currentAttendeeCount: 0,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-15T10:30:00Z',
};

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('EventSettingsTab — Moderator Section', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the Event Moderator section heading', () => {
    renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT-2024-01" />);
    expect(screen.getByText('Event Moderator')).toBeInTheDocument();
  });

  it('pre-selects the current organizerUsername in the dropdown', () => {
    renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT-2024-01" />);
    const select = screen.getByTestId('moderator-select') as HTMLSelectElement;
    expect(select.value).toBe('john.doe');
  });

  it('calls updateEvent mutation with new organizer on change', async () => {
    mockMutateAsync.mockResolvedValueOnce({});
    renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT-2024-01" />);

    const select = screen.getByTestId('moderator-select');
    await userEvent.selectOptions(select, 'jane.smith');

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({
        eventCode: 'BAT-2024-01',
        data: { organizerUsername: 'jane.smith' },
      });
    });
  });

  it('shows success snackbar after a successful update', async () => {
    mockMutateAsync.mockResolvedValueOnce({});
    renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT-2024-01" />);

    const select = screen.getByTestId('moderator-select');
    await userEvent.selectOptions(select, 'jane.smith');

    await waitFor(() => {
      expect(screen.getByText('Moderator updated successfully')).toBeInTheDocument();
    });
  });

  it('shows error alert and reverts selection on failed update', async () => {
    mockMutateAsync.mockRejectedValueOnce(new Error('Network error'));
    renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT-2024-01" />);

    const select = screen.getByTestId('moderator-select') as HTMLSelectElement;
    await userEvent.selectOptions(select, 'jane.smith');

    await waitFor(() => {
      expect(screen.getByText('Network error')).toBeInTheDocument();
    });
    // Value reverts to previous
    expect(select.value).toBe('john.doe');
  });

  it('does not render the old "Event Information" section', () => {
    renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT-2024-01" />);
    expect(screen.queryByText('Event Information')).not.toBeInTheDocument();
  });
});
