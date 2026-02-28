/**
 * EmailTemplateEditModal Tests (Story 10.2 — Code Review fix)
 *
 * Tests:
 * - Layout mode: no subject field, Monaco editor shown, missing-{{content}} warning
 * - Content mode: subject field shown, TinyMCE shown, layoutKey selector shown
 * - Edit mode: fields pre-populated from template prop
 * - Create mode: templateKey + locale fields shown
 * - Save validates htmlBody required
 * - Save in content mode validates subject required
 * - Save calls updateTemplate in edit mode
 * - Save calls createTemplate in create mode
 * - Cancel calls onClose
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { EmailTemplateEditModal } from './EmailTemplateEditModal';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (_k: string, fallback?: string) => fallback ?? _k }),
}));

// Monaco Editor — replace with a simple textarea for tests
vi.mock('@monaco-editor/react', () => ({
  default: ({ value, onChange }: { value?: string; onChange?: (v: string) => void }) => (
    <textarea
      data-testid="monaco-editor"
      value={value ?? ''}
      onChange={(e) => onChange?.(e.target.value)}
    />
  ),
}));

// TinyMCE — replace with a simple textarea for tests
vi.mock('@tinymce/tinymce-react', () => ({
  Editor: ({ value, onEditorChange }: { value?: string; onEditorChange?: (v: string) => void }) => (
    <textarea
      data-testid="tinymce-editor"
      value={value ?? ''}
      onChange={(e) => onEditorChange?.(e.target.value)}
    />
  ),
}));

// Hooks — use vi.fn() directly in factory to avoid hoisting issues
vi.mock('@/hooks/useEmailTemplates', () => ({
  useLayoutTemplates: vi.fn(),
  useCreateEmailTemplate: vi.fn(),
  useUpdateEmailTemplate: vi.fn(),
}));

import {
  useLayoutTemplates,
  useCreateEmailTemplate,
  useUpdateEmailTemplate,
} from '@/hooks/useEmailTemplates';

const existingContentTemplate = {
  templateKey: 'speaker-invitation',
  locale: 'de',
  category: 'SPEAKER',
  subject: 'Einladung als Referent',
  htmlBody: '<p>Liebe {{firstName}}</p>',
  variables: null,
  isLayout: false,
  layoutKey: 'batbern-default',
  isSystemTemplate: true,
  updatedAt: '2026-02-24T10:00:00Z',
};

const existingLayoutTemplate = {
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

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderModal = (props: {
  template?: typeof existingContentTemplate | typeof existingLayoutTemplate;
  isLayoutMode?: boolean;
  onClose?: () => void;
}) =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <EmailTemplateEditModal
        template={props.template}
        isLayoutMode={props.isLayoutMode ?? false}
        onClose={props.onClose ?? vi.fn()}
      />
    </QueryClientProvider>
  );

describe('EmailTemplateEditModal — layout mode', () => {
  const mockUpdateMutateAsync = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useLayoutTemplates).mockReturnValue({ data: [] } as never);
    vi.mocked(useCreateEmailTemplate).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(useUpdateEmailTemplate).mockReturnValue({
      mutateAsync: mockUpdateMutateAsync,
      isPending: false,
    } as never);
    mockUpdateMutateAsync.mockResolvedValue({});
  });

  it('shows Monaco editor in layout mode', () => {
    renderModal({ template: existingLayoutTemplate, isLayoutMode: true });
    expect(screen.getByTestId('monaco-editor')).toBeInTheDocument();
    expect(screen.queryByTestId('tinymce-editor')).not.toBeInTheDocument();
  });

  it('no subject field in layout mode', () => {
    renderModal({ template: existingLayoutTemplate, isLayoutMode: true });
    expect(screen.queryByLabelText(/subject/i)).not.toBeInTheDocument();
  });

  it('shows missing-{{content}} warning when body lacks placeholder', () => {
    renderModal({
      template: { ...existingLayoutTemplate, htmlBody: '<html>no placeholder</html>' },
      isLayoutMode: true,
    });
    expect(screen.getByText(/\{\{content\}\} placeholder missing/i)).toBeInTheDocument();
  });

  it('no warning when {{content}} is present', () => {
    renderModal({ template: existingLayoutTemplate, isLayoutMode: true });
    expect(screen.queryByText(/placeholder missing/i)).not.toBeInTheDocument();
  });

  it('calls updateTemplate on save', async () => {
    const user = userEvent.setup();
    renderModal({ template: existingLayoutTemplate, isLayoutMode: true });
    await user.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalledOnce());
  });
});

describe('EmailTemplateEditModal — content mode', () => {
  const mockUpdateMutateAsync = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useLayoutTemplates).mockReturnValue({
      data: [{ templateKey: 'batbern-default', locale: 'de' }],
    } as never);
    vi.mocked(useCreateEmailTemplate).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(useUpdateEmailTemplate).mockReturnValue({
      mutateAsync: mockUpdateMutateAsync,
      isPending: false,
    } as never);
    mockUpdateMutateAsync.mockResolvedValue({});
  });

  it('shows TinyMCE editor in content mode', () => {
    renderModal({ template: existingContentTemplate, isLayoutMode: false });
    expect(screen.getByTestId('tinymce-editor')).toBeInTheDocument();
    expect(screen.queryByTestId('monaco-editor')).not.toBeInTheDocument();
  });

  it('shows subject field in content mode', () => {
    renderModal({ template: existingContentTemplate, isLayoutMode: false });
    expect(screen.getByLabelText(/subject/i)).toBeInTheDocument();
  });

  it('shows layoutKey selector in content mode', () => {
    renderModal({ template: existingContentTemplate, isLayoutMode: false });
    // The layout Select renders the currently-selected option's text in its trigger
    // (layouts mock: [{ templateKey: 'batbern-default', locale: 'de' }])
    expect(screen.getByText('batbern-default (de)')).toBeInTheDocument();
  });

  it('pre-populates subject from template', () => {
    renderModal({ template: existingContentTemplate, isLayoutMode: false });
    expect(screen.getByDisplayValue('Einladung als Referent')).toBeInTheDocument();
  });

  it('shows detected {{variable}} chips', () => {
    renderModal({ template: existingContentTemplate, isLayoutMode: false });
    expect(screen.getByText('{{firstName}}')).toBeInTheDocument();
  });

  it('shows validation error when subject cleared and save clicked', async () => {
    const user = userEvent.setup();
    renderModal({ template: existingContentTemplate, isLayoutMode: false });
    await user.clear(screen.getByDisplayValue('Einladung als Referent'));
    await user.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(screen.getByText(/subject is required/i)).toBeInTheDocument());
    expect(mockUpdateMutateAsync).not.toHaveBeenCalled();
  });

  it('calls updateTemplate on valid save', async () => {
    const user = userEvent.setup();
    renderModal({ template: existingContentTemplate, isLayoutMode: false });
    await user.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalledOnce());
  });

  it('calls onClose after successful save', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderModal({ template: existingContentTemplate, isLayoutMode: false, onClose });
    await user.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('calls onClose when Cancel clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderModal({ template: existingContentTemplate, isLayoutMode: false, onClose });
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });
});

describe('EmailTemplateEditModal — create mode (no template)', () => {
  const mockCreateMutateAsync = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useLayoutTemplates).mockReturnValue({ data: [] } as never);
    vi.mocked(useCreateEmailTemplate).mockReturnValue({
      mutateAsync: mockCreateMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(useUpdateEmailTemplate).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    mockCreateMutateAsync.mockResolvedValue({ templateKey: 'new', isSystemTemplate: false });
  });

  it('shows templateKey and locale inputs in create mode', () => {
    renderModal({ isLayoutMode: false });
    // Template key is a standard TextField — accessible via label
    expect(screen.getByLabelText(/template key/i)).toBeInTheDocument();
    // Locale is a MUI Select — its InputLabel renders "Locale" as visible text in the DOM
    expect(screen.getAllByText('Locale').length).toBeGreaterThanOrEqual(1);
  });

  it('shows validation error when templateKey empty on save (subject filled)', async () => {
    const user = userEvent.setup();
    renderModal({ isLayoutMode: false });
    await user.type(screen.getByLabelText(/subject/i), 'Betreff');
    // Leave templateKey empty, put some body text via textarea
    await user.type(screen.getByTestId('tinymce-editor'), '<p>body</p>');
    await user.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(screen.getByText(/template key is required/i)).toBeInTheDocument());
  });
});
