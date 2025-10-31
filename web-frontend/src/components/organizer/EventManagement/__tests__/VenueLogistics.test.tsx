/**
 * VenueLogistics Component Tests (Task 11a - RED Phase)
 *
 * Story 2.5.3 - AC6: Venue & Logistics Management
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1 (lines 48-66)
 *
 * Tests cover:
 * - Test 6.1: should_displayVenueDetails_when_venueSelected
 * - Test 6.2: should_showBookingStatus_when_venueBooked
 * - Test 6.3: should_openVenueSelector_when_changeVenueClicked
 * - Test 6.4: should_openCateringConfig_when_configureMenuClicked
 *
 * Expected Result: ALL tests should FAIL (component doesn't exist yet)
 */

import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { VenueLogistics } from '../VenueLogistics';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import type { EventDetail } from '@/types/event.types';

// Mock data
const mockVenue = {
  venueCode: 'KURSAAL_BERN',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3, 3013 Bern',
  venueCapacity: 200,
  parking: true,
  wheelchairAccess: true,
  amenities: ['WiFi', 'Projector', 'Sound System'],
};

const mockBooking = {
  status: 'confirmed' as const,
  confirmationNumber: 'KB-2025-03-001',
  contact: {
    name: 'Anna Schmidt',
    email: 'anna.schmidt@kursaal-bern.ch',
    phone: '+41 31 339 55 00',
  },
};

const mockCatering = {
  provider: 'Swiss Catering AG',
  menuConfigured: true,
  dietaryRequirements: {
    vegetarian: 5,
    vegan: 2,
    glutenFree: 3,
  },
};

const mockEvent: EventDetail = {
  eventId: '123e4567-e89b-12d3-a456-426614174000',
  eventCode: 'BAT54',
  eventNumber: 54,
  title: 'Spring Conference 2025',
  description: 'Advanced microservices architecture',
  date: '2025-03-15T09:00:00Z',
  registrationDeadline: '2025-03-10T23:59:59Z',
  venueName: mockVenue.venueName,
  venueAddress: mockVenue.venueAddress,
  venueCapacity: mockVenue.venueCapacity,
  status: 'published',
  organizerUsername: 'john.doe',
  currentAttendeeCount: 87,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
  // Extended venue details
  venue: mockVenue,
  booking: mockBooking,
  catering: mockCatering,
};

const mockEventWithoutVenue: EventDetail = {
  ...mockEvent,
  venueName: '',
  venueAddress: '',
  venue: undefined,
  booking: undefined,
  catering: undefined,
};

const mockEventWithPendingBooking: EventDetail = {
  ...mockEvent,
  booking: {
    status: 'pending',
    confirmationNumber: undefined,
    contact: undefined,
  },
};

// Test wrapper with providers
const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </QueryClientProvider>
  );
};

describe('VenueLogistics Component (AC6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Venue Display (Test 6.1)', () => {
    it('should_displayVenueDetails_when_venueSelected', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      // Venue name should be displayed
      expect(screen.getByText('Kursaal Bern')).toBeInTheDocument();

      // Address should be displayed
      expect(screen.getByText('Kornhausstrasse 3, 3013 Bern')).toBeInTheDocument();

      // Capacity should be displayed
      expect(screen.getByText(/Capacity: 200/i)).toBeInTheDocument();

      // Amenities should be displayed
      expect(screen.getByText(/Parking: Available/i)).toBeInTheDocument();
      expect(screen.getByText(/Wheelchair Access: Yes/i)).toBeInTheDocument();
    });

    it('should_displayPlaceholder_when_noVenueSelected', () => {
      renderWithProviders(<VenueLogistics event={mockEventWithoutVenue} onUpdate={vi.fn()} />);

      expect(screen.getByText(/No venue selected/i)).toBeInTheDocument();
      expect(screen.getByText(/Select a venue to continue/i)).toBeInTheDocument();
    });

    it('should_displayVenueAmenities_when_amenitiesExist', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      // Check for WiFi amenity
      expect(screen.getByText(/WiFi/i)).toBeInTheDocument();
      // Check for Projector
      expect(screen.getByText(/Projector/i)).toBeInTheDocument();
      // Check for Sound System
      expect(screen.getByText(/Sound System/i)).toBeInTheDocument();
    });

    it('should_displayAccessibility_when_wheelchairAccessAvailable', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const accessibilityText = screen.getByText(/Wheelchair Access/i);
      expect(accessibilityText).toBeInTheDocument();
      expect(accessibilityText.textContent).toContain('Yes');
    });

    it('should_displayNoAccessibility_when_wheelchairAccessNotAvailable', () => {
      const eventNoAccess = {
        ...mockEvent,
        venue: { ...mockVenue, wheelchairAccess: false },
      };
      renderWithProviders(<VenueLogistics event={eventNoAccess} onUpdate={vi.fn()} />);

      const accessibilityText = screen.getByText(/Wheelchair Access/i);
      expect(accessibilityText.textContent).toContain('No');
    });
  });

  describe('Booking Status Display (Test 6.2)', () => {
    it('should_showBookingStatus_when_venueBooked', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      // Should show "Confirmed" status
      expect(screen.getByText(/Booking Status:/i)).toBeInTheDocument();
      expect(screen.getByText(/✓ Confirmed/i)).toBeInTheDocument();

      // Should show confirmation number
      expect(screen.getByText(/KB-2025-03-001/i)).toBeInTheDocument();
    });

    it('should_showPendingStatus_when_bookingPending', () => {
      renderWithProviders(
        <VenueLogistics event={mockEventWithPendingBooking} onUpdate={vi.fn()} />
      );

      expect(screen.getByText(/Pending/i)).toBeInTheDocument();
      expect(screen.queryByText(/Confirmation/i)).not.toBeInTheDocument();
    });

    it('should_showNotBookedStatus_when_noBooking', () => {
      renderWithProviders(<VenueLogistics event={mockEventWithoutVenue} onUpdate={vi.fn()} />);

      expect(screen.getByText(/Not Booked/i)).toBeInTheDocument();
    });

    it('should_displayContactInfo_when_bookingConfirmed', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      // Contact name
      expect(screen.getByText(/Anna Schmidt/i)).toBeInTheDocument();

      // Contact email
      expect(screen.getByText(/anna.schmidt@kursaal-bern.ch/i)).toBeInTheDocument();

      // Contact phone
      expect(screen.getByText(/\+41 31 339 55 00/i)).toBeInTheDocument();
    });

    it('should_notDisplayContactInfo_when_bookingNotConfirmed', () => {
      renderWithProviders(
        <VenueLogistics event={mockEventWithPendingBooking} onUpdate={vi.fn()} />
      );

      expect(screen.queryByText(/Contact:/i)).not.toBeInTheDocument();
    });

    it('should_displayConfirmationNumber_when_bookingConfirmed', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const confirmationText = screen.getByText(/Confirmation #:/i);
      expect(confirmationText).toBeInTheDocument();
      expect(confirmationText.textContent).toContain('KB-2025-03-001');
    });
  });

  describe('Venue Selection (Test 6.3)', () => {
    it('should_openVenueSelector_when_changeVenueClicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      // Modal should open
      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.getByText(/Select Venue/i)).toBeInTheDocument();
      });
    });

    it('should_displayVenueList_when_venueModalOpened', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      // Should show venue options
      await waitFor(() => {
        expect(screen.getByText(/Kursaal Bern/i)).toBeInTheDocument();
        // Other venues from mock list
        expect(screen.getByText(/Kornhausforum Bern/i)).toBeInTheDocument();
        expect(screen.getByText(/Hotel Schweizerhof/i)).toBeInTheDocument();
      });
    });

    it('should_closeVenueSelector_when_cancelClicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      // Open modal
      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Close modal
      const cancelButton = screen.getByRole('button', { name: /Cancel/i });
      await user.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
    });

    it('should_updateVenue_when_venueSelected', async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={onUpdate} />);

      // Open modal
      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Select new venue
      const newVenueOption = screen.getByText(/Kornhausforum Bern/i);
      await user.click(newVenueOption);

      // Confirm selection
      const confirmButton = screen.getByRole('button', { name: /Confirm/i });
      await user.click(confirmButton);

      // Should call onUpdate with new venue
      await waitFor(() => {
        expect(onUpdate).toHaveBeenCalledWith({
          venueCode: 'KORNHAUSFORUM_BERN',
          venueName: 'Kornhausforum Bern',
        });
      });
    });

    it('should_displayVenueCapacity_when_selectingVenue', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      await waitFor(() => {
        // Should show capacity for each venue
        expect(screen.getByText(/Capacity: 200/i)).toBeInTheDocument();
        expect(screen.getByText(/Capacity: 150/i)).toBeInTheDocument();
      });
    });
  });

  describe('Catering Configuration (Test 6.4)', () => {
    it('should_openCateringConfig_when_configureMenuClicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const configureButton = screen.getByRole('button', { name: /Configure Menu/i });
      await user.click(configureButton);

      // Modal should open
      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.getByText(/Catering Configuration/i)).toBeInTheDocument();
      });
    });

    it('should_displayCateringProvider_when_providerConfigured', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      expect(screen.getByText(/Provider:/i)).toBeInTheDocument();
      expect(screen.getByText(/Swiss Catering AG/i)).toBeInTheDocument();
    });

    it('should_displayMenuStatus_when_menuConfigured', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      expect(screen.getByText(/Menu:/i)).toBeInTheDocument();
      expect(screen.getByText(/Configured/i)).toBeInTheDocument();
    });

    it('should_displayMenuNotConfigured_when_menuNotSet', () => {
      const eventNoMenu = {
        ...mockEvent,
        catering: { ...mockCatering, menuConfigured: false },
      };
      renderWithProviders(<VenueLogistics event={eventNoMenu} onUpdate={vi.fn()} />);

      expect(screen.getByText(/Not configured/i)).toBeInTheDocument();
    });

    it('should_displayDietaryRequirements_when_requirementsSet', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      expect(screen.getByText(/5 vegetarian/i)).toBeInTheDocument();
      expect(screen.getByText(/2 vegan/i)).toBeInTheDocument();
      expect(screen.getByText(/3 gluten-free/i)).toBeInTheDocument();
    });

    it('should_saveCateringConfig_when_formSubmitted', async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={onUpdate} />);

      // Open catering config
      const configureButton = screen.getByRole('button', { name: /Configure Menu/i });
      await user.click(configureButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Fill form
      const providerSelect = screen.getByLabelText(/Catering Provider/i);
      await user.click(providerSelect);
      await user.click(screen.getByText(/Swiss Catering AG/i));

      const vegetarianInput = screen.getByLabelText(/Vegetarian/i);
      await user.clear(vegetarianInput);
      await user.type(vegetarianInput, '10');

      // Submit
      const saveButton = screen.getByRole('button', { name: /Save/i });
      await user.click(saveButton);

      // Should call onUpdate
      await waitFor(() => {
        expect(onUpdate).toHaveBeenCalledWith(
          expect.objectContaining({
            catering: expect.objectContaining({
              provider: 'Swiss Catering AG',
              dietaryRequirements: expect.objectContaining({
                vegetarian: 10,
              }),
            }),
          })
        );
      });
    });

    it('should_closeCateringConfig_when_cancelClicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      // Open modal
      const configureButton = screen.getByRole('button', { name: /Configure Menu/i });
      await user.click(configureButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Close modal
      const cancelButton = screen.getByRole('button', { name: /Cancel/i });
      await user.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    it('should_haveAriaLabels_when_rendered', () => {
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      expect(screen.getByLabelText(/Venue and Logistics section/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Change Venue/i })).toHaveAttribute(
        'aria-label',
        'Change Venue'
      );
      expect(screen.getByRole('button', { name: /Configure Menu/i })).toHaveAttribute(
        'aria-label',
        'Configure Menu'
      );
    });

    it('should_supportKeyboardNavigation_when_interacting', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });

      // Tab to button
      await user.tab();
      expect(changeVenueButton).toHaveFocus();

      // Press Enter to activate
      await user.keyboard('{Enter}');

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });
    });

    it('should_trapFocus_when_modalOpen', async () => {
      const user = userEvent.setup();
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Focus should be trapped within modal
      const dialog = screen.getByRole('dialog');
      const focusableElements = within(dialog).getAllByRole('button');

      expect(focusableElements.length).toBeGreaterThan(0);
    });
  });

  describe('Internationalization (i18n)', () => {
    it('should_displayEnglishText_when_localeIsEn', () => {
      i18n.changeLanguage('en');
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      expect(screen.getByText(/Booking Status:/i)).toBeInTheDocument();
      expect(screen.getByText(/Catering/i)).toBeInTheDocument();
    });

    it('should_displayGermanText_when_localeIsDe', async () => {
      await i18n.changeLanguage('de');
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      expect(screen.getByText(/Buchungsstatus:/i)).toBeInTheDocument();
      expect(screen.getByText(/Catering/i)).toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('should_displayError_when_venueUpdateFails', async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn().mockRejectedValue(new Error('Network error'));
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={onUpdate} />);

      // Open venue selector
      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Select venue
      const newVenueOption = screen.getByText(/Kornhausforum Bern/i);
      await user.click(newVenueOption);

      const confirmButton = screen.getByRole('button', { name: /Confirm/i });
      await user.click(confirmButton);

      // Should show error
      await waitFor(() => {
        expect(screen.getByText(/Failed to update venue/i)).toBeInTheDocument();
      });
    });

    it('should_displayError_when_cateringUpdateFails', async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn().mockRejectedValue(new Error('Network error'));
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={onUpdate} />);

      // Open catering config
      const configureButton = screen.getByRole('button', { name: /Configure Menu/i });
      await user.click(configureButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Submit
      const saveButton = screen.getByRole('button', { name: /Save/i });
      await user.click(saveButton);

      // Should show error
      await waitFor(() => {
        expect(screen.getByText(/Failed to save catering configuration/i)).toBeInTheDocument();
      });
    });
  });

  describe('Loading States', () => {
    it('should_displayLoadingSpinner_when_updatingVenue', async () => {
      const user = userEvent.setup();
      const onUpdate = vi
        .fn()
        .mockImplementation(() => new Promise((resolve) => setTimeout(resolve, 1000)));
      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={onUpdate} />);

      // Open venue selector
      const changeVenueButton = screen.getByRole('button', { name: /Change Venue/i });
      await user.click(changeVenueButton);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });

      // Select venue
      const newVenueOption = screen.getByText(/Kornhausforum Bern/i);
      await user.click(newVenueOption);

      const confirmButton = screen.getByRole('button', { name: /Confirm/i });
      await user.click(confirmButton);

      // Should show loading state
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Responsive Design', () => {
    it('should_stackVertically_when_mobileViewport', () => {
      // Mock mobile viewport
      global.innerWidth = 375;
      global.dispatchEvent(new Event('resize'));

      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const container = screen.getByLabelText(/Venue and Logistics section/i);
      expect(container).toHaveStyle({ flexDirection: 'column' });
    });

    it('should_displayHorizontal_when_desktopViewport', () => {
      // Mock desktop viewport
      global.innerWidth = 1920;
      global.dispatchEvent(new Event('resize'));

      renderWithProviders(<VenueLogistics event={mockEvent} onUpdate={vi.fn()} />);

      const container = screen.getByLabelText(/Venue and Logistics section/i);
      expect(container).toHaveStyle({ flexDirection: 'row' });
    });
  });
});
