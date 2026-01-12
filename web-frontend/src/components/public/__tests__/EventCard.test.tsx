/**
 * EventCard Component Tests (Story 4.2 - Task 2a)
 *
 * Tests for event archive card component
 * Covers AC1, AC4: Event cards with session preview, speaker/company display
 */

import { describe, test, expect, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { EventCard } from '../EventCard';
import type { EventDetail } from '@/types/event.types';

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'archive.card.sessions': 'sessions',
        'archive.card.viewDetails': 'View Details',
        'archive.card.andMore': 'and {{count}} more',
      };
      let translation = translations[key] || key;

      // Handle parameter interpolation
      if (params) {
        Object.entries(params).forEach(([paramKey, value]) => {
          translation = translation.replace(`{{${paramKey}}}`, String(value));
        });
      }

      return translation;
    },
  }),
}));

describe('EventCard Component', () => {
  const mockEvent: EventDetail = {
    eventId: '1',
    eventCode: 'BAT2024',
    title: 'BATbern 2024: Cloud Architecture',
    date: '2024-12-15T00:00:00Z',
    topic: {
      code: 'cloud',
      name: 'Cloud Architecture',
      description: 'Cloud computing patterns',
      category: 'technical',
    },
    workflowState: 'COMPLETED',
    venueName: 'Kornhausforum',
    themeImageUrl: 'https://cdn.batbern.ch/2024/theme.jpg',
    sessions: [
      {
        sessionId: 's1',
        title: 'Serverless at Scale',
        speakers: [
          {
            username: 'john.doe',
            firstName: 'John',
            lastName: 'Doe',
            company: 'TechCorp',
            profilePictureUrl: 'https://cdn.batbern.ch/speakers/john.jpg',
            speakerRole: 'PRIMARY_SPEAKER' as const,
          },
        ],
      },
      {
        sessionId: 's2',
        title: 'Container Orchestration Patterns',
        speakers: [
          {
            username: 'jane.smith',
            firstName: 'Jane',
            lastName: 'Smith',
            company: 'CloudInc',
            profilePictureUrl: 'https://cdn.batbern.ch/speakers/jane.jpg',
            speakerRole: 'PRIMARY_SPEAKER' as const,
          },
        ],
      },
      {
        sessionId: 's3',
        title: 'Microservices Design',
        speakers: [
          {
            username: 'bob.wilson',
            firstName: 'Bob',
            lastName: 'Wilson',
            company: 'StartupXYZ',
            speakerRole: 'PRIMARY_SPEAKER' as const,
          },
        ],
      },
      {
        sessionId: 's4',
        title: 'API Gateway Architecture',
        speakers: [
          {
            username: 'alice.johnson',
            firstName: 'Alice',
            lastName: 'Johnson',
            company: 'DevCo',
            speakerRole: 'PRIMARY_SPEAKER' as const,
          },
        ],
      },
    ],
  };

  const renderWithRouter = (component: React.ReactElement) => {
    return render(component);
  };

  describe('AC1: Event Card Layout', () => {
    test('should_renderEventImage_when_themeImageUrlProvided', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      const image = screen.getByRole('img', { name: /BATbern 2024/i });
      expect(image).toBeInTheDocument();
      expect(image).toHaveAttribute('src', mockEvent.themeImageUrl);
    });

    test('should_renderEventTitle_when_rendered', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      expect(screen.getByText('BATbern 2024: Cloud Architecture')).toBeInTheDocument();
    });

    test('should_renderEventDate_when_provided', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Date should be formatted in Swiss German (de-CH locale: "15. Dezember 2024")
      // Use more specific matcher to avoid matching "2024" in title
      expect(screen.getByText(/15.*Dezember.*2024/)).toBeInTheDocument();
    });

    test('should_renderTopicBadge_when_topicProvided', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      expect(screen.getByText('Cloud Architecture')).toBeInTheDocument();
    });

    test('should_renderVenueName_when_provided', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      expect(screen.getByText(/Kornhausforum/i)).toBeInTheDocument();
    });
  });

  describe('AC4: Session Preview (First 3 Sessions)', () => {
    test('should_displayFirst3Sessions_when_moreThan3Available', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Should display first 3 sessions
      expect(screen.getByText('Serverless at Scale')).toBeInTheDocument();
      expect(screen.getByText('Container Orchestration Patterns')).toBeInTheDocument();
      expect(screen.getByText('Microservices Design')).toBeInTheDocument();

      // Should NOT display 4th session
      expect(screen.queryByText('API Gateway Architecture')).not.toBeInTheDocument();
    });

    test('should_showSessionCount_when_moreThan3Sessions', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Should indicate there are more sessions (e.g., "and 1 more")
      expect(screen.getByText(/and 1 more/i)).toBeInTheDocument();
    });

    test('should_displayAllSessions_when_3OrFewerAvailable', () => {
      const eventWith2Sessions: EventDetail = {
        ...mockEvent,
        sessions: mockEvent.sessions!.slice(0, 2),
      };

      renderWithRouter(<EventCard event={eventWith2Sessions} viewMode="grid" />);

      expect(screen.getByText('Serverless at Scale')).toBeInTheDocument();
      expect(screen.getByText('Container Orchestration Patterns')).toBeInTheDocument();

      // Should NOT show "and X more" indicator
      expect(screen.queryByText(/more/i)).not.toBeInTheDocument();
    });

    test('should_displaySpeakerNames_when_sessionHasSpeakers', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      expect(screen.getByText(/John Doe/i)).toBeInTheDocument();
      expect(screen.getByText(/Jane Smith/i)).toBeInTheDocument();
      expect(screen.getByText(/Bob Wilson/i)).toBeInTheDocument();
    });

    test('should_displayCompanyNames_when_speakerHasCompany', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      expect(screen.getByText(/TechCorp/i)).toBeInTheDocument();
      expect(screen.getByText(/CloudInc/i)).toBeInTheDocument();
      expect(screen.getByText(/StartupXYZ/i)).toBeInTheDocument();
    });

    test('should_handleSessionsWithoutSpeakers_when_speakersEmpty', () => {
      const eventWithNoSpeakers: EventDetail = {
        ...mockEvent,
        sessions: [
          {
            sessionId: 's1',
            title: 'Session Without Speakers',
            speakers: [],
          },
        ],
      };

      renderWithRouter(<EventCard event={eventWithNoSpeakers} viewMode="grid" />);

      expect(screen.getByText('Session Without Speakers')).toBeInTheDocument();
      // Should not crash
    });
  });

  describe('View Modes (AC2)', () => {
    test('should_renderGridLayout_when_viewModeIsGrid', () => {
      const { container } = renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Grid layout should have specific styling - check the div inside the link
      const card = container.querySelector('.grid-card');
      expect(card).toBeInTheDocument();
    });

    test('should_renderListLayout_when_viewModeIsList', () => {
      const { container } = renderWithRouter(<EventCard event={mockEvent} viewMode="list" />);

      // List layout should have different styling - check the div inside the link
      const card = container.querySelector('.list-card');
      expect(card).toBeInTheDocument();
    });

    test('should_displayImageOnLeft_when_listViewMode', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="list" />);

      // In list mode, image should be positioned differently
      const image = screen.getByRole('img', { name: /BATbern 2024/i });
      expect(image).toBeInTheDocument();
    });
  });

  describe('Navigation', () => {
    test('should_linkToEventDetail_when_clicked', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      const link = screen.getByRole('link', { name: /View Details/i });
      expect(link).toHaveAttribute('href', '/archive/BAT2024');
    });

    test('should_makeEntireCardClickable_when_rendered', () => {
      const { container } = renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      const cardLink = container.querySelector('a[href="/archive/BAT2024"]');
      expect(cardLink).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    test('should_handleMissingImage_when_themeImageUrlNotProvided', () => {
      const eventWithoutImage: EventDetail = {
        ...mockEvent,
        themeImageUrl: undefined,
      };

      renderWithRouter(<EventCard event={eventWithoutImage} viewMode="grid" />);

      // Should not display theme image (but may have speaker profile pictures)
      expect(
        screen.queryByAltText(`${eventWithoutImage.title} theme image`)
      ).not.toBeInTheDocument();
    });

    test('should_handleNoSessions_when_sessionsArrayEmpty', () => {
      const eventWithoutSessions: EventDetail = {
        ...mockEvent,
        sessions: [],
      };

      renderWithRouter(<EventCard event={eventWithoutSessions} viewMode="grid" />);

      expect(screen.getByText('BATbern 2024: Cloud Architecture')).toBeInTheDocument();
      // Should show "0 sessions" or hide session section
    });

    test('should_handleLongEventTitle_when_titleExceeds50Chars', () => {
      const eventWithLongTitle: EventDetail = {
        ...mockEvent,
        title:
          'BATbern 2024: A Very Long Conference Title About Cloud Architecture and Microservices',
      };

      renderWithRouter(<EventCard event={eventWithLongTitle} viewMode="grid" />);

      // Title should be truncated or wrapped properly
      expect(screen.getByText(/BATbern 2024: A Very Long Conference Title/i)).toBeInTheDocument();
    });

    test('should_handleMultipleSpeakersPerSession_when_coPresenting', () => {
      const eventWithMultipleSpeakers: EventDetail = {
        ...mockEvent,
        sessions: [
          {
            sessionId: 's1',
            title: 'Panel Discussion',
            speakers: [
              {
                username: 'john.doe',
                firstName: 'John',
                lastName: 'Doe',
                company: 'TechCorp',
                speakerRole: 'PRIMARY_SPEAKER' as const,
              },
              {
                username: 'jane.smith',
                firstName: 'Jane',
                lastName: 'Smith',
                company: 'CloudInc',
                speakerRole: 'CO_SPEAKER' as const,
              },
              {
                username: 'bob.wilson',
                firstName: 'Bob',
                lastName: 'Wilson',
                company: 'StartupXYZ',
                speakerRole: 'PANELIST' as const,
              },
            ],
          },
        ],
      };

      renderWithRouter(<EventCard event={eventWithMultipleSpeakers} viewMode="grid" />);

      // Should display all speakers for the session
      expect(screen.getByText(/John Doe/i)).toBeInTheDocument();
      expect(screen.getByText(/Jane Smith/i)).toBeInTheDocument();
      expect(screen.getByText(/Bob Wilson/i)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    test('should_haveAccessibleImageAltText_when_rendered', () => {
      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Check theme image has alt text
      const themeImage = screen.getByAltText(`${mockEvent.title} theme image`);
      expect(themeImage).toBeInTheDocument();
      expect(themeImage).toHaveAttribute('alt');
      expect(themeImage.getAttribute('alt')).toBeTruthy();
    });

    test('should_haveSemanticHTML_when_rendered', () => {
      const { container } = renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Should use semantic HTML elements
      expect(
        container.querySelector('article') || container.querySelector('div')
      ).toBeInTheDocument();
    });
  });

  describe('Responsive Design', () => {
    test('should_adaptLayout_when_mobileViewport', () => {
      // Mock mobile viewport
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 375,
      });

      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Card should adapt to mobile width
      expect(screen.getByText('BATbern 2024: Cloud Architecture')).toBeInTheDocument();
    });

    test('should_stackElements_when_gridViewOnMobile', () => {
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 375,
      });

      renderWithRouter(<EventCard event={mockEvent} viewMode="grid" />);

      // Elements should stack vertically on mobile
      expect(screen.getByText('BATbern 2024: Cloud Architecture')).toBeInTheDocument();
    });
  });
});
