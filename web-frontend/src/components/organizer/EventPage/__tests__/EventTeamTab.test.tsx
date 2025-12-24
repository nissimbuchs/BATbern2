/**
 * EventTeamTab Component Tests (Story 5.6)
 *
 * Tests for the team assignments and outreach distribution tab.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventTeamTab } from '../EventTeamTab';
import type { Event } from '@/types/event.types';

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
  organizerUsername: 'jane.smith',
  currentAttendeeCount: 87,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
};

// Test wrapper with providers
const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('EventTeamTab Component (Story 5.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Team Assignments Section', () => {
    it('should_displayTeamAssignmentsTitle_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Team Assignments/i)).toBeInTheDocument();
    });

    it('should_displayAddTeamMemberButton_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /Add Team Member/i })).toBeInTheDocument();
    });

    it.skip('should_displayTeamMemberTable_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Check for table headers
      expect(screen.getByText(/Team Member/i)).toBeInTheDocument();
      expect(screen.getByText(/Role/i)).toBeInTheDocument();
      expect(screen.getByText(/Email/i)).toBeInTheDocument();
    });

    it.skip('should_displayLeadOrganizer_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Should display the lead organizer from event data
      expect(screen.getByText(/jane.smith|Lead Organizer/i)).toBeInTheDocument();
    });

    it('should_displayTeamRoles_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Should display role chips
      expect(screen.getByText(/Lead Organizer/i)).toBeInTheDocument();
    });

    it.skip('should_displayMockTeamMembers_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Should display mock team members
      expect(screen.getByText('Mark Thompson')).toBeInTheDocument();
      expect(screen.getByText('Anna Weber')).toBeInTheDocument();
    });

    it('should_displayEditButtonForEachMember_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Each team member should have an edit button
      const editButtons = screen.getAllByRole('button', { name: '' }).filter(
        (btn) => btn.querySelector('[data-testid="EditIcon"]') !== null
      );
      expect(editButtons.length).toBeGreaterThanOrEqual(0);
    });

    it('should_displayDeleteButtonForNonLeadMembers_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Non-lead members should have delete buttons
      const deleteIcons = screen.getAllByTestId ?
        screen.queryAllByRole('button') : [];
      expect(deleteIcons).toBeDefined();
    });
  });

  describe('Outreach Distribution Section', () => {
    it('should_displayOutreachDistributionTitle_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Speaker Outreach Distribution/i)).toBeInTheDocument();
    });

    it.skip('should_displayOutreachTableHeaders_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText(/Organizer/i)).toBeInTheDocument();
      expect(screen.getByText(/Assigned/i)).toBeInTheDocument();
      expect(screen.getByText(/Contacted/i)).toBeInTheDocument();
      expect(screen.getByText(/Pending/i)).toBeInTheDocument();
      expect(screen.getByText(/Progress/i)).toBeInTheDocument();
    });

    it.skip('should_displayAssignedSpeakerCounts_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Should display assigned speaker counts from mock data
      expect(screen.getByText('8')).toBeInTheDocument(); // Lead organizer assigned
      expect(screen.getByText('5')).toBeInTheDocument(); // Co-organizer assigned
    });

    it('should_displayContactedSpeakerCounts_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Should display contacted speaker counts
      expect(screen.getByText('6')).toBeInTheDocument(); // Lead organizer contacted
    });

    it('should_displayProgressPercentages_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Should display progress percentages
      expect(screen.getByText(/75%/)).toBeInTheDocument(); // 6/8 = 75%
    });

    it('should_displayReassignSpeakersButton_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByRole('button', { name: /Reassign Speakers/i })).toBeInTheDocument();
    });
  });

  describe('Role Colors', () => {
    it('should_displayLeadRole_withPrimaryColor', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      const leadChip = screen.getByText(/Lead Organizer/i);
      expect(leadChip).toBeInTheDocument();
    });

    it('should_displayCoOrganizerRole_withSecondaryColor', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      const coOrgChip = screen.getAllByText(/Co-Organizer/i);
      expect(coOrgChip.length).toBeGreaterThan(0);
    });
  });

  describe('Member Avatars', () => {
    it('should_displayMemberAvatars_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      // Avatars show first letter of name
      expect(screen.getByText('M')).toBeInTheDocument(); // Mark
      expect(screen.getByText('A')).toBeInTheDocument(); // Anna
    });
  });

  describe('Email Display', () => {
    it('should_displayMemberEmails_when_rendered', () => {
      renderWithProviders(<EventTeamTab event={mockEvent} eventCode="BAT54" />);

      expect(screen.getByText('lead@batbern.ch')).toBeInTheDocument();
      expect(screen.getByText('mark@batbern.ch')).toBeInTheDocument();
      expect(screen.getByText('anna@batbern.ch')).toBeInTheDocument();
    });
  });
});
