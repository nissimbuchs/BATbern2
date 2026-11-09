/**
 * TaskWidget tests (T8f, docs/plans/organizer-cockpit-attention-cards.md #9).
 *
 * The Events-overview widget lists ALL of the organizer's open tasks across events
 * (not only those due within 3 days), with the same visibility rule as the Cockpit
 * attention cards, sorted by due date. Completing a task sends `PATCH /tasks/{taskId}`
 * with status `completed` (Story 4.9, no verb endpoints).
 *
 * Runs on the MSW harness: the real taskService, hooks and child components; the harness
 * checks every request against event-tasks-api and answers with typed overrides.
 */
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import type { EventTaskResponse } from '@/services/taskService';
import { TaskWidget } from '../TaskWidget';

const DAY = 24 * 60 * 60 * 1000;
const iso = (offsetDays: number) => new Date(Date.now() + offsetDays * DAY).toISOString();

const task = (over: Partial<EventTaskResponse>): EventTaskResponse => ({
  id: '3cf5db30-92c0-44d2-bb74-4b0f2b4aa574',
  eventId: '8dd9c9a4-35c1-4adb-b1aa-ef81e7b65fe3',
  eventCode: 'BATbern60',
  templateId: null,
  taskName: 'Task',
  triggerState: 'agenda_published',
  dueDate: null,
  assignedOrganizerUsername: 'nissim.buchs',
  status: 'todo',
  notes: null,
  completedDate: null,
  completedByUsername: null,
  createdAt: '2026-10-25T09:46:11.349885Z',
  updatedAt: '2026-10-25T09:46:11.350173Z',
  ...over,
});

const myTasks = (tasks: EventTaskResponse[]) => api.on('getMyTasks', () => tasks);

const renderWidget = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <I18nextProvider i18n={i18n}>
          <TaskWidget organizerUsername="nissim.buchs" />
        </I18nextProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('TaskWidget — my open tasks', () => {
  beforeEach(() => {
    void i18n.changeLanguage('en');
  });

  it('should fetch all my tasks without the 3-day critical filter', async () => {
    myTasks([]);
    renderWidget();
    await screen.findByText(/no open tasks/i);
    const [call] = api.calls('getMyTasks');
    expect(call.query.toString()).toBe('');
  });

  it('should show tasks due far in the future, not only those due within 3 days', async () => {
    myTasks([task({ id: 'far', taskName: 'Partner Meeting Coordination', dueDate: iso(30) })]);
    renderWidget();
    expect(await screen.findByText('Partner Meeting Coordination')).toBeInTheDocument();
  });

  it('should apply the cockpit visibility rule (hide completed, cancelled, not-yet-due pending)', async () => {
    myTasks([
      task({ id: 'a', taskName: 'Open task', status: 'todo', dueDate: iso(5) }),
      task({ id: 'b', taskName: 'Done task', status: 'completed', dueDate: iso(-5) }),
      task({ id: 'c', taskName: 'Cancelled task', status: 'cancelled' as never }),
      task({ id: 'd', taskName: 'Slides Are Online', status: 'pending', dueDate: iso(14) }),
      task({ id: 'e', taskName: 'Stuck catering', status: 'pending', dueDate: iso(-17) }),
    ]);
    renderWidget();
    expect(await screen.findByText('Open task')).toBeInTheDocument();
    expect(screen.getByText('Stuck catering')).toBeInTheDocument();
    expect(screen.queryByText('Done task')).not.toBeInTheDocument();
    expect(screen.queryByText('Cancelled task')).not.toBeInTheDocument();
    expect(screen.queryByText('Slides Are Online')).not.toBeInTheDocument();
  });

  it('should sort by due date: overdue first, undated last', async () => {
    myTasks([
      task({ id: 'none', taskName: 'No date', dueDate: null }),
      task({ id: 'later', taskName: 'Later', dueDate: iso(20) }),
      task({ id: 'over', taskName: 'Overdue', dueDate: iso(-3) }),
      task({ id: 'soon', taskName: 'Soon', dueDate: iso(2) }),
    ]);
    renderWidget();
    const list = await screen.findByTestId('critical-tasks-list');
    await within(list).findByText('Overdue');
    const names = within(list)
      .getAllByText(/^(No date|Later|Overdue|Soon)$/)
      .map((el) => el.textContent);
    expect(names).toEqual(['Overdue', 'Soon', 'Later', 'No date']);
  });

  it('should use the "My open tasks" heading', async () => {
    myTasks([task({ taskName: 'X', dueDate: iso(1) })]);
    renderWidget();
    expect(await screen.findByRole('heading', { name: /my open tasks/i })).toBeInTheDocument();
  });

  it('should complete a task with PATCH status completed and refetch the list', async () => {
    const open = task({ taskName: 'Order wine', dueDate: iso(2) });
    let served = [open];
    api.on('getMyTasks', () => served);
    api.on('updateTask', ({ body }) => {
      served = [{ ...open, status: 'completed', completedByUsername: 'nissim.buchs' }];
      return { ...open, ...body, status: 'completed' };
    });
    renderWidget();

    await userEvent.click(await screen.findByRole('button', { name: /mark complete/i }));

    await waitFor(() => expect(api.calls('updateTask')).toHaveLength(1));
    const [call] = api.calls('updateTask');
    expect(call.method).toBe('PATCH');
    expect(call.params).toEqual({ taskId: open.id });
    expect(call.body).toEqual({ status: 'completed' });
    expect(await screen.findByText(/no open tasks/i)).toBeInTheDocument();
  });
});
