/**
 * EmailTemplatePreviewModal Tests (Story 10.2 — Code Review fix)
 *
 * Tests:
 * - Layout template: replaces {{content}} with CONTENT AREA placeholder in iframe
 * - Content template with layoutKey: fetches layout and merges, renders iframe
 * - Standalone content template: renders htmlBody directly in iframe
 * - Shows subject when present
 * - Shows layout badge when layoutKey present
 * - Close button calls onClose
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { EmailTemplatePreviewModal } from './EmailTemplatePreviewModal';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (_k: string, fallback?: string) => fallback ?? _k }),
}));

vi.mock('@/services/emailTemplateService', () => ({
  emailTemplateService: {
    getTemplate: vi.fn(),
  },
}));

const layoutTemplate = {
  templateKey: 'batbern-default',
  locale: 'de',
  category: 'LAYOUT',
  subject: null,
  htmlBody: '<html><body>HEADER{{content}}FOOTER</body></html>',
  variables: null,
  isLayout: true,
  layoutKey: null,
  isSystemTemplate: true,
  updatedAt: '2026-02-24T10:00:00Z',
};

const contentWithLayout = {
  templateKey: 'speaker-invitation',
  locale: 'de',
  category: 'SPEAKER',
  subject: 'Einladung als Referent',
  htmlBody: '<p>Hallo {{firstName}}</p>',
  variables: { firstName: 'string' },
  isLayout: false,
  layoutKey: 'batbern-default',
  isSystemTemplate: true,
  updatedAt: '2026-02-24T10:00:00Z',
};

const standaloneContent = {
  ...contentWithLayout,
  templateKey: 'standalone-template',
  layoutKey: null,
};

const renderModal = (
  template: typeof layoutTemplate | typeof contentWithLayout,
  onClose = vi.fn()
) => render(<EmailTemplatePreviewModal template={template as never} onClose={onClose} />);

describe('EmailTemplatePreviewModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders iframe for layout template with CONTENT AREA placeholder', async () => {
    renderModal(layoutTemplate);
    await waitFor(() => {
      const iframe = screen.getByTitle('Email Preview') as HTMLIFrameElement;
      expect(iframe.srcdoc).toContain('CONTENT AREA');
      expect(iframe.srcdoc).not.toContain('{{content}}');
    });
  });

  it('renders iframe merging layout with content for template with layoutKey', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.getTemplate).mockResolvedValue(layoutTemplate as never);

    renderModal(contentWithLayout);

    await waitFor(() =>
      expect(emailTemplateService.getTemplate).toHaveBeenCalledWith('batbern-default', 'de')
    );
    await waitFor(() => {
      const iframe = screen.getByTitle('Email Preview') as HTMLIFrameElement;
      expect(iframe.srcdoc).toContain('HEADER');
      expect(iframe.srcdoc).toContain('FOOTER');
    });
  });

  it('falls back to content HTML when layout fetch fails', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.getTemplate).mockRejectedValue(new Error('not found'));

    renderModal(contentWithLayout);

    await waitFor(() => {
      const iframe = screen.getByTitle('Email Preview') as HTMLIFrameElement;
      expect(iframe.srcdoc).toContain('Hallo');
    });
  });

  it('renders iframe with htmlBody directly for standalone template', async () => {
    renderModal(standaloneContent);
    await waitFor(() => {
      const iframe = screen.getByTitle('Email Preview') as HTMLIFrameElement;
      expect(iframe.srcdoc).toContain('Hallo');
    });
  });

  it('shows subject when present', () => {
    renderModal(contentWithLayout);
    expect(screen.getByText('Einladung als Referent')).toBeInTheDocument();
  });

  it('shows layoutKey badge when layoutKey present', () => {
    renderModal(contentWithLayout);
    expect(screen.getByText('Layout: batbern-default')).toBeInTheDocument();
  });

  it('calls onClose when close button clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderModal(contentWithLayout, onClose);
    // The close button is an IconButton with no text label — it's the only button in the modal
    await user.click(screen.getByRole('button'));
    expect(onClose).toHaveBeenCalled();
  });

  it('shows variables chips when template has variables', () => {
    renderModal(contentWithLayout);
    expect(screen.getByText('{{firstName}}')).toBeInTheDocument();
  });
});
