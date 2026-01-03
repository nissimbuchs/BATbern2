/**
 * EventProgram Component Tests (Story 4.1.4)
 * Tests for vertical timeline program display
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EventProgram } from './EventProgram';
import type { Session } from '@/types/event.types';

// Mock API clients
vi.mock('@/services/companyApiClient', () => ({
  companyApiClient: {
    getCompany: vi.fn(() => Promise.resolve({ name: 'Test Company', logoUrl: null })),
  },
}));

describe('EventProgram', () => {
  let queryClient: QueryClient;

  const renderWithProviders = (ui: React.ReactElement) => {
    return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
  };

  beforeEach(() => {
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

  const mockSessions: Session[] = [
    {
      sessionSlug: 'keynote-morning',
      eventCode: 'BATbern142',
      title: 'Opening Keynote',
      description: 'Welcome and introduction to the conference.',
      sessionType: 'keynote',
      startTime: '2025-05-15T09:00:00Z',
      endTime: '2025-05-15T10:00:00Z',
      room: 'Main Hall',
      capacity: 200,
      language: 'de',
      speakers: [
        {
          username: 'john.doe',
          firstName: 'John',
          lastName: 'Doe',
          speakerRole: 'PRIMARY_SPEAKER',
          isConfirmed: true,
        },
      ],
    },
    {
      sessionSlug: 'workshop-morning',
      eventCode: 'BATbern142',
      title: 'Morning Workshop',
      description: 'Interactive workshop session.',
      sessionType: 'workshop',
      startTime: '2025-05-15T09:00:00Z', // Same time as keynote
      endTime: '2025-05-15T10:00:00Z',
      room: 'Workshop Room A',
      capacity: 50,
      language: 'de',
      speakers: [
        {
          username: 'jane.smith',
          firstName: 'Jane',
          lastName: 'Smith',
          speakerRole: 'PRIMARY_SPEAKER',
          isConfirmed: true,
        },
      ],
    },
    {
      sessionSlug: 'lunch-break',
      eventCode: 'BATbern142',
      title: 'Lunch Break',
      description: 'Networking lunch with catering.',
      sessionType: 'lunch',
      startTime: '2025-05-15T12:00:00Z',
      endTime: '2025-05-15T13:00:00Z',
      room: 'Dining Hall',
      capacity: 300,
      language: 'de',
      speakers: [],
    },
  ];

  it('should_displayEventProgram_when_sessionsProvided', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    expect(screen.getByText('Event Program')).toBeInTheDocument();
    expect(screen.getByText('Opening Keynote')).toBeInTheDocument();
    expect(screen.getByText('Morning Workshop')).toBeInTheDocument();
    expect(screen.getByText('Lunch Break')).toBeInTheDocument();
  });

  it('should_groupSessionsByTime_when_multipleSessions', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    // Should have 2 time slots (09:00 and 12:00)
    const timeIndicators = screen.getAllByText(/^\d{2}:\d{2}$/);
    expect(timeIndicators.length).toBe(2);
  });

  it('should_displaySessionDetails_when_rendered', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    // Check descriptions
    expect(screen.getByText(/Welcome and introduction to the conference/i)).toBeInTheDocument();
    expect(screen.getByText(/Interactive workshop session/i)).toBeInTheDocument();

    // Check rooms
    expect(screen.getByText('Main Hall')).toBeInTheDocument();
    expect(screen.getByText('Workshop Room A')).toBeInTheDocument();
    expect(screen.getByText('Dining Hall')).toBeInTheDocument();
  });

  it('should_displaySpeakerNames_when_speakersAssigned', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    expect(screen.getByText(/John Doe/i)).toBeInTheDocument();
    expect(screen.getByText(/Jane Smith/i)).toBeInTheDocument();
  });

  it('should_displaySpeakerTBA_when_noSpeakersAssigned', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    expect(screen.getByText(/Speaker TBA/i)).toBeInTheDocument();
  });

  it('should_displaySessionTypes_when_rendered', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    expect(screen.getByText('keynote')).toBeInTheDocument();
    expect(screen.getByText('workshop')).toBeInTheDocument();
    expect(screen.getByText('lunch')).toBeInTheDocument();
  });

  it('should_sortTimeSlots_when_rendered', () => {
    const { container } = renderWithProviders(<EventProgram sessions={mockSessions} />);

    // Find time indicators in the timeline circles
    const timeIndicators = container.querySelectorAll('.rounded-full .text-sm');
    const times = Array.from(timeIndicators).map((el) => el.textContent);

    // Should have 2 unique time slots
    expect(times.length).toBe(2);
    // First time should be earlier than second time (string comparison works for HH:mm format)
    expect(times[0]! < times[1]!).toBe(true);
  });

  it('should_displayTimelineLine_when_rendered', () => {
    const { container } = renderWithProviders(<EventProgram sessions={mockSessions} />);

    // Check for timeline vertical line
    const timeline = container.querySelector('.absolute.w-px.bg-zinc-800');
    expect(timeline).toBeInTheDocument();
  });

  it('should_renderNull_when_noSessionsProvided', () => {
    const { container } = renderWithProviders(<EventProgram sessions={[]} />);

    expect(container.firstChild).toBeNull();
  });

  it('should_formatSessionDuration_when_rendered', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    // Check for formatted time ranges
    const timeRanges = screen.getAllByText(/\d{2}:\d{2}\s*-\s*\d{2}:\d{2}/);
    expect(timeRanges.length).toBeGreaterThan(0);
  });

  it('should_handleParallelSessions_when_sameStartTime', () => {
    renderWithProviders(<EventProgram sessions={mockSessions} />);

    // Both morning sessions start at 09:00
    // They should both appear under the same time slot
    const openingKeynote = screen.getByText('Opening Keynote');
    const morningWorkshop = screen.getByText('Morning Workshop');

    expect(openingKeynote).toBeInTheDocument();
    expect(morningWorkshop).toBeInTheDocument();
  });
});
