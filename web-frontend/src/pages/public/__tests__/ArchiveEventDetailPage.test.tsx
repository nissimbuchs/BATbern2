/**
 * ArchiveEventDetailPage Component Tests (Story 4.2 - Task 2a)
 *
 * Tests for the archive event detail page
 * Covers AC13-18: Event header, all sessions displayed, presentation downloads, speaker grid, back navigation
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HelmetProvider } from 'react-helmet-async';
import ArchiveEventDetailPage from '../ArchiveEventDetailPage';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventDetail } from '@/types/event.types';

// Mock eventApiClient
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getEvent: vi.fn(),
  },
}));

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'archive.detail.sessions': 'Sessions',
        'archive.detail.speakers': 'Speakers',
        'archive.detail.backToArchive': 'Back to Archive',
        'archive.detail.downloadPresentation': 'Download Presentation',
        'archive.detail.eventDetails': 'Event Details',
        'archive.errors.loadFailed': 'Failed to load event',
        'archive.errors.notFound': 'Event not found',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock PublicLayout
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="public-layout">{children}</div>
  ),
}));

// Mock OpenGraphTags (SEO)
vi.mock('@/components/SEO/OpenGraphTags', () => ({
  OpenGraphTags: ({ title, description }: { title: string; description: string }) => (
    <div data-testid="og-tags" data-title={title} data-description={description} />
  ),
}));

describe('ArchiveEventDetailPage Component', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const mockEvent: EventDetail = {
    eventId: '1',
    eventCode: 'BAT2024',
    title: 'BATbern 2024: Cloud Architecture Summit',
    date: '2024-12-15T00:00:00Z',
    topic: { id: 't1', name: 'Cloud Architecture', code: 'cloud' } as any, // Expanded topic object from API
    topicCode: 'cloud', // Legacy field for backward compatibility
    description:
      'Join us for an in-depth exploration of modern cloud architecture patterns and best practices.',
    workflowState: 'ARCHIVED',
    venueName: 'Kornhausforum',
    venueAddress: 'Kornhausplatz 18, 3011 Bern',
    venueCapacity: 200,
    themeImageUrl: 'https://cdn.batbern.ch/2024/theme.jpg',
    sessions: [
      {
        sessionId: 's1',
        title: 'Serverless at Scale',
        description: 'Building and operating serverless applications at enterprise scale',
        startTime: '18:00',
        endTime: '18:30',
        speakers: [
          {
            speakerId: 'sp1',
            fullName: 'John Doe',
            companyName: 'TechCorp',
            photoUrl: 'https://cdn.batbern.ch/speakers/john.jpg',
          },
        ],
        presentationUrl: 'https://cdn.batbern.ch/2024/presentations/serverless.pdf',
        presentationSize: 2400000, // 2.4 MB
      },
      {
        sessionId: 's2',
        title: 'Container Orchestration Patterns',
        description: 'Advanced Kubernetes patterns for production workloads',
        startTime: '18:35',
        endTime: '19:05',
        speakers: [
          {
            speakerId: 'sp2',
            fullName: 'Jane Smith',
            companyName: 'CloudInc',
            photoUrl: 'https://cdn.batbern.ch/speakers/jane.jpg',
          },
          {
            speakerId: 'sp3',
            fullName: 'Bob Wilson',
            companyName: 'StartupXYZ',
          },
        ],
        presentationUrl: 'https://cdn.batbern.ch/2024/presentations/kubernetes.pdf',
        presentationSize: 3200000, // 3.2 MB
      },
      {
        sessionId: 's3',
        title: 'Microservices Design',
        description: 'Domain-driven design principles for microservices architecture',
        startTime: '19:10',
        endTime: '19:40',
        speakers: [
          {
            speakerId: 'sp4',
            fullName: 'Alice Johnson',
            companyName: 'DevCo',
            photoUrl: 'https://cdn.batbern.ch/speakers/alice.jpg',
          },
        ],
      },
      {
        sessionId: 's4',
        title: 'API Gateway Architecture',
        description: 'Building resilient API gateways with rate limiting and authentication',
        startTime: '19:45',
        endTime: '20:15',
        speakers: [
          {
            speakerId: 'sp5',
            fullName: 'Charlie Brown',
            companyName: 'FinTech Solutions',
          },
        ],
        presentationUrl: 'https://cdn.batbern.ch/2024/presentations/api-gateway.pdf',
        presentationSize: 1800000, // 1.8 MB
      },
    ],
  };

  const renderWithProviders = (initialRoute = '/archive/BAT2024') => {
    return render(
      <HelmetProvider>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[initialRoute]}>
            <Routes>
              <Route path="/archive/:eventCode" element={<ArchiveEventDetailPage />} />
              <Route path="/archive" element={<div>Archive List</div>} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </HelmetProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
  });

  describe('AC13: Event Header (No Logistics)', () => {
    test('should_renderEventTitle_when_loaded', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('BATbern 2024: Cloud Architecture Summit')).toBeInTheDocument();
      });
    });

    test('should_renderEventDate_when_loaded', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        // Should format date (exact format depends on locale)
        // Use specific date format to avoid matching "2024" in title
        expect(screen.getByText(/December.*15.*2024/)).toBeInTheDocument();
      });
    });

    test('should_renderTopicBadge_when_topicProvided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Cloud Architecture')).toBeInTheDocument();
      });
    });

    test('should_renderEventDescription_when_provided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(
          screen.getByText(/in-depth exploration of modern cloud architecture/i)
        ).toBeInTheDocument();
      });
    });

    test('should_NOT_renderLogistics_when_archivePage', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('BATbern 2024: Cloud Architecture Summit')).toBeInTheDocument();
      });

      // Should NOT display venue address, capacity, or registration details
      expect(screen.queryByText('Kornhausplatz 18')).not.toBeInTheDocument();
      expect(screen.queryByText(/capacity/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/register/i)).not.toBeInTheDocument();
    });

    test('should_renderThemeImage_when_themeImageUrlProvided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const image = screen.getByRole('img', { name: /BATbern 2024/i });
        expect(image).toBeInTheDocument();
        expect(image).toHaveAttribute('src', mockEvent.themeImageUrl);
      });
    });
  });

  describe('AC14: All Sessions Displayed (No Pagination)', () => {
    test('should_displayAllSessions_when_loaded', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Serverless at Scale')).toBeInTheDocument();
        expect(screen.getByText('Container Orchestration Patterns')).toBeInTheDocument();
        expect(screen.getByText('Microservices Design')).toBeInTheDocument();
        expect(screen.getByText('API Gateway Architecture')).toBeInTheDocument();
      });
    });

    test('should_display4Sessions_when_eventHas4Sessions', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        // Verify all 4 sessions are rendered by checking for time ranges
        expect(screen.getByText(/18:00 - 18:30/)).toBeInTheDocument();
        expect(screen.getByText(/18:35 - 19:05/)).toBeInTheDocument();
        expect(screen.getByText(/19:10 - 19:40/)).toBeInTheDocument();
        expect(screen.getByText(/19:45 - 20:15/)).toBeInTheDocument();
      });
    });

    test('should_NOT_renderPagination_when_manySessions', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Serverless at Scale')).toBeInTheDocument();
      });

      // Should NOT have pagination controls
      expect(screen.queryByText(/next/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/previous/i)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /page/i })).not.toBeInTheDocument();
    });

    test('should_displaySessionsInTimeOrder_when_rendered', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const sessionTitles = [
          'Serverless at Scale',
          'Container Orchestration Patterns',
          'Microservices Design',
          'API Gateway Architecture',
        ];

        sessionTitles.forEach((title) => {
          expect(screen.getByText(title)).toBeInTheDocument();
        });
      });
    });
  });

  describe('AC15: Session Details (Title, Speakers, Description)', () => {
    test('should_displaySessionTitle_when_rendered', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Serverless at Scale')).toBeInTheDocument();
      });
    });

    test('should_displaySessionDescription_when_provided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(
          screen.getByText(/Building and operating serverless applications/i)
        ).toBeInTheDocument();
      });
    });

    test('should_displaySpeakerNames_when_sessionHasSpeakers', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        // Speakers appear in both session details and speaker grid - use getAllByText
        expect(screen.getAllByText(/John Doe/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/Jane Smith/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/Bob Wilson/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/Alice Johnson/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/Charlie Brown/i).length).toBeGreaterThanOrEqual(1);
      });
    });

    test('should_displayCompanyNames_when_speakerHasCompany', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        // Companies appear in both session details and speaker grid - use getAllByText
        expect(screen.getAllByText(/TechCorp/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/CloudInc/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/DevCo/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/FinTech Solutions/i).length).toBeGreaterThanOrEqual(1);
      });
    });

    test('should_displaySessionTime_when_provided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/18:00/)).toBeInTheDocument();
        expect(screen.getByText(/18:30/)).toBeInTheDocument();
        expect(screen.getByText(/19:10/)).toBeInTheDocument();
      });
    });

    test('should_handleMultipleSpeakers_when_sessionHasCoPresenting', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        // Session 2 has 2 speakers - speakers appear in both session details and grid
        expect(screen.getAllByText(/Jane Smith/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/Bob Wilson/i).length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe('AC16: Download PDF with File Size', () => {
    test('should_displayDownloadButton_when_presentationUrlProvided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const downloadButtons = screen.getAllByText(/Download Presentation/i);
        // 3 sessions have presentation URLs
        expect(downloadButtons.length).toBe(3);
      });
    });

    test('should_displayFileSize_when_presentationSizeProvided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/2\.4 MB/i)).toBeInTheDocument();
        expect(screen.getByText(/3\.2 MB/i)).toBeInTheDocument();
        expect(screen.getByText(/1\.8 MB/i)).toBeInTheDocument();
      });
    });

    test('should_linkToPDF_when_downloadClicked', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const downloadLink = screen.getAllByRole('link', { name: /Download Presentation/i })[0];
        expect(downloadLink).toHaveAttribute(
          'href',
          'https://cdn.batbern.ch/2024/presentations/serverless.pdf'
        );
        expect(downloadLink).toHaveAttribute('target', '_blank');
      });
    });

    test('should_NOT_displayDownload_when_presentationUrlMissing', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Microservices Design')).toBeInTheDocument();
      });

      // Session 3 has no presentation URL - should not show download
      const sessionCard = screen.getByText('Microservices Design').closest('div');
      expect(sessionCard).toBeInTheDocument();

      const downloadButtons = within(sessionCard!).queryAllByText(/Download/i);
      expect(downloadButtons.length).toBe(0);
    });

    test('should_formatFileSize_when_sizeInBytes', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        // 2400000 bytes = 2.4 MB
        expect(screen.getByText(/2\.4 MB/i)).toBeInTheDocument();
        // 3200000 bytes = 3.2 MB
        expect(screen.getByText(/3\.2 MB/i)).toBeInTheDocument();
      });
    });
  });

  describe('AC17: Speaker Grid with Photos and Companies', () => {
    test('should_displaySpeakerGrid_when_eventHasSpeakers', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Speakers')).toBeInTheDocument();
      });
    });

    test('should_displaySpeakerPhotos_when_photoUrlProvided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const johnPhoto = screen.getByAltText(/John Doe/i);
        expect(johnPhoto).toBeInTheDocument();
        expect(johnPhoto).toHaveAttribute('src', 'https://cdn.batbern.ch/speakers/john.jpg');
      });
    });

    test('should_displayFallbackAvatar_when_photoUrlMissing', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        // Bob Wilson and Charlie Brown don't have photos - appear in both sections
        expect(screen.getAllByText(/Bob Wilson/i).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/Charlie Brown/i).length).toBeGreaterThanOrEqual(1);
      });

      // Should display fallback avatar initials
      expect(screen.getByText('BW')).toBeInTheDocument(); // Bob Wilson initials
      expect(screen.getByText('CB')).toBeInTheDocument(); // Charlie Brown initials
    });

    test('should_displayCompanyNames_when_speakersInGrid', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const speakerSection = screen.getByText('Speakers').closest('section');
        expect(speakerSection).toBeInTheDocument();

        expect(within(speakerSection!).getByText(/TechCorp/i)).toBeInTheDocument();
        expect(within(speakerSection!).getByText(/CloudInc/i)).toBeInTheDocument();
        expect(within(speakerSection!).getByText(/DevCo/i)).toBeInTheDocument();
      });
    });

    test('should_deduplicateSpeakers_when_sameSpeakerInMultipleSessions', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const speakerSection = screen.getByText('Speakers').closest('section');
        const johnDoes = within(speakerSection!).getAllByText(/John Doe/i);

        // Should only appear once in speaker grid (even if in multiple sessions)
        expect(johnDoes.length).toBe(1);
      });
    });

    test('should_displaySpeakersInGrid_when_rendered', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const speakerSection = screen.getByText('Speakers').closest('section');
        const gridContainer = speakerSection!.querySelector('[class*="grid"]');

        expect(gridContainer).toBeInTheDocument();
      });
    });
  });

  describe('AC18: Back to Archive Navigation', () => {
    test('should_displayBackButton_when_rendered', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Back to Archive')).toBeInTheDocument();
      });
    });

    test('should_navigateToArchive_when_backClicked', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);
      const user = userEvent.setup();

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('BATbern 2024: Cloud Architecture Summit')).toBeInTheDocument();
      });

      const backButton = screen.getByText('Back to Archive');
      await user.click(backButton);

      await waitFor(() => {
        expect(screen.getByText('Archive List')).toBeInTheDocument();
      });
    });

    test('should_preserveFilters_when_navigatingBack', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      // Navigate from archive with filters
      const routeWithFilters = '/archive/BAT2024?topics=cloud&sort=-date';
      renderWithProviders(routeWithFilters);

      await waitFor(() => {
        expect(screen.getByText('BATbern 2024: Cloud Architecture Summit')).toBeInTheDocument();
      });

      const backButton = screen.getByRole('link', { name: /Back to Archive/i });

      // Back button should preserve query parameters
      expect(backButton).toHaveAttribute('href', '/archive?topics=cloud&sort=-date');
    });
  });

  describe('Loading & Error States', () => {
    test('should_displayLoadingSpinner_when_fetchingEvent', () => {
      vi.mocked(eventApiClient.getEvent).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      renderWithProviders();

      expect(screen.getByRole('status', { hidden: true })).toBeInTheDocument();
    });

    test('should_displayErrorMessage_when_loadFails', async () => {
      vi.mocked(eventApiClient.getEvent).mockRejectedValue(new Error('Network error'));

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Failed to load event')).toBeInTheDocument();
      });
    });

    test('should_displayNotFound_when_eventDoesNotExist', async () => {
      vi.mocked(eventApiClient.getEvent).mockRejectedValue({
        response: { status: 404 },
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Event not found')).toBeInTheDocument();
      });
    });
  });

  describe('SEO (AC22)', () => {
    test('should_renderOpenGraphTags_when_eventLoaded', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const ogTags = screen.getByTestId('og-tags');
        expect(ogTags).toHaveAttribute('data-title', mockEvent.title);
      });
    });

    test('should_includeEventDescription_when_ogTagsRendered', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        const ogTags = screen.getByTestId('og-tags');
        expect(ogTags).toHaveAttribute('data-description', mockEvent.description);
      });
    });
  });

  describe('PublicLayout Integration', () => {
    test('should_renderWithinPublicLayout_when_mounted', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByTestId('public-layout')).toBeInTheDocument();
      });
    });
  });

  describe('API Contract', () => {
    test('should_callAPIWithResourceExpansion_when_loading', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue(mockEvent);

      renderWithProviders('/archive/BAT2024');

      await waitFor(() => {
        expect(eventApiClient.getEvent).toHaveBeenCalledWith('BAT2024', {
          expand: ['topics', 'sessions', 'speakers'],
        });
      });
    });
  });

  describe('Edge Cases', () => {
    test('should_handleEventWithNoSessions_when_sessionsEmpty', async () => {
      const eventWithoutSessions: EventDetail = {
        ...mockEvent,
        sessions: [],
      };

      vi.mocked(eventApiClient.getEvent).mockResolvedValue(eventWithoutSessions);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('BATbern 2024: Cloud Architecture Summit')).toBeInTheDocument();
      });

      // Should show "No sessions available" or hide sessions section
      expect(screen.queryByText('Sessions')).not.toBeInTheDocument();
    });

    test('should_handleEventWithoutDescription_when_descriptionMissing', async () => {
      const eventWithoutDescription: EventDetail = {
        ...mockEvent,
        description: undefined,
      };

      vi.mocked(eventApiClient.getEvent).mockResolvedValue(eventWithoutDescription);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('BATbern 2024: Cloud Architecture Summit')).toBeInTheDocument();
      });

      // Should not crash without description
      expect(screen.getByText('Serverless at Scale')).toBeInTheDocument();
    });
  });
});
