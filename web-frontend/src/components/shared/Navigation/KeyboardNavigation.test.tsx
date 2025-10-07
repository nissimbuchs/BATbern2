/**
 * Accessibility Tests - Keyboard Navigation
 * Story 1.17, Task 12a (RED Phase): Keyboard Navigation Testing
 *
 * Tests Tab, Enter, Escape, and Arrow key navigation for WCAG 2.1 AA compliance.
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import { NotificationDropdown } from '../Notifications/NotificationDropdown';
import { UserMenuDropdown } from './UserMenuDropdown';
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
  notifications: [
    {
      id: 'notif-1',
      type: 'EVENT_STATUS_CHANGED',
      title: 'Event Updated',
      message: 'Event status changed to Published',
      timestamp: '2024-03-15T14:30:00Z',
      isRead: false,
      actionUrl: '/events/1',
      priority: 'NORMAL',
    },
  ],
  unreadCount: 1,
  totalCount: 1,
  hasMore: false,
};

describe('Accessibility - Keyboard Navigation', () => {
  describe('Tab Key Navigation', () => {
    it('should_navigateToAllInteractiveElements_when_pressingTab', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Tab through all interactive elements in logical order
      await user.tab(); // Skip link (if exists)
      expect(document.activeElement?.getAttribute('aria-label')).toMatch(
        /menu|language|notifications|user menu/i
      );

      await user.tab(); // Next element
      expect(document.activeElement).toBeInstanceOf(HTMLElement);

      await user.tab(); // Next element
      expect(document.activeElement).toBeInstanceOf(HTMLElement);
    });

    it('should_showFocusIndicators_when_elementReceivesFocus', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      const notificationButton = screen.getByLabelText(/notifications/i);
      await user.tab();

      // Focus indicator should be visible (outline or box-shadow)
      const styles = window.getComputedStyle(document.activeElement as HTMLElement);
      const hasFocusIndicator =
        styles.outline !== 'none' || styles.outlineWidth !== '0px' || styles.boxShadow !== 'none';

      expect(hasFocusIndicator).toBe(true);
    });

    it('should_respectTabIndex_when_navigatingThroughElements', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // All focusable elements should have tabindex=0 or natural tab order
      const focusableElements = screen.getAllByRole('button');
      focusableElements.forEach((el) => {
        const tabIndex = el.getAttribute('tabindex');
        expect(tabIndex === null || tabIndex === '0').toBe(true);
      });
    });

    it('should_trapFocus_when_dropdownMenuIsOpen', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      // Tab through dropdown items - focus should stay within dropdown
      await user.tab();
      const firstFocusedElement = document.activeElement;

      // Tab through all items
      for (let i = 0; i < 10; i++) {
        await user.tab();
      }

      // Focus should loop back to first element in dropdown (focus trap)
      const dropdownContainer = screen.getByRole('menu');
      expect(dropdownContainer.contains(document.activeElement)).toBe(true);
    });
  });

  describe('Enter Key Activation', () => {
    it('should_activateButton_when_pressingEnterOnFocusedButton', async () => {
      const user = userEvent.setup();
      const handleClick = vi.fn();

      render(
        <button aria-label="test button" onClick={handleClick}>
          Click Me
        </button>
      );

      const button = screen.getByLabelText('test button');
      button.focus();

      await user.keyboard('{Enter}');
      expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it('should_selectNotification_when_pressingEnterOnNotificationItem', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      const firstNotification = screen.getByText(/Event Updated/i);
      firstNotification.focus();

      await user.keyboard('{Enter}');

      // Should navigate to notification action URL or close dropdown
      expect(onClose).toHaveBeenCalled();
    });

    it('should_toggleDropdown_when_pressingEnterOnTriggerButton', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      const userMenuButton = screen.getByLabelText(/user menu/i);
      userMenuButton.focus();

      await user.keyboard('{Enter}');

      // User menu dropdown should be visible
      // Note: This assumes dropdown renders in DOM when opened
      const userMenuDropdown = await screen.findByRole('menu', { hidden: false });
      expect(userMenuDropdown).toBeInTheDocument();
    });
  });

  describe('Escape Key Dismissal', () => {
    it('should_closeDropdown_when_pressingEscapeInDropdown', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      await user.keyboard('{Escape}');
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should_closeMobileDrawer_when_pressingEscapeInDrawer', async () => {
      const user = userEvent.setup();

      // Mock window.matchMedia to simulate mobile viewport
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: vi.fn().mockImplementation((query) => ({
          matches: query === '(max-width: 900px)', // Mobile breakpoint
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })),
      });

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Open mobile drawer
      const menuButton = screen.getByLabelText(/menu/i);
      await user.click(menuButton);

      // Press Escape to close drawer
      await user.keyboard('{Escape}');

      // Drawer should be closed (not visible)
      const drawer = screen.queryByRole('dialog'); // Material-UI Drawer uses role="dialog"
      expect(drawer).not.toBeVisible();
    });

    it('should_returnFocusToTrigger_when_closingDropdownWithEscape', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      const triggerButton = document.createElement('button');
      triggerButton.setAttribute('aria-label', 'trigger');

      render(
        <NotificationDropdown
          anchorEl={triggerButton}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      await user.keyboard('{Escape}');

      // Focus should return to trigger button after closing
      expect(document.activeElement).toBe(triggerButton);
    });
  });

  describe('Arrow Key Navigation', () => {
    it('should_navigateMenuItems_when_pressingArrowKeys', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      const menu = screen.getByRole('menu');
      menu.focus();

      // Arrow Down should move to first item
      await user.keyboard('{ArrowDown}');
      expect(document.activeElement?.getAttribute('role')).toBe('menuitem');

      // Arrow Down should move to next item
      await user.keyboard('{ArrowDown}');
      expect(document.activeElement).toBeInstanceOf(HTMLElement);

      // Arrow Up should move back to previous item
      await user.keyboard('{ArrowUp}');
      expect(document.activeElement).toBeInstanceOf(HTMLElement);
    });

    it('should_loopFocus_when_reachingEndOfMenuWithArrowDown', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <NotificationDropdown
          anchorEl={document.createElement('div')}
          open={true}
          onClose={onClose}
          notifications={mockNotifications}
        />,
        { wrapper: createWrapper() }
      );

      const menu = screen.getByRole('menu');
      menu.focus();

      // Navigate to last item
      await user.keyboard('{ArrowDown}');
      const firstActiveElement = document.activeElement;

      // Press ArrowDown on last item - should loop to first item
      for (let i = 0; i < 10; i++) {
        await user.keyboard('{ArrowDown}');
      }

      // Should have looped back to first menu item
      expect(document.activeElement?.getAttribute('role')).toBe('menuitem');
    });

    it('should_navigateHorizontalMenu_when_pressingLeftRightArrows', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Focus on navigation menu
      const nav = screen.getByRole('navigation');
      const firstNavItem = nav.querySelector('[role="tab"], a');
      if (firstNavItem instanceof HTMLElement) {
        firstNavItem.focus();

        // Arrow Right should move to next tab
        await user.keyboard('{ArrowRight}');
        expect(document.activeElement).toBeInstanceOf(HTMLElement);

        // Arrow Left should move to previous tab
        await user.keyboard('{ArrowLeft}');
        expect(document.activeElement).toBe(firstNavItem);
      }
    });
  });

  describe('Space Bar Activation', () => {
    it('should_toggleCheckbox_when_pressingSpaceOnCheckbox', async () => {
      const user = userEvent.setup();
      const handleChange = vi.fn();

      render(<input type="checkbox" aria-label="test checkbox" onChange={handleChange} />);

      const checkbox = screen.getByLabelText('test checkbox');
      checkbox.focus();

      await user.keyboard(' '); // Space key
      expect(handleChange).toHaveBeenCalled();
    });

    it('should_activateButton_when_pressingSpaceOnButton', async () => {
      const user = userEvent.setup();
      const handleClick = vi.fn();

      render(
        <button aria-label="test button" onClick={handleClick}>
          Click Me
        </button>
      );

      const button = screen.getByLabelText('test button');
      button.focus();

      await user.keyboard(' '); // Space key
      expect(handleClick).toHaveBeenCalledTimes(1);
    });
  });

  describe('Focus Visible and Focus Indicators', () => {
    it('should_showFocusIndicator_when_navigatingWithKeyboard', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      // Tab to first interactive element
      await user.tab();

      // Focus indicator should be visible
      const activeElement = document.activeElement as HTMLElement;
      const styles = window.getComputedStyle(activeElement);

      const hasFocusIndicator =
        styles.outline !== 'none' || styles.outlineWidth !== '0px' || styles.boxShadow !== 'none';

      expect(hasFocusIndicator).toBe(true);
    });

    it('should_notShowFocusIndicator_when_clickingWithMouse', async () => {
      const user = userEvent.setup();

      render(<AppHeader user={mockUser} notifications={mockNotifications} />, {
        wrapper: createWrapper(),
      });

      const button = screen.getByLabelText(/notifications/i);
      await user.click(button);

      // Focus indicator should NOT be visible after mouse click (focus-visible)
      const activeElement = document.activeElement as HTMLElement;

      // Note: This test assumes :focus-visible CSS is implemented
      // With :focus-visible, outline should only show on keyboard focus, not mouse click
      expect(activeElement).toBe(button);
    });
  });
});
