/**
 * EventTasksLiveTab tests (Epic 14, Story 14.F.2)
 *
 * The template checklist made live: toggling a template creates/deletes its task
 * instance immediately, assignee changes persist, a state-carrying delete confirms.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EventTasksLiveTab } from '../EventTasksLiveTab';
import type { Event } from '@/types/event.types';
import { taskService } from '@/services/taskService';

vi.mock('@/services/taskService', () => ({
  taskService: {
    listEventTasks: vi.fn(),
    createTasksFromTemplates: vi.fn().mockResolvedValue([]),
    deleteTask: vi.fn().mockResolvedValue(undefined),
    updateTask: vi.fn().mockResolvedValue({}),
  },
}));
vi.mock('../../Tasks/CustomTaskModal', () => ({ CustomTaskModal: () => null }));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: string | Record<string, unknown>) =>
      typeof opts === 'string' ? opts : ((opts?.defaultValue as string) ?? key),
  }),
}));

// Stub the presentational EventTasksTab to expose the live handlers as buttons.
vi.mock('@/components/organizer/Tasks/EventTasksTab', () => ({
  EventTasksTab: ({
    selectedTemplates,
    onTemplateToggle,
    onAssigneeChange,
  }: {
    selectedTemplates: string[];
    onTemplateToggle: (id: string, checked: boolean) => void;
    onAssigneeChange: (id: string, assignee: string) => void;
  }) => (
    <div>
      <div data-testid="selected">{selectedTemplates.join(',')}</div>
      <button data-testid="toggle-on" onClick={() => onTemplateToggle('tpl-new', true)} />
      <button data-testid="toggle-off-clean" onClick={() => onTemplateToggle('tpl-clean', false)} />
      <button
        data-testid="toggle-off-stateful"
        onClick={() => onTemplateToggle('tpl-state', false)}
      />
      <button data-testid="reassign" onClick={() => onAssigneeChange('tpl-clean', 'bob')} />
    </div>
  ),
}));

const event = { eventCode: 'BAT54', organizerUsername: 'amelia' } as unknown as Event;

const TASKS = [
  {
    id: 't-clean',
    templateId: 'tpl-clean',
    status: 'pending',
    notes: null,
    assignedOrganizerUsername: null,
    taskName: 'Clean',
  },
  {
    id: 't-state',
    templateId: 'tpl-state',
    status: 'completed',
    notes: 'done note',
    assignedOrganizerUsername: null,
    taskName: 'Stateful',
  },
];

function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <EventTasksLiveTab event={event} eventCode="BAT54" />
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(taskService.listEventTasks).mockResolvedValue(TASKS as never);
});

describe('EventTasksLiveTab', () => {
  it('derives selected templates from live tasks', async () => {
    renderTab();
    await waitFor(() =>
      expect(screen.getByTestId('selected')).toHaveTextContent('tpl-clean,tpl-state')
    );
  });

  it('toggling a template ON creates its task instance live', async () => {
    renderTab();
    await screen.findByTestId('toggle-on');
    fireEvent.click(screen.getByTestId('toggle-on'));
    await waitFor(() =>
      expect(taskService.createTasksFromTemplates).toHaveBeenCalledWith('BAT54', {
        templates: [{ templateId: 'tpl-new', assignedOrganizerUsername: undefined }],
      })
    );
  });

  it('toggling a stateless template OFF deletes it immediately (no confirm)', async () => {
    renderTab();
    await screen.findByTestId('toggle-off-clean');
    fireEvent.click(screen.getByTestId('toggle-off-clean'));
    await waitFor(() => expect(taskService.deleteTask).toHaveBeenCalledWith('t-clean'));
  });

  it('toggling a state-carrying template OFF asks for confirmation first', async () => {
    renderTab();
    await screen.findByTestId('toggle-off-stateful');
    fireEvent.click(screen.getByTestId('toggle-off-stateful'));
    // confirm dialog appears; delete NOT called yet
    expect(await screen.findByTestId('tasks-confirm-delete')).toBeInTheDocument();
    expect(taskService.deleteTask).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('tasks-confirm-delete'));
    await waitFor(() => expect(taskService.deleteTask).toHaveBeenCalledWith('t-state'));
  });

  it('changing an assignee persists via updateTask', async () => {
    renderTab();
    await screen.findByTestId('reassign');
    fireEvent.click(screen.getByTestId('reassign'));
    await waitFor(() =>
      expect(taskService.updateTask).toHaveBeenCalledWith('t-clean', {
        assignedOrganizerUsername: 'bob',
      })
    );
  });
});
