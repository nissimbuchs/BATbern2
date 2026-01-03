/**
 * SpeakerGrid Component Tests (Story 4.1.4)
 * Tests for speaker grid display functionality
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerGrid } from './SpeakerGrid';
import type { Session } from '@/types/event.types';

// Mock API clients
vi.mock('@/services/companyApiClient', () => ({
  companyApiClient: {
    getCompany: vi.fn(() => Promise.resolve({ name: 'Test Company', logoUrl: null })),
  },
}));

describe('SpeakerGrid', () => {
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
      sessionSlug: 'keynote-session',
      eventCode: 'BATbern142',
      title: 'Opening Keynote: Future of Architecture',
      description:
        'An inspiring look at the future of Swiss architecture and sustainable building practices.',
      sessionType: 'keynote',
      startTime: '2025-05-15T09:00:00Z',
      endTime: '2025-05-15T10:30:00Z',
      room: 'Main Hall',
      capacity: 200,
      language: 'de',
      speakers: [
        {
          username: 'john.doe',
          firstName: 'John',
          lastName: 'Doe',
          company: 'GoogleZH',
          profilePictureUrl: 'https://cdn.batbern.ch/logos/2025/users/john.doe/profile.jpg',
          speakerRole: 'PRIMARY_SPEAKER',
          isConfirmed: true,
        },
      ],
    },
    {
      sessionSlug: 'workshop-sustainable',
      eventCode: 'BATbern142',
      title: 'Workshop: Sustainable Materials',
      description:
        'Hands-on workshop exploring sustainable building materials and their applications.',
      sessionType: 'workshop',
      startTime: '2025-05-15T11:00:00Z',
      endTime: '2025-05-15T12:30:00Z',
      room: 'Workshop Room A',
      capacity: 50,
      language: 'de',
      speakers: [
        {
          username: 'jane.smith',
          firstName: 'Jane',
          lastName: 'Smith',
          company: 'AcmeCorp',
          profilePictureUrl: 'https://cdn.batbern.ch/logos/2025/users/jane.smith/profile.jpg',
          speakerRole: 'PRIMARY_SPEAKER',
          presentationTitle: 'Green Building Innovations',
          isConfirmed: true,
        },
      ],
    },
  ];

  it('should_displaySpeakerGrid_when_sessionsWithSpeakersProvided', () => {
    renderWithProviders(<SpeakerGrid sessions={mockSessions} />);

    // Check heading
    expect(screen.getByText('Speakers')).toBeInTheDocument();

    // Check speaker names
    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('Jane Smith')).toBeInTheDocument();
  });

  it('should_displayCompanyNames_when_speakersHaveCompanies', () => {
    renderWithProviders(<SpeakerGrid sessions={mockSessions} />);

    expect(screen.getByText('GoogleZH')).toBeInTheDocument();
    expect(screen.getByText('AcmeCorp')).toBeInTheDocument();
  });

  it('should_displaySessionTitles_when_speakersAssignedToSessions', () => {
    renderWithProviders(<SpeakerGrid sessions={mockSessions} />);

    expect(screen.getByText('Opening Keynote: Future of Architecture')).toBeInTheDocument();
    expect(screen.getByText('Green Building Innovations')).toBeInTheDocument(); // presentationTitle
  });

  it('should_storeSessionDescription_when_sessionHasDescription', () => {
    // Session description is stored in speaker data but not displayed in card
    // Only session title and speaker bio are shown
    renderWithProviders(<SpeakerGrid sessions={mockSessions} />);

    // Verify session titles are displayed
    expect(screen.getByText('Opening Keynote: Future of Architecture')).toBeInTheDocument();
    expect(screen.getByText('Green Building Innovations')).toBeInTheDocument();

    // Session descriptions are not rendered in the UI (kept minimal)
    expect(
      screen.queryByText(/An inspiring look at the future of Swiss architecture/i)
    ).not.toBeInTheDocument();
  });

  it('should_displayInitials_when_noProfilePictureProvided', () => {
    const sessionsWithoutPhotos: Session[] = [
      {
        ...mockSessions[0],
        speakers: [
          {
            username: 'test.user',
            firstName: 'Test',
            lastName: 'User',
            speakerRole: 'PRIMARY_SPEAKER',
            isConfirmed: true,
          },
        ],
      },
    ];

    renderWithProviders(<SpeakerGrid sessions={sessionsWithoutPhotos} />);

    expect(screen.getByText('TU')).toBeInTheDocument(); // Initials
  });

  it('should_renderNull_when_noSpeakersInSessions', () => {
    const sessionsWithoutSpeakers: Session[] = [
      {
        sessionSlug: 'empty-session',
        eventCode: 'BATbern142',
        title: 'Empty Session',
        description: 'A session without speakers',
        sessionType: 'keynote',
        startTime: '2025-05-15T09:00:00Z',
        endTime: '2025-05-15T10:30:00Z',
        room: 'Main Hall',
        capacity: 200,
        language: 'de',
        speakers: [],
      },
    ];

    const { container } = renderWithProviders(<SpeakerGrid sessions={sessionsWithoutSpeakers} />);

    expect(container.firstChild).toBeNull();
  });

  it('should_dedupeSpeakers_when_speakerAppearsInMultipleSessions', () => {
    const sessionsWithDuplicateSpeaker: Session[] = [
      mockSessions[0],
      {
        ...mockSessions[0],
        sessionSlug: 'another-session',
        title: 'Another Session',
      },
    ];

    renderWithProviders(<SpeakerGrid sessions={sessionsWithDuplicateSpeaker} />);

    // Should only appear once despite being in 2 sessions
    const speakerCards = screen.getAllByText('John Doe');
    expect(speakerCards).toHaveLength(1);
  });

  it('should_useGridLayout_when_rendered', () => {
    const { container } = renderWithProviders(<SpeakerGrid sessions={mockSessions} />);

    // Check for responsive grid classes
    const gridContainer = container.querySelector('.grid');
    expect(gridContainer).toBeInTheDocument();
    expect(gridContainer).toHaveClass('grid-cols-1', 'md:grid-cols-2', 'lg:grid-cols-3');
  });

  it('should_applyHoverStyles_when_cardHovered', () => {
    const { container } = renderWithProviders(<SpeakerGrid sessions={mockSessions} />);

    const cards = container.querySelectorAll('.group');
    expect(cards.length).toBeGreaterThan(0);

    // Check for hover transition class
    cards.forEach((card) => {
      expect(card).toHaveClass('hover:border-blue-400', 'transition-colors');
    });
  });
});
