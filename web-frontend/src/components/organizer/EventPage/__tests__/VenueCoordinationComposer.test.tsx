/**
 * VenueCoordinationComposer tests
 *
 * Covers the four user-facing flows:
 *   A. Unconfigured state → warning + admin link shown, controls disabled.
 *   B. Configured state → template selector, recipient checkboxes, notes + preview/send visible.
 *   C. Preview flow → calls service, renders subject/To/Cc/Reply-To + iframe.
 *   D. Send flow → opens confirm dialog, fires send mutation on confirm.
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { VenueCoordinationComposer } from '../VenueCoordinationComposer';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    // Handle both t('key', 'fallback string') and t('key', { defaultValue: 'fallback', ... })
    t: (key: string, optsOrDefault?: unknown) => {
      if (typeof optsOrDefault === 'string') return optsOrDefault;
      if (optsOrDefault && typeof optsOrDefault === 'object' && 'defaultValue' in optsOrDefault) {
        return (optsOrDefault as Record<string, unknown>).defaultValue as string;
      }
      return key;
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}));

const mockGetAdminSetting = vi.fn();
vi.mock('@/services/adminSettingsService', () => ({
  getAdminSetting: (...args: unknown[]) => mockGetAdminSetting(...args),
  updateAdminSetting: vi.fn(),
}));

const mockListTemplates = vi.fn();
vi.mock('@/services/emailTemplateService', () => ({
  emailTemplateService: { listTemplates: (...args: unknown[]) => mockListTemplates(...args) },
}));

const mockPreview = vi.fn();
const mockSend = vi.fn();
vi.mock('@/services/venueCoordinationService', () => ({
  venueCoordinationService: {
    preview: (...args: unknown[]) => mockPreview(...args),
    send: (...args: unknown[]) => mockSend(...args),
  },
}));

// ── Helpers ───────────────────────────────────────────────────────────────────

const FULL_CONFIG = JSON.stringify({
  venue: { salutation: 'Frau', name: 'Gabriela Senn', email: 'venue@test.invalid' },
  catering: { salutation: 'Herr', name: 'Stefan Oppliger', email: 'catering@test.invalid' },
  coordinatorUsername: 'nissim.buchs',
});

const DE_TEMPLATES = [
  { templateKey: 'venue-timetable', locale: 'de', category: 'VENUE_COORDINATION', isLayout: false },
  {
    templateKey: 'catering-offerte-request',
    locale: 'de',
    category: 'VENUE_COORDINATION',
    isLayout: false,
  },
];

const createQC = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

const renderComposer = (eventCode = 'BATtest59') =>
  render(
    <QueryClientProvider client={createQC()}>
      <BrowserRouter>
        <VenueCoordinationComposer eventCode={eventCode} />
      </BrowserRouter>
    </QueryClientProvider>
  );

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('VenueCoordinationComposer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: fully configured + templates available
    mockGetAdminSetting.mockResolvedValue({ key: 'venue.coordination.config', value: FULL_CONFIG });
    mockListTemplates.mockResolvedValue(DE_TEMPLATES);
    mockPreview.mockResolvedValue({
      subject: 'BATtest Zeitplan',
      htmlBody: '<p>Guten Tag Frau Senn</p>',
      toEmail: 'venue@test.invalid',
      ccEmails: [],
      replyToEmail: 'nissim.buchs@elca.ch',
    });
    mockSend.mockResolvedValue({ sentTo: ['VENUE'] });
  });

  // ── A. Unconfigured state ─────────────────────────────────────────────────

  it('shows warning and admin link when config is missing', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: 'venue.coordination.config', value: null });

    renderComposer();

    await waitFor(() => {
      expect(screen.getByText(/contacts are not configured/i)).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: /administration/i })).toBeInTheDocument();
  });

  it('disables the preview button when not configured', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: 'venue.coordination.config', value: null });

    renderComposer();

    await waitFor(() => {
      const preview = screen.getByRole('button', { name: /preview/i });
      expect(preview).toBeDisabled();
    });
  });

  // ── B. Configured state ───────────────────────────────────────────────────

  it('renders template selector and recipient checkboxes when configured', async () => {
    renderComposer();

    await waitFor(() => {
      // Template combobox loaded (accessible label comes from InputLabel)
      expect(screen.getByRole('combobox')).toBeInTheDocument();
      // Both recipient checkboxes present with contact info
      expect(screen.getByTestId('venue-coord-send-venue')).toBeInTheDocument();
      expect(screen.getByTestId('venue-coord-send-catering')).toBeInTheDocument();
    });

    // Contact labels show name + email
    expect(screen.getByText(/Gabriela Senn/)).toBeInTheDocument();
    expect(screen.getByText(/Stefan Oppliger/)).toBeInTheDocument();
  });

  it('enables preview button once template and config are loaded', async () => {
    renderComposer();

    await waitFor(() => {
      const preview = screen.getByRole('button', { name: /preview/i });
      expect(preview).not.toBeDisabled();
    });
  });

  // ── C. Preview flow ───────────────────────────────────────────────────────

  it('calls preview service with selected recipients and shows the result', async () => {
    const user = userEvent.setup();
    renderComposer();

    // Wait for both config + template to load so the button becomes enabled
    const previewBtn = await screen.findByRole('button', { name: /preview/i });
    await waitFor(() => expect(previewBtn).not.toBeDisabled(), { timeout: 3000 });

    await user.click(previewBtn);

    await waitFor(() => {
      expect(mockPreview).toHaveBeenCalledWith(
        'BATtest59',
        expect.objectContaining({
          templateKey: 'venue-timetable',
          recipients: expect.arrayContaining(['VENUE']),
          locale: 'de',
        })
      );
    });

    // Preview pane: subject contains "BATtest Zeitplan" (rendered inside "Subject: BATtest Zeitplan")
    await waitFor(
      () => {
        expect(screen.getByText(/BATtest Zeitplan/)).toBeInTheDocument();
      },
      { timeout: 3000 }
    );
    expect(screen.getByTestId('venue-coord-preview-iframe')).toBeInTheDocument();
  });

  it('shows Cc line when both recipients are selected', async () => {
    const user = userEvent.setup();
    mockPreview.mockResolvedValue({
      subject: 'BATtest Zeitplan',
      htmlBody: '<p>Guten Tag</p>',
      toEmail: 'venue@test.invalid',
      ccEmails: ['catering@test.invalid'],
      replyToEmail: 'nissim.buchs@elca.ch',
    });

    renderComposer();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /preview/i })).not.toBeDisabled()
    );

    await user.click(screen.getByRole('button', { name: /preview/i }));

    await waitFor(() => {
      // "Cc:" is rendered as t('...cc', 'Cc') + ":" in separate nodes — match the container text
      const ccElements = screen.getAllByText((_, el) => el?.textContent?.includes('Cc:') === true);
      expect(ccElements.length).toBeGreaterThan(0);
      expect(screen.getAllByText(/catering@test\.invalid/).length).toBeGreaterThanOrEqual(1);
    });
  });

  // ── D. Send flow ──────────────────────────────────────────────────────────

  it('opens confirm dialog when send button is clicked', async () => {
    const user = userEvent.setup();
    renderComposer();

    await waitFor(() => expect(screen.getByTestId('venue-coord-send-button')).not.toBeDisabled());

    await user.click(screen.getByTestId('venue-coord-send-button'));

    await waitFor(() => {
      // MUI Dialog renders in a portal — the button text is "Yes, send" from the defaultValue fallback
      expect(screen.getByRole('button', { name: 'Yes, send' })).toBeInTheDocument();
    });
  });

  it('calls send service with the correct recipients on confirm', async () => {
    const user = userEvent.setup();
    renderComposer();

    await waitFor(() => expect(screen.getByTestId('venue-coord-send-button')).not.toBeDisabled());

    await user.click(screen.getByTestId('venue-coord-send-button'));
    await waitFor(() => screen.getByRole('button', { name: 'Yes, send' }));
    await user.click(screen.getByRole('button', { name: 'Yes, send' }));

    await waitFor(() => {
      expect(mockSend).toHaveBeenCalledWith(
        'BATtest59',
        expect.objectContaining({
          recipients: expect.arrayContaining(['VENUE', 'CATERING']),
        })
      );
    });
  });

  it('unchecking both recipients disables the send button', async () => {
    const user = userEvent.setup();
    renderComposer();

    await waitFor(() => expect(screen.getByTestId('venue-coord-send-venue')).toBeInTheDocument());

    await user.click(screen.getByTestId('venue-coord-send-venue'));
    await user.click(screen.getByTestId('venue-coord-send-catering'));

    expect(screen.getByTestId('venue-coord-send-button')).toBeDisabled();
  });
});
