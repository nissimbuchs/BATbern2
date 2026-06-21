/**
 * EventTypeConfigurationForm Component Tests (RED Phase - TDD)
 *
 * Story 5.1 - Task 3a
 * AC: 7 (React Component - EventTypeConfigurationForm for admin editing)
 * Wireframe: docs/wireframes/story-5.1-event-type-configuration.md v1.0 (Screen 5)
 *
 * Tests for event type configuration form with:
 * - Form fields for all configuration properties
 * - Validation (minSlots <= maxSlots, slotDuration >= 15, etc.)
 * - Save callback with validated data
 * - Cancel callback
 * - i18n compliance (all text uses react-i18next)
 * - Generated types usage from events-api.types.ts
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventTypeConfigurationForm } from './EventTypeConfigurationForm';
import { computeScheduleEndTime } from './scheduleTimeline';
import type { components } from '@/types/generated/events-api.types';

type EventType = components['schemas']['EventType'];
type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];
type EventSlotConfigurationResponse = components['schemas']['EventSlotConfigurationResponse'];

// Mock useEventType hook to return test data
const mockEventTypeData: EventSlotConfigurationResponse = {
  type: 'FULL_DAY',
  minSlots: 6,
  maxSlots: 8,
  slotDuration: 45,
  theoreticalSlotsAM: true,
  breakSlots: 2,
  lunchSlots: 1,
  defaultCapacity: 200,
  typicalStartTime: '09:00',
  typicalEndTime: '17:00',
};

vi.mock('@/hooks/useEventTypes', () => ({
  useEventType: vi.fn(() => ({
    data: mockEventTypeData,
    isLoading: false,
    error: null,
  })),
}));

describe('EventTypeConfigurationForm Component', () => {
  const mockOnSave = vi.fn(() => Promise.resolve());
  const mockOnCancel = vi.fn();

  const mockEventType: EventType = 'FULL_DAY';

  const createWrapper = () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    return ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>{children}</I18nextProvider>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    mockOnSave.mockClear();
    mockOnCancel.mockClear();
  });

  /**
   * Test 7.5a: should_displayAllFormFields_when_formRendered
   * AC7: Form displays all configuration fields
   */
  it('should_displayAllFormFields_when_formRendered', () => {
    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByLabelText(/minimum slots/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/maximum slots/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/slot duration/i)).toBeInTheDocument();
    expect(screen.getByText(/theoretical slots/i)).toBeInTheDocument(); // FormControlLabel uses text, not label
    expect(screen.getByLabelText(/break slots/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/lunch slots/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/default capacity/i)).toBeInTheDocument();
  });

  /**
   * Test 7.5a-bis: should_displayStartAndEndTimeFields_when_formRendered
   * Regression: the modal previously omitted the start/end time inputs, so editing
   * any other field round-tripped the times as undefined and the mapper nulled them.
   */
  it('should_displayStartAndEndTimeFields_when_formRendered', () => {
    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    const startInput = screen.getByLabelText(/start time/i) as HTMLInputElement;
    const endInput = screen.getByLabelText(/end time/i) as HTMLInputElement;
    expect(startInput).toBeInTheDocument();
    expect(endInput).toBeInTheDocument();
    // Start time is editable and pre-populated from the current configuration.
    expect(startInput.value).toBe('09:00');
    expect(startInput.readOnly).toBe(false);
    // End time is read-only and derived from the computed timetable (NOT the stored 17:00).
    expect(endInput.readOnly).toBe(true);
    expect(endInput.value).toBe(computeScheduleEndTime(mockEventTypeData));
  });

  /**
   * Test 7.5a-ter: should_preserveStartTime_and_recomputeEnd_when_otherFieldEdited
   * Regression guard for the data trap: editing slot count must NOT drop the start time;
   * the end time follows the recomputed timetable.
   */
  it('should_preserveStartTime_and_recomputeEnd_when_otherFieldEdited', async () => {
    const user = userEvent.setup();

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    await user.clear(screen.getByLabelText(/maximum slots/i));
    await user.type(screen.getByLabelText(/maximum slots/i), '7');

    await user.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => {
      expect(mockOnSave).toHaveBeenCalledWith(
        expect.objectContaining({
          maxSlots: 7,
          typicalStartTime: '09:00',
          typicalEndTime: computeScheduleEndTime({ ...mockEventTypeData, maxSlots: 7 }),
        })
      );
    });
  });

  /**
   * Test 7.5a-quater: should_recomputeEndTime_when_startTimeChanged
   * Start time is the only editable timing input; the end time shifts with it.
   */
  it('should_recomputeEndTime_when_startTimeChanged', async () => {
    const user = userEvent.setup();

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    const startInput = screen.getByLabelText(/start time/i) as HTMLInputElement;
    // time inputs: set via fireEvent.change (userEvent.type is unreliable for type=time)
    fireEvent.change(startInput, { target: { value: '16:00' } });

    // The read-only end field reflects the recomputed end immediately.
    const endInput = screen.getByLabelText(/end time/i) as HTMLInputElement;
    const expectedEnd = computeScheduleEndTime({ ...mockEventTypeData, typicalStartTime: '16:00' });
    await waitFor(() => expect(endInput.value).toBe(expectedEnd));

    await user.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => {
      expect(mockOnSave).toHaveBeenCalledWith(
        expect.objectContaining({
          typicalStartTime: '16:00',
          typicalEndTime: expectedEnd,
        })
      );
    });
  });

  /**
   * Test 7.5b: should_displaySaveAndCancelButtons_when_formRendered
   * AC7: Form displays Save and Cancel buttons
   */
  it('should_displaySaveAndCancelButtons_when_formRendered', () => {
    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  /**
   * Test 7.5c: should_callOnSave_when_validFormSubmitted
   * AC7: Submitting valid form calls onSave with correct data
   */
  it('should_callOnSave_when_validFormSubmitted', async () => {
    const user = userEvent.setup();

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    // Fill form with valid data
    await user.clear(screen.getByLabelText(/minimum slots/i));
    await user.type(screen.getByLabelText(/minimum slots/i), '6');

    await user.clear(screen.getByLabelText(/maximum slots/i));
    await user.type(screen.getByLabelText(/maximum slots/i), '8');

    await user.clear(screen.getByLabelText(/slot duration/i));
    await user.type(screen.getByLabelText(/slot duration/i), '45');

    await user.clear(screen.getByLabelText(/default capacity/i));
    await user.type(screen.getByLabelText(/default capacity/i), '200');

    // Submit form
    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    // Verify onSave was called with correct data
    await waitFor(() => {
      expect(mockOnSave).toHaveBeenCalledWith(
        expect.objectContaining({
          minSlots: 6,
          maxSlots: 8,
          slotDuration: 45,
          defaultCapacity: 200,
        })
      );
    });
  });

  /**
   * Test 7.5d: should_showValidationError_when_minSlotsExceedsMax
   * AC8: Validation error when minSlots > maxSlots
   */
  it('should_showValidationError_when_minSlotsExceedsMax', async () => {
    const user = userEvent.setup();

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    // Set minSlots > maxSlots
    await user.clear(screen.getByLabelText(/minimum slots/i));
    await user.type(screen.getByLabelText(/minimum slots/i), '10');

    await user.clear(screen.getByLabelText(/maximum slots/i));
    await user.type(screen.getByLabelText(/maximum slots/i), '5');

    // Submit form
    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    // Verify validation error is displayed
    await waitFor(() => {
      expect(
        screen.getByText(/minimum slots must be less than or equal to maximum slots/i)
      ).toBeInTheDocument();
    });

    // Verify onSave was NOT called
    expect(mockOnSave).not.toHaveBeenCalled();
  });

  /**
   * Test 7.5e: should_preventSave_when_slotDurationTooSmall
   * AC8: Validation prevents save when slotDuration < 15 minutes
   */
  it('should_preventSave_when_slotDurationTooSmall', async () => {
    const user = userEvent.setup();

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    // Set slotDuration < 15
    const slotDurationInput = screen.getByLabelText(/slot duration/i);
    await user.clear(slotDurationInput);
    await user.type(slotDurationInput, '10');

    // Submit form
    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    // Verify onSave was NOT called (validation prevents save)
    await waitFor(() => {
      expect(mockOnSave).not.toHaveBeenCalled();
    });
  });

  /**
   * Test 7.5f: should_callOnCancel_when_cancelButtonClicked
   * AC7: Clicking Cancel button calls onCancel
   */
  it('should_callOnCancel_when_cancelButtonClicked', async () => {
    const user = userEvent.setup();

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    await user.click(cancelButton);

    expect(mockOnCancel).toHaveBeenCalled();
  });

  /**
   * Test 7.5g: should_useI18nKeys_when_formRendered
   * i18n compliance: All user-facing text uses react-i18next
   */
  it('should_useI18nKeys_when_formRendered', () => {
    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    // Verify i18n keys are used (from events.form.* and events.validation.*)
    expect(screen.getByLabelText(/minimum slots/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  /**
   * Test 7.5h: should_useGeneratedTypes_when_formSubmitted
   * ADR-006: Form uses generated types from events-api.types.ts
   * (This is a TypeScript compile-time check - if types are wrong, component won't compile)
   */
  it('should_useGeneratedTypes_when_formSubmitted', async () => {
    const user = userEvent.setup();

    // Type check: This will fail to compile if generated types are not used
    const typedOnSave = vi.fn((data: UpdateEventSlotConfigurationRequest) => Promise.resolve());

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={typedOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    // Fill form with valid data
    await user.clear(screen.getByLabelText(/minimum slots/i));
    await user.type(screen.getByLabelText(/minimum slots/i), '6');

    await user.clear(screen.getByLabelText(/maximum slots/i));
    await user.type(screen.getByLabelText(/maximum slots/i), '8');

    await user.clear(screen.getByLabelText(/slot duration/i));
    await user.type(screen.getByLabelText(/slot duration/i), '45');

    await user.clear(screen.getByLabelText(/default capacity/i));
    await user.type(screen.getByLabelText(/default capacity/i), '200');

    // Submit form
    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    // Verify callback was called with correct type
    await waitFor(() => {
      expect(typedOnSave).toHaveBeenCalled();
    });
  });

  /**
   * Story 15.2: apéro is a checkbox (on/off); duration + position reveal only when enabled.
   */
  it('should_toggleAperitifFields_when_aperitifCheckboxClicked', async () => {
    const user = userEvent.setup();

    render(
      <EventTypeConfigurationForm
        eventType={mockEventType}
        onSave={mockOnSave}
        onCancel={mockOnCancel}
      />,
      { wrapper: createWrapper() }
    );

    // Apéro off by default (mock template has no apéro) → duration/position hidden
    expect(screen.queryByTestId('event-type-config-aperitif-duration')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText(/add an apéro/i));

    await waitFor(() => {
      expect(screen.getByTestId('event-type-config-aperitif-duration')).toBeInTheDocument();
      expect(screen.getByTestId('event-type-config-aperitif-position')).toBeInTheDocument();
    });
  });
});
