/**
 * LanguageSwitcher: switches i18next, remembers the choice locally and, for a signed-in user,
 * stores it in the one settings resource (`PATCH /users/me/settings`, Story 4.9).
 *
 * Real auth context (`src/test/auth.tsx`), real userApi on the MSW harness.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { I18nextProvider, useTranslation } from 'react-i18next';
import type { ReactElement } from 'react';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import { AuthTestProvider, signedInAs, signedOut } from '@/test/auth';
import LanguageSwitcher from './LanguageSwitcher';

const renderSwitcher = (ui: ReactElement = <LanguageSwitcher />, auth = signedOut()) =>
  render(
    <AuthTestProvider auth={auth}>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </AuthTestProvider>
  );

describe('LanguageSwitcher', () => {
  beforeEach(async () => {
    localStorage.clear();
    await i18n.changeLanguage('de');
  });

  it('should_renderLanguageSelector_when_componentMounted', () => {
    renderSwitcher();

    expect(screen.getByTestId('language-selector')).toBeInTheDocument();
  });

  it('should_showGermanSelected_when_defaultLanguage', () => {
    renderSwitcher();

    expect(screen.getByTestId('language-selector')).toHaveTextContent('DE');
  });

  it('should_changeLanguageAndRememberIt_when_dropdownChanged', async () => {
    renderSwitcher();

    fireEvent.change(screen.getByTestId('language-selector'), { target: { value: 'en' } });

    await waitFor(() => expect(i18n.language).toBe('en'));
    expect(localStorage.getItem('batbern-language')).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('should_updateAllText_when_languageChanged', async () => {
    const TestComponent = () => {
      const { t } = useTranslation('auth');
      return (
        <div>
          <LanguageSwitcher />
          <div>{t('login.title')}</div>
        </div>
      );
    };
    renderSwitcher(<TestComponent />);

    expect(screen.getByText('Willkommen zurück')).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('language-selector'), { target: { value: 'en' } });

    await waitFor(() => expect(screen.getByText('Welcome Back')).toBeInTheDocument());
  });

  it('should_storeTheLanguageInTheUserSettings_when_signedIn', async () => {
    renderSwitcher(<LanguageSwitcher />, signedInAs('speaker'));

    fireEvent.change(screen.getByTestId('language-selector'), { target: { value: 'fr' } });

    await waitFor(() => expect(api.calls('updateUserSettings')).toHaveLength(1));
    const [call] = api.calls('updateUserSettings');
    expect(call.method).toBe('PATCH');
    expect(call.body).toEqual({ language: 'fr' });
  });

  it('should_notCallTheApi_when_signedOut', async () => {
    renderSwitcher();

    fireEvent.change(screen.getByTestId('language-selector'), { target: { value: 'it' } });

    // positive control: the switch itself happened
    await waitFor(() => expect(i18n.language).toBe('it'));
    expect(api.calls('updateUserSettings')).toHaveLength(0);
  });
});
