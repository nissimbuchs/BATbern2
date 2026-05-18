/**
 * PromoteSpeakerDialog Tests — Story 11.E.4 AC4
 *
 * Required-field validation behaviour for the tightened promote endpoint.
 * Backend now rejects blank firstName/lastName with 400 (@NotBlank on the DTO); the dialog
 * surfaces this at the form layer so the submit button is disabled until both populated.
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PromoteSpeakerDialog } from './PromoteSpeakerDialog';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (
      key: string,
      defOrOpts?: string | Record<string, unknown>,
      opts?: Record<string, unknown>
    ) => {
      const params = (typeof defOrOpts === 'string' ? opts : defOrOpts) ?? {};
      const fallback = typeof defOrOpts === 'string' ? defOrOpts : key;
      return Object.entries(params as Record<string, unknown>).reduce(
        (acc, [k, v]) => acc.replace(`{{${k}}}`, String(v)),
        fallback
      );
    },
  }),
}));

const promoteMutateMock = vi.fn();
const promoteMutationStub = {
  mutate: promoteMutateMock,
  isPending: false,
  isError: false,
  error: null,
};

vi.mock('@/hooks/useSpeakerPool', () => ({
  usePromoteSpeakerToReady: () => promoteMutationStub,
}));

const speaker: SpeakerPoolEntry = {
  id: 'speaker-1',
  eventId: 'event-1',
  speakerName: '', // intentionally blank to force form-driven entry
  status: 'CONTACTED',
  createdAt: '2026-05-18T00:00:00Z',
  updatedAt: '2026-05-18T00:00:00Z',
};

describe('PromoteSpeakerDialog — Story 11.E.4 AC4 required-field validation', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  const renderDialog = (overrides: Partial<SpeakerPoolEntry> = {}) =>
    render(
      <QueryClientProvider client={queryClient}>
        <PromoteSpeakerDialog
          open={true}
          eventCode="BATbern56"
          speaker={{ ...speaker, ...overrides }}
          onClose={vi.fn()}
        />
      </QueryClientProvider>
    );

  it('disables submit when firstName is empty', async () => {
    const user = userEvent.setup();
    renderDialog({ speakerName: '' });

    await user.type(screen.getByTestId('promote-email-field'), 'jane@example.com');
    // Leave firstName empty; populate lastName only
    await user.type(screen.getByTestId('promote-last-name-field'), 'Smith');

    const submit = screen.getByTestId('promote-submit-button') as HTMLButtonElement;
    await waitFor(() => expect(submit).toBeDisabled());
  });

  it('disables submit when lastName is empty', async () => {
    const user = userEvent.setup();
    renderDialog({ speakerName: '' });

    await user.type(screen.getByTestId('promote-email-field'), 'jane@example.com');
    await user.type(screen.getByTestId('promote-first-name-field'), 'Jane');
    // Leave lastName empty

    const submit = screen.getByTestId('promote-submit-button') as HTMLButtonElement;
    await waitFor(() => expect(submit).toBeDisabled());
  });

  it('disables submit when both firstName and lastName are empty', async () => {
    const user = userEvent.setup();
    renderDialog({ speakerName: '' });

    await user.type(screen.getByTestId('promote-email-field'), 'jane@example.com');

    const submit = screen.getByTestId('promote-submit-button') as HTMLButtonElement;
    await waitFor(() => expect(submit).toBeDisabled());
  });

  it('shows helper-text error after user blurs empty firstName field', async () => {
    const user = userEvent.setup();
    renderDialog({ speakerName: '' });

    const first = screen.getByTestId('promote-first-name-field');
    await user.click(first);
    await user.tab();

    await waitFor(() => {
      expect(screen.getByText('First name is required')).toBeInTheDocument();
    });
  });

  it('shows helper-text error after user blurs empty lastName field', async () => {
    const user = userEvent.setup();
    renderDialog({ speakerName: '' });

    const last = screen.getByTestId('promote-last-name-field');
    await user.click(last);
    await user.tab();

    await waitFor(() => {
      expect(screen.getByText('Last name is required')).toBeInTheDocument();
    });
  });

  it('fires mutation with { email, firstName, lastName } when all three fields are populated', async () => {
    const user = userEvent.setup();
    renderDialog({ speakerName: '' });

    await user.type(screen.getByTestId('promote-email-field'), 'jane@example.com');
    await user.type(screen.getByTestId('promote-first-name-field'), 'Jane');
    await user.type(screen.getByTestId('promote-last-name-field'), 'Smith');

    const submit = screen.getByTestId('promote-submit-button') as HTMLButtonElement;
    await waitFor(() => expect(submit).not.toBeDisabled());

    await user.click(submit);

    await waitFor(() => {
      expect(promoteMutateMock).toHaveBeenCalledTimes(1);
    });
    expect(promoteMutateMock).toHaveBeenCalledWith(
      {
        eventCode: 'BATbern56',
        speakerId: 'speaker-1',
        request: {
          email: 'jane@example.com',
          firstName: 'Jane',
          lastName: 'Smith',
        },
      },
      expect.any(Object)
    );
  });

  it('trims whitespace and pre-fills the form from speakerName for happy path', async () => {
    const user = userEvent.setup();
    renderDialog({ speakerName: 'Jane Smith' });

    // firstName + lastName should be pre-filled by splitFullName
    expect((screen.getByTestId('promote-first-name-field') as HTMLInputElement).value).toBe('Jane');
    expect((screen.getByTestId('promote-last-name-field') as HTMLInputElement).value).toBe('Smith');

    await user.type(screen.getByTestId('promote-email-field'), 'jane@example.com');

    const submit = screen.getByTestId('promote-submit-button') as HTMLButtonElement;
    await waitFor(() => expect(submit).not.toBeDisabled());

    await user.click(submit);

    await waitFor(() => expect(promoteMutateMock).toHaveBeenCalledTimes(1));
    expect(promoteMutateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        request: expect.objectContaining({
          email: 'jane@example.com',
          firstName: 'Jane',
          lastName: 'Smith',
        }),
      }),
      expect.any(Object)
    );
  });
});
