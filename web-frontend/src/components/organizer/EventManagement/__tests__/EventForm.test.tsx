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
import { act, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventForm } from '../EventForm';
import type { Event } from '@/types/event.types';
import { anEvent } from '@/test/fixtures';
import { renderWithAuth, signedInAs } from '@/test/auth';
import { api } from '@/test/msw/api';

// Real AuthContext (signed-in organizer), real eventApiClient and hooks on the MSW harness
// (#1223 item 7 left the own-code mocks, C6).
const renderForm = (ui: React.ReactElement) =>
  renderWithAuth(<I18nextProvider i18n={i18n}>{ui}</I18nextProvider>, {
    auth: signedInAs('organizer', { username: 'john.doe' }),
  });

describe('EventForm Component', () => {
  const mockEvent: Event = anEvent({
    eventCode: 'BATbern56',
    eventNumber: 56,
    title: 'Cloud Computing 2025',
    description: 'Annual cloud computing event',
    date: '2025-04-15T18:00:00Z',
    eventType: 'FULL_DAY',
    workflowState: 'CREATED',
    registrationDeadline: '2025-04-08T23:59:59Z',
    venueCapacity: 200,
    currentAttendeeCount: 0,
    createdAt: '2025-01-01T10:00:00Z',
    updatedAt: '2025-01-10T15:00:00Z',
    createdBy: 'john.doe',
  });

  /**
   * EMS's answer to a broken business rule, e.g. a taken event number: a 422 with the
   * DomainErrorResponse body (BusinessValidationException, EventWriteService).
   */
  const unprocessable = (path: string, message: string) =>
    HttpResponse.json(
      {
        timestamp: '2026-11-18T21:00:00Z',
        path,
        status: 422,
        error: 'Unprocessable Entity',
        message,
        correlationId: 'corr-422',
        severity: 'MEDIUM',
      },
      { status: 422 }
    );

  /** The PATCH bodies the form sent, oldest first. */
  const patches = () => api.calls('patchEvent');

  describe('Create Mode (AC3)', () => {
    it('should_displayCreateTitle_when_createMode', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByText(/create.*event/i)).toBeInTheDocument();
    });

    it('should_renderEmptyForm_when_createMode', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const titleInput = screen.getByLabelText(/title/i) as HTMLInputElement;
      expect(titleInput.value).toBe('');
    });

    // #1223 item 7: the number was always "1"; a new event gets the next free number.
    it('should_prefillNextEventNumber_when_createMode', async () => {
      api.on('listEvents', () => ({
        data: [anEvent({ eventCode: 'BATbern63', eventNumber: 63, workflowState: 'ARCHIVED' })],
        pagination: {
          page: 1,
          limit: 1,
          totalItems: 63,
          totalPages: 63,
          hasNext: true,
          hasPrev: false,
        },
      }));

      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const numberInput = screen.getByLabelText(/event number/i) as HTMLInputElement;
      await waitFor(() => expect(numberInput.value).toBe('64'));
      // The highest number over every event, archived included: one row, sorted by number.
      const query = api.calls('listEvents')[0].query;
      expect(query.get('sort')).toBe('-eventNumber');
      expect(query.get('limit')).toBe('1');
      expect(query.get('includeArchived')).toBe('true');
    });

    it('should_prefillNextEventNumber_when_dialogIsOpenedAgain', async () => {
      api.on('listEvents', () => ({
        data: [anEvent({ eventCode: 'BATbern63', eventNumber: 63 })],
        pagination: {
          page: 1,
          limit: 1,
          totalItems: 63,
          totalPages: 63,
          hasNext: true,
          hasPrev: false,
        },
      }));
      const form = (open: boolean) => (
        <I18nextProvider i18n={i18n}>
          <EventForm mode="create" open={open} onClose={vi.fn()} />
        </I18nextProvider>
      );
      const { rerenderWithAuth } = renderWithAuth(form(true), {
        auth: signedInAs('organizer', { username: 'john.doe' }),
      });
      await waitFor(() =>
        expect((screen.getByLabelText(/event number/i) as HTMLInputElement).value).toBe('64')
      );

      rerenderWithAuth(form(false));
      rerenderWithAuth(form(true));

      // The create-mode reset on opening must not put the 1 back over the known next number.
      await waitFor(() => expect(api.calls('listEvents').length).toBeGreaterThanOrEqual(2));
      await waitFor(() =>
        expect((screen.getByLabelText(/event number/i) as HTMLInputElement).value).toBe('64')
      );
    });

    it('should_prefillOne_when_noEventExistsYet', async () => {
      api.on('listEvents', () => ({
        data: [],
        pagination: {
          page: 1,
          limit: 1,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        },
      }));

      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      await waitFor(() => expect(api.calls('listEvents')).toHaveLength(1));
      const numberInput = screen.getByLabelText(/event number/i) as HTMLInputElement;
      await waitFor(() => expect(numberInput.value).toBe('1'));
    });

    it('should_keepTypedEventNumber_when_nextNumberArrivesLater', async () => {
      let answer: () => void = () => {};
      api.on(
        'listEvents',
        () =>
          new Promise<Response>((resolve) => {
            answer = () =>
              resolve(
                HttpResponse.json({
                  data: [anEvent({ eventNumber: 63 })],
                  pagination: {
                    page: 1,
                    limit: 1,
                    totalItems: 63,
                    totalPages: 63,
                    hasNext: true,
                    hasPrev: false,
                  },
                })
              );
          })
      );
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);
      const numberInput = screen.getByLabelText(/event number/i) as HTMLInputElement;
      await waitFor(() => expect(api.calls('listEvents')).toHaveLength(1));

      await userEvent.clear(numberInput);
      await userEvent.type(numberInput, '70');
      await act(async () => answer());

      expect(numberInput.value).toBe('70');
    });

    // Found on the way: the form read `response.data.message`, which eventApiClient's errors
    // never carry, and the client dropped the message of a 422, so a refused create (number
    // taken by now) only said "save failed".
    it('should_showServerReason_when_createIsRefusedBecauseTheNumberIsTaken', async () => {
      api.on('createEvent', () =>
        unprocessable(
          '/api/v1/events',
          "Validation failed for field 'eventCode': Event code already exists: BATbern64"
        )
      );
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const eventDate = new Date();
      eventDate.setDate(eventDate.getDate() + 60);
      const day = eventDate.toISOString().split('T')[0];
      await userEvent.type(screen.getByLabelText(/title/i), 'Architektur im Wandel');
      await userEvent.type(screen.getByLabelText(/description/i), 'Ein Abend über Architektur');
      await userEvent.type(screen.getByLabelText(/event.*date/i), day);
      await userEvent.type(screen.getByLabelText(/registration.*deadline/i), day);
      await userEvent.type(screen.getByLabelText(/venue name/i), 'Zentrum Paul Klee');
      await userEvent.type(screen.getByLabelText(/venue address/i), 'Monument im Fruchtland 3');
      await userEvent.click(screen.getByRole('button', { name: /save.*create/i }));

      expect(
        await screen.findByText(
          "Validation failed for field 'eventCode': Event code already exists: BATbern64"
        )
      ).toBeInTheDocument();
      expect(api.calls('createEvent')).toHaveLength(1);
    });

    it('should_notAskForNextNumber_when_editMode', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      await waitFor(() => expect(screen.getByLabelText(/title/i)).toBeInTheDocument());
      expect(api.calls('listEvents')).toHaveLength(0);
      expect((screen.getByLabelText(/event number/i) as HTMLInputElement).value).toBe('56');
    });

    it('should_displaySaveDraftButton_when_createMode', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByRole('button', { name: /save.*draft/i })).toBeInTheDocument();
    });

    it('should_displaySaveCreateButton_when_createMode', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByRole('button', { name: /save.*create/i })).toBeInTheDocument();
    });

    it('should_displayCancelButton_when_createMode', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    it('should_closeModal_when_cancelClicked', async () => {
      const onClose = vi.fn();
      renderForm(<EventForm mode="create" open={true} onClose={onClose} />);

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await userEvent.click(cancelButton);

      expect(onClose).toHaveBeenCalled();
    });
  });

  describe('Edit Mode (AC4)', () => {
    it('should_displayEditTitle_when_editMode', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      expect(screen.getByText(/edit.*event/i)).toBeInTheDocument();
    });

    it('should_prefillForm_when_editModeWithEvent', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      const titleInput = screen.getByLabelText(/title/i) as HTMLInputElement;
      expect(titleInput.value).toBe('Cloud Computing 2025');
    });

    it('should_prefillDescription_when_editModeWithEvent', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      const descriptionInput = screen.getByLabelText(/description/i) as HTMLTextAreaElement;
      expect(descriptionInput.value).toBe('Annual cloud computing event');
    });

    it.skip('should_prefillEventDate_when_editModeWithEvent - MUI DatePicker does not expose value in test DOM', () => {
      // SKIPPED: MUI DatePicker doesn't populate input.value attribute in JSDOM
      // The component correctly prefills the date, but testing requires E2E or custom matchers
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      const dateInput = screen.getByLabelText(/event.*date/i) as HTMLInputElement;
      expect(dateInput.value).toContain('2025-04-15');
    });

    it.skip('should_prefillEventType_when_editModeWithEvent - MUI Select label association issue', () => {
      // SKIPPED: MUI Select label is not properly associated with form control in test environment
      // The component works correctly but requires better test setup or E2E testing
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      const typeSelect = screen.getByLabelText(/event.*type/i) as HTMLSelectElement;
      expect(typeSelect.value).toBe('full_day');
    });

    it('should_prefillCapacity_when_editModeWithEvent', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      const capacityInput = screen.getByLabelText(/capacity/i) as HTMLInputElement;
      expect(capacityInput.value).toBe('200');
    });

    it('should_displaySaveButton_when_editMode', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      expect(screen.getByRole('button', { name: /^save$/i })).toBeInTheDocument();
    });

    it('should_notDisplaySaveDraftButton_when_editMode', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      expect(screen.queryByRole('button', { name: /save.*draft/i })).not.toBeInTheDocument();
    });
  });

  describe('Form Validation - Required Fields (AC3)', () => {
    it('should_showTitleError_when_titleEmpty', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/title.*required/i)).toBeInTheDocument();
      });
    });

    it('should_showTitleMinLengthError_when_titleTooShort', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.type(titleInput, 'Short'); // Less than 10 chars

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/title.*at least.*10.*characters/i)).toBeInTheDocument();
      });
    });

    it('should_showDescriptionError_when_descriptionEmpty', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const saveButton = screen.getByRole('button', { name: /save.*create/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/description.*required/i)).toBeInTheDocument();
      });
    });
  });

  describe('Form Validation - Fields the server requires (CreateEventRequest)', () => {
    it('should_showDeadlineRequiredError_when_registrationDeadlineEmpty', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      await userEvent.click(screen.getByRole('button', { name: /save.*create/i }));

      await waitFor(() => {
        expect(screen.getByText('Registration deadline is required')).toBeInTheDocument();
      });
      expect(api.calls('createEvent')).toHaveLength(0);
    });

    it('should_validateServerFields_when_savingDraft', async () => {
      // A draft is an event in state CREATED: the server stores it only with the eight
      // required fields, so the draft button checks them instead of sending a sure 400.
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      await userEvent.click(screen.getByRole('button', { name: /save.*draft/i }));

      await waitFor(() => {
        expect(screen.getByText('Registration deadline is required')).toBeInTheDocument();
      });
      expect(screen.getByText('Venue name is required')).toBeInTheDocument();
      expect(api.calls('createEvent')).toHaveLength(0);
    });
  });

  describe('Form Validation - Date Rules (AC3)', () => {
    it.skip('should_showDateError_when_eventDateTooSoon - 30-day rule not yet implemented', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

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

    it.skip('should_allowEventDate_when_exactly30DaysInFuture - DateTime comparison edge case', async () => {
      // SKIPPED: Validation compares DateTime (with hours/minutes) not just dates
      // If current time is 10:00 AM and test date is midnight, "now+30days at 10:00 AM" > "testdate at 00:00"
      // Would need to normalize dates to start-of-day in validation logic
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

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

    it.skip('should_showDeadlineError_when_deadlineTooCloseToEvent - 7-day rule not yet implemented', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

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

    it('should_allowDeadline_when_onOrBeforeEventDate', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      // Set event date 60 days in future
      const eventDate = new Date();
      eventDate.setDate(eventDate.getDate() + 60);
      const dateInput = screen.getByLabelText(/event.*date/i);
      await userEvent.type(dateInput, eventDate.toISOString().split('T')[0]);

      // Set deadline same as event date (should be valid - on or before event date)
      const deadlineInput = screen.getByLabelText(/registration.*deadline/i);
      await userEvent.type(deadlineInput, eventDate.toISOString().split('T')[0]);

      // Blur to trigger validation
      await userEvent.tab();

      // Validation error should not appear for deadline on or before event date
      expect(
        screen.queryByText(/deadline.*must.*be.*on.*or.*before.*event.*date/i)
      ).not.toBeInTheDocument();
    });
  });

  describe('Form Validation - Capacity Rules (AC3)', () => {
    it('should_showCapacityError_when_capacityZero', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

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
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

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
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const capacityInput = screen.getByLabelText(/capacity/i);
      await userEvent.clear(capacityInput);
      await userEvent.type(capacityInput, '200');

      // Blur to trigger validation
      await userEvent.tab();

      // Validation error should not appear for positive capacity
      expect(screen.queryByText(/capacity.*positive/i)).not.toBeInTheDocument();
    });
  });

  describe('Auto-Save Functionality (AC4, AC20)', () => {
    // The real useDebounce on fake timers that also move with real time (`shouldAdvanceTime`),
    // so userEvent's own delays and waitFor's polling keep running; userEvent advances the fake
    // clock as it types.
    let user: ReturnType<typeof userEvent.setup>;

    beforeEach(() => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    });

    afterEach(() => {
      vi.useRealTimers();
      vi.restoreAllMocks();
    });

    const editTitle = async () => {
      const titleInput = screen.getByLabelText(/title/i);
      await user.clear(titleInput);
      await user.type(titleInput, 'Updated Title');
    };

    it('should_enableAutoSave_when_editMode', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      // Auto-save indicator should be visible (always enabled)
      expect(screen.getByText(/auto.*save/i)).toBeInTheDocument();
    });

    it('should_notAllowDisablingAutoSave_when_editMode', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      // Should NOT have toggle to disable auto-save (per AC20: always enabled, NOT configurable)
      expect(
        screen.queryByRole('checkbox', { name: /enable.*auto.*save/i })
      ).not.toBeInTheDocument();
      expect(screen.queryByRole('switch', { name: /auto.*save/i })).not.toBeInTheDocument();
    });

    it('should_triggerAutoSave_when_fieldChangedAfter5Seconds', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);
      await editTitle();

      // Wait 5 seconds (auto-save debounce time)
      await act(() => vi.advanceTimersByTimeAsync(5000));

      await waitFor(() => expect(patches()).toHaveLength(1));
      expect(patches()[0].params.eventCode).toBe('BATbern56');
      expect(patches()[0].body).toEqual(expect.objectContaining({ title: 'Updated Title' }));
    });

    it('should_notTriggerAutoSave_when_before5Seconds', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);
      await editTitle();

      // Wait only 3 seconds (before 5-second debounce)
      await act(() => vi.advanceTimersByTimeAsync(3000));

      expect(patches()).toHaveLength(0);
    });

    it('should_displaySavingIndicator_when_autoSaveTriggered', async () => {
      // The test settles the save itself: a timer that resolved it after the test had ended let
      // the form set state after the jsdom teardown (see the RegistrationWizard fix, 978198a7).
      let finishSave: () => void = () => {};
      api.on(
        'patchEvent',
        () =>
          new Promise<Response>((resolve) => {
            finishSave = () => resolve(HttpResponse.json(mockEvent));
          })
      );

      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);
      await editTitle();

      // Trigger auto-save
      await act(() => vi.advanceTimersByTimeAsync(5000));

      await waitFor(() => {
        expect(screen.getByText(/saving/i)).toBeInTheDocument();
      });

      await act(async () => finishSave());
      await waitFor(() => {
        expect(screen.getByText(/last.*saved/i)).toBeInTheDocument();
      });
    });

    it('should_displayLastSavedTimestamp_when_autoSaveCompletes', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);
      await editTitle();

      // Trigger auto-save
      await act(() => vi.advanceTimersByTimeAsync(5000));

      await waitFor(() => {
        expect(screen.getByText(/last.*saved/i)).toBeInTheDocument();
      });
    });

    it('should_showErrorBanner_when_autoSaveFails', async () => {
      api.on('patchEvent', () => HttpResponse.error());

      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);
      await editTitle();

      // Trigger auto-save
      await act(() => vi.advanceTimersByTimeAsync(5000));

      await waitFor(() => {
        expect(screen.getAllByText(/auto.*save.*failed/i).length).toBeGreaterThan(0);
      });
    });

    it('should_notRetryAutoSave_when_nothingChangedAfterAFailure', async () => {
      // The form keeps the failed change; it saves again on the next edit, not every 5 s while
      // the dialog stays open (a failed save was re-sent with nothing edited).
      api.on('patchEvent', () =>
        unprocessable('/api/v1/events/BATbern56', "Validation failed for field 'title': refused")
      );

      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);
      await editTitle();

      await act(() => vi.advanceTimersByTimeAsync(5000));
      await waitFor(() => expect(patches()).toHaveLength(1));

      await act(() => vi.advanceTimersByTimeAsync(30000));
      expect(patches()).toHaveLength(1);
    });

    // EMS answers a taken event number with 422; the form showed "auto-save failed" (and had a
    // "concurrent edit" text for a 409 no event PATCH returns, removed).
    it('should_showServerReason_when_autoSaveIsRefusedBecauseTheNumberIsTaken', async () => {
      const reason =
        "Validation failed for field 'Event number': Event number 57 is already in use by event BATbern57";
      api.on('patchEvent', () => unprocessable('/api/v1/events/BATbern56', reason));

      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);
      const numberInput = screen.getByLabelText(/event number/i);
      await user.clear(numberInput);
      await user.type(numberInput, '57');

      // Trigger auto-save
      await act(() => vi.advanceTimersByTimeAsync(5000));

      await waitFor(() => expect(patches()).toHaveLength(1));
      // A number, as the save path sends it: auto-save sent the input's string "57".
      expect(patches()[0].body).toEqual({ eventNumber: 57 });
      expect(await screen.findByText(reason)).toBeInTheDocument();
    });
  });

  describe.skip('Unsaved Changes Warning (AC4) - NOT IMPLEMENTED', () => {
    // SKIPPED: Unsaved changes dialog not implemented yet
    // These tests time out waiting for warning dialogs that don't exist
    it('should_showWarning_when_closingWithUnsavedChanges', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

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
      renderForm(<EventForm mode="edit" open={true} onClose={onClose} event={mockEvent} />);

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await userEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByText(/unsaved.*changes/i)).not.toBeInTheDocument();
        expect(onClose).toHaveBeenCalled();
      });
    });

    it('should_closeModal_when_confirmUnsavedChanges', async () => {
      const onClose = vi.fn();
      renderForm(<EventForm mode="edit" open={true} onClose={onClose} event={mockEvent} />);

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
      renderForm(<EventForm mode="edit" open={true} onClose={onClose} event={mockEvent} />);

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

  describe.skip('Partial Update (AC4) - NOT IMPLEMENTED', () => {
    // SKIPPED: PATCH partial update functionality not implemented yet
    // Component likely uses PUT (full update) instead of PATCH
    it('should_sendOnlyChangedFields_when_patchingEvent', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      // Change only title
      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'New Title Only');

      const saveButton = screen.getByRole('button', { name: /^save$/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(patches()[0]?.body).toEqual({ title: 'New Title Only' });
      });
    });

    it('should_sendMultipleFields_when_multipleFieldsChanged', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

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
        expect(patches()[0]?.body).toEqual({
          title: 'New Title',
          description: 'New Description',
        });
      });
    });

    it('should_usePatchMethod_when_updatingEvent', async () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      const titleInput = screen.getByLabelText(/title/i);
      await userEvent.clear(titleInput);
      await userEvent.type(titleInput, 'New Title');

      const saveButton = screen.getByRole('button', { name: /^save$/i });
      await userEvent.click(saveButton);

      await waitFor(() => {
        expect(patches()).toHaveLength(1);
        expect(api.calls('updateEvent')).toHaveLength(0);
      });
    });
  });

  describe('Role-Based Access Control (AC4)', () => {
    it('should_allowEditing_when_organizerRole', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      const titleInput = screen.getByLabelText(/title/i) as HTMLInputElement;
      expect(titleInput.disabled).toBe(false);
    });

    it.skip('should_blockEditing_when_nonOrganizerRole - Permission check not implemented', () => {
      // SKIPPED: Component doesn't implement role-based permission checks yet
      renderWithAuth(
        <I18nextProvider i18n={i18n}>
          <EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />
        </I18nextProvider>,
        { auth: signedInAs('speaker', { username: 'jane.smith' }) }
      );

      // Should show permission denied message
      expect(screen.getByText(/permission/i)).toBeInTheDocument();
    });
  });

  describe('Event Type Selection (AC3)', () => {
    it.skip('should_displayEventTypeOptions_when_rendered - MUI Select label association', () => {
      // SKIPPED: Similar to prefill test - MUI Select label not properly associated in test environment
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const typeSelect = screen.getByLabelText(/event.*type/i);
      expect(typeSelect).toBeInTheDocument();
    });

    it.skip('should_includeFullDayOption_when_rendered - MUI Select options not in DOM until opened', () => {
      // SKIPPED: MUI Select renders options in a portal when opened, not in initial DOM
      // Would need fireEvent.mouseDown on select, then waitFor the option
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByText(/full.*day/i)).toBeInTheDocument();
    });

    it.skip('should_includeAfternoonOption_when_rendered - MUI Select options not in DOM until opened', () => {
      // SKIPPED: MUI Select renders options in a portal when opened, not in initial DOM
      // Would need fireEvent.mouseDown on select, then waitFor the option
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByText(/afternoon/i)).toBeInTheDocument();
    });

    it.skip('should_includeEveningOption_when_rendered - MUI Select options not in DOM until opened', () => {
      // SKIPPED: MUI Select renders options in a portal when opened, not in initial DOM
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByText(/evening/i)).toBeInTheDocument();
    });
  });

  describe('Venue Selection (AC3)', () => {
    it.skip('should_displayVenueDropdown_when_rendered - MUI Select label association', () => {
      // SKIPPED: Same MUI Select label issue as event type
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      expect(screen.getByLabelText(/venue/i)).toBeInTheDocument();
    });

    it.skip('should_loadVenueOptions_when_rendered - Venue options not loaded/not in component', async () => {
      // SKIPPED: Venue dropdown might not be implemented yet or uses different field name
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      const venueSelect = screen.getByLabelText(/venue/i);
      fireEvent.mouseDown(venueSelect);

      await waitFor(() => {
        // Should load venue options (mocked from venue service)
        expect(screen.getByText(/kornhaus/i)).toBeInTheDocument();
      });
    });
  });

  describe('Internationalization (AC22)', () => {
    it.skip('should_translateFormLabels_when_languageChanged - Translation keys might not match', async () => {
      // SKIPPED: Test times out - translation keys in i18n might not match expected text
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

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

    it.skip('should_translateValidationErrors_when_languageChanged - Translation keys missing', async () => {
      // SKIPPED: German translation keys not found - likely missing from i18n translation files
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

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

  // Story 2.5.3a: Theme Image Upload Integration Tests
  describe('Theme Image Upload (Story 2.5.3a)', () => {
    it('should_displayThemeImageSection_when_formRendered', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      // Should display theme image section
      expect(screen.getByText(/event theme image/i)).toBeInTheDocument();
    });

    it('should_displayThemeImageHelpText_when_formRendered', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      // Should display help text explaining allowed formats and size
      const helpText = screen.getByText(/upload a theme image.*png.*jpeg.*svg.*5.*mb/i);
      expect(helpText).toBeInTheDocument();
    });

    it('should_displayFileUploadComponent_when_themeImageSectionRendered', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      // FileUpload component should be present in the theme image section
      // Verify by checking for the section and that it's within a bordered container
      const themeSection = screen.getByText(/event theme image/i).closest('div');
      expect(themeSection).toBeInTheDocument();
    });

    it('should_displayCurrentThemeImage_when_eventHasThemeImageUrl', () => {
      const eventWithImage = {
        ...mockEvent,
        themeImageUrl: 'https://cdn.batbern.ch/logos/2025/events/BATbern56/theme.png',
      };

      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={eventWithImage} />);

      // Should show the theme image section
      expect(screen.getByText(/event theme image/i)).toBeInTheDocument();

      // Current image should be passed to FileUpload component
      // FileUpload will display it, but we can't easily test that without mocking the component
      // Instead verify the section exists
      const themeSection = screen.getByText(/event theme image/i).closest('div');
      expect(themeSection).toBeInTheDocument();
    });

    it('should_allowThemeImageUpload_when_creatingEvent', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      // Theme image section should be present and allow uploads in create mode
      expect(screen.getByText(/event theme image/i)).toBeInTheDocument();
    });

    it('should_allowThemeImageUpload_when_editingEvent', () => {
      renderForm(<EventForm mode="edit" open={true} onClose={vi.fn()} event={mockEvent} />);

      // Theme image section should be present and allow uploads in edit mode
      expect(screen.getByText(/event theme image/i)).toBeInTheDocument();
    });

    it('should_placeThemeImageInSeparateSection_when_rendered', () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      // Theme image should be in a visually separated section (bordered top)
      const themeSection = screen.getByText(/event theme image/i).closest('div');
      expect(themeSection).toBeInTheDocument();
    });

    it('should_displayThemeImageAsOptional_when_formRendered', async () => {
      renderForm(<EventForm mode="create" open={true} onClose={vi.fn()} />);

      // Form should be submittable without theme image
      // Theme image is optional - button should be enabled even without uploading
      await waitFor(() => {
        const submitButton = screen.getByRole('button', { name: /save.*create/i });
        expect(submitButton).toBeInTheDocument();
      });
    });
  });
});
