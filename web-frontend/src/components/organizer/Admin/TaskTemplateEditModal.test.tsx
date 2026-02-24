/**
 * TaskTemplateEditModal Tests (Story 10.1 - Task 4)
 *
 * Tests:
 * - AC4: Renders fields pre-populated from template
 * - AC4: Shows offset days field only when dueDateType is relative_to_event
 * - AC4: Calls taskService.updateTemplate on save
 * - AC4: Save button disabled when name is empty
 * - AC4: min=0 enforced on offset days input
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { TaskTemplateEditModal } from './TaskTemplateEditModal';
import type { TaskTemplateResponse } from '@/services/taskService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
  }),
}));

vi.mock('@/services/taskService', () => ({
  taskService: {
    updateTemplate: vi.fn(),
  },
}));

const baseTemplate: TaskTemplateResponse = {
  id: 'tmpl-1',
  name: 'My Template',
  isDefault: false,
  triggerState: 'DRAFT',
  dueDateType: 'immediate',
  dueDateOffsetDays: null,
};

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderModal = (template = baseTemplate, open = true, onClose = vi.fn()) =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <TaskTemplateEditModal open={open} onClose={onClose} template={template} />
    </QueryClientProvider>
  );

describe('TaskTemplateEditModal', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    const { taskService } = await import('@/services/taskService');
    vi.mocked(taskService.updateTemplate).mockResolvedValue({} as never);
  });

  it('renders name field pre-populated', () => {
    renderModal();
    expect(screen.getByTestId('template-name-input')).toHaveValue('My Template');
  });

  it('does not show offset days field when dueDateType is immediate', () => {
    renderModal();
    expect(screen.queryByTestId('template-offset-days-input')).not.toBeInTheDocument();
  });

  it('shows offset days field when dueDateType is relative_to_event', () => {
    renderModal({ ...baseTemplate, dueDateType: 'relative_to_event', dueDateOffsetDays: 7 });
    expect(screen.getByTestId('template-offset-days-input')).toBeInTheDocument();
  });

  it('offset days input has min=0', () => {
    renderModal({ ...baseTemplate, dueDateType: 'relative_to_event', dueDateOffsetDays: 7 });
    const input = screen.getByTestId('template-offset-days-input');
    expect(input).toHaveAttribute('min', '0');
  });

  it('disables Save when name is empty', async () => {
    const user = userEvent.setup();
    renderModal();
    const nameInput = screen.getByTestId('template-name-input');
    await user.clear(nameInput);
    expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
  });

  it('calls updateTemplate with correct data on save', async () => {
    const user = userEvent.setup();
    renderModal();
    await user.click(screen.getByRole('button', { name: /save/i }));
    const { taskService } = await import('@/services/taskService');
    await waitFor(() =>
      expect(taskService.updateTemplate).toHaveBeenCalledWith('tmpl-1', {
        name: 'My Template',
        triggerState: 'DRAFT',
        dueDateType: 'immediate',
        dueDateOffsetDays: undefined,
      })
    );
  });

  it('calls onClose after successful save', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderModal(baseTemplate, true, onClose);
    await user.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('calls onClose when Cancel clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderModal(baseTemplate, true, onClose);
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
