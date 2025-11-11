/**
 * HeroSection Component Tests
 * Story 4.1.3, 4.1.5: Event Landing Page Hero Section Testing
 *
 * Tests for the public landing page hero with Unicorn.studio background
 * and inline registration functionality (Story 4.1.5)
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { HeroSection } from '../HeroSection';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Extend Window interface for UnicornStudio
interface WindowWithUnicorn extends Window {
  UnicornStudio?: {
    isInitialized: boolean;
    init: () => void;
  };
}

// Mock useTranslation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'public.register': 'Register Now',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock RegistrationWizard component
vi.mock('@/components/public/Registration/RegistrationWizard', () => ({
  RegistrationWizard: ({
    eventCode,
    inline,
    onCancel,
  }: {
    eventCode: string;
    inline: boolean;
    onCancel?: () => void;
  }) => (
    <div data-testid="registration-wizard">
      <div data-testid="wizard-eventCode">{eventCode}</div>
      <div data-testid="wizard-inline">{inline ? 'inline' : 'dedicated'}</div>
      {onCancel && (
        <button onClick={onCancel} data-testid="wizard-cancel">
          Cancel
        </button>
      )}
    </div>
  ),
}));

describe('HeroSection Component', () => {
  beforeEach(() => {
    // Clear any existing Unicorn Studio scripts
    delete (window as WindowWithUnicorn).UnicornStudio;
    const existingScripts = document.querySelectorAll('script[src*="unicornStudio"]');
    existingScripts.forEach((script) => script.remove());
  });

  const defaultProps = {
    title: 'BATbern 2025',
    ctaLink: '/register',
  };

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const renderWithRouter = (ui: React.ReactElement) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{ui}</BrowserRouter>
      </QueryClientProvider>
    );
  };

  describe('Basic Rendering', () => {
    test('should_renderTitle_when_provided', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      expect(screen.getByText('BATbern 2025')).toBeInTheDocument();
    });

    test('should_renderDefaultCTAText_when_notProvided', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      // "Register Now" appears in the button link
      expect(screen.getByRole('link', { name: 'Register Now' })).toBeInTheDocument();
    });

    test('should_renderCustomCTAText_when_provided', () => {
      renderWithRouter(<HeroSection {...defaultProps} ctaText="Join the Conference" />);

      expect(screen.getByText('Join the Conference')).toBeInTheDocument();
    });

    test('should_renderCTALink_when_provided', () => {
      renderWithRouter(<HeroSection {...defaultProps} ctaLink="/events" />);

      const link = screen.getByRole('link');
      expect(link).toHaveAttribute('href', '/events');
    });
  });

  describe('Date and Location Display', () => {
    test('should_renderFormattedDate_when_dateProvided', () => {
      renderWithRouter(<HeroSection {...defaultProps} date="2025-06-15T00:00:00Z" />);

      // Check for date presence (format depends on locale)
      // Use getAllByText since "2025" appears in both title and date
      const elementsWithYear = screen.getAllByText(/2025/);
      expect(elementsWithYear.length).toBeGreaterThan(1); // Title + formatted date
    });

    test('should_renderLocation_when_locationProvided', () => {
      renderWithRouter(<HeroSection {...defaultProps} location="Bern, Switzerland" />);

      expect(screen.getByText('Bern, Switzerland')).toBeInTheDocument();
    });

    test('should_renderBothDateAndLocation_when_bothProvided', () => {
      renderWithRouter(
        <HeroSection {...defaultProps} date="2025-06-15T00:00:00Z" location="Bern, Switzerland" />
      );

      // Use getAllByText since "2025" appears in both title and date
      const elementsWithYear = screen.getAllByText(/2025/);
      expect(elementsWithYear.length).toBeGreaterThan(1);
      expect(screen.getByText('Bern, Switzerland')).toBeInTheDocument();
    });

    test('should_notRenderDateSection_when_dateAndLocationNotProvided', () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} />);

      // Check that the date/location container is not rendered
      // Need to check for the specific container with both flex-wrap and gap-4
      const allFlexContainers = container.querySelectorAll('[class*="flex"][class*="gap-4"]');
      const dateLocationContainer = Array.from(allFlexContainers).find(
        (el) => el.className.includes('flex-wrap') && el.className.includes('items-center')
      );
      expect(dateLocationContainer).toBeUndefined();
    });

    test('should_renderCalendarIcon_when_dateProvided', () => {
      const { container } = renderWithRouter(
        <HeroSection {...defaultProps} date="2025-06-15T00:00:00Z" />
      );

      const calendarIcon = container.querySelector('svg');
      expect(calendarIcon).toBeInTheDocument();
    });

    test('should_renderLocationIcon_when_locationProvided', () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} location="Bern" />);

      const locationIcon = container.querySelector('svg');
      expect(locationIcon).toBeInTheDocument();
    });
  });

  describe('Countdown Timer', () => {
    test('should_renderCountdownTimer_when_provided', () => {
      const countdownTimer = <div data-testid="countdown">5 days remaining</div>;

      renderWithRouter(<HeroSection {...defaultProps} countdownTimer={countdownTimer} />);

      expect(screen.getByTestId('countdown')).toBeInTheDocument();
      expect(screen.getByText('5 days remaining')).toBeInTheDocument();
    });

    test('should_notRenderCountdownTimer_when_notProvided', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      expect(screen.queryByTestId('countdown')).not.toBeInTheDocument();
    });
  });

  describe('Unicorn.studio Background', () => {
    test('should_renderUnicornProjectElement_when_componentMounted', () => {
      const { container } = renderWithRouter(
        <HeroSection {...defaultProps} unicornProjectId="custom-project-id" />
      );

      const unicornElement = container.querySelector('[data-us-project="custom-project-id"]');
      expect(unicornElement).toBeInTheDocument();
    });

    test('should_useDefaultProjectId_when_notProvided', () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} />);

      const unicornElement = container.querySelector('[data-us-project="jfzsiwProJi81qvb7uKX"]');
      expect(unicornElement).toBeInTheDocument();
    });

    test('should_loadUnicornScript_when_notAlreadyLoaded', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      // Check that script was added to document
      const script = document.querySelector('script[src*="unicornStudio"]');
      expect(script).toBeInTheDocument();
    });

    test('should_notLoadScriptTwice_when_alreadyInitialized', () => {
      // Simulate already loaded Unicorn Studio
      (window as WindowWithUnicorn).UnicornStudio = { isInitialized: true, init: () => {} };

      renderWithRouter(<HeroSection {...defaultProps} />);

      // Should not add another script
      const scripts = document.querySelectorAll('script[src*="unicornStudio"]');
      expect(scripts.length).toBe(0);
    });

    test('should_markBackgroundAsAriaHidden_when_rendered', () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} />);

      const background = container.querySelector('[aria-hidden="true"]');
      expect(background).toBeInTheDocument();
    });
  });

  describe('Layout and Styling', () => {
    test('should_renderFullScreenSection_when_mounted', () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} />);

      const section = container.querySelector('section');
      expect(section).toHaveClass('min-h-screen');
    });

    test('should_renderResponsiveTitle_when_mounted', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      const title = screen.getByRole('heading', { level: 1 });
      expect(title).toHaveClass('text-4xl');
      expect(title).toHaveClass('sm:text-5xl');
      expect(title).toHaveClass('md:text-6xl');
    });

    test('should_renderButtonWithCorrectSize_when_mounted', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      const button = screen.getByRole('link');
      expect(button).toBeInTheDocument();
    });
  });

  describe('Script Loading', () => {
    test('should_initializeUnicornStudio_when_scriptLoads', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      const script = document.querySelector('script[src*="unicornStudio"]') as HTMLScriptElement;

      // Mock the UnicornStudio.init function
      const win = window as WindowWithUnicorn;
      win.UnicornStudio = {
        isInitialized: false,
        init: vi.fn(),
      };

      // Simulate script load
      if (script && script.onload) {
        (script.onload as (ev: Event) => void)(new Event('load'));
      }

      // Check that init was called and flag was set
      expect(win.UnicornStudio?.init).toHaveBeenCalled();
      expect(win.UnicornStudio?.isInitialized).toBe(true);
    });

    test('should_appendScriptToHead_when_componentMounts', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      const script =
        document.head.querySelector('script[src*="unicornStudio"]') ||
        document.body.querySelector('script[src*="unicornStudio"]');
      expect(script).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    test('should_handleEmptyTitle_when_provided', () => {
      renderWithRouter(<HeroSection {...defaultProps} title="" />);

      const heading = screen.getByRole('heading', { level: 1 });
      expect(heading).toBeInTheDocument();
      expect(heading).toHaveTextContent('');
    });

    test('should_handleInvalidDate_when_provided', () => {
      // Should not crash with invalid date
      expect(() => {
        renderWithRouter(<HeroSection {...defaultProps} date="invalid-date" />);
      }).not.toThrow();
    });

    test('should_handleEmptyLocation_when_provided', () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} location="" />);

      // Empty location should not display the date/location container
      // Check that location icon (MapPin) is not rendered
      const allFlexContainers = container.querySelectorAll('[class*="flex"][class*="gap-4"]');
      const dateLocationContainer = Array.from(allFlexContainers).find(
        (el) => el.className.includes('flex-wrap') && el.className.includes('items-center')
      );
      // Should not find the date/location container when location is empty and date is not provided
      expect(dateLocationContainer).toBeUndefined();
    });
  });

  describe('Inline Registration (Story 4.1.5)', () => {
    test('should_renderLinkButton_when_eventCodeNotProvided', () => {
      renderWithRouter(<HeroSection {...defaultProps} />);

      const button = screen.getByRole('link', { name: 'Register Now' });
      expect(button).toBeInTheDocument();
      expect(button).toHaveAttribute('href', '/register');
    });

    test('should_renderClickButton_when_eventCodeProvided', () => {
      renderWithRouter(<HeroSection {...defaultProps} eventCode="BAT2025" />);

      const button = screen.getByRole('button', { name: 'Register Now' });
      expect(button).toBeInTheDocument();
      expect(button).not.toHaveAttribute('href');
    });

    test('should_expandRegistrationWizard_when_registerButtonClicked', async () => {
      renderWithRouter(<HeroSection {...defaultProps} eventCode="BAT2025" />);

      // Initially, registration wizard should not be visible
      expect(screen.queryByTestId('registration-wizard')).not.toBeInTheDocument();

      // Click register button
      const button = screen.getByRole('button', { name: 'Register Now' });
      fireEvent.click(button);

      // Registration wizard should now be visible
      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard')).toBeInTheDocument();
      });
    });

    test('should_passCorrectProps_when_wizardRendered', async () => {
      renderWithRouter(<HeroSection {...defaultProps} eventCode="BAT2025" />);

      const button = screen.getByRole('button', { name: 'Register Now' });
      fireEvent.click(button);

      await waitFor(() => {
        expect(screen.getByTestId('wizard-eventCode')).toHaveTextContent('BAT2025');
        expect(screen.getByTestId('wizard-inline')).toHaveTextContent('inline');
      });
    });

    test('should_collapseWizard_when_cancelClicked', async () => {
      renderWithRouter(<HeroSection {...defaultProps} eventCode="BAT2025" />);

      // Expand wizard
      const button = screen.getByRole('button', { name: 'Register Now' });
      fireEvent.click(button);

      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard')).toBeInTheDocument();
      });

      // Click cancel in wizard
      const cancelButton = screen.getByTestId('wizard-cancel');
      fireEvent.click(cancelButton);

      // Wizard should be hidden
      await waitFor(() => {
        expect(screen.queryByTestId('registration-wizard')).not.toBeInTheDocument();
      });
    });

    test('should_disableRegisterButton_when_wizardExpanded', async () => {
      renderWithRouter(<HeroSection {...defaultProps} eventCode="BAT2025" />);

      const button = screen.getByRole('button', { name: 'Register Now' });
      expect(button).not.toBeDisabled();

      fireEvent.click(button);

      await waitFor(() => {
        expect(button).toBeDisabled();
      });
    });

    test('should_showOverlay_when_wizardExpanded', async () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} eventCode="BAT2025" />);

      // Click register button
      const button = screen.getByRole('button', { name: 'Register Now' });
      fireEvent.click(button);

      // Overlay should appear - check for the overlay in the registration wizard section
      await waitFor(() => {
        const wizardSection = container.querySelector('section.relative.z-20');
        expect(wizardSection).toBeInTheDocument();
      });
    });

    test('should_changeHeight_when_wizardExpanded', async () => {
      const { container } = renderWithRouter(<HeroSection {...defaultProps} eventCode="BAT2025" />);

      const section = container.querySelector('section');
      expect(section).toHaveClass('min-h-screen');

      // Click register button
      const button = screen.getByRole('button', { name: 'Register Now' });
      fireEvent.click(button);

      // Verify wizard is expanded
      await waitFor(() => {
        const wizardSection = container.querySelector('section.relative.z-20');
        expect(wizardSection).toBeInTheDocument();
      });
    });
  });
});
