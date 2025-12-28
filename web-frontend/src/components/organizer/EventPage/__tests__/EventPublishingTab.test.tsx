/**
 * EventPublishingTab Component Tests (Story 5.6)
 *
 * Tests for the publishing configuration, timeline, and quality checkpoints tab.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventPublishingTab } from '../EventPublishingTab';
import type { Event } from '@/types/event.types';

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
const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('EventPublishingTab Component (Story 5.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('renders all sections with expected content', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      // Publishing Status Section
      expect(screen.getByText(/Publishing Status/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Configure/i })).toBeInTheDocument();
      expect(screen.getByText(/Strategy/i)).toBeInTheDocument();
      expect(screen.getByText(/Progressive Publishing/i)).toBeInTheDocument();
      expect(screen.getByText(/Current Phase/i)).toBeInTheDocument();

      // Publishing Timeline Section
      expect(screen.getByRole('heading', { name: /Publishing Timeline/i })).toBeInTheDocument();
      expect(screen.getByText(/Jan 5, 2025|5 Jan 2025/i)).toBeInTheDocument();
      expect(screen.getByText(/Feb 15, 2025|15 Feb 2025/i)).toBeInTheDocument();
      const completedPhases = screen.getAllByText(/Topic Published|Speakers Published/i);
      expect(completedPhases.length).toBeGreaterThan(0);

      // Quality Checkpoints Section
      expect(screen.getByText(/Quality Checkpoints/i)).toBeInTheDocument();
      expect(screen.getByText(/Abstract length validation/i)).toBeInTheDocument();
      expect(screen.getByText(/Lessons learned requirement/i)).toBeInTheDocument();
      expect(screen.getByText(/All materials submitted/i)).toBeInTheDocument();
      expect(screen.getByText(/Moderator review complete/i)).toBeInTheDocument();
      expect(screen.getByText(/2 pending/i)).toBeInTheDocument();
      expect(
        screen.getByText(/Resolve all checkpoints before publishing final agenda/i)
      ).toBeInTheDocument();

      // Actions Section
      expect(screen.getByText(/^Actions$/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Preview Public Page/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Republish Event/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Notify Attendees/i })).toBeInTheDocument();
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
    it('opens public page in new tab when preview button clicked', () => {
      renderWithProviders(<EventPublishingTab event={mockEvent} eventCode="BAT54" />);

      const previewButton = screen.getByRole('button', { name: /Preview Public Page/i });
      fireEvent.click(previewButton);

      expect(mockWindowOpen).toHaveBeenCalledWith('/events/BAT54', '_blank');
    });
  });
});
