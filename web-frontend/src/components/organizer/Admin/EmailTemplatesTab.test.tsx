/**
 * EmailTemplatesTab Tests (Story 10.2 — Code Review fix)
 *
 * Tests:
 * - Renders Layout Templates section with list
 * - Renders Content Templates section with category/locale filters
 * - Edit layout template opens EmailTemplateEditModal in layout mode
 * - Edit content template opens EmailTemplateEditModal in content mode
 * - Preview opens EmailTemplatePreviewModal
 * - Delete calls mutation after confirm
 * - Delete does not fire when confirm cancelled
 * - System template shows no delete button
 * - Loading state shows BATbernLoader
 * - Error state shows Alert
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import { EmailTemplatesTab } from './EmailTemplatesTab';

/**
 * Deterministic matchMedia mock keyed on a simulated viewport width so
 * useBreakpoints().isMobile (down('md')) flips between the mobile Select and the
 * desktop ToggleButtonGroup. Mirrors PartnerCreateEditModal.test.tsx.
 */
const installMatchMediaForWidth = (viewportWidth: number) => {
  window.matchMedia = vi.fn((query: string) => {
    const maxMatch = /max-width:\s*([\d.]+)px/.exec(query);
    const minMatch = /min-width:\s*([\d.]+)px/.exec(query);
    let matches = false;
    if (maxMatch) matches = viewportWidth <= parseFloat(maxMatch[1]);
    else if (minMatch) matches = viewportWidth >= parseFloat(minMatch[1]);
    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    };
  }) as unknown as typeof window.matchMedia;
};

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (_k: string, fallback?: string) => fallback ?? _k }),
}));

vi.mock('date-fns', () => ({
  format: () => '24.02.2026',
}));

vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="loader" />,
}));

const mockLayoutTemplate = {
  templateKey: 'batbern-default',
  locale: 'de',
  category: 'LAYOUT',
  subject: null,
  htmlBody: '<html>{{content}}</html>',
  variables: null,
  isLayout: true,
  layoutKey: null,
  isSystemTemplate: true,
  updatedAt: '2026-02-24T10:00:00Z',
};

const mockSystemTemplate = {
  templateKey: 'speaker-invitation',
  locale: 'de',
  category: 'SPEAKER',
  subject: 'Einladung',
  htmlBody: '<p>Hello</p>',
  variables: null,
  isLayout: false,
  layoutKey: 'batbern-default',
  isSystemTemplate: true,
  updatedAt: '2026-02-24T10:00:00Z',
};

const mockCustomTemplate = {
  templateKey: 'my-custom',
  locale: 'de',
  category: 'SPEAKER',
  subject: 'Custom Betreff',
  htmlBody: '<p>Custom</p>',
  variables: null,
  isLayout: false,
  layoutKey: 'batbern-default',
  isSystemTemplate: false,
  updatedAt: '2026-02-24T10:00:00Z',
};

const mockDeleteMutateAsync = vi.fn();

vi.mock('@/hooks/useEmailTemplates', () => ({
  useLayoutTemplates: vi.fn(),
  useEmailTemplates: vi.fn(),
  useDeleteEmailTemplate: vi.fn(),
}));

import {
  useLayoutTemplates,
  useEmailTemplates,
  useDeleteEmailTemplate,
} from '@/hooks/useEmailTemplates';

vi.mock('./EmailTemplateEditModal', () => ({
  EmailTemplateEditModal: ({
    template,
    isLayoutMode,
    onClose,
  }: {
    template?: { templateKey: string };
    isLayoutMode: boolean;
    onClose: () => void;
  }) => (
    <div
      data-testid="email-template-edit-modal"
      data-layout-mode={String(isLayoutMode)}
      data-template-key={template?.templateKey ?? 'new'}
    >
      <button onClick={onClose}>Close</button>
    </div>
  ),
}));

vi.mock('./EmailTemplatePreviewModal', () => ({
  EmailTemplatePreviewModal: ({
    template,
    onClose,
  }: {
    template: { templateKey: string };
    onClose: () => void;
  }) => (
    <div data-testid="email-template-preview-modal" data-template-key={template.templateKey}>
      <button onClick={onClose}>ClosePreview</button>
    </div>
  ),
}));

const setupMocks = (
  overrides: {
    layouts?: unknown[];
    loadingLayouts?: boolean;
    content?: unknown[];
    loadingContent?: boolean;
    contentError?: Error | null;
  } = {}
) => {
  vi.mocked(useLayoutTemplates).mockReturnValue({
    data: overrides.layouts ?? [mockLayoutTemplate],
    isLoading: overrides.loadingLayouts ?? false,
  } as never);

  vi.mocked(useEmailTemplates).mockReturnValue({
    data: overrides.content ?? [mockSystemTemplate, mockCustomTemplate],
    isLoading: overrides.loadingContent ?? false,
    error: overrides.contentError ?? null,
  } as never);

  vi.mocked(useDeleteEmailTemplate).mockReturnValue({
    mutateAsync: mockDeleteMutateAsync,
  } as never);
};

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderTab = () =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <EmailTemplatesTab />
    </QueryClientProvider>
  );

describe('EmailTemplatesTab', () => {
  const originalMatchMedia = window.matchMedia;
  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  beforeEach(() => {
    vi.clearAllMocks();
    mockDeleteMutateAsync.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    setupMocks();
  });

  it('renders Layout Templates section heading', () => {
    renderTab();
    expect(screen.getByText('Layout Templates')).toBeInTheDocument();
  });

  it('renders layout template key in the list', () => {
    renderTab();
    // batbern-default appears in both the layout list and as a chip in content templates
    expect(screen.getAllByText('batbern-default').length).toBeGreaterThanOrEqual(1);
  });

  it('renders Content Templates section heading', () => {
    renderTab();
    expect(screen.getByText('Email Content')).toBeInTheDocument();
  });

  it('renders category filter buttons', () => {
    renderTab();
    expect(screen.getByText('Speakers')).toBeInTheDocument();
    expect(screen.getByText('Registration')).toBeInTheDocument();
    expect(screen.getByText('Task Reminders')).toBeInTheDocument();
  });

  it('renders locale filter buttons', () => {
    renderTab();
    // Use button role to find the toggle filter buttons specifically (DE/EN also appear as chips)
    expect(screen.getByRole('button', { name: 'DE' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'EN' })).toBeInTheDocument();
  });

  it('system template has no delete button', () => {
    renderTab();
    expect(
      screen.queryByTestId('delete-email-template-speaker-invitation')
    ).not.toBeInTheDocument();
  });

  it('custom template has a delete button', () => {
    renderTab();
    expect(screen.getByTestId('delete-email-template-my-custom')).toBeInTheDocument();
  });

  it('edit layout template opens modal in layout mode', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('edit-layout-batbern-default-de'));
    const modal = await screen.findByTestId('email-template-edit-modal');
    expect(modal).toBeInTheDocument();
    expect(modal.dataset.layoutMode).toBe('true');
    expect(modal.dataset.templateKey).toBe('batbern-default');
  });

  it('edit content template opens modal in content mode', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('edit-email-template-speaker-invitation'));
    const modal = await screen.findByTestId('email-template-edit-modal');
    expect(modal.dataset.layoutMode).toBe('false');
    expect(modal.dataset.templateKey).toBe('speaker-invitation');
  });

  it('preview opens EmailTemplatePreviewModal', async () => {
    const user = userEvent.setup();
    renderTab();
    const previewBtns = screen.getAllByLabelText(/Preview/);
    await user.click(previewBtns[0]);
    expect(await screen.findByTestId('email-template-preview-modal')).toBeInTheDocument();
  });

  it('delete calls mutateAsync after confirm', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('delete-email-template-my-custom'));
    await waitFor(() =>
      expect(mockDeleteMutateAsync).toHaveBeenCalledWith({
        templateKey: 'my-custom',
        locale: 'de',
      })
    );
  });

  it('delete does not fire when confirm is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('delete-email-template-my-custom'));
    expect(mockDeleteMutateAsync).not.toHaveBeenCalled();
  });

  it('shows loader when loading', async () => {
    setupMocks({ loadingLayouts: true });
    renderTab();
    expect(screen.getByTestId('loader')).toBeInTheDocument();
  });

  it('shows error alert when content fetch fails', async () => {
    setupMocks({ contentError: new Error('fetch failed') });
    renderTab();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('no add-template button is rendered', () => {
    renderTab();
    expect(screen.queryByTestId('add-email-template-btn')).not.toBeInTheDocument();
  });

  it('should_renderCategorySelect_when_mobileViewport', async () => {
    installMatchMediaForWidth(375);
    const user = userEvent.setup();
    renderTab();
    // The mobile category filter is a Select (combobox), not a ToggleButtonGroup
    const select = screen.getByTestId('email-template-category-select');
    expect(select).toBeInTheDocument();
    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    // All 5 category options appear in the opened listbox
    const options = await screen.findAllByRole('option');
    expect(options).toHaveLength(5);
  });

  it('should_renderCategoryToggleGroup_when_desktopViewport', () => {
    installMatchMediaForWidth(1280);
    renderTab();
    // Desktop renders the ToggleButtonGroup (no Select combobox for category)
    expect(screen.queryByTestId('email-template-category-select')).not.toBeInTheDocument();
    expect(screen.getByText('Speakers')).toBeInTheDocument();
    expect(screen.getByText('Registration')).toBeInTheDocument();
  });
});
