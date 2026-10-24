/**
 * TaskWidget tests (T8f, docs/plans/organizer-cockpit-attention-cards.md #9).
 *
 * The Events-overview widget lists ALL of the organizer's open tasks across events
 * (not only those due within 3 days), with the same visibility rule as the Cockpit
 * attention cards, sorted by due date.
 */
import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import i18n from '@/i18n/config';
import { taskService, type EventTaskResponse } from '@/services/taskService';
import { TaskWidget } from '../TaskWidget';

vi.mock('@/services/taskService', () => ({
  taskService: { getMyTasks: vi.fn(), completeTask: vi.fn() },
}));
vi.mock('../CustomTaskModal', () => ({ CustomTaskModal: () => null }));
vi.mock('@/components/shared/OrganizerChip', () => ({ OrganizerChip: () => null }));

const DAY = 24 * 60 * 60 * 1000;
const iso = (offsetDays: number) => new Date(Date.now() + offsetDays * DAY).toISOString();

const task = (over: Partial<EventTaskResponse>): EventTaskResponse => ({
  id: 't',
  eventId: 'e1',
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
  createdAt: '',
  updatedAt: '',
  ...over,
});

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
    vi.mocked(taskService.getMyTasks).mockReset();
    void i18n.changeLanguage('en');
  });

  it('should fetch all my tasks without the 3-day critical filter', async () => {
    vi.mocked(taskService.getMyTasks).mockResolvedValue([]);
    renderWidget();
    await screen.findByText(/no open tasks/i);
    expect(taskService.getMyTasks).toHaveBeenCalledWith();
  });

  it('should show tasks due far in the future, not only those due within 3 days', async () => {
    vi.mocked(taskService.getMyTasks).mockResolvedValue([
      task({ id: 'far', taskName: 'Partner Meeting Coordination', dueDate: iso(30) }),
    ]);
    renderWidget();
    expect(await screen.findByText('Partner Meeting Coordination')).toBeInTheDocument();
  });

  it('should apply the cockpit visibility rule (hide completed, cancelled, not-yet-due pending)', async () => {
    vi.mocked(taskService.getMyTasks).mockResolvedValue([
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
    vi.mocked(taskService.getMyTasks).mockResolvedValue([
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
    vi.mocked(taskService.getMyTasks).mockResolvedValue([task({ taskName: 'X', dueDate: iso(1) })]);
    renderWidget();
    expect(await screen.findByRole('heading', { name: /my open tasks/i })).toBeInTheDocument();
  });
});
