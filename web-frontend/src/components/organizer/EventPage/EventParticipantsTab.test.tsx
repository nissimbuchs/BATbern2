/**
 * EventParticipantsTab Component Tests
 *
 * TDD Tests for event participants tab container component
 * RED Phase: Tests written first
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import EventParticipantsTab from './EventParticipantsTab';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock EventParticipantList component
vi.mock('./EventParticipantList', () => ({
  default: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="participant-list">List for {eventCode}</div>
  ),
}));

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

const mockEvent = {
  eventCode: 'BAT-2024-01',
  eventNumber: 123,
  title: 'Test Event',
  subtitle: 'Test Subtitle',
  date: '2024-06-15T18:00:00Z',
  registrationDeadline: '2024-06-10T23:59:59Z',
  venueName: 'Test Venue',
  venueAddress: 'Test Address',
  venueCapacity: 200,
  organizerUsername: 'admin',
  currentWorkflowState: 'DRAFT' as const,
  workflowHistory: [],
  registrationCount: 0,
  attendanceCount: 0,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
};

describe('EventParticipantsTab Component', () => {
  describe('Rendering', () => {
    it('should render the participants list', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      expect(screen.getByTestId('participant-list')).toBeInTheDocument();
    });

    it('should pass event code to participant list', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      expect(screen.getByText('List for BAT-2024-01')).toBeInTheDocument();
    });

    it('should render tab title', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      expect(screen.getByText('eventPage.participantsTab.title')).toBeInTheDocument();
    });

    it('should render participant count badge', () => {
      const eventWithParticipants = {
        ...mockEvent,
        confirmedCount: 42,
        waitlistCount: 0,
      };

      renderWithProviders(<EventParticipantsTab event={eventWithParticipants} />);

      expect(screen.getByText('42')).toBeInTheDocument();
    });
  });

  describe('Event Data', () => {
    it('should handle event without registration count', () => {
      const eventNoCount = {
        ...mockEvent,
        registrationCount: undefined,
      };

      renderWithProviders(<EventParticipantsTab event={eventNoCount as any} />);

      expect(screen.getByText('0')).toBeInTheDocument();
    });

    it('should use event code from event prop', () => {
      const customEvent = {
        ...mockEvent,
        eventCode: 'CUSTOM-2024',
      };

      renderWithProviders(<EventParticipantsTab event={customEvent} />);

      expect(screen.getByText('List for CUSTOM-2024')).toBeInTheDocument();
    });
  });

  describe('Layout', () => {
    it('should render in a container box', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      const container = screen.getByTestId('participant-list').parentElement;
      expect(container).toBeInTheDocument();
    });

    it('should have proper spacing', () => {
      const { container } = renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      // Component should have proper MUI Box structure
      const boxes = container.querySelectorAll('.MuiBox-root');
      expect(boxes.length).toBeGreaterThan(0);
    });
  });
});
