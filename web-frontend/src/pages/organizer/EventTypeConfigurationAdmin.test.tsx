/**
 * EventTypeConfigurationAdmin Page Tests (RED Phase - TDD)
 *
 * Story 5.1 - Task 3a
 * AC: 7 (React Component - Admin page for event type configuration)
 * Wireframe: docs/wireframes/story-5.1-event-type-configuration.md v1.0 (Screen 4)
 *
 * Tests for event type configuration admin page with:
 * - List of all event type configurations (3 cards)
 * - Edit button for each event type (opens modal)
 * - ORGANIZER-only access (role-based)
 * - i18n compliance (all text uses react-i18next)
 * - Generated types usage from events-api.types.ts
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventTypeConfigurationAdmin } from './EventTypeConfigurationAdmin';
import type { components } from '@/types/generated/events-api.types';

type EventSlotConfigurationResponse = components['schemas']['EventSlotConfigurationResponse'];

// Mock eventTypeService with generated types
const mockEventTypes: EventSlotConfigurationResponse[] = [
  {
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
  },
  {
    type: 'AFTERNOON',
    minSlots: 6,
    maxSlots: 8,
    slotDuration: 30,
    theoreticalSlotsAM: false,
    breakSlots: 1,
    lunchSlots: 0,
    defaultCapacity: 150,
    typicalStartTime: '13:00',
    typicalEndTime: '18:00',
  },
  {
    type: 'EVENING',
    minSlots: 3,
    maxSlots: 4,
    slotDuration: 45,
    theoreticalSlotsAM: false,
    breakSlots: 1,
    lunchSlots: 0,
    defaultCapacity: 100,
    typicalStartTime: '18:00',
    typicalEndTime: '21:00',
  },
];

vi.mock('@/services/eventTypeService', () => ({
  eventTypeService: {
    getAllEventTypes: vi.fn(() => Promise.resolve(mockEventTypes)),
    updateEventType: vi.fn(() => Promise.resolve(mockEventTypes[0])),
  },
}));

// Mock authStore (ORGANIZER role by default)
const mockAuthStore = vi.fn(() => ({
  user: { username: 'admin', role: 'organizer' },
  isAuthenticated: true,
}));

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => mockAuthStore(),
}));

describe('EventTypeConfigurationAdmin Page', () => {
  let queryClient: QueryClient;

  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    return ({ children }: { children: React.ReactNode }) => (
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <I18nextProvider i18n={i18n}>{children}</I18nextProvider>
        </QueryClientProvider>
      </BrowserRouter>
    );
  };

  beforeEach(() => {
    queryClient?.clear();
  });

  /**
   * Test 7.8a: should_displayThreeEventTypeCards_when_pageRendered
   * AC7: Page displays all three event type configurations as cards
   */
  it('should_displayThreeEventTypeCards_when_pageRendered', async () => {
    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText(/full day event/i)).toBeInTheDocument();
      expect(screen.getByText(/afternoon event/i)).toBeInTheDocument();
      expect(screen.getByText(/evening event/i)).toBeInTheDocument();
    });
  });

  /**
   * Test 7.8b: should_displayEditButtons_when_pageRendered
   * AC7: Each event type card has an edit button
   */
  it('should_displayEditButtons_when_pageRendered', async () => {
    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    await waitFor(() => {
      const editButtons = screen.getAllByRole('button', { name: /edit/i });
      expect(editButtons).toHaveLength(3);
    });
  });

  /**
   * Test 7.8c: should_openEditModal_when_editButtonClicked
   * AC7: Clicking edit button opens EventTypeConfigurationForm modal
   */
  it('should_openEditModal_when_editButtonClicked', async () => {
    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText(/full day event/i)).toBeInTheDocument();
    });

    // Click first edit button (FULL_DAY)
    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    fireEvent.click(editButtons[0]);

    // Verify modal is opened with form
    await waitFor(() => {
      expect(screen.getByLabelText(/minimum slots/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/maximum slots/i)).toBeInTheDocument();
    });
  });

  /**
   * Test 7.8d: should_displayConfigurationDetails_when_pageRendered
   * AC7: Each card displays slot range, duration, and capacity
   */
  it('should_displayConfigurationDetails_when_pageRendered', async () => {
    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    await waitFor(() => {
      // FULL_DAY configuration (unique values)
      expect(screen.getByText(/capacity: 200/i)).toBeInTheDocument();

      // AFTERNOON configuration (unique values)
      expect(screen.getByText(/30 min each/i)).toBeInTheDocument();
      expect(screen.getByText(/capacity: 150/i)).toBeInTheDocument();

      // EVENING configuration (unique values)
      expect(screen.getByText(/3-4 slots/i)).toBeInTheDocument();
      expect(screen.getByText(/capacity: 100/i)).toBeInTheDocument();

      // Check that slot ranges are present (use getAllByText for duplicates)
      const slotRanges = screen.getAllByText(/6-8 slots/i);
      expect(slotRanges.length).toBeGreaterThanOrEqual(1); // FULL_DAY and AFTERNOON both have 6-8 slots
    });
  });

  /**
   * Test 7.8e: should_closeModal_when_cancelButtonClicked
   * AC7: Clicking cancel in modal closes the edit form
   */
  it('should_closeModal_when_cancelButtonClicked', async () => {
    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText(/full day event/i)).toBeInTheDocument();
    });

    // Open modal
    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    fireEvent.click(editButtons[0]);

    await waitFor(() => {
      expect(screen.getByLabelText(/minimum slots/i)).toBeInTheDocument();
    });

    // Click cancel
    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelButton);

    // Verify modal is closed
    await waitFor(() => {
      expect(screen.queryByLabelText(/minimum slots/i)).not.toBeInTheDocument();
    });
  });

  /**
   * Test 7.8f: should_useI18nKeys_when_pageRendered
   * i18n compliance: All user-facing text uses react-i18next
   */
  it('should_useI18nKeys_when_pageRendered', async () => {
    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    await waitFor(() => {
      // Page title from events.eventTypeConfig.title
      expect(screen.getByText(/event type configuration/i)).toBeInTheDocument();

      // Event type names from events.form.eventTypes.*
      expect(screen.getByText(/full day event/i)).toBeInTheDocument();
      expect(screen.getByText(/afternoon event/i)).toBeInTheDocument();
      expect(screen.getByText(/evening event/i)).toBeInTheDocument();
    });
  });

  /**
   * Test 7.8g: should_requireOrganizerRole_when_accessAttempted
   * AC7: Page requires ORGANIZER role (tested via authStore mock)
   */
  it('should_requireOrganizerRole_when_accessAttempted', async () => {
    // Mock non-organizer user
    mockAuthStore.mockReturnValueOnce({
      user: { username: 'speaker', role: 'speaker' },
      isAuthenticated: true,
    });

    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    // Verify access denied message is displayed
    await waitFor(() => {
      expect(screen.getByText(/access denied/i)).toBeInTheDocument();
    });
  });

  /**
   * Test 7.8h: should_useGeneratedTypes_when_pageRendered
   * ADR-006: Page uses generated types from events-api.types.ts
   * (This is a TypeScript compile-time check - if types are wrong, component won't compile)
   */
  it('should_useGeneratedTypes_when_pageRendered', async () => {
    render(<EventTypeConfigurationAdmin />, { wrapper: createWrapper() });

    await waitFor(() => {
      // Type check: Verify we can access generated type properties
      const testConfig: EventSlotConfigurationResponse = mockEventTypes[0];
      expect(testConfig.type).toBe('FULL_DAY');
      expect(testConfig.minSlots).toBe(6);

      // Runtime check: Verify component renders correctly with generated types
      expect(screen.getByText(/full day event/i)).toBeInTheDocument();
    });
  });
});
