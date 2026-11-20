/**
 * EventSettingsTab Component Tests (Story 5.6)
 *
 * Tests for the event settings, notifications, and danger zone tab.
 */

import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventSettingsTab } from '../EventSettingsTab';
import type { Event } from '@/types/event.types';
import { anEvent, aUser } from '@/test/fixtures';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';

// HTTP on the MSW harness: the real eventApiClient runs (Story 17.11c moved this off vi.mock).
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
  ...anEvent(),
  eventCode: 'BAT54',
  eventNumber: 54,
  title: 'Spring Conference 2025',
  description: 'Advanced microservices architecture',
  date: '2025-03-15T09:00:00Z',
  registrationDeadline: '2025-03-10T23:59:59Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3, 3013 Bern',
  venueCapacity: 200,
  workflowState: 'SPEAKER_IDENTIFICATION',
  organizerUsername: 'john.doe',
  currentAttendeeCount: 0,
  realAttendeeCount: 0,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
};

// Real, self-registered attendees → delete must be blocked.
const mockEventWithAttendees: Event = {
  ...mockEvent,
  currentAttendeeCount: 87,
  realAttendeeCount: 87,
};

/** The organizer list the moderator select loads (useUserList, role ORGANIZER). */
const ORGANIZERS = {
  data: [
    aUser({ id: 'john.doe', firstName: 'John', lastName: 'Doe', roles: ['ORGANIZER'] }),
    aUser({ id: 'jane.smith', firstName: 'Jane', lastName: 'Smith', roles: ['ORGANIZER'] }),
  ],
  pagination: { page: 1, limit: 100, totalItems: 2, totalPages: 1, hasNext: false, hasPrev: false },
};

const problem = (status: number, message: string) =>
  HttpResponse.json({ timestamp: '2026-11-20T08:00:00Z', status, error: 'x', message }, { status });

/** The query keys a mutation marked stale. */
const invalidatedKeys = (spy: { mock: { calls: unknown[][] } }): unknown[][] =>
  spy.mock.calls.map((call) => (call[0] as { queryKey: unknown[] }).queryKey);

// Only programmatic registrations (auto-enrolled organizers/partners): total count is
// non-zero but realAttendeeCount is 0 → delete must be ENABLED. This is the core fix.
const mockEventProgrammaticOnly: Event = {
  ...mockEvent,
  currentAttendeeCount: 15,
  realAttendeeCount: 0,
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

      // Event Moderator Section (replaced "Event Information" in Story 5.7)
      expect(screen.getByText(/Event Moderator/i)).toBeInTheDocument();
      expect(
        screen.getByText(/The organizer responsible for moderating this event/i)
      ).toBeInTheDocument();

      // Danger Zone Section
      expect(screen.getByText(/Danger Zone/i)).toBeInTheDocument();
      expect(screen.getByText(/These actions are irreversible/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Cancel Event/i })).toBeInTheDocument();
      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      expect(deleteButton).toBeInTheDocument();
      expect(deleteButton).not.toBeDisabled();
    });

    // Story 7.5 rework: per-event Q&A settings section.
    it('renders the Q&A settings section with the trigger options + controls', () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByTestId('qna-settings-section')).toBeInTheDocument();
      expect(screen.getByTestId('qna-enabled-switch')).toBeInTheDocument();
      expect(screen.getByTestId('qna-trigger-select')).toBeInTheDocument();
      expect(screen.getByTestId('qna-days-field')).toBeInTheDocument();
      expect(screen.getByTestId('qna-settings-save-btn')).toBeInTheDocument();
      expect(screen.getByTestId('qna-close-now-btn')).toBeInTheDocument();
    });

    it('should_notRenderMockNotificationData_when_rendered', () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      // The mock notification-rules section was removed (backend integration never landed).
      expect(screen.queryByText(/MOCK DATA/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/mock notification data/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/active automations/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Speaker deadline reminders/i)).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: /Manage All Notifications/i })
      ).not.toBeInTheDocument();
    });
  });

  describe('Conditional Rendering - Delete Button State', () => {
    it('enables delete when no attendees', () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      expect(deleteButton).not.toBeDisabled();
    });

    it('disables delete and shows warning when has real attendees', () => {
      renderWithProviders(<EventSettingsTab event={mockEventWithAttendees} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      expect(deleteButton).toBeDisabled();
      expect(
        screen.getByText(/Cannot delete event with attendee registrations/i)
      ).toBeInTheDocument();
    });

    it('enables delete when only programmatic registrations exist', () => {
      // currentAttendeeCount=15 (auto-enrolled organizers/partners) but realAttendeeCount=0.
      renderWithProviders(<EventSettingsTab event={mockEventProgrammaticOnly} eventCode="BAT54" />);

      const deleteButton = screen.getByRole('button', { name: /Delete Event/i });
      expect(deleteButton).not.toBeDisabled();
      expect(
        screen.queryByText(/Cannot delete event with attendee registrations/i)
      ).not.toBeInTheDocument();
    });
  });

  // Moved here from the vi.mock-based EventSettingsTab.test.tsx (touch rule, #824): the real
  // OrganizerSelect loads the organizers, the real useUpdateEvent PATCHes the event.
  describe('Event Moderator', () => {
    const chooseModerator = async (username: string) => {
      // The select is disabled until the organizers have loaded
      await within(screen.getByTestId('moderator-select')).findByText('John Doe');
      fireEvent.mouseDown(within(screen.getByTestId('moderator-select')).getByRole('combobox'));
      fireEvent.click(await screen.findByTestId(`organizer-option-${username}`));
    };

    beforeEach(() => {
      api.on('listUsers', () => ORGANIZERS);
    });

    it('shows the current moderator when rendered', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BATbern54" />);

      expect(screen.getByText('Event Moderator')).toBeInTheDocument();
      expect(
        await within(screen.getByTestId('moderator-select')).findByText('John Doe')
      ).toBeInTheDocument();
      expect(screen.queryByText('Event Information')).not.toBeInTheDocument();
    });

    it('saves the new moderator and confirms it', async () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BATbern54" />);

      await chooseModerator('jane.smith');

      expect(await screen.findByText('Moderator updated successfully')).toBeInTheDocument();
      expect(api.calls('patchEvent')[0].body).toEqual({ organizerUsername: 'jane.smith' });
    });

    it('shows the error and keeps the previous moderator when the save fails', async () => {
      api.on('patchEvent', () => problem(500, 'boom'));
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BATbern54" />);

      await chooseModerator('jane.smith');

      expect(await screen.findByText(/Server Error/)).toBeInTheDocument();
      expect(
        within(screen.getByTestId('moderator-select')).getByText('John Doe')
      ).toBeInTheDocument();
    });
  });

  describe('Event state section (regression: PR #788 lost the state override)', () => {
    it('shows the state section with the current state, nothing to apply yet', () => {
      renderWithProviders(
        <EventSettingsTab
          event={{ ...mockEvent, workflowState: 'SLOT_ASSIGNMENT' }}
          eventCode="BATbern54"
        />
      );
      expect(screen.getByTestId('workflow-state-section')).toBeInTheDocument();
      expect(screen.getByTestId('apply-workflow-state-button')).toBeDisabled();
    });
  });

  describe('Cancel Event Dialog', () => {
    // #824 item 2: confirming used to only log; the event now moves to CANCELLED
    it('should_cancelEvent_andRefreshEventAndRegistrations_when_cancelConfirmed', async () => {
      const spy = vi.spyOn(queryClient, 'invalidateQueries');
      api.on('transitionEventWorkflowState', () => ({
        eventCode: 'BATbern54',
        workflowState: 'CANCELLED',
        updatedAt: '2026-11-20T08:00:00Z',
      }));
      renderWithProviders(
        <EventSettingsTab event={mockEventWithAttendees} eventCode="BATbern54" />
      );

      fireEvent.click(screen.getByRole('button', { name: /Cancel Event/i }));
      fireEvent.click(await screen.findByRole('button', { name: /Yes, Cancel Event/i }));

      await waitFor(() => expect(api.calls('transitionEventWorkflowState')).toHaveLength(1));
      const [call] = api.calls('transitionEventWorkflowState');
      expect(call.params).toEqual({ eventCode: 'BATbern54' });
      expect(call.body).toMatchObject({ targetState: 'CANCELLED' });
      expect(await screen.findByText(/The event was cancelled/i)).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByText(/Cancel Event\?/i)).not.toBeInTheDocument());
      expect(invalidatedKeys(spy)).toContainEqual(['event', 'BATbern54']);
      expect(invalidatedKeys(spy)).toContainEqual(['event-registrations', 'BATbern54']);
    });

    it('should_showServerMessage_andKeepDialog_when_cancelRefused', async () => {
      api.on('transitionEventWorkflowState', () =>
        problem(422, 'Cannot transition from terminal state ARCHIVED')
      );
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BATbern54" />);

      fireEvent.click(screen.getByRole('button', { name: /Cancel Event/i }));
      fireEvent.click(await screen.findByRole('button', { name: /Yes, Cancel Event/i }));

      expect(
        await screen.findByText('Cannot transition from terminal state ARCHIVED')
      ).toBeInTheDocument();
      expect(screen.getByText(/Cancel Event\?/i)).toBeInTheDocument();
    });

    it.each(['EVENT_COMPLETED', 'ARCHIVED', 'CANCELLED'] as const)(
      'should_disableCancel_when_eventIs_%s',
      (workflowState) => {
        renderWithProviders(
          <EventSettingsTab event={{ ...mockEvent, workflowState }} eventCode="BATbern54" />
        );
        expect(screen.getByRole('button', { name: /^Cancel Event$/i })).toBeDisabled();
      }
    );

    it('should_sayTheEventIsCancelled_when_eventCancelled', () => {
      renderWithProviders(
        <EventSettingsTab
          event={{ ...mockEvent, workflowState: 'CANCELLED' }}
          eventCode="BATbern54"
        />
      );
      expect(screen.getByText(/This event was cancelled/i)).toBeInTheDocument();
    });

    it('should_enableCancel_when_eventStillPlanned (positive control)', () => {
      renderWithProviders(<EventSettingsTab event={mockEvent} eventCode="BATbern54" />);
      expect(screen.getByRole('button', { name: /^Cancel Event$/i })).not.toBeDisabled();
    });

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

      // The count is interpolated (it rendered as "{87}" before #824)
      expect(
        await screen.findByText(/This will cancel the event and notify all 0 registered attendees/i)
      ).toBeInTheDocument();
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

      expect(
        await screen.findByText(/delete the event "Spring Conference 2025"\./i)
      ).toBeInTheDocument();
    });

    it('should_navigateToEvents_when_deleteConfirmed', async () => {
      api.on('deleteEvent', () => new HttpResponse(null, { status: 204 }));
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
      expect(api.calls('deleteEvent')[0].params).toEqual({ eventCode: 'BAT54' });
    });
  });
});
