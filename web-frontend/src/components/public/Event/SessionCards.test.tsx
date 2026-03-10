/**
 * SessionCards Component Tests (Story 4.1.4)
 * Tests for session cards display and filtering functionality
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SessionCards } from './SessionCards';
import type { SessionUI, SessionMaterial } from '@/types/event.types';

// Mock API clients
vi.mock('@/services/companyApiClient', () => ({
  companyApiClient: {
    getCompany: vi.fn(() => Promise.resolve({ name: 'Test Company', logoUrl: null })),
  },
}));

describe('SessionCards', () => {
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

  const mockSessions: SessionUI[] = [
    {
      sessionSlug: 'keynote-session',
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
      sessionSlug: 'workshop-sustainable',
      eventCode: 'BATbern142',
      title: 'Sustainable Materials Workshop',
      description: 'Hands-on workshop about sustainable building materials.',
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
          speakerRole: 'PRIMARY_SPEAKER',
          isConfirmed: true,
        },
      ],
    },
  ];

  const mockTopics = [
    { id: 'topic-1', name: 'Sustainability' },
    { id: 'topic-2', name: 'Innovation' },
  ];

  it('should_displaySessionCards_when_sessionsProvided', () => {
    renderWithProviders(<SessionCards sessions={mockSessions} />);

    expect(screen.getByText('Sessions')).toBeInTheDocument();
    expect(screen.getByText('Opening Keynote')).toBeInTheDocument();
    expect(screen.getByText('Sustainable Materials Workshop')).toBeInTheDocument();
  });

  it('should_displaySessionDetails_when_rendered', () => {
    renderWithProviders(<SessionCards sessions={mockSessions} />);

    // Check session descriptions
    expect(screen.getByText(/Welcome and introduction to the conference/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Hands-on workshop about sustainable building materials/i)
    ).toBeInTheDocument();

    // Check room names
    expect(screen.getByText('Main Hall')).toBeInTheDocument();
    expect(screen.getByText('Workshop Room A')).toBeInTheDocument();

    // Check capacity
    expect(screen.getByText('200 seats')).toBeInTheDocument();
    expect(screen.getByText('50 seats')).toBeInTheDocument();
  });

  it('should_displaySpeakerNames_when_speakersAssigned', () => {
    renderWithProviders(<SessionCards sessions={mockSessions} />);

    expect(screen.getByText(/John Doe/i)).toBeInTheDocument();
    expect(screen.getByText(/Jane Smith/i)).toBeInTheDocument();
  });

  it('should_toggleToGridView_when_gridButtonClicked', () => {
    const { container } = renderWithProviders(<SessionCards sessions={mockSessions} />);

    const listButton = screen.getByLabelText('List view');
    fireEvent.click(listButton);

    // Check for list layout class
    const sessionContainer = container.querySelector('.space-y-4');
    expect(sessionContainer).toBeInTheDocument();

    const gridButton = screen.getByLabelText('Grid view');
    fireEvent.click(gridButton);

    // Check for grid layout class
    const gridContainer = container.querySelector('.grid');
    expect(gridContainer).toBeInTheDocument();
  });

  it('should_toggleToListView_when_listButtonClicked', () => {
    const { container } = renderWithProviders(<SessionCards sessions={mockSessions} />);

    const listButton = screen.getByLabelText('List view');
    fireEvent.click(listButton);

    // Check for list layout class
    const sessionContainer = container.querySelector('.space-y-4');
    expect(sessionContainer).toBeInTheDocument();
  });

  it('should_displayTopicFilters_when_topicsProvided', () => {
    renderWithProviders(<SessionCards sessions={mockSessions} topics={mockTopics} />);

    expect(screen.getByText('Sustainability')).toBeInTheDocument();
    expect(screen.getByText('Innovation')).toBeInTheDocument();
  });

  it('should_toggleTopicFilter_when_topicBadgeClicked', () => {
    renderWithProviders(<SessionCards sessions={mockSessions} topics={mockTopics} />);

    const sustainabilityBadge = screen.getByText('Sustainability');
    fireEvent.click(sustainabilityBadge);

    // Topic should be highlighted (bg-blue-400)
    expect(sustainabilityBadge.closest('.bg-blue-400')).toBeInTheDocument();

    // Click again to deselect
    fireEvent.click(sustainabilityBadge);
    expect(sustainabilityBadge.closest('.bg-zinc-800')).toBeInTheDocument();
  });

  it('should_displaySessionType_when_rendered', () => {
    renderWithProviders(<SessionCards sessions={mockSessions} />);

    expect(screen.getByText('keynote')).toBeInTheDocument();
    expect(screen.getByText('workshop')).toBeInTheDocument();
  });

  it('should_formatTime_when_sessionTimesProvided', () => {
    renderWithProviders(<SessionCards sessions={mockSessions} />);

    // Check for formatted times (09:00 - 10:00 and 11:00 - 12:30)
    const timeElements = screen.getAllByText(/\d{2}:\d{2}\s*-\s*\d{2}:\d{2}/);
    expect(timeElements.length).toBeGreaterThan(0);
  });

  it('should_renderNull_when_noSessionsProvided', () => {
    const { container } = renderWithProviders(<SessionCards sessions={[]} />);

    expect(container.firstChild).toBeNull();
  });

  it('should_displaySpeakerTBA_when_noSpeakersAssigned', () => {
    const sessionsWithoutSpeakers: SessionUI[] = [
      {
        ...mockSessions[0],
        speakers: [],
      },
    ];

    renderWithProviders(<SessionCards sessions={sessionsWithoutSpeakers} />);

    expect(screen.getByText(/Speaker TBA/i)).toBeInTheDocument();
  });

  it('should_useGridLayoutByDefault_when_rendered', () => {
    const { container } = renderWithProviders(<SessionCards sessions={mockSessions} />);

    // Grid layout should be active by default
    const gridContainer = container.querySelector('.grid');
    expect(gridContainer).toBeInTheDocument();
    expect(gridContainer).toHaveClass('grid-cols-1', 'md:grid-cols-2');
  });

  it('should_excludeStructuralSessions_when_rendered', () => {
    const sessionsWithStructural: SessionUI[] = [
      ...mockSessions,
      {
        sessionSlug: 'lunch-break',
        eventCode: 'BATbern142',
        title: 'Lunch Break',
        sessionType: 'lunch',
        language: 'de',
      },
      {
        sessionSlug: 'morning-break',
        eventCode: 'BATbern142',
        title: 'Coffee Break',
        sessionType: 'break',
        language: 'de',
      },
      {
        sessionSlug: 'moderation-intro',
        eventCode: 'BATbern142',
        title: 'Opening Moderation',
        sessionType: 'moderation',
        language: 'de',
      },
    ];

    renderWithProviders(<SessionCards sessions={sessionsWithStructural} />);

    // Content sessions are shown
    expect(screen.getByText('Opening Keynote')).toBeInTheDocument();
    expect(screen.getByText('Sustainable Materials Workshop')).toBeInTheDocument();

    // Structural sessions are hidden
    expect(screen.queryByText('Lunch Break')).not.toBeInTheDocument();
    expect(screen.queryByText('Coffee Break')).not.toBeInTheDocument();
    expect(screen.queryByText('Opening Moderation')).not.toBeInTheDocument();
  });

  it('should_returnNull_when_allSessionsAreStructural', () => {
    const structuralOnly: SessionUI[] = [
      {
        sessionSlug: 'lunch',
        eventCode: 'BATbern142',
        title: 'Lunch',
        sessionType: 'lunch',
        language: 'de',
      },
      {
        sessionSlug: 'break',
        eventCode: 'BATbern142',
        title: 'Break',
        sessionType: 'break',
        language: 'de',
      },
    ];

    const { container } = renderWithProviders(<SessionCards sessions={structuralOnly} />);
    expect(container.firstChild).toBeNull();
  });

  it('should_showMaterials_when_showMaterialsTrue', () => {
    const material: SessionMaterial = {
      id: 'mat-1',
      uploadId: 'up-1',
      cloudFrontUrl: 'https://cdn.example.com/slides.pdf',
      fileName: 'slides.pdf',
      fileSize: 2097152, // 2 MB
      materialType: 'PRESENTATION',
      uploadedBy: 'john.doe',
      createdAt: '2025-05-15T10:00:00Z',
    };

    const sessionsWithMaterials: SessionUI[] = [{ ...mockSessions[0], materials: [material] }];

    renderWithProviders(
      <SessionCards sessions={sessionsWithMaterials} showMaterials={true} eventCode="BATbern142" />
    );

    expect(screen.getByText('slides.pdf')).toBeInTheDocument();
    expect(screen.getByText('2.0 MB')).toBeInTheDocument();
  });

  it('should_hideMaterials_when_showMaterialsFalse', () => {
    const material: SessionMaterial = {
      id: 'mat-1',
      uploadId: 'up-1',
      cloudFrontUrl: 'https://cdn.example.com/slides.pdf',
      fileName: 'slides.pdf',
      fileSize: 2097152,
      materialType: 'PRESENTATION',
      uploadedBy: 'john.doe',
      createdAt: '2025-05-15T10:00:00Z',
    };

    const sessionsWithMaterials: SessionUI[] = [{ ...mockSessions[0], materials: [material] }];

    renderWithProviders(
      <SessionCards sessions={sessionsWithMaterials} showMaterials={false} eventCode="BATbern142" />
    );

    expect(screen.queryByText('slides.pdf')).not.toBeInTheDocument();
  });
});
