/**
 * TaskTemplateEditModal (Story 10.1, #1218).
 *
 * Real i18next and the MSW harness: the PUT is checked against the spec and read back from
 * `api.calls`. The offered trigger states are compared with the set the backend accepts, every
 * `EventWorkflowState` of the spec in lower case plus `speakers_published`.
 */
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect } from 'vitest';
import { TaskTemplateEditModal } from './TaskTemplateEditModal';
import { renderWithProviders } from '@/test/test-utils';
import { api } from '@/test/msw/api';
import { backendTriggerStates } from '@/test/triggerStates';
import type { TaskTemplateResponse } from '@/services/taskService';

const TEMPLATE_ID = '3f2a9c4e-1b7d-4e8a-9c3f-2d1e5b6a7c8d';

const baseTemplate: TaskTemplateResponse = {
  id: TEMPLATE_ID,
  name: 'My Template',
  isDefault: false,
  triggerState: 'topic_selection',
  dueDateType: 'immediate',
  dueDateOffsetDays: null,
  activationLeadDays: null,
  createdByUsername: null,
  createdAt: '2026-11-01T08:00:00Z',
  updatedAt: '2026-11-01T08:00:00Z',
};

const renderModal = (template = baseTemplate, onClose = vi.fn()) =>
  renderWithProviders(<TaskTemplateEditModal open onClose={onClose} template={template} />);

const openTriggerSelect = async () => {
  const user = userEvent.setup();
  await user.click(screen.getByLabelText(/trigger state/i));
  return { user, listbox: await screen.findByRole('listbox') };
};

describe('TaskTemplateEditModal', () => {
  it('should_offerExactlyTheBackendTriggerStates_when_triggerSelectOpened', async () => {
    renderModal();
    const { listbox } = await openTriggerSelect();

    const offered = within(listbox)
      .getAllByRole('option')
      .map((option) => option.getAttribute('data-value'));
    expect([...offered].sort()).toEqual([...backendTriggerStates()].sort());
    expect(within(listbox).getByRole('option', { name: 'Speaker Identification' })).toBeVisible();
  });

  it('should_saveTheChangedTrigger_when_saveClicked', async () => {
    api.on('updateTemplate', ({ body }) => ({ ...baseTemplate, ...body }));
    const onClose = vi.fn();
    renderModal(baseTemplate, onClose);

    const { user, listbox } = await openTriggerSelect();
    await user.click(within(listbox).getByRole('option', { name: 'Slot Assignment' }));
    await user.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(api.calls('updateTemplate')[0].body).toEqual({
      name: 'My Template',
      triggerState: 'slot_assignment',
      dueDateType: 'immediate',
    });
  });

  it('should_requireANewTrigger_when_templateHasALegacyState', async () => {
    renderModal({ ...baseTemplate, triggerState: 'speaker_outreach' });
    const save = screen.getByRole('button', { name: /save/i });
    expect(save).toBeDisabled();
    expect(screen.getByText('Trigger state is required')).toBeInTheDocument();

    const { user, listbox } = await openTriggerSelect();
    await user.click(within(listbox).getByRole('option', { name: 'Topic Selection' }));

    expect(save).toBeEnabled();
    expect(screen.queryByText('Trigger state is required')).not.toBeInTheDocument();
  });

  it('should_showOffsetDays_only_when_relativeToEvent', () => {
    const { unmount } = renderModal();
    expect(screen.queryByTestId('template-offset-days-input')).not.toBeInTheDocument();
    unmount();

    renderModal({ ...baseTemplate, dueDateType: 'relative_to_event', dueDateOffsetDays: 7 });
    expect(screen.getByTestId('template-offset-days-input')).toHaveAttribute('min', '0');
  });

  it('should_disableSave_when_nameIsEmpty', async () => {
    const user = userEvent.setup();
    renderModal();
    expect(screen.getByRole('button', { name: /save/i })).toBeEnabled();

    await user.clear(screen.getByTestId('template-name-input'));

    expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
  });

  it('should_close_when_cancelClicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderModal(baseTemplate, onClose);
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
