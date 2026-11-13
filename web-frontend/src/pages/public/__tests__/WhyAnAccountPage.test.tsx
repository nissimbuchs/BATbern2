/**
 * /why-an-account (story "Visitors see what a BATbern account gives them"): the public page that
 * lists the four benefits of a free account. Logged out it offers sign-up, logged in it leads to the
 * profile. Real components, real `en` bundles; nothing of ours is mocked.
 */
import { afterEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import i18n from '@/i18n/config';
import { renderWithAuth, signedInAs, signedOut } from '@/test/auth';
import WhyAnAccountPage from '../WhyAnAccountPage';

const LOCALES = ['de', 'en', 'fr', 'it', 'rm', 'es', 'fi', 'nl', 'ja', 'gsw-BE'];

describe('WhyAnAccountPage', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('should_listTheFourBenefits_and_offerSignUp_when_loggedOut', () => {
    renderWithAuth(<WhyAnAccountPage />, { auth: signedOut() });

    expect(
      screen.getByRole('heading', { level: 1, name: 'What a free account gives you' })
    ).toBeInTheDocument();
    const items = within(screen.getByTestId('account-benefits-list')).getAllByRole('listitem');
    expect(items.map((li) => li.textContent)).toEqual([
      'Ask questions and comment on the talks, from two weeks before to two weeks after the event',
      'Suggest topics for coming BATbern events',
      'All your BATbern visits in one place, including registrations from before you had an account',
      "Your company's logo next to your name",
    ]);
    expect(screen.getByRole('link', { name: 'Create a free account' })).toHaveAttribute(
      'href',
      '/auth/register'
    );
    expect(screen.queryByRole('link', { name: 'Go to your profile' })).not.toBeInTheDocument();
  });

  it('should_leadToTheProfile_insteadOfSignUp_when_loggedIn', () => {
    renderWithAuth(<WhyAnAccountPage />, { auth: signedInAs('attendee') });

    expect(screen.getAllByRole('listitem').length).toBeGreaterThanOrEqual(4);
    expect(screen.getByRole('link', { name: 'Go to your profile' })).toHaveAttribute(
      'href',
      '/profile'
    );
    expect(screen.queryByRole('link', { name: 'Create a free account' })).not.toBeInTheDocument();
  });

  it.each(LOCALES)('should_showTranslatedBenefits_when_localeIs_%s', async (locale) => {
    await i18n.changeLanguage(locale);
    renderWithAuth(<WhyAnAccountPage />, { auth: signedOut() });

    const items = within(screen.getByTestId('account-benefits-list')).getAllByRole('listitem');
    expect(items).toHaveLength(4);
    for (const item of items) {
      expect(item.textContent).not.toMatch(/accountBenefits\./);
      expect(item.textContent?.trim()).not.toBe('');
    }
    // The locale's own bundle holds the block (no fallback to de/en)
    for (const key of ['title', 'items.qna', 'items.topics', 'items.history', 'items.logo']) {
      expect(i18n.getResource(locale, 'registration', `accountBenefits.${key}`)).toEqual(
        expect.any(String)
      );
    }
  });

  it('should_useTheApprovedGermanWording_when_localeIsDe', async () => {
    await i18n.changeLanguage('de');
    renderWithAuth(<WhyAnAccountPage />, { auth: signedOut() });

    expect(
      screen.getByRole('heading', { level: 1, name: 'Was Ihnen ein kostenloses Konto bietet' })
    ).toBeInTheDocument();
    expect(screen.getByText('Das Logo Ihrer Firma neben Ihrem Namen')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Kostenloses Konto erstellen' })).toBeInTheDocument();
  });
});
