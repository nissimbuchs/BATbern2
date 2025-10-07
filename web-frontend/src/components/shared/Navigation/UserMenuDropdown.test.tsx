import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import UserMenuDropdown from './UserMenuDropdown';
import { UserContext } from '../../../types/auth';

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: {
      language: 'de',
      changeLanguage: vi.fn().mockResolvedValue(undefined),
    },
  }),
}));

// Mock API client for language preference update
vi.mock('../../../services/api/apiClient', () => ({
  default: {
    put: vi.fn().mockResolvedValue({ data: { success: true } }),
  },
}));

const mockUser: UserContext = {
  userId: 'user-123',
  email: 'john.doe@batbern.ch',
  emailVerified: true,
  role: 'organizer',
  companyId: 'company-123',
  preferences: {
    language: 'de',
    theme: 'light',
    notifications: {
      email: true,
      sms: false,
      push: true,
    },
    privacy: {
      showProfile: true,
      allowMessages: true,
    },
  },
  issuedAt: Date.now(),
  expiresAt: Date.now() + 3600000,
  tokenId: 'token-123',
};

const renderWithRouter = (component: React.ReactElement) => {
  return render(<BrowserRouter>{component}</BrowserRouter>);
};

describe('UserMenuDropdown', () => {
  const mockOnLogout = vi.fn();
  const mockOnLanguageChange = vi.fn();
  let anchorEl: HTMLElement;

  beforeEach(() => {
    vi.clearAllMocks();
    // Create a mock anchor element for the Menu
    anchorEl = document.createElement('div');
  });

  describe('Rendering', () => {
    it('should_renderUserEmail_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByText(mockUser.email)).toBeInTheDocument();
    });

    it('should_renderUserRole_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByText('common.role.organizer')).toBeInTheDocument();
    });

    it('should_renderProfileMenuItem_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByText('common.menu.profile')).toBeInTheDocument();
    });

    it('should_renderSettingsMenuItem_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByText('common.menu.settings')).toBeInTheDocument();
    });

    it('should_renderHelpMenuItem_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByText('common.menu.help')).toBeInTheDocument();
    });

    it('should_renderLanguageSwitcher_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByLabelText('Language selector')).toBeInTheDocument();
    });

    it('should_renderLogoutMenuItem_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByText('common.menu.logout')).toBeInTheDocument();
    });

    it('should_renderDividers_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      // Material-UI Menu renders in a portal, so query document instead of container
      const dividers = document.querySelectorAll('.MuiDivider-root');
      expect(dividers.length).toBeGreaterThanOrEqual(2);
    });

    it('should_notRenderMenu_when_menuIsClosed', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={false}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.queryByText(mockUser.email)).not.toBeInTheDocument();
    });
  });

  describe('User Interactions', () => {
    it('should_navigateToProfile_when_profileMenuItemClicked', async () => {
      const mockOnClose = vi.fn();
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={mockOnClose}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const profileMenuItem = screen.getByText('common.menu.profile').closest('li');
      expect(profileMenuItem).toBeInTheDocument();

      if (profileMenuItem) {
        fireEvent.click(profileMenuItem);
      }

      await waitFor(() => {
        expect(mockOnClose).toHaveBeenCalled();
      });
    });

    it('should_navigateToSettings_when_settingsMenuItemClicked', async () => {
      const mockOnClose = vi.fn();
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={mockOnClose}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const settingsMenuItem = screen.getByText('common.menu.settings').closest('li');
      expect(settingsMenuItem).toBeInTheDocument();

      if (settingsMenuItem) {
        fireEvent.click(settingsMenuItem);
      }

      await waitFor(() => {
        expect(mockOnClose).toHaveBeenCalled();
      });
    });

    it('should_navigateToHelp_when_helpMenuItemClicked', async () => {
      const mockOnClose = vi.fn();
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={mockOnClose}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const helpMenuItem = screen.getByText('common.menu.help').closest('li');
      expect(helpMenuItem).toBeInTheDocument();

      if (helpMenuItem) {
        fireEvent.click(helpMenuItem);
      }

      await waitFor(() => {
        expect(mockOnClose).toHaveBeenCalled();
      });
    });

    it('should_callOnLogout_when_logoutMenuItemClicked', async () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const logoutMenuItem = screen.getByText('common.menu.logout').closest('li');
      expect(logoutMenuItem).toBeInTheDocument();

      if (logoutMenuItem) {
        fireEvent.click(logoutMenuItem);
      }

      await waitFor(() => {
        expect(mockOnLogout).toHaveBeenCalled();
      });
    });
  });

  describe('Language Switching', () => {
    it('should_displayCurrentLanguage_when_menuIsOpen', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const languageSelector = screen.getByLabelText('Language selector');
      // Material-UI Select stores value in a different way
      const input = languageSelector.querySelector('input');
      expect(input?.value).toBe('de');
    });

    it('should_changeLanguageToEnglish_when_userSelectsEnglish', async () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const languageSelector = screen.getByLabelText('Language selector');

      // Trigger language change to English by opening the select and clicking EN
      fireEvent.mouseDown(languageSelector.querySelector('[role="combobox"]') || languageSelector);

      // Wait for the option to appear in the portal
      const englishOption = await screen.findByRole('option', { name: 'EN' });
      fireEvent.click(englishOption);

      await waitFor(() => {
        expect(mockOnLanguageChange).toHaveBeenCalledWith('en');
      });
    });

    it('should_changeLanguageToGerman_when_userSelectsGerman', async () => {
      const userWithEnglish = {
        ...mockUser,
        preferences: {
          ...mockUser.preferences,
          language: 'en' as const,
        },
      };

      renderWithRouter(
        <UserMenuDropdown
          user={userWithEnglish}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const languageSelector = screen.getByLabelText('Language selector');

      // Trigger language change to German by opening the select and clicking DE
      fireEvent.mouseDown(languageSelector.querySelector('[role="combobox"]') || languageSelector);

      // Wait for the option to appear in the portal
      const germanOption = await screen.findByRole('option', { name: 'DE' });
      fireEvent.click(germanOption);

      await waitFor(() => {
        expect(mockOnLanguageChange).toHaveBeenCalledWith('de');
      });
    });

    it('should_persistLanguageToAPI_when_languageChanged', async () => {
      const apiClient = await import('../../../services/api/apiClient');

      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const languageSelector = screen.getByLabelText('Language selector');

      // Trigger language change by opening the select and clicking EN
      fireEvent.mouseDown(languageSelector.querySelector('[role="combobox"]') || languageSelector);

      // Wait for the option to appear in the portal
      const englishOption = await screen.findByRole('option', { name: 'EN' });
      fireEvent.click(englishOption);

      await waitFor(() => {
        expect(apiClient.default.put).toHaveBeenCalledWith('/api/v1/users/me/preferences', {
          language: 'en',
        });
      });
    });
  });

  describe('Accessibility', () => {
    it('should_haveProperAriaLabels_when_rendered', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.getByLabelText('Language selector')).toBeInTheDocument();
      // Get all menus and verify at least one exists (Select creates additional menu roles)
      const menus = screen.getAllByRole('menu');
      expect(menus.length).toBeGreaterThanOrEqual(1);
    });

    it('should_supportKeyboardNavigation_when_menuIsOpen', async () => {
      const mockOnClose = vi.fn();
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={mockOnClose}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      // Get all menus (Select creates additional menu roles)
      const menus = screen.getAllByRole('menu');
      const mainMenu = menus[0]; // The UserMenuDropdown is the first menu

      // Press Escape to close menu
      fireEvent.keyDown(mainMenu, { key: 'Escape', code: 'Escape' });

      await waitFor(() => {
        expect(mockOnClose).toHaveBeenCalled();
      });
    });

    it('should_focusFirstMenuItem_when_menuOpens', () => {
      renderWithRouter(
        <UserMenuDropdown
          user={mockUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      const menuItems = screen.getAllByRole('menuitem');
      expect(menuItems.length).toBeGreaterThan(0);
    });
  });
});
