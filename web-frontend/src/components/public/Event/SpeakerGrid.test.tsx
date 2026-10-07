/**
 * SpeakerGrid Component Tests (Story 4.1.4)
 *
 * Since the Public Events read model (2026-10-07) the grid renders the `speakers` the server
 * delivers. Which speakers and whether talk titles are shown is decided by the server per
 * publishing phase (SPEAKERS phase: no titles), not by this component.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerGrid } from './SpeakerGrid';
import type { PublicSpeaker } from '@/types/event.types';

vi.mock('@/services/companyApiClient', () => ({
  companyApiClient: {
    getCompany: vi.fn(() => Promise.resolve({ name: 'Test Company', logoUrl: null })),
  },
}));

describe('SpeakerGrid', () => {
  let queryClient: QueryClient;

  const renderWithProviders = (ui: React.ReactElement) =>
    render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  const agendaSpeakers: PublicSpeaker[] = [
    {
      username: 'john.doe',
      firstName: 'John',
      lastName: 'Doe',
      company: 'GoogleZH',
      profilePictureUrl: 'https://cdn.batbern.ch/logos/2025/users/john.doe/profile.jpg',
      speakerRole: 'PRIMARY_SPEAKER',
      bio: 'Architect and author.',
      talkTitle: 'Opening Keynote: Future of Architecture',
      sessionSlug: 'keynote-session',
    },
    {
      username: 'jane.smith',
      firstName: 'Jane',
      lastName: 'Smith',
      company: 'AcmeCorp',
      profilePictureUrl: 'https://cdn.batbern.ch/logos/2025/users/jane.smith/profile.jpg',
      speakerRole: 'PRIMARY_SPEAKER',
      talkTitle: 'Green Building Innovations',
      sessionSlug: 'workshop-sustainable',
    },
  ];

  const lineupOnly: PublicSpeaker[] = agendaSpeakers.map(
    ({ talkTitle: _t, sessionSlug: _s, ...rest }) => rest
  );

  it('should_renderOneCardPerSpeaker_when_speakersProvided', () => {
    renderWithProviders(<SpeakerGrid speakers={agendaSpeakers} />);

    expect(screen.getByTestId('speaker-grid')).toBeInTheDocument();
    expect(screen.getAllByTestId('speaker-card')).toHaveLength(2);
    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('Jane Smith')).toBeInTheDocument();
  });

  it('should_displayCompanyNames_when_speakersHaveCompanies', () => {
    renderWithProviders(<SpeakerGrid speakers={agendaSpeakers} />);

    expect(screen.getByText('GoogleZH')).toBeInTheDocument();
    expect(screen.getByText('AcmeCorp')).toBeInTheDocument();
  });

  it('should_displayTalkTitles_when_serverDeliversThem', () => {
    renderWithProviders(<SpeakerGrid speakers={agendaSpeakers} />);

    expect(screen.getByText('Opening Keynote: Future of Architecture')).toBeInTheDocument();
    expect(screen.getByText('Green Building Innovations')).toBeInTheDocument();
  });

  it('should_showNoTalkTitles_when_serverDeliversLineupOnly', () => {
    renderWithProviders(<SpeakerGrid speakers={lineupOnly} />);

    expect(screen.getAllByTestId('speaker-card')).toHaveLength(2);
    expect(screen.queryByTestId('speaker-talk-title')).not.toBeInTheDocument();
  });

  it('should_displayBio_when_speakerHasBio', () => {
    renderWithProviders(<SpeakerGrid speakers={agendaSpeakers} />);

    expect(screen.getByText('Architect and author.')).toBeInTheDocument();
  });

  it('should_displayInitials_when_noProfilePictureProvided', () => {
    renderWithProviders(
      <SpeakerGrid speakers={[{ ...lineupOnly[0], profilePictureUrl: undefined }]} />
    );

    expect(screen.getByText('JD')).toBeInTheDocument();
  });

  it('should_renderNull_when_noSpeakers', () => {
    const { container } = renderWithProviders(<SpeakerGrid speakers={[]} />);

    expect(container.firstChild).toBeNull();
  });

  it('should_useGridLayout_when_rendered', () => {
    renderWithProviders(<SpeakerGrid speakers={agendaSpeakers} />);

    expect(screen.getByTestId('speaker-grid').querySelector('.grid')).toHaveClass(
      'grid-cols-1',
      'md:grid-cols-2',
      'lg:grid-cols-3'
    );
  });

  it('should_applyHoverStyles_when_cardHovered', () => {
    renderWithProviders(<SpeakerGrid speakers={agendaSpeakers} />);

    screen.getAllByTestId('speaker-card').forEach((card) => {
      expect(card).toHaveClass('hover:border-blue-400', 'transition-colors');
    });
  });
});
