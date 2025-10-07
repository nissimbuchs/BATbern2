/**
 * NavigationMenu Component Tests
 * Story 1.17, Task 6a: TDD for role-adaptive navigation menu
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { NavigationMenu } from './NavigationMenu';
import type { UserRole } from '@/types/auth';

describe('NavigationMenu Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderWithRouter = (component: React.ReactElement) => {
    return render(<BrowserRouter>{component}</BrowserRouter>);
  };

  describe('Organizer Navigation', () => {
    test('should_renderOrganizerMenuItems_when_roleIsOrganizer', () => {
      renderWithRouter(<NavigationMenu userRole="organizer" />);

      // Organizer should see full menu: Events, Speakers, Partners, Analytics
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/speakers/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/partners/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/analytics/i)[0]).toBeInTheDocument();
    });

    test('should_linkToEventsManagement_when_eventsClicked', () => {
      renderWithRouter(<NavigationMenu userRole="organizer" />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveAttribute('href', '/organizer/events');
    });

    test('should_linkToSpeakersManagement_when_speakersClicked', () => {
      renderWithRouter(<NavigationMenu userRole="organizer" />);

      const speakersLink = screen.getAllByText(/speakers/i)[0].closest('a');
      expect(speakersLink).toHaveAttribute('href', '/organizer/speakers');
    });

    test('should_linkToPartnersManagement_when_partnersClicked', () => {
      renderWithRouter(<NavigationMenu userRole="organizer" />);

      const partnersLink = screen.getAllByText(/partners/i)[0].closest('a');
      expect(partnersLink).toHaveAttribute('href', '/organizer/partners');
    });
  });

  describe('Speaker Navigation', () => {
    test('should_renderSpeakerMenuItems_when_roleIsSpeaker', () => {
      renderWithRouter(<NavigationMenu userRole="speaker" />);

      // Speaker should see: Dashboard, My Events, My Content, Profile
      expect(screen.getAllByText(/dashboard/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my content/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/profile/i)[0]).toBeInTheDocument();
    });

    test('should_notShowPartnersMenu_when_roleIsSpeaker', () => {
      renderWithRouter(<NavigationMenu userRole="speaker" />);

      // Speaker should NOT see Partners menu
      expect(screen.queryByText(/^partners$/i)).not.toBeInTheDocument();
    });

    test('should_linkToMyEvents_when_myEventsClicked', () => {
      renderWithRouter(<NavigationMenu userRole="speaker" />);

      const myEventsLink = screen.getAllByText(/my events/i)[0].closest('a');
      expect(myEventsLink).toHaveAttribute('href', '/speaker/events');
    });
  });

  describe('Partner Navigation', () => {
    test('should_renderPartnerMenuItems_when_roleIsPartner', () => {
      renderWithRouter(<NavigationMenu userRole="partner" />);

      // Partner should see: Dashboard, Events (read-only), Analytics, Profile
      expect(screen.getAllByText(/dashboard/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/analytics/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/profile/i)[0]).toBeInTheDocument();
    });

    test('should_notShowSpeakersMenu_when_roleIsPartner', () => {
      renderWithRouter(<NavigationMenu userRole="partner" />);

      // Partner should NOT see Speakers menu
      expect(screen.queryByText(/speakers/i)).not.toBeInTheDocument();
    });

    test('should_linkToPartnerDashboard_when_dashboardClicked', () => {
      renderWithRouter(<NavigationMenu userRole="partner" />);

      const dashboardLink = screen.getAllByText(/dashboard/i)[0].closest('a');
      expect(dashboardLink).toHaveAttribute('href', '/partner/dashboard');
    });
  });

  describe('Attendee Navigation', () => {
    test('should_renderAttendeeMenuItems_when_roleIsAttendee', () => {
      renderWithRouter(<NavigationMenu userRole="attendee" />);

      // Attendee should see: Events, Speakers, My Registrations
      expect(screen.getAllByText(/events/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/speakers/i)[0]).toBeInTheDocument();
      expect(screen.getAllByText(/my registrations/i)[0]).toBeInTheDocument();
    });

    test('should_notShowAnalytics_when_roleIsAttendee', () => {
      renderWithRouter(<NavigationMenu userRole="attendee" />);

      // Attendee should NOT see Analytics menu
      expect(screen.queryByText(/analytics/i)).not.toBeInTheDocument();
    });

    test('should_linkToEventsList_when_eventsClicked', () => {
      renderWithRouter(<NavigationMenu userRole="attendee" />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveAttribute('href', '/attendee/events');
    });
  });

  describe('Active State', () => {
    test('should_highlightActiveMenuItem_when_onCurrentPage', () => {
      // Mock current location
      window.history.pushState({}, '', '/organizer/events');

      renderWithRouter(<NavigationMenu userRole="organizer" />);

      const eventsLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(eventsLink).toHaveClass(/active|selected/i);
    });

    test('should_notHighlightInactiveMenuItems_when_onDifferentPage', () => {
      // Mock current location
      window.history.pushState({}, '', '/organizer/events');

      renderWithRouter(<NavigationMenu userRole="organizer" />);

      const speakersLink = screen.getAllByText(/speakers/i)[0].closest('a');
      expect(speakersLink).not.toHaveClass(/active|selected/i);
    });
  });

  describe('Accessibility', () => {
    test('should_haveProperAriaLabels_when_rendered', () => {
      renderWithRouter(<NavigationMenu userRole="organizer" />);

      const navigation = screen.getByRole('navigation');
      expect(navigation).toHaveAttribute('aria-label', 'main navigation');
    });

    test('should_supportKeyboardNavigation_when_rendered', () => {
      renderWithRouter(<NavigationMenu userRole="organizer" />);

      const firstLink = screen.getAllByText(/events/i)[0].closest('a');
      expect(firstLink).toHaveAttribute('tabindex', '0');
    });
  });

  describe('Icons', () => {
    test('should_displayIconsForMenuItems_when_rendered', () => {
      const { container } = renderWithRouter(<NavigationMenu userRole="organizer" />);

      // Should have Material-UI icons for each menu item
      const icons = container.querySelectorAll('svg');
      expect(icons.length).toBeGreaterThan(0);
    });
  });
});
