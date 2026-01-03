/**
 * EventSettingsTab Component Tests (Story 5.6)
 *
 * Tests for the event settings, notifications, and danger zone tab.
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventSettingsTab } from '../EventSettingsTab';
import type { Event } from '@/types/event.types';

// Mock event API client
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    deleteEvent: vi.fn(() => Promise.resolve()),
    cancelEvent: vi.fn(() => Promise.resolve()),
  },
}));

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock event data
const mockEvent: Event = {
  eventId: '123e4567-e89b-12d3-a456-426614174000',
  eventCode: 'BAT54',
  eventNumber: 54,
  title: 'Spring Conference 2025',
  description: 'Advanced microservices architecture',
  date: '2025-03-15T09:00:00Z',
  registrationDeadline: '2025-03-10T23:59:59Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3, 3013 Bern',
  venueCapacity: 200,
  status: 'published',
  workflowState: 'SPEAKER_CONFIRMATION',
  organizerUsername: 'john.doe',
  currentAttendeeCount: 0,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
};

const mockEventWithAttendees: Event = {
  ...mockEvent,
  currentAttendeeCount: 87,
};

describe('EventSettingsTab Component (Story 5.6)', () => {
  let queryClient: QueryClient;

  // Test wrapper with providers
  const renderWithProviders = (ui: React.ReactElement) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
        </BrowserRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('Rendering', () => {
    it('renders all sections with expected content', () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      // Event Information Section
      expect(screen.getByText(/Event Information/i)).toBeInTheDocument();
      expect(screen.getByText(/Event Number/i)).toBeInTheDocument();
      expect(screen.getByText('54')).toBeInTheDocument();
      expect(screen.getByText(/Event Code/i)).toBeInTheDocument();
      expect(screen.getByText('BAT54')).toBeInTheDocument();
      expect(screen.getByText(/Created By/i)).toBeInTheDocument();
      expect(screen.getByText('john.doe')).toBeInTheDocument();
      expect(screen.getByText(/Last Modified/i)).toBeInTheDocument();
      expect(screen.getByText(/January 15, 2025|15 January 2025/i)).toBeInTheDocument();

      // Notifications Section
      expect(screen.getByText(/^Notifications$/i)).toBeInTheDocument();
      expect(screen.getByText(/active automations/i)).toBeInTheDocument();
      expect(screen.getByText(/Speaker deadline reminders/i)).toBeInTheDocument();
      expect(screen.getByText(/Registration confirmation emails/i)).toBeInTheDocument();
      expect(screen.getByText(/Final agenda distribution/i)).toBeInTheDocument();
      expect(screen.getByText(/Event day check-in reminders/i)).toBeInTheDocument();
      expect(screen.getByText(/3 days before deadline/i)).toBeInTheDocument();
      expect(screen.getByText(/Immediate on registration/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Manage All Notifications/i })).toBeInTheDocument();
      expect(screen.getByText(/Mar 1, 2025|1 Mar 2025/i)).toBeInTheDocument();

      // Danger Zone Section
      expect(screen.getByText(/Danger Zone/i)).toBeInTheDocument();
      expect(screen.getByText(/These actions are irreversible/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Cancel Event/i })).toBeInTheDocument();
      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      expect(deleteButton).toBeInTheDocument();
      expect(deleteButton).not.toBeDisabled();
    });

    it.skip('displays notification switches when rendered', () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const switches = screen.getAllByRole('checkbox');
      expect(switches.length).toBeGreaterThanOrEqual(4);
    });

    it.skip('toggles notification when switch clicked', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const switches = screen.getAllByRole('checkbox');
      const firstSwitch = switches[0];
      expect(firstSwitch).toBeChecked();

      fireEvent.click(firstSwitch);

      await waitFor(() => {
        expect(firstSwitch).not.toBeChecked();
      });
    });
  });

  describe('Conditional Rendering - Delete Button State', () => {
    it('enables delete when no attendees', () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      expect(deleteButton).not.toBeDisabled();
    });

    it('disables delete and shows warning when has attendees', () => {
      renderWithProviders(<EventSettingsTab event={mockEventWithAttendees} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      expect(deleteButton).toBeDisabled();
      expect(screen.getByText(/Cannot delete event with registrations/i)).toBeInTheDocument();
    });
  });

  describe('Cancel Event Dialog', () => {
    it('should_openCancelDialog_when_cancelButtonClicked', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const cancelButton = screen.getByRole('button', { name: /Cancel Event/i });
      fireEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.getByText(/Cancel Event\?/i)).toBeInTheDocument();
      });
    });

    it('should_displayCancelConfirmationText_when_dialogOpen', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const cancelButton = screen.getByRole('button', { name: /Cancel Event/i });
      fireEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.getByText(/This will cancel the event and notify/i)).toBeInTheDocument();
      });
    });

    it('should_closeCancelDialog_when_cancelClicked', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const cancelButton = screen.getByRole('button', { name: /Cancel Event/i });
      fireEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.getByText(/Cancel Event\?/i)).toBeInTheDocument();
      });

      // Click cancel in dialog
      const dialogCancelButton = screen.getByRole('button', { name: /^Cancel$/i });
      fireEvent.click(dialogCancelButton);

      await waitFor(() => {
        expect(screen.queryByText(/Cancel Event\?/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Delete Event Dialog', () => {
    it('should_openDeleteDialog_when_deleteButtonClicked', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      fireEvent.click(deleteButton);

      await waitFor(() => {
        expect(screen.getByText(/Delete Event\?/i)).toBeInTheDocument();
      });
    });

    it('should_displayEventTitleInDeleteConfirmation_when_dialogOpen', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      fireEvent.click(deleteButton);

      await waitFor(() => {
        expect(screen.getByText(/Spring Conference 2025/i)).toBeInTheDocument();
      });
    });

    it('should_navigateToEvents_when_deleteConfirmed', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      fireEvent.click(deleteButton);

      await waitFor(() => {
        expect(screen.getByText(/Delete Event\?/i)).toBeInTheDocument();
      });

      // Confirm delete
      const confirmButton = screen.getByRole('button', { name: /Yes, Delete Event/i });
      fireEvent.click(confirmButton);

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/organizer/events');
      });
    });
  });

  describe('Edge Cases', () => {
    it('should_handleMissingCreatedAt_gracefully', () => {
      const eventWithoutDates = { ...mockEvent, createdAt: undefined };
      renderWithProviders(<EventSettingsTab event={eventWithoutDates} eventCode="BAT54" />);

      expect(screen.getAllByText('-').length).toBeGreaterThan(0);
    });

    it('should_handleMissingUpdatedAt_gracefully', () => {
      const eventWithoutDates = { ...mockEvent, updatedAt: undefined };
      renderWithProviders(<EventSettingsTab event={eventWithoutDates} eventCode="BAT54" />);

      expect(screen.getAllByText('-').length).toBeGreaterThan(0);
    });
  });
});
