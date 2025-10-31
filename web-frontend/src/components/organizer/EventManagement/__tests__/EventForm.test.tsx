/**
 * EventForm Component Tests (RED Phase - TDD)
 *
 * Story 2.5.3 - Task 9a
 * AC: 3 (Create Event Form), 4 (Edit Event Form), 20 (Auto-Save Functionality)
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1 (Event Information section)
 *
 * Tests for event create/edit modal form with:
 * - Create/edit mode switching
 * - Form validation (date must be 30+ days future, deadline 7+ days before event, capacity positive)
 * - Auto-save functionality (5s debounce, always enabled, NOT configurable)
 * - Unsaved changes warning
 * - Partial update (PATCH for changed fields only)
 * - Role-based access control (organizer-only editing)
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventForm } from '../EventForm';
import type { Event } from '@/types/event.types';

// Mock API client
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    createEvent: vi.fn(),
    updateEvent: vi.fn(),
    patchEvent: vi.fn(),
  },
}));

// Mock auth hook (from Story 1.17)
const mockUseAuth = vi.fn(() => ({
  user: { username: 'john.doe', role: 'organizer' },
  isAuthenticated: true,
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => mockUseAuth(),
}));

// Mock debounce hook
vi.mock('@/hooks/useDebounce', () => ({
  useDebounce: <T,>(value: T) => value, // Return value immediately for tests
}));

describe('EventForm Component', () => {
  let queryClient: QueryClient;

  const mockEvent: Event = {
    eventCode: 'BATbern56',
    eventNumber: 56,
    title: 'Cloud Computing 2025',
    description: 'Annual cloud computing event',
    eventDate: '2025-04-15T18:00:00Z',
    eventType: 'full_day',
    status: 'draft',
    workflowState: 'initial_setup',
    registrationDeadline: '2025-04-08T23:59:59Z',
    capacity: 200,
    theme: 'Cloud & DevOps',
    venueCode: 'kornhaus-bern',
    currentAttendeeCount: 0,
    createdAt: '2025-01-01T10:00:00Z',
    updatedAt: '2025-01-10T15:00:00Z',
    createdBy: 'john.doe',
    version: 1,
  };

  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    return ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          <BrowserRouter>{children}</BrowserRouter>
        </I18nextProvider>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('Create Mode (AC3)', () => {
    it('should_displayCreateTitle_when_createMode', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText(/create.*event/i)).toBeInTheDocument();
    });

    it('should_renderEmptyForm_when_createMode', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i) as HTMLInputElement;
      expect(titleInput.value).toBe('');
    });

    it('should_displaySaveDraftButton_when_createMode', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByRole('button', { name: /save.*draft/i })).toBeInTheDocument();
    });

    it('should_displaySaveCreateButton_when_createMode', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByRole('button', { name: /save.*create/i })).toBeInTheDocument();
    });

    it('should_displayCancelButton_when_createMode', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    it('should_closeModal_when_cancelClicked', async () => {
      const onClose = vi.fn();
      render(<EventForm mode="create" open={true} onClose={onClose} />, {
        wrapper: createWrapper(),
      });

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await userEvent.click(cancelButton);

      expect(onClose).toHaveBeenCalled();
    });
  });

  describe('Edit Mode (AC4)', () => {
    it('should_displayEditTitle_when_editMode', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText(/edit.*event/i)).toBeInTheDocument();
    });

    it('should_prefillForm_when_editModeWithEvent', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i) as HTMLInputElement;
      expect(titleInput.value).toBe('Cloud Computing 2025');
    });

    it('should_prefillDescription_when_editModeWithEvent', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const descriptionInput = screen.getByLabelText(/description/i) as HTMLTextAreaElement;
      expect(descriptionInput.value).toBe('Annual cloud computing event');
    });

    it('should_prefillEventDate_when_editModeWithEvent', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const dateInput = screen.getByLabelText(/event.*date/i) as HTMLInputElement;
      expect(dateInput.value).toContain('2025-04-15');
    });

    it('should_prefillEventType_when_editModeWithEvent', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const typeSelect = screen.getByLabelText(/event.*type/i) as HTMLSelectElement;
      expect(typeSelect.value).toBe('full_day');
    });

    it('should_prefillCapacity_when_editModeWithEvent', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const capacityInput = screen.getByLabelText(/capacity/i) as HTMLInputElement;
      expect(capacityInput.value).toBe('200');
    });

    it('should_displaySaveButton_when_editMode', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByRole('button', { name: /^save$/i })).toBeInTheDocument();
    });

    it('should_notDisplaySaveDraftButton_when_editMode', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      expect(screen.queryByRole('button', { name: /save.*draft/i })).not.toBeInTheDocument();
    });
  });

  describe('Form Validation - Required Fields (AC3)', () => {
    it('should_showTitleError_when_titleEmpty', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/title.*required/i)).toBeInTheDocument();
      });
    });

    it('should_showTitleMinLengthError_when_titleTooShort', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.type(titleInput, 'Short'); // Less than 10 chars

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/title.*at least.*10.*characters/i)).toBeInTheDocument();
      });
    });

    it('should_showDescriptionError_when_descriptionEmpty', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/description.*required/i)).toBeInTheDocument();
      });
    });
  });

  describe('Form Validation - Date Rules (AC3)', () => {
    it('should_showDateError_when_eventDateTooSoon', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.type(titleInput, 'Test Event 2025');

      const descriptionInput = screen.getByLabelText(/description/i);
      await userEvent.type(descriptionInput, 'Test description for event');

      // Set date to 15 days in future (less than required 30 days)
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 15);
      const dateInput = screen.getByLabelText(/event.*date/i);
      await userEvent.type(dateInput, tomorrow.toISOString().split('T')[0]);

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/event.*date.*at least.*30.*days.*future/i)).toBeInTheDocument();
      });
    });

    it('should_allowEventDate_when_exactly30DaysInFuture', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.type(titleInput, 'Test Event 2025');

      const descriptionInput = screen.getByLabelText(/description/i);
      await userEvent.type(descriptionInput, 'Test description for event');

      // Set date to exactly 30 days in future (should be valid)
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 30);
      const dateInput = screen.getByLabelText(/event.*date/i);
      await userEvent.type(dateInput, futureDate.toISOString().split('T')[0]);

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(
          screen.queryByText(/event.*date.*at least.*30.*days.*future/i)
        ).not.toBeInTheDocument();
      });
    });

    it('should_showDeadlineError_when_deadlineTooCloseToEvent', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.type(titleInput, 'Test Event 2025');

      const descriptionInput = screen.getByLabelText(/description/i);
      await userEvent.type(descriptionInput, 'Test description for event');

      // Set event date 60 days in future
      const eventDate = new Date();
      eventDate.setDate(eventDate.getDate() + 60);
      const dateInput = screen.getByLabelText(/event.*date/i);
      await userEvent.type(dateInput, eventDate.toISOString().split('T')[0]);

      // Set deadline only 3 days before event (less than required 7 days)
      const deadlineDate = new Date(eventDate);
      deadlineDate.setDate(deadlineDate.getDate() - 3);
      const deadlineInput = screen.getByLabelText(/registration.*deadline/i);
      await userEvent.type(deadlineInput, deadlineDate.toISOString().split('T')[0]);

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/deadline.*at least.*7.*days.*before.*event/i)).toBeInTheDocument();
      });
    });

    it('should_allowDeadline_when_exactly7DaysBeforeEvent', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.type(titleInput, 'Test Event 2025');

      const descriptionInput = screen.getByLabelText(/description/i);
      await userEvent.type(descriptionInput, 'Test description for event');

      // Set event date 60 days in future
      const eventDate = new Date();
      eventDate.setDate(eventDate.getDate() + 60);
      const dateInput = screen.getByLabelText(/event.*date/i);
      await userEvent.type(dateInput, eventDate.toISOString().split('T')[0]);

      // Set deadline exactly 7 days before event (should be valid)
      const deadlineDate = new Date(eventDate);
      deadlineDate.setDate(deadlineDate.getDate() - 7);
      const deadlineInput = screen.getByLabelText(/registration.*deadline/i);
      await userEvent.type(deadlineInput, deadlineDate.toISOString().split('T')[0]);

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(
          screen.queryByText(/deadline.*at least.*7.*days.*before.*event/i)
        ).not.toBeInTheDocument();
      });
    });
  });

  describe('Form Validation - Capacity Rules (AC3)', () => {
    it('should_showCapacityError_when_capacityZero', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const capacityInput = screen.getByLabelText(/capacity/i);
      await userEvent.clear(capacityInput);
      await userEvent.type(capacityInput, '0');

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/capacity.*positive/i)).toBeInTheDocument();
      });
    });

    it('should_showCapacityError_when_capacityNegative', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const capacityInput = screen.getByLabelText(/capacity/i);
      await userEvent.clear(capacityInput);
      await userEvent.type(capacityInput, '-10');

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/capacity.*positive/i)).toBeInTheDocument();
      });
    });

    it('should_allowCapacity_when_capacityPositive', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const capacityInput = screen.getByLabelText(/capacity/i);
      await userEvent.type(capacityInput, '200');

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.queryByText(/capacity.*positive/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Save Draft Functionality (AC3)', () => {
    it('should_allowSaveDraft_when_incompleteData', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.createEvent as ReturnType<typeof vi.fn>).mockResolvedValue({
        eventCode: 'BATbern99',
        status: 'draft',
      });

      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.type(titleInput, 'Incomplete Event');

      const saveDraftButton = screen.getByRole('button', { name: /save.*draft/i });
      await userEvent.click(saveDraftButton);

      await waitFor(() => {
        expect(eventApiClient.createEvent).toHaveBeenCalledWith(
          expect.objectContaining({
            title: 'Incomplete Event',
            status: 'draft',
          })
        );
      });
    });

    it('should_notValidateRequiredFields_when_savingDraft', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.createEvent as ReturnType<typeof vi.fn>).mockResolvedValue({
        eventCode: 'BATbern99',
        status: 'draft',
      });

      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const saveDraftButton = screen.getByRole('button', { name: /save.*draft/i });
      await userEvent.click(saveDraftButton);

      // Should not show validation errors
      await waitFor(() => {
        expect(screen.queryByText(/title.*required/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/description.*required/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Auto-Save Functionality (AC4, AC20)', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('should_enableAutoSave_when_editMode', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      // Auto-save indicator should be visible (always enabled)
      expect(screen.getByText(/auto.*save/i)).toBeInTheDocument();
    });

    it('should_notAllowDisablingAutoSave_when_editMode', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      // Should NOT have toggle to disable auto-save (per AC20: always enabled, NOT configurable)
      expect(
        screen.queryByRole('checkbox', { name: /enable.*auto.*save/i })
      ).not.toBeInTheDocument();
      expect(screen.queryByRole('switch', { name: /auto.*save/i })).not.toBeInTheDocument();
    });

    it('should_triggerAutoSave_when_fieldChangedAfter5Seconds', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockResolvedValue(mockEvent);

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Updated Title');

      // Wait 5 seconds (auto-save debounce time)
      vi.advanceTimersByTime(5000);

      await waitFor(() => {
        expect(eventApiClient.patchEvent).toHaveBeenCalledWith(
          'BATbern56',
          expect.objectContaining({
            title: 'Updated Title',
          })
        );
      });
    });

    it('should_notTriggerAutoSave_when_before5Seconds', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockResolvedValue(mockEvent);

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Updated Title');

      // Wait only 3 seconds (before 5-second debounce)
      vi.advanceTimersByTime(3000);

      await waitFor(() => {
        expect(eventApiClient.patchEvent).not.toHaveBeenCalled();
      });
    });

    it('should_displaySavingIndicator_when_autoSaveTriggered', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve(mockEvent), 1000))
      );

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Updated Title');

      // Trigger auto-save
      vi.advanceTimersByTime(5000);

      await waitFor(() => {
        expect(screen.getByText(/saving/i)).toBeInTheDocument();
      });
    });

    it('should_displayLastSavedTimestamp_when_autoSaveCompletes', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockResolvedValue(mockEvent);

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Updated Title');

      // Trigger auto-save
      vi.advanceTimersByTime(5000);

      await waitFor(() => {
        expect(screen.getByText(/last.*saved/i)).toBeInTheDocument();
      });
    });

    it('should_showErrorBanner_when_autoSaveFails', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('Network error')
      );

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Updated Title');

      // Trigger auto-save
      vi.advanceTimersByTime(5000);

      await waitFor(() => {
        expect(screen.getByText(/auto.*save.*failed/i)).toBeInTheDocument();
      });
    });

    it('should_detectConflict_when_concurrentEdit', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockRejectedValue({
        response: {
          status: 409,
          data: { message: 'Version conflict' },
        },
      });

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Updated Title');

      // Trigger auto-save
      vi.advanceTimersByTime(5000);

      await waitFor(() => {
        expect(screen.getByText(/concurrent.*edit.*detected/i)).toBeInTheDocument();
      });
    });
  });

  describe('Unsaved Changes Warning (AC4)', () => {
    it('should_showWarning_when_closingWithUnsavedChanges', async () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Modified Title');

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await userEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.getByText(/unsaved.*changes/i)).toBeInTheDocument();
      });
    });

    it('should_notShowWarning_when_noChanges', async () => {
      const onClose = vi.fn();
      render(<EventForm mode="edit" open={true} onClose={onClose} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await userEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByText(/unsaved.*changes/i)).not.toBeInTheDocument();
        expect(onClose).toHaveBeenCalled();
      });
    });

    it('should_closeModal_when_confirmUnsavedChanges', async () => {
      const onClose = vi.fn();
      render(<EventForm mode="edit" open={true} onClose={onClose} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Modified Title');

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await userEvent.click(cancelButton);

      // Confirm unsaved changes
      const confirmButton = await screen.findByRole('button', { name: /confirm/i });
      await userEvent.click(confirmButton);

      await waitFor(() => {
        expect(onClose).toHaveBeenCalled();
      });
    });

    it('should_keepModalOpen_when_cancelUnsavedChanges', async () => {
      const onClose = vi.fn();
      render(<EventForm mode="edit" open={true} onClose={onClose} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'Modified Title');

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await userEvent.click(cancelButton);

      // Cancel unsaved changes dialog
      const cancelDialogButton = await screen.findByRole('button', {
        name: /^cancel$/i,
      });
      await userEvent.click(cancelDialogButton);

      await waitFor(() => {
        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
      });
    });
  });

  describe('Partial Update (AC4)', () => {
    it('should_sendOnlyChangedFields_when_patchingEvent', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockResolvedValue(mockEvent);

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      // Change only title
      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'New Title Only');

      const saveButton = screen.getByRole('button', { name: /^save$/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(eventApiClient.patchEvent).toHaveBeenCalledWith('BATbern56', {
          title: 'New Title Only',
        });
      });
    });

    it('should_sendMultipleFields_when_multipleFieldsChanged', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockResolvedValue(mockEvent);

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      // Change title and description
      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'New Title');

      const descriptionInput = screen.getByLabelText(/description/i);
      await userEvent.clear(descriptionInput);
      await userEvent.type(descriptionInput, 'New Description');

      const saveButton = screen.getByRole('button', { name: /^save$/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(eventApiClient.patchEvent).toHaveBeenCalledWith('BATbern56', {
          title: 'New Title',
          description: 'New Description',
        });
      });
    });

    it('should_usePatchMethod_when_updatingEvent', async () => {
      const { eventApiClient } = await import('@/services/eventApiClient');
      (eventApiClient.patchEvent as ReturnType<typeof vi.fn>).mockResolvedValue(mockEvent);

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'New Title');

      const saveButton = screen.getByRole('button', { name: /^save$/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(eventApiClient.patchEvent).toHaveBeenCalled();
        expect(eventApiClient.updateEvent).not.toHaveBeenCalled();
      });
    });
  });

  describe('Role-Based Access Control (AC4)', () => {
    it('should_allowEditing_when_organizerRole', () => {
      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      const titleInput = screen.getByLabelText(/title/i) as HTMLInputElement;
      expect(titleInput.disabled).toBe(false);
    });

    it('should_blockEditing_when_nonOrganizerRole', () => {
      // Override mock for this test
      mockUseAuth.mockReturnValueOnce({
        user: { username: 'jane.smith', role: 'speaker' },
        isAuthenticated: true,
      });

      render(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />, {
        wrapper: createWrapper(),
      });

      // Should show permission denied message
      expect(screen.getByText(/permission/i)).toBeInTheDocument();
    });
  });

  describe('Event Type Selection (AC3)', () => {
    it('should_displayEventTypeOptions_when_rendered', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const typeSelect = screen.getByLabelText(/event.*type/i);
      expect(typeSelect).toBeInTheDocument();
    });

    it('should_includeFullDayOption_when_rendered', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText(/full.*day/i)).toBeInTheDocument();
    });

    it('should_includeAfternoonOption_when_rendered', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText(/afternoon/i)).toBeInTheDocument();
    });

    it('should_includeEveningOption_when_rendered', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText(/evening/i)).toBeInTheDocument();
    });
  });

  describe('Venue Selection (AC3)', () => {
    it('should_displayVenueDropdown_when_rendered', () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByLabelText(/venue/i)).toBeInTheDocument();
    });

    it('should_loadVenueOptions_when_rendered', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const venueSelect = screen.getByLabelText(/venue/i);
      fireEvent.mouseDown(venueSelect);

      await waitFor(() => {
        // Should load venue options (mocked from venue service)
        expect(screen.getByText(/kornhaus/i)).toBeInTheDocument();
      });
    });
  });

  describe('Internationalization (AC22)', () => {
    it('should_translateFormLabels_when_languageChanged', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      // Check German translations (default)
      await i18n.changeLanguage('de');
      await waitFor(() => {
        expect(screen.getByText(/neue.*veranstaltung/i)).toBeInTheDocument();
      });

      // Change to English
      await i18n.changeLanguage('en');
      await waitFor(() => {
        expect(screen.getByText(/create.*event/i)).toBeInTheDocument();
      });
    });

    it('should_translateValidationErrors_when_languageChanged', async () => {
      render(<EventForm mode="create" open={true} onClose={vi.fn()} />, {
        wrapper: createWrapper(),
      });

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      // Check German validation error
      await i18n.changeLanguage('de');
      await waitFor(() => {
        expect(screen.getByText(/titel.*erforderlich/i)).toBeInTheDocument();
      });

      // Change to English
      await i18n.changeLanguage('en');
      await waitFor(() => {
        expect(screen.getByText(/title.*required/i)).toBeInTheDocument();
      });
    });
  });
});
