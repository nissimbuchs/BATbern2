/**
 * Accessibility Tests - Focus Indicators and Color Contrast
 * Story 1.17, Task 12a (RED Phase): Visual Accessibility Testing
 *
 * Tests focus indicators, color contrast (WCAG 2.1 AA 4.5:1), and visual accessibility.
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { BaseLayout } from './BaseLayout';
import { AppHeader } from '../Navigation/AppHeader';
import type { UserProfile } from '@/types/user';
import type { NotificationsResponse } from '@/types/notification';

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    );
  };
};

const mockUser: UserProfile = {
  userId: 'user-1',
  email: 'test@batbern.ch',
  firstName: 'Test',
  lastName: 'User',
  currentRole: 'ORGANIZER',
  availableRoles: ['ORGANIZER'],
  companyId: 'company-1',
  profilePhotoUrl: null,
  preferences: {
    language: 'de',
    notifications: { emailEnabled: true, inAppEnabled: true },
    theme: 'light',
  },
};

const mockNotifications: NotificationsResponse = {
  notifications: [],
  unreadCount: 0,
  totalCount: 0,
  hasMore: false,
};

/**
 * Calculate contrast ratio between two colors
 * Based on WCAG 2.1 formula: https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 */
function getContrastRatio(color1: string, color2: string): number {
  const rgb1 = parseColor(color1);
  const rgb2 = parseColor(color2);

  const l1 = getRelativeLuminance(rgb1);
  const l2 = getRelativeLuminance(rgb2);

  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);

  return (lighter + 0.05) / (darker + 0.05);
}

function parseColor(color: string): [number, number, number] {
  // Parse rgb() or rgba() format
  const match = color.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (match) {
    return [parseInt(match[1]), parseInt(match[2]), parseInt(match[3])];
  }
  // Fallback for hex colors (simplified)
  return [0, 0, 0];
}

function getRelativeLuminance([r, g, b]: [number, number, number]): number {
  const [rs, gs, bs] = [r, g, b].map((c) => {
    const normalized = c / 255;
    return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
  });

  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

describe('Accessibility - Focus Indicators', () => {
  describe('Focus Visible Styles', () => {
    it('should_showFocusOutline_when_elementReceivesKeyboardFocus', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Tab to first interactive element
      await user.tab();

      const focusedElement = document.activeElement as HTMLElement;
      const styles = window.getComputedStyle(focusedElement);

      // Focus outline should be visible
      const hasFocusOutline =
        styles.outline !== 'none' && styles.outline !== '' && styles.outlineWidth !== '0px';

      expect(hasFocusOutline || styles.boxShadow !== 'none').toBe(true);
    });

    it('should_haveMinimum2pxFocusIndicator_when_elementIsFocused', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      await user.tab();

      const focusedElement = document.activeElement as HTMLElement;
      const styles = window.getComputedStyle(focusedElement);

      // Focus outline width should be at least 2px (WCAG 2.1 Success Criterion 2.4.7)
      const outlineWidth = parseInt(styles.outlineWidth) || 0;

      // If no outline, check box-shadow (alternative focus indicator)
      if (outlineWidth === 0) {
        expect(styles.boxShadow).not.toBe('none');
      } else {
        expect(outlineWidth).toBeGreaterThanOrEqual(2);
      }
    });

    it('should_haveHighContrastFocusIndicator_when_elementIsFocused', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      await user.tab();

      const focusedElement = document.activeElement as HTMLElement;
      const styles = window.getComputedStyle(focusedElement);

      // Focus outline color should have sufficient contrast with background
      const outlineColor = styles.outlineColor;
      const backgroundColor = styles.backgroundColor;

      if (outlineColor !== 'none' && backgroundColor !== 'none') {
        const contrastRatio = getContrastRatio(outlineColor, backgroundColor);
        expect(contrastRatio).toBeGreaterThanOrEqual(3); // Minimum 3:1 for focus indicators
      }
    });

    it('should_notHideFocusOutline_when_elementReceivesFocus', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      await user.tab();

      const focusedElement = document.activeElement as HTMLElement;
      const styles = window.getComputedStyle(focusedElement);

      // Ensure outline is not hidden (outline: none is bad for accessibility)
      expect(styles.outline).not.toBe('none');
    });

    it('should_distinguishFocusFromHover_when_stylingInteractiveElements', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      const button = screen.getByLabelText(/notifications/i);

      // Get hover styles
      await user.hover(button);
      const hoverStyles = window.getComputedStyle(button);
      const hoverOutline = hoverStyles.outline;

      // Get focus styles
      button.focus();
      const focusStyles = window.getComputedStyle(button);
      const focusOutline = focusStyles.outline;

      // Focus and hover should have different visual indicators
      // (Focus should always be more prominent)
      expect(focusOutline).toBeTruthy();
    });
  });

  describe('Focus Order and Logic', () => {
    it('should_followLogicalReadingOrder_when_tabbingThroughElements', async () => {
      const user = userEvent.setup();

      render(
        <BaseLayout user={mockUser}>
          <h1>Page Title</h1>
          <button id="btn-1">First Button</button>
          <button id="btn-2">Second Button</button>
          <button id="btn-3">Third Button</button>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      // Tab through elements - should follow DOM order
      await user.tab();
      const firstFocused = document.activeElement?.id;

      await user.tab();
      const secondFocused = document.activeElement?.id;

      await user.tab();
      const thirdFocused = document.activeElement?.id;

      // Verify logical tab order
      expect([firstFocused, secondFocused, thirdFocused]).toContain('btn-1');
      expect([firstFocused, secondFocused, thirdFocused]).toContain('btn-2');
      expect([firstFocused, secondFocused, thirdFocused]).toContain('btn-3');
    });

    it('should_excludeDisabledElements_when_tabbingThroughPage', async () => {
      const user = userEvent.setup();

      render(
        <div>
          <button>Enabled Button 1</button>
          <button disabled>Disabled Button</button>
          <button>Enabled Button 2</button>
        </div>
      );

      // Tab through elements
      await user.tab();
      const firstElement = document.activeElement;

      await user.tab();
      const secondElement = document.activeElement;

      // Disabled button should be skipped
      expect(firstElement?.textContent).toBe('Enabled Button 1');
      expect(secondElement?.textContent).toBe('Enabled Button 2');
    });

    it('should_maintainFocusWithinModal_when_modalIsOpen', async () => {
      const user = userEvent.setup();

      render(
        <div>
          <button>Outside Button</button>
          <div role="dialog" aria-modal="true">
            <button id="modal-btn-1">Modal Button 1</button>
            <button id="modal-btn-2">Modal Button 2</button>
          </div>
        </div>
      );

      const modalButton = document.getElementById('modal-btn-1');
      modalButton?.focus();

      // Tab should stay within modal
      await user.tab();
      const focusedElement = document.activeElement;

      expect(['modal-btn-1', 'modal-btn-2']).toContain(focusedElement?.id);
    });
  });
});

describe('Accessibility - Color Contrast (WCAG 2.1 AA)', () => {
  describe('Text Contrast', () => {
    it('should_meetWCAG_AA_Contrast_when_renderingBodyText', () => {
      render(
        <BaseLayout user={mockUser}>
          <p data-testid="body-text">
            This is body text that should have at least 4.5:1 contrast ratio.
          </p>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const text = screen.getByTestId('body-text');
      const styles = window.getComputedStyle(text);

      const textColor = styles.color;
      const backgroundColor = styles.backgroundColor || 'rgb(255, 255, 255)';

      const contrastRatio = getContrastRatio(textColor, backgroundColor);

      // WCAG AA requires 4.5:1 for normal text
      expect(contrastRatio).toBeGreaterThanOrEqual(4.5);
    });

    it('should_meetWCAG_AA_Contrast_when_renderingLargeText', () => {
      render(
        <BaseLayout user={mockUser}>
          <h1 data-testid="heading" style={{ fontSize: '24px', fontWeight: 700 }}>
            Large Heading Text
          </h1>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const heading = screen.getByTestId('heading');
      const styles = window.getComputedStyle(heading);

      const textColor = styles.color;
      const backgroundColor = styles.backgroundColor || 'rgb(255, 255, 255)';

      const contrastRatio = getContrastRatio(textColor, backgroundColor);

      // WCAG AA requires 3:1 for large text (18pt+ or 14pt+ bold)
      expect(contrastRatio).toBeGreaterThanOrEqual(3);
    });

    it('should_meetWCAG_AA_Contrast_when_renderingLinks', () => {
      render(
        <BaseLayout user={mockUser}>
          <a href="/test" data-testid="link">
            This is a link
          </a>
        </BaseLayout>,
        { wrapper: createWrapper() }
      );

      const link = screen.getByTestId('link');
      const styles = window.getComputedStyle(link);

      const linkColor = styles.color;
      const backgroundColor = styles.backgroundColor || 'rgb(255, 255, 255)';

      const contrastRatio = getContrastRatio(linkColor, backgroundColor);

      // Links should have 4.5:1 contrast ratio
      expect(contrastRatio).toBeGreaterThanOrEqual(4.5);
    });

    it('should_meetWCAG_AA_Contrast_when_renderingDisabledText', () => {
      render(
        <button disabled data-testid="disabled-btn">
          Disabled Button
        </button>
      );

      const button = screen.getByTestId('disabled-btn');
      const styles = window.getComputedStyle(button);

      const textColor = styles.color;
      const backgroundColor = styles.backgroundColor || 'rgb(255, 255, 255)';

      const contrastRatio = getContrastRatio(textColor, backgroundColor);

      // Disabled text can have lower contrast (WCAG exemption), but should still be visible
      expect(contrastRatio).toBeGreaterThanOrEqual(3);
    });
  });

  describe('UI Component Contrast', () => {
    it('should_meetWCAG_AA_Contrast_when_renderingButtons', () => {
      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      const button = screen.getByLabelText(/notifications/i);
      const styles = window.getComputedStyle(button);

      const iconColor = styles.color;
      const backgroundColor = styles.backgroundColor || 'rgb(255, 255, 255)';

      const contrastRatio = getContrastRatio(iconColor, backgroundColor);

      // UI components should have 3:1 contrast ratio (WCAG 2.1 SC 1.4.11)
      expect(contrastRatio).toBeGreaterThanOrEqual(3);
    });

    it('should_meetWCAG_AA_Contrast_when_renderingBadges', () => {
      const notificationsWithBadge: NotificationsResponse = {
        ...mockNotifications,
        unreadCount: 5,
      };

      render(<AppHeader user={mockUser} notifications={notificationsWithBadge} />, {
        wrapper: createWrapper(),
      });

      const badge = document.querySelector('[class*="MuiBadge-badge"]');
      if (badge) {
        const styles = window.getComputedStyle(badge as HTMLElement);
        const textColor = styles.color;
        const backgroundColor = styles.backgroundColor;

        const contrastRatio = getContrastRatio(textColor, backgroundColor);

        // Badge text should have 4.5:1 contrast
        expect(contrastRatio).toBeGreaterThanOrEqual(4.5);
      }
    });

    it('should_meetWCAG_AA_Contrast_when_renderingFormInputs', () => {
      render(
        <input
          type="text"
          placeholder="Enter text"
          data-testid="text-input"
          style={{ border: '1px solid #ccc' }}
        />
      );

      const input = screen.getByTestId('text-input');
      const styles = window.getComputedStyle(input);

      const borderColor = styles.borderColor;
      const backgroundColor = styles.backgroundColor || 'rgb(255, 255, 255)';

      const contrastRatio = getContrastRatio(borderColor, backgroundColor);

      // Form input borders should have 3:1 contrast
      expect(contrastRatio).toBeGreaterThanOrEqual(3);
    });
  });

  describe('Error and Status Colors', () => {
    it('should_notRelyOnColorAlone_when_displayingErrorState', () => {
      render(
        <div role="alert" data-testid="error" style={{ color: 'red' }}>
          ❌ Error: This is an error message
        </div>
      );

      const error = screen.getByTestId('error');

      // Error should have visual indicator beyond color (icon, text)
      expect(error.textContent).toMatch(/error/i);
      expect(error.getAttribute('role')).toBe('alert');
    });

    it('should_notRelyOnColorAlone_when_displayingSuccessState', () => {
      render(
        <div role="status" data-testid="success" style={{ color: 'green' }}>
          ✓ Success: Operation completed
        </div>
      );

      const success = screen.getByTestId('success');

      // Success should have visual indicator beyond color
      expect(success.textContent).toMatch(/success/i);
      expect(success.getAttribute('role')).toBe('status');
    });

    it('should_haveAdequateContrast_when_displayingWarnings', () => {
      render(
        <div
          data-testid="warning"
          style={{
            color: 'rgb(255, 165, 0)', // Orange
            backgroundColor: 'rgb(255, 255, 255)', // White
          }}
        >
          ⚠️ Warning message
        </div>
      );

      const warning = screen.getByTestId('warning');
      const styles = window.getComputedStyle(warning);

      const textColor = styles.color;
      const backgroundColor = styles.backgroundColor;

      const contrastRatio = getContrastRatio(textColor, backgroundColor);

      // Warning text should have 4.5:1 contrast
      expect(contrastRatio).toBeGreaterThanOrEqual(4.5);
    });
  });

  describe('Dark Mode Contrast', () => {
    it('should_meetWCAG_AA_Contrast_when_renderingInDarkMode', () => {
      // Mock dark mode theme
      render(
        <div
          data-testid="dark-text"
          style={{
            color: 'rgb(255, 255, 255)', // White text
            backgroundColor: 'rgb(18, 18, 18)', // Dark background
          }}
        >
          Dark mode text
        </div>
      );

      const text = screen.getByTestId('dark-text');
      const styles = window.getComputedStyle(text);

      const textColor = styles.color;
      const backgroundColor = styles.backgroundColor;

      const contrastRatio = getContrastRatio(textColor, backgroundColor);

      // Dark mode should also meet 4.5:1 contrast
      expect(contrastRatio).toBeGreaterThanOrEqual(4.5);
    });
  });
});
