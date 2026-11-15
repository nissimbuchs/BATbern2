/**
 * CustomTaskModal trigger states (#1218): the create dialog offers exactly the states the backend
 * accepts, the spec's `EventWorkflowState` values in lower case plus `speakers_published`. Real
 * i18next and the MSW harness (the event list and the organizer list come from spec examples).
 */
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { CustomTaskModal } from './CustomTaskModal';
import { renderWithProviders } from '@/test/test-utils';
import { backendTriggerStates } from '@/test/triggerStates';

describe('CustomTaskModal trigger states', () => {
  it('should_offerExactlyTheBackendTriggerStates_when_creatingATask', async () => {
    renderWithProviders(
      <CustomTaskModal open onClose={vi.fn()} eventId="BATbern60" organizerUsername="nora.org" />
    );

    await userEvent.setup().click(screen.getByRole('combobox', { name: /^trigger/i }));
    const listbox = await screen.findByRole('listbox');
    const offered = within(listbox)
      .getAllByRole('option')
      .map((option) => option.getAttribute('data-value'));

    expect([...offered].sort()).toEqual([...backendTriggerStates()].sort());
    expect(within(listbox).getByRole('option', { name: 'Speakers published' })).toBeVisible();
  });
});
