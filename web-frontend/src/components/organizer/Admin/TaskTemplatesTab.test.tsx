/**
 * TaskTemplatesTab Tests (Story 10.1 - Task 4)
 *
 * Tests:
 * - AC4: Renders default and custom template sections
 * - AC4: Create button opens CustomTaskModal
 * - AC4: Edit button opens TaskTemplateEditModal
 * - AC4: Delete calls taskService.deleteTemplate after confirm
 * - AC4: Delete failure shows Snackbar error
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { TaskTemplatesTab } from './TaskTemplatesTab';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
  }),
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({ user: { username: 'testorganizer', role: 'organizer' } }),
}));

vi.mock('@/services/taskService', () => ({
  taskService: {
    listAllTemplates: vi.fn().mockResolvedValue([
      {
        id: 'tmpl-1',
        name: 'Default Task',
        isDefault: true,
        triggerState: 'DRAFT',
        dueDateType: 'immediate',
        dueDateOffsetDays: null,
      },
      {
        id: 'tmpl-2',
        name: 'Custom Task',
        isDefault: false,
        triggerState: 'PUBLISHED',
        dueDateType: 'relative_to_event',
        dueDateOffsetDays: 7,
      },
    ]),
    deleteTemplate: vi.fn(),
  },
}));

vi.mock('@/components/organizer/Tasks/CustomTaskModal', () => ({
  CustomTaskModal: ({ open, onClose }: { open: boolean; onClose: () => void }) =>
    open ? (
      <div data-testid="custom-task-modal">
        <button onClick={onClose}>Close</button>
      </div>
    ) : null,
}));

vi.mock('./TaskTemplateEditModal', () => ({
  TaskTemplateEditModal: ({ open, onClose }: { open: boolean; onClose: () => void }) =>
    open ? (
      <div data-testid="task-template-edit-modal">
        <button onClick={onClose}>Close</button>
      </div>
    ) : null,
}));

vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="loader" />,
}));

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderTab = () =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <TaskTemplatesTab />
    </QueryClientProvider>
  );

describe('TaskTemplatesTab', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    const { taskService } = await import('@/services/taskService');
    vi.mocked(taskService.deleteTemplate).mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('renders default templates section', async () => {
    renderTab();
    await waitFor(() => expect(screen.getByText('Default Task')).toBeInTheDocument());
    expect(screen.getByTestId('default-templates-list')).toBeInTheDocument();
  });

  it('renders custom templates section', async () => {
    renderTab();
    await waitFor(() => expect(screen.getByText('Custom Task')).toBeInTheDocument());
    expect(screen.getByTestId('custom-templates-list')).toBeInTheDocument();
  });

  it('opens CustomTaskModal when Add Template clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    await waitFor(() => screen.getByTestId('add-template-btn'));
    await user.click(screen.getByTestId('add-template-btn'));
    expect(screen.getByTestId('custom-task-modal')).toBeInTheDocument();
  });

  it('opens TaskTemplateEditModal when Edit clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    await waitFor(() => screen.getByTestId('edit-template-tmpl-2'));
    await user.click(screen.getByTestId('edit-template-tmpl-2'));
    expect(screen.getByTestId('task-template-edit-modal')).toBeInTheDocument();
  });

  it('calls deleteTemplate after window.confirm', async () => {
    const user = userEvent.setup();
    renderTab();
    await waitFor(() => screen.getByTestId('delete-template-tmpl-2'));
    await user.click(screen.getByTestId('delete-template-tmpl-2'));
    const { taskService } = await import('@/services/taskService');
    await waitFor(() => expect(taskService.deleteTemplate).toHaveBeenCalledWith('tmpl-2'));
  });

  it('does not call deleteTemplate when confirm is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderTab();
    await waitFor(() => screen.getByTestId('delete-template-tmpl-2'));
    await user.click(screen.getByTestId('delete-template-tmpl-2'));
    const { taskService } = await import('@/services/taskService');
    expect(taskService.deleteTemplate).not.toHaveBeenCalled();
  });

  it('shows snackbar error when delete fails', async () => {
    const { taskService } = await import('@/services/taskService');
    vi.mocked(taskService.deleteTemplate).mockRejectedValue(new Error('server error'));
    const user = userEvent.setup();
    renderTab();
    await waitFor(() => screen.getByTestId('delete-template-tmpl-2'));
    await user.click(screen.getByTestId('delete-template-tmpl-2'));
    await waitFor(() =>
      expect(screen.getByText('Failed to delete template. Please try again.')).toBeInTheDocument()
    );
  });
});
