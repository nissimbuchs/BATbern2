/**
 * EventLogistics Component Tests
 * Story 4.1.3: Event Landing Page Hero Section
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EventLogistics } from './EventLogistics';
import type { EventDetail } from '@/types/event.types';

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'public.logistics.date': 'Date',
        'public.logistics.time': 'Time',
        'public.logistics.location': 'Location',
        'public.logistics.capacity': 'Capacity',
      };
      return translations[key] || key;
    },
  }),
}));

describe('EventLogistics', () => {
  const mockEvent: EventDetail = {
    id: 'BAT-025',
    eventNumber: 25,
    title: 'Test Event',
    description: 'Test Description',
    date: '2025-03-15T18:00:00Z',
    registrationDeadline: '2025-03-10T23:59:59Z',
    venueName: 'Bern TechHub',
    venueAddress: 'Waisenhausplatz 30, 3011 Bern',
    venueCapacity: 120,
    currentAttendeeCount: 87,
    status: 'PUBLISHED',
    organizerId: 'org-1',
    typicalStartTime: '16:00',
    typicalEndTime: '19:00',
  };

  it('should render date correctly', () => {
    render(<EventLogistics event={mockEvent} />);
    expect(screen.getByText('Date')).toBeInTheDocument();
    expect(screen.getByText(/March 15/)).toBeInTheDocument();
  });

  it('should render time correctly', () => {
    render(<EventLogistics event={mockEvent} />);
    expect(screen.getByText('Time')).toBeInTheDocument();
    // Time is displayed in the format "HH:MMh - HH:MMh"
    // The component uses typicalStartTime and typicalEndTime from event type
    expect(screen.getByText('16:00h - 19:00h')).toBeInTheDocument();
  });

  it('should render location with venue name', () => {
    render(<EventLogistics event={mockEvent} />);
    expect(screen.getByText('Location')).toBeInTheDocument();
    expect(screen.getByText('Bern TechHub')).toBeInTheDocument();
  });

  it('should render venue address city', () => {
    render(<EventLogistics event={mockEvent} />);
    expect(screen.getByText('Waisenhausplatz 30')).toBeInTheDocument();
  });

  it('should render capacity with current count', () => {
    render(<EventLogistics event={mockEvent} />);
    expect(screen.getByText('Capacity')).toBeInTheDocument();
    expect(screen.getByText('87 / 120')).toBeInTheDocument();
  });

  it('should render capacity with 0 when no currentAttendeeCount', () => {
    const eventWithoutAttendees = { ...mockEvent, currentAttendeeCount: undefined };
    render(<EventLogistics event={eventWithoutAttendees} />);
    expect(screen.getByText('0 / 120')).toBeInTheDocument();
  });

  it('should not render time section when typicalStartTime is missing', () => {
    const eventWithoutTime = { ...mockEvent, typicalStartTime: undefined };
    render(<EventLogistics event={eventWithoutTime} />);
    expect(screen.queryByText('Time')).not.toBeInTheDocument();
  });

  it('should not render location section when venueName is missing', () => {
    const eventWithoutVenue = { ...mockEvent, venueName: undefined };
    render(<EventLogistics event={eventWithoutVenue} />);
    expect(screen.queryByText('Location')).not.toBeInTheDocument();
  });

  it('should not render capacity section when venueCapacity is missing', () => {
    const eventWithoutCapacity = { ...mockEvent, venueCapacity: undefined };
    render(<EventLogistics event={eventWithoutCapacity} />);
    expect(screen.queryByText('Capacity')).not.toBeInTheDocument();
  });

  it('should render with responsive grid layout', () => {
    const { container } = render(<EventLogistics event={mockEvent} />);
    const gridContainer = container.querySelector('.grid');
    expect(gridContainer).toHaveClass('grid-cols-1', 'sm:grid-cols-2', 'lg:grid-cols-4');
  });

  it('should display icons with correct styling', () => {
    const { container } = render(<EventLogistics event={mockEvent} />);
    const icons = container.querySelectorAll('svg');
    expect(icons.length).toBeGreaterThan(0);

    icons.forEach((icon) => {
      expect(icon).toHaveClass('text-primary');
    });
  });
});
