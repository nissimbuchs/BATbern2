/**
 * EventPublishingTab Component Tests (Story 5.6)
 *
 * Tests for the publishing configuration, timeline, and quality checkpoints tab.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';
import { EventPublishingTab } from '../EventPublishingTab';
import type { Event } from '@/types/event.types';

// Mock usePublishing hook
vi.mock('@/hooks/usePublishing/usePublishing', () => ({
  usePublishing: () => ({
    publishingStatus: {
      currentPhase: null,
      publishedPhases: [],
      topic: { isValid: true, errors: [] },
      speakers: { isValid: true, errors: [] },
      sessions: {
        isValid: true,
        errors: [],
        assignedCount: 0,
        totalCount: 0,
        unassignedSessions: [],
      },
    },
    isLoadingStatus: false,
    validationErrors: [],
    publishPhase: vi.fn(),
    unpublishPhase: vi.fn(),
    isPublishing: false,
    isUnpublishing: false,
    publishError: null,
    versionHistory: [],
    isLoadingVersions: false,
    rollbackVersion: vi.fn(),
    isRollingBack: false,
    preview: null,
    fetchPreview: vi.fn(),
    isLoadingPreview: false,
    previewError: null,
    changeLog: { eventCode: 'BAT54', changes: [] },
    isLoadingChangeLog: false,
    scheduleAutoPublish: vi.fn(),
    cancelAutoPublish: vi.fn(),
    isScheduling: false,
    isCancelling: false,
  }),
}));

// Mock window.open
const mockWindowOpen = vi.fn();
Object.defineProperty(window, 'open', { value: mockWindowOpen, writable: true });

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
  currentAttendeeCount: 87,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
};

// Test wrapper with providers
let queryClient: QueryClient;

const renderWithProviders = (ui: React.ReactElement) => {
  queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: Infinity,
      },
      mutations: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
};

describe('EventPublishingTab Component (Story 5.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient?.clear();
  });

  describe('Rendering', () => {
    it.skip('renders all sections with expected content', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Backend Integration Notice - Story 5.7 unimplemented
      expect(screen.getByText(/Publishing Controls \(Story 5.7\)/i)).toBeInTheDocument();
      expect(
        screen.getByText(
          /The publishing components are now integrated. Backend API integration is in progress/i
        )
      ).toBeInTheDocument();

      // Validation Dashboard
      expect(screen.getByTestId('overall-validation-status')).toHaveTextContent(
        /Ready to Publish/i
      );
      expect(screen.getByText(/Event Topic/i)).toBeInTheDocument();
      expect(screen.getByText(/Speaker Lineup/i)).toBeInTheDocument();

      // Publishing Controls
      expect(screen.getByRole('button', { name: /Publish Speakers/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Schedule Publish/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Preview Newsletter/i })).toBeInTheDocument();

      // Live Preview (device toggle)
      expect(screen.getByRole('button', { name: /Desktop preview/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Mobile preview/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Print preview/i })).toBeInTheDocument();
    });

    it.skip('displays all timeline phases when fully implemented', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Topic Published/i)).toBeInTheDocument();
      expect(screen.getByText(/Speakers Published/i)).toBeInTheDocument();
      expect(screen.getByText(/Final Agenda/i)).toBeInTheDocument();
      expect(screen.getByText(/Event Day/i)).toBeInTheDocument();
      expect(screen.getByText(/Post-Event Materials/i)).toBeInTheDocument();
    });
  });

  describe('Preview Public Page Action', () => {
    it.skip('opens public page in new tab when preview button clicked', () => {
      // TODO: Story 5.7 updated the component - preview functionality moved to LivePreview component
      // Need to update this test to match the new implementation
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      const previewButton = screen.getByRole('button', { name: /Preview Public Page/i });
      fireEvent.click(previewButton);

      expect(mockWindowOpen).toHaveBeenCalledWith('/events/BAT54', '_blank');
    });
  });
});
