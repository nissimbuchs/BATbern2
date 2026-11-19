/**
 * EventTasksLiveTab tests (Epic 14, Story 14.F.2)
 *
 * The template checklist made live: toggling a template creates/deletes its task
 * instance immediately, assignee changes persist, a state-carrying delete confirms.
 * #1223: the intro says so (it read "will be created when you save the event").
 *
 * Real EventTasksTab, taskService, organizer directory and i18next on the MSW harness.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/test/msw/api';
import { renderWithAuth } from '@/test/auth';
import { aUser, anEventTask } from '@/test/fixtures';
import type { Event } from '@/types/event.types';
import type { TaskTemplateResponse } from '@/services/taskService';
import { EventTasksLiveTab } from '../EventTasksLiveTab';

const EVENT_CODE = 'BAT54';
const event = { eventCode: EVENT_CODE, organizerUsername: 'amelia' } as unknown as Event;

const TPL_NEW = '0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b301';
const TPL_CLEAN = '0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b302';
const TPL_STATE = '0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b303';

const template = (id: string, name: string): TaskTemplateResponse => ({
  id,
  name,
  isDefault: true,
  triggerState: 'created',
  dueDateType: 'relative_to_event',
  dueDateOffsetDays: -30,
  activationLeadDays: null,
  createdByUsername: null,
  createdAt: '2026-11-01T08:00:00Z',
  updatedAt: '2026-11-01T08:00:00Z',
});

const TEMPLATES = [
  template(TPL_NEW, 'Newsletter: Save the date'),
  template(TPL_CLEAN, 'Book the venue'),
  template(TPL_STATE, 'Catering coordination'),
];

const TASKS = [
  anEventTask({
    id: '7d1f0c2a-5b3e-4c1d-9a8f-000000000011',
    eventCode: EVENT_CODE,
    templateId: TPL_CLEAN,
    taskName: 'Book the venue',
    status: 'pending',
  }),
  anEventTask({
    id: '7d1f0c2a-5b3e-4c1d-9a8f-000000000012',
    eventCode: EVENT_CODE,
    templateId: TPL_STATE,
    taskName: 'Catering coordination',
    status: 'completed',
    notes: 'done note',
  }),
];

const checkboxOf = async (templateId: string) =>
  within(await screen.findByTestId(`task-template-${templateId}`)).getByRole('checkbox');

const renderTab = () => renderWithAuth(<EventTasksLiveTab event={event} eventCode={EVENT_CODE} />);

beforeEach(() => {
  api.on('listAllTemplates', () => TEMPLATES);
  api.on('listEventTasks', () => TASKS);
  api.on('listUsers', () => ({
    data: [aUser({ id: 'bob.muster', firstName: 'Bob', lastName: 'Muster', roles: ['ORGANIZER'] })],
    pagination: {
      page: 1,
      limit: 100,
      totalItems: 1,
      totalPages: 1,
      hasNext: false,
      hasPrev: false,
    },
  }));
});

describe('EventTasksLiveTab', () => {
  it('ticks the templates that have a task', async () => {
    renderTab();

    await waitFor(async () => expect(await checkboxOf(TPL_CLEAN)).toBeChecked());
    expect(await checkboxOf(TPL_STATE)).toBeChecked();
    expect(await checkboxOf(TPL_NEW)).not.toBeChecked();
    expect(api.calls('listEventTasks')[0].params.eventCode).toBe(EVENT_CODE);
  });

  it('says that ticking creates the task at once (#1223)', async () => {
    renderTab();

    expect(await screen.findByText(/Ticking one creates its task right away/)).toBeInTheDocument();
    expect(screen.queryByText(/when you save the event/)).not.toBeInTheDocument();
  });

  it('toggling a template ON creates its task instance live', async () => {
    const user = userEvent.setup();
    renderTab();
    await waitFor(async () => expect(await checkboxOf(TPL_CLEAN)).toBeChecked());

    await user.click(await checkboxOf(TPL_NEW));

    await waitFor(() => expect(api.calls('createTasksFromTemplates')).toHaveLength(1));
    const [call] = api.calls('createTasksFromTemplates');
    expect(call.params.eventCode).toBe(EVENT_CODE);
    expect(call.body).toEqual({ templates: [{ templateId: TPL_NEW }] });
  });

  it('toggling a stateless template OFF deletes it immediately (no confirm)', async () => {
    const user = userEvent.setup();
    renderTab();
    await waitFor(async () => expect(await checkboxOf(TPL_CLEAN)).toBeChecked());

    await user.click(await checkboxOf(TPL_CLEAN));

    await waitFor(() => expect(api.calls('deleteTask')).toHaveLength(1));
    expect(api.calls('deleteTask')[0].params.taskId).toBe(TASKS[0].id);
    expect(screen.queryByTestId('tasks-confirm-delete')).not.toBeInTheDocument();
  });

  it('toggling a state-carrying template OFF asks for confirmation first', async () => {
    const user = userEvent.setup();
    renderTab();
    await waitFor(async () => expect(await checkboxOf(TPL_STATE)).toBeChecked());

    await user.click(await checkboxOf(TPL_STATE));

    expect(await screen.findByTestId('tasks-confirm-delete')).toBeInTheDocument();
    expect(api.calls('deleteTask')).toHaveLength(0);
    await user.click(screen.getByTestId('tasks-confirm-delete'));
    await waitFor(() => expect(api.calls('deleteTask')).toHaveLength(1));
    expect(api.calls('deleteTask')[0].params.taskId).toBe(TASKS[1].id);
  });

  it('changing an assignee persists via updateTask', async () => {
    const user = userEvent.setup();
    renderTab();
    await waitFor(async () => expect(await checkboxOf(TPL_CLEAN)).toBeChecked());

    const select = within(await screen.findByTestId(`task-assignee-${TPL_CLEAN}`)).getByRole(
      'combobox'
    );
    await user.click(select);
    await user.click(await screen.findByTestId('organizer-option-bob.muster'));

    await waitFor(() => expect(api.calls('updateTask')).toHaveLength(1));
    const [call] = api.calls('updateTask');
    expect(call.params.taskId).toBe(TASKS[0].id);
    expect(call.body).toEqual({ assignedOrganizerUsername: 'bob.muster' });
  });
});
