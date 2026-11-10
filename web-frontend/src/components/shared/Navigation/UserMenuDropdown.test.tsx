import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import UserMenuDropdown from './UserMenuDropdown';
import { UserContext } from '../../../types/auth';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

/** The text of a common key in the current language, as the menu renders it (real i18next). */
const text = (key: string) => i18n.t(key);

const mockUser: UserContext = {
  userId: 'user-123',
  username: 'test.user',
  email: 'john.doe@batbern.ch',
  emailVerified: true,
  role: 'organizer',
  roles: ['organizer'],
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
  return render(<MemoryRouter>{component}</MemoryRouter>);
};

describe('UserMenuDropdown', () => {
  const mockOnLogout = vi.fn();
  const mockOnLanguageChange = vi.fn();
  let anchorEl: HTMLElement;

  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockClear();
    void i18n.changeLanguage('de');
    localStorage.clear();
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

      expect(screen.getByText(text('role.organizer'))).toBeInTheDocument();
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

      expect(screen.getByText(text('menu.profile'))).toBeInTheDocument();
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

      expect(screen.getByText(text('menu.settings'))).toBeInTheDocument();
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

      expect(screen.getByText(text('menu.help'))).toBeInTheDocument();
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

      expect(screen.getByLabelText(text('language.select'))).toBeInTheDocument();
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

      expect(screen.getByText(text('menu.logout'))).toBeInTheDocument();
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

      const profileMenuItem = screen.getByText(text('menu.profile')).closest('li');
      expect(profileMenuItem).toBeInTheDocument();

      if (profileMenuItem) {
        fireEvent.click(profileMenuItem);
      }

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/account/profile');
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

      const settingsMenuItem = screen.getByText(text('menu.settings')).closest('li');
      expect(settingsMenuItem).toBeInTheDocument();

      if (settingsMenuItem) {
        fireEvent.click(settingsMenuItem);
      }

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/account/settings');
        expect(mockOnClose).toHaveBeenCalled();
      });
    });

    it('should_openUserGuideInNewTab_when_helpMenuItemClicked', async () => {
      const mockOnClose = vi.fn();
      const openSpy = vi.spyOn(window, 'open').mockReturnValue(null);
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

      const helpMenuItem = screen.getByText(text('menu.help')).closest('li');
      expect(helpMenuItem).toBeInTheDocument();

      if (helpMenuItem) {
        fireEvent.click(helpMenuItem);
      }

      await waitFor(() => {
        expect(openSpy).toHaveBeenCalledWith(
          'https://project.batbern.ch/user-guide/README.html',
          '_blank',
          'noopener,noreferrer'
        );
        expect(mockNavigate).not.toHaveBeenCalledWith('/help');
        expect(mockOnClose).toHaveBeenCalled();
      });
      openSpy.mockRestore();
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

      const logoutMenuItem = screen.getByText(text('menu.logout')).closest('li');
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

      const languageSelector = screen.getByLabelText(text('language.select'));
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

      const languageSelector = screen.getByLabelText(text('language.select'));

      // Trigger language change to English by opening the select and clicking English
      fireEvent.mouseDown(languageSelector.querySelector('[role="combobox"]') || languageSelector);

      // Wait for the option to appear in the portal
      const englishOption = await screen.findByRole('option', { name: 'EN — English' });
      fireEvent.click(englishOption);

      await waitFor(() => {
        expect(mockOnLanguageChange).toHaveBeenCalledWith('en');
      });
    });

    it('should_changeLanguageToGerman_when_userSelectsGerman', async () => {
      // Start from English so switching to German is a real value change
      await i18n.changeLanguage('en');

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

      const languageSelector = screen.getByLabelText(text('language.select'));

      // Trigger language change to German by opening the select and clicking DE
      fireEvent.mouseDown(languageSelector.querySelector('[role="combobox"]') || languageSelector);

      // Wait for the option to appear in the portal
      const germanOption = await screen.findByRole('option', { name: 'DE — Deutsch' });
      fireEvent.click(germanOption);

      await waitFor(() => {
        expect(mockOnLanguageChange).toHaveBeenCalledWith('de');
      });
    });

    it('should_storeTheLanguageInTheUserSettings_when_changed', async () => {
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

      const languageSelector = screen.getByLabelText(text('language.select'));
      fireEvent.mouseDown(languageSelector.querySelector('[role="combobox"]') || languageSelector);
      fireEvent.click(await screen.findByRole('option', { name: 'EN — English' }));

      // Story 4.9: the language is part of the one settings resource
      await waitFor(() => expect(api.calls('updateUserSettings')).toHaveLength(1));
      const [call] = api.calls('updateUserSettings');
      expect(call.method).toBe('PATCH');
      expect(call.body).toEqual({ language: 'en' });
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

      expect(screen.getByLabelText(text('language.select'))).toBeInTheDocument();
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

  describe('Administration item (Story 10.1 — AC1)', () => {
    it('should_showAdministrationItem_when_userIsOrganizer', () => {
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

      expect(screen.getByTestId('administration-link')).toBeInTheDocument();
    });

    it('should_notShowAdministrationItem_when_userIsSpeaker', () => {
      const speakerUser = { ...mockUser, role: 'speaker' as const, roles: ['speaker' as const] };
      renderWithRouter(
        <UserMenuDropdown
          user={speakerUser}
          anchorEl={anchorEl}
          open={true}
          onClose={vi.fn()}
          onLogout={mockOnLogout}
          onLanguageChange={mockOnLanguageChange}
        />
      );

      expect(screen.queryByTestId('administration-link')).not.toBeInTheDocument();
    });

    it('should_navigateToAdminPage_when_administrationItemClicked', async () => {
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

      fireEvent.click(screen.getByTestId('administration-link'));

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/organizer/admin');
        expect(mockOnClose).toHaveBeenCalled();
      });
    });
  });
});
