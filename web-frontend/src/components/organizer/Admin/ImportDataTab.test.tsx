/**
 * ImportDataTab Tests (Story 10.1 - Task 3)
 *
 * The real tab, the five real import modals and the real i18next (en and de bundles) run;
 * whatever a modal loads goes to the MSW harness.
 *
 * - AC3: Renders 5 import cards (Events, Sessions, Companies, Speakers, Participants)
 * - AC3: Clicking each button opens the corresponding modal
 */

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import i18n from '@/i18n/config';
import { renderWithProviders } from '@/test/test-utils';
import { ImportDataTab } from './ImportDataTab';

const CARDS = ['events', 'sessions', 'companies', 'speakers', 'participants'] as const;

describe('ImportDataTab', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('renders 5 import cards', () => {
    renderWithProviders(<ImportDataTab />);

    for (const title of ['Events', 'Sessions', 'Companies', 'Speakers', 'Participants']) {
      expect(screen.getByRole('heading', { name: title })).toBeInTheDocument();
    }
    expect(screen.getByTestId('import-events-btn')).toHaveTextContent('Import Events');
  });

  it('renders the cards in German from the admin bundle, not the English fallbacks', async () => {
    await i18n.changeLanguage('de');

    renderWithProviders(<ImportDataTab />);

    expect(screen.getByRole('heading', { name: 'Veranstaltungen' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Unternehmen' })).toBeInTheDocument();
    expect(screen.getByTestId('import-events-btn')).toHaveTextContent(
      'Veranstaltungen importieren'
    );
    expect(screen.queryByText('Import Events')).not.toBeInTheDocument();
  });

  it.each(CARDS)('opens the %s import dialog when its button is clicked', async (card) => {
    const user = userEvent.setup();
    renderWithProviders(<ImportDataTab />);
    // Positive control for "starts closed": no dialog before the click.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(screen.getByTestId(`import-${card}-btn`));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getAllByText(/import/i).length).toBeGreaterThan(0);
  });
});
