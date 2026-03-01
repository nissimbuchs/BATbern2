/**
 * NavigationMenu Component Tests
 * Story 1.17, Task 6a: TDD for role-adaptive navigation menu
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { NavigationMenu } from './NavigationMenu';

// Mock i18next with actual translations
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'navigation.dashboard': 'Dashboard',
        'navigation.events': 'Events',
        'navigation.speakers': 'Speakers',
        'navigation.partners': 'Partners',
        'navigation.partnerTopics': 'Partner Topics',
        'navigation.partnerMeetings': 'Partner Meetings',
        'navigation.analytics': 'Analytics',
        'navigation.content': 'Content',
        'navigation.profile': 'Profile',
        'navigation.myEvents': 'My Events',
        'navigation.myContent': 'My Content',
        'navigation.myRegistrations': 'My Registrations',
        'navigation.myCompany': 'My Company',
        'navigation.topics': 'Topics',
        'navigation.mainNav': 'main navigation',
      };
      return translations[key] || key;
    },
    i18n: {
      language: 'en',
    },
  }),
}));

describe('NavigationMenu Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderWithRouter = (component: React.ReactElement) => {
    return render(<BrowserRouter>{component}</BrowserRouter>);
  };

  describe('Organizer Navigation', () => {
    test('should_renderOrganizerMenuItems_when_roleIsOrganizer', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      // Organizer should see full menu: Events, Speakers, Partners, Analytics
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/speakers/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/partners/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/analytics/i)[0]).toBeInTheDocument();
    });

    test('should_linkToEventsManagement_when_eventsClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveAttribute('href', '/organizer/events');
    });

    test('should_linkToSpeakersManagement_when_speakersClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const speakersLink = screen.getAllByText(/speakers/i)[0].closest('a');
      expect(speakersLink).toHaveAttribute('href', '/organizer/speakers');
    });

    test('should_showPartnersDropdown_when_partnersClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      // Partners is now a dropdown group button (queried by testid to avoid JSDOM block-in-inline issues)
      const partnersButton = screen.getByTestId('nav-group-organizer-partners');
      expect(partnersButton).toBeInTheDocument();
      expect(partnersButton).toHaveAttribute('aria-haspopup', 'true');
      expect(partnersButton).toHaveAttribute('aria-expanded', 'false');

      // After clicking, aria-expanded becomes true (dropdown opens)
      fireEvent.click(partnersButton);
      expect(partnersButton).toHaveAttribute('aria-expanded', 'true');
    });
  });

  describe('Speaker Navigation', () => {
    test('should_renderSpeakerMenuItems_when_roleIsSpeaker', () => {
      renderWithRouter(<NavigationMenu userRoles={['speaker']} />);

      // Speaker should see: Dashboard, My Events, My Content, Profile
      expect(screen.getAllByText(/dashboard/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my content/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/profile/i)[0]).toBeInTheDocument();
    });

    test('should_notShowPartnersMenu_when_roleIsSpeaker', () => {
      renderWithRouter(<NavigationMenu userRoles={['speaker']} />);

      // Speaker should NOT see Partners menu
      expect(screen.queryByText(/^partners$/i)).not.toBeInTheDocument();
    });

    test('should_linkToMyEvents_when_myEventsClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['speaker']} />);

      const myEventsLink = screen.getAllByText(/my events/i)[0].closest('a');
      expect(myEventsLink).toHaveAttribute('href', '/speaker/events');
    });
  });

  describe('Partner Navigation', () => {
    test('should_renderPartnerMenuItems_when_roleIsPartner', () => {
      renderWithRouter(<NavigationMenu userRoles={['partner']} />);

      // Partner should see: My Company, Topics, and public site
      expect(screen.getAllByText(/my company/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/topics/i)[0]).toBeInTheDocument();
    });

    test('should_notShowSpeakersMenu_when_roleIsPartner', () => {
      renderWithRouter(<NavigationMenu userRoles={['partner']} />);

      // Partner should NOT see Speakers menu
      expect(screen.queryByText(/speakers/i)).not.toBeInTheDocument();
    });

    test('should_linkToPartnerTopics_when_topicsClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['partner']} />);

      const topicsLink = screen.getAllByText(/topics/i)[0].closest('a');
      expect(topicsLink).toHaveAttribute('href', '/partners/topics');
    });
  });

  describe('Attendee Navigation', () => {
    test('should_renderAttendeeMenuItems_when_roleIsAttendee', () => {
      renderWithRouter(<NavigationMenu userRoles={['attendee']} />);

      // Attendee should see: Events, Speakers, My Registrations
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/speakers/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my registrations/i)[0]).toBeInTheDocument();
    });

    test('should_notShowAnalytics_when_roleIsAttendee', () => {
      renderWithRouter(<NavigationMenu userRoles={['attendee']} />);

      // Attendee should NOT see Analytics menu
      expect(screen.queryByText(/analytics/i)).not.toBeInTheDocument();
    });

    test('should_linkToEventsList_when_eventsClicked', () => {
      renderWithRouter(<NavigationMenu userRoles={['attendee']} />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveAttribute('href', '/attendee/events');
    });
  });

  describe('Active State', () => {
    test('should_highlightActiveMenuItem_when_onCurrentPage', () => {
      // Mock current location
      window.history.pushState({}, '', '/organizer/events');

      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveClass(/active|selected/i);
    });

    test('should_notHighlightInactiveMenuItems_when_onDifferentPage', () => {
      // Mock current location
      window.history.pushState({}, '', '/organizer/events');

      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const speakersLink = screen.getAllByText(/speakers/i)[0].closest('a');
      expect(speakersLink).not.toHaveClass(/active|selected/i);
    });
  });

  describe('Accessibility', () => {
    test('should_haveProperAriaLabels_when_rendered', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const navigation = screen.getByRole('navigation');
      expect(navigation).toHaveAttribute('aria-label', 'main navigation');
    });

    test('should_supportKeyboardNavigation_when_rendered', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      const firstLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(firstLink).toHaveAttribute('tabindex', '0');
    });
  });

  describe('Multi-Role Navigation', () => {
    test('should_showBothOrganizerAndPartnerItems_when_rolesIncludeBoth', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer', 'partner']} />);

      // Organizer items present
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/speakers/i)[0]).toBeInTheDocument();
      // Partner items also present
      expect(screen.getAllByText(/my company/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/topics/i)[0]).toBeInTheDocument();
    });

    test('should_deduplicatePublicSite_when_multipleRolesHaveSameItem', () => {
      renderWithRouter(<NavigationMenu userRoles={['organizer', 'partner']} />);

      // "Public Site" nav item exists for both organizer and partner — should appear only once
      const publicLinks = screen.queryAllByRole('link', { name: /public site/i });
      expect(publicLinks.length).toBeLessThanOrEqual(1);
    });
  });

  describe('Icons', () => {
    test('should_displayIconsForMenuItems_when_rendered', () => {
      const { container } = renderWithRouter(<NavigationMenu userRoles={['organizer']} />);

      // Should have Material-UI icons for each menu item
      const icons = container.querySelectorAll('svg');
      expect(icons.length).toBeGreaterThan(0);
    });
  });
});
