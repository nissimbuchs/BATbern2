/**
 * The homepage hero shows the event date in the UI language (#1222 item 6, #1223 item 4): it was
 * formatted with a fixed `de-CH`, so an English visitor read "Freitag, 6. November 2026". Real
 * i18next, network at the wire (MSW), no own-code mocks.
 */
import { describe, it, expect, afterEach } from 'vitest';
import { screen } from '@testing-library/react';
import i18n from '@/i18n/config';
import { renderWithAuth, signedOut } from '@/test/auth';
import { HeroSection } from '../HeroSection';

const renderHero = () =>
  renderWithAuth(
    <HeroSection title="BATbern 60" date="2026-11-06T16:00:00Z" ctaLink="/register" />,
    { auth: signedOut() }
  );

describe('HeroSection date follows the UI language', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('should_showTheEnglishDate_when_uiIsEnglish', async () => {
    await i18n.changeLanguage('en');
    renderHero();
    expect(await screen.findByText('Friday, November 6, 2026')).toBeInTheDocument();
    expect(screen.queryByText(/Freitag/)).not.toBeInTheDocument();
  });

  it('should_showTheGermanDate_when_uiIsGerman', async () => {
    await i18n.changeLanguage('de');
    renderHero();
    expect(await screen.findByText('Freitag, 6. November 2026')).toBeInTheDocument();
  });

  it('should_showTheGermanDate_when_uiIsSwissGerman', async () => {
    await i18n.changeLanguage('gsw-BE');
    renderHero();
    expect(await screen.findByText('Freitag, 6. November 2026')).toBeInTheDocument();
  });
});
