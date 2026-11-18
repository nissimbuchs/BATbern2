/**
 * TaskTemplatesTab (Story 10.1, #1218): the default and custom lists, the dialogs, delete.
 *
 * Real i18next, auth helper and dialogs; the network is the MSW harness, so the list, the delete
 * and its failure are answered by the spec and read back with `api.calls`.
 */
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { TaskTemplatesTab } from './TaskTemplatesTab';
import { renderWithAuth } from '@/test/auth';
import { api } from '@/test/msw/api';
import type { TaskTemplateResponse } from '@/services/taskService';

const DEFAULT_ID = '0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4';
const CUSTOM_ID = '1c2d3e4f-5a6b-4c7d-9e8f-0a1b2c3d4e5f';

const templates: TaskTemplateResponse[] = [
  {
    id: DEFAULT_ID,
    name: 'Default Task',
    isDefault: true,
    triggerState: 'agenda_published',
    dueDateType: 'immediate',
    dueDateOffsetDays: null,
    activationLeadDays: null,
    createdByUsername: null,
    createdAt: '2026-11-01T08:00:00Z',
    updatedAt: '2026-11-01T08:00:00Z',
  },
  {
    id: CUSTOM_ID,
    name: 'Custom Task',
    isDefault: false,
    triggerState: 'speakers_published',
    dueDateType: 'relative_to_event',
    dueDateOffsetDays: 7,
    activationLeadDays: null,
    createdByUsername: null,
    createdAt: '2026-11-01T08:00:00Z',
    updatedAt: '2026-11-01T08:00:00Z',
  },
];

describe('TaskTemplatesTab', () => {
  beforeEach(() => {
    api.on('listAllTemplates', () => templates);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('should_listDefaultAndCustomTemplates_withTranslatedTriggers', async () => {
    renderWithAuth(<TaskTemplatesTab />);

    const defaults = await screen.findByTestId('default-templates-list');
    expect(within(defaults).getByText('Default Task')).toBeInTheDocument();
    expect(within(defaults).getByText('Agenda Published')).toBeInTheDocument();
    const custom = screen.getByTestId('custom-templates-list');
    expect(within(custom).getByText('Custom Task')).toBeInTheDocument();
    expect(within(custom).getByText('Speakers published')).toBeInTheDocument();
  });

  it.each([
    // #1223 item 5: "-90d before event"; the slides newsletter (+1) read "1d before event".
    ['Mailing: Save the date', -90, '90 days before the event'],
    ['Newsletter: Slides Are Online', 1, '1 day after the event'],
    ['Event day briefing', 0, 'On the event day'],
    ['Call for speakers', -1, '1 day before the event'],
    ['Feedback survey', 14, '14 days after the event'],
  ])('should_describeTheOffset_when_%s_isDue_%i', async (name, offset, text) => {
    api.on('listAllTemplates', () => [
      { ...templates[0], name, dueDateType: 'relative_to_event', dueDateOffsetDays: offset },
    ]);
    renderWithAuth(<TaskTemplatesTab />);

    const defaults = await screen.findByTestId('default-templates-list');
    expect(within(defaults).getByText(text)).toBeInTheDocument();
    expect(defaults).not.toHaveTextContent(/-\d+d|\dd before/);
  });

  it('should_sayImmediately_when_templateIsDueImmediately', async () => {
    renderWithAuth(<TaskTemplatesTab />);

    const defaults = await screen.findByTestId('default-templates-list');
    expect(within(defaults).getByText('Immediate')).toBeInTheDocument();
  });

  it('should_openTheCreateDialog_when_addTemplateClicked', async () => {
    const user = userEvent.setup();
    renderWithAuth(<TaskTemplatesTab />);
    await user.click(await screen.findByTestId('add-template-btn'));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('should_openTheEditDialog_withTheTemplate_when_editClicked', async () => {
    const user = userEvent.setup();
    renderWithAuth(<TaskTemplatesTab />);
    await user.click(await screen.findByTestId(`edit-template-${CUSTOM_ID}`));
    expect(await screen.findByTestId('template-name-input')).toHaveValue('Custom Task');
  });

  it('should_deleteTheTemplate_when_confirmed', async () => {
    const user = userEvent.setup();
    renderWithAuth(<TaskTemplatesTab />);
    await user.click(await screen.findByTestId(`delete-template-${CUSTOM_ID}`));
    await waitFor(() => expect(api.calls('deleteTemplate')).toHaveLength(1));
    expect(api.calls('deleteTemplate')[0].params.templateId).toBe(CUSTOM_ID);
  });

  it('should_notDelete_when_confirmCancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithAuth(<TaskTemplatesTab />);
    await user.click(await screen.findByTestId(`delete-template-${CUSTOM_ID}`));
    expect(window.confirm).toHaveBeenCalled();
    expect(api.calls('deleteTemplate')).toHaveLength(0);
  });

  it('should_showAnError_when_deleteFails', async () => {
    api.on('deleteTemplate', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));
    const user = userEvent.setup();
    renderWithAuth(<TaskTemplatesTab />);
    await user.click(await screen.findByTestId(`delete-template-${CUSTOM_ID}`));
    expect(
      await screen.findByText('Failed to delete template. Please try again.')
    ).toBeInTheDocument();
  });
});
