/**
 * EventVenueTab Component Tests (Story 5.6)
 *
 * Tests for the venue & logistics tab showing venue details, catering, and schedule.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventVenueTab } from '../EventVenueTab';
import type { Event, EventDetailUI } from '@/types/event.types';

// Mock VenueLogistics component
vi.mock('@/components/organizer/EventManagement', () => ({
  VenueLogistics: ({ event }: { event: Event }) => (
    <div data-testid="venue-logistics">{event.eventCode}</div>
  ),
}));

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

const mockEventWithBooking: EventDetailUI = {
  ...mockEvent,
  booking: {
    confirmationNumber: 'CONF-2025-001',
    confirmed: true,
  },
  catering: {
    provider: 'Bern Catering Services',
    dietaryRequirements: {
      vegetarian: 15,
      vegan: 8,
      glutenFree: 5,
    },
  },
};

// Test wrapper with providers
const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('EventVenueTab Component (Story 5.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('renders venue information with all details', () => {
      renderWithProviders(<EventVenueTab event={mockEvent} />);

      // Venue details
      expect(screen.getByText('Kursaal Bern')).toBeInTheDocument();
      expect(screen.getByText('Kornhausstrasse 3, 3013 Bern')).toBeInTheDocument();
      expect(screen.getByText(/200/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Change Venue/i })).toBeInTheDocument();
      expect(screen.getByText(/Parking/i)).toBeInTheDocument();
      expect(screen.getByText(/Accessible/i)).toBeInTheDocument();

      // Day Schedule
      expect(screen.getByText(/Day Schedule/i)).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: /Edit/i }).length).toBeGreaterThan(0);
      expect(screen.getByText('08:00')).toBeInTheDocument();
      expect(screen.getByText('09:00')).toBeInTheDocument();
      expect(screen.getByText('12:30')).toBeInTheDocument();
      expect(screen.getByText('19:00')).toBeInTheDocument();
      expect(screen.getByText(/Registration.*Coffee/i)).toBeInTheDocument();
      expect(screen.getByText(/Lunch Break/i)).toBeInTheDocument();
      expect(screen.getByText(/Networking/i)).toBeInTheDocument();
      expect(screen.getByText(/Event Ends/i)).toBeInTheDocument();

      // VenueLogistics integration
      expect(screen.getByTestId('venue-logistics')).toBeInTheDocument();
      expect(screen.getByTestId('venue-logistics')).toHaveTextContent('BAT54');
    });
  });

  describe('Conditional Rendering - Booking Status', () => {
    it('displays warning when venue not booked', () => {
      renderWithProviders(<EventVenueTab event={mockEvent} />);

      expect(screen.getByText(/Venue not yet booked/i)).toBeInTheDocument();
    });

    it('displays confirmation when booking exists', () => {
      renderWithProviders(<EventVenueTab event={mockEventWithBooking} />);

      expect(screen.getByText(/Confirmed/i)).toBeInTheDocument();
      expect(screen.getByText(/CONF-2025-001/)).toBeInTheDocument();
    });
  });

  describe('Conditional Rendering - Catering', () => {
    it('displays configure message when no catering', () => {
      renderWithProviders(<EventVenueTab event={mockEvent} />);

      expect(screen.getByRole('button', { name: /Configure/i })).toBeInTheDocument();
      expect(screen.getByText(/Catering not yet configured/i)).toBeInTheDocument();
    });

    it('displays dietary requirements when catering configured', () => {
      renderWithProviders(<EventVenueTab event={mockEventWithBooking} />);

      expect(screen.getByText(/15 Vegetarian/i)).toBeInTheDocument();
      expect(screen.getByText(/8 Vegan/i)).toBeInTheDocument();
      expect(screen.getByText(/5 Gluten-free/i)).toBeInTheDocument();
    });
  });

  describe('Edge Cases - Missing Data', () => {
    it('handles missing venue name gracefully', () => {
      const eventWithoutVenue = { ...mockEvent, venueName: undefined };
      renderWithProviders(<EventVenueTab event={eventWithoutVenue} />);

      expect(screen.getByText('-')).toBeInTheDocument();
    });

    it('handles missing address gracefully', () => {
      const eventWithoutAddress = { ...mockEvent, venueAddress: undefined };
      renderWithProviders(<EventVenueTab event={eventWithoutAddress} />);

      expect(screen.getByText(/No address provided/i)).toBeInTheDocument();
    });

    it('handles missing capacity gracefully', () => {
      const eventWithoutCapacity = { ...mockEvent, venueCapacity: undefined };
      renderWithProviders(<EventVenueTab event={eventWithoutCapacity} />);

      expect(screen.getByText('-')).toBeInTheDocument();
    });
  });
});
