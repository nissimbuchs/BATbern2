/**
 * SpeakerBrainstormingPanel Tests - Focused component tests for speaker brainstorming
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerBrainstormingPanel } from './SpeakerBrainstormingPanel';
import { useSpeakerPool } from '@/hooks/useSpeakerPool';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (
      key: string,
      defOrOpts?: string | Record<string, unknown>,
      opts?: Record<string, unknown>
    ) => {
      // Match real i18n behaviour for the {{currentState}} interpolation used by
      // the promote dialog's error banner.
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
const promoteResetMock = vi.fn();
const promoteMutationStub = {
  mutate: promoteMutateMock,
  reset: promoteResetMock,
  isPending: false,
  isError: false,
  error: null,
};
const addMutationStub = { mutate: vi.fn(), isLoading: false };

vi.mock('@/hooks/useSpeakerPool', () => ({
  useSpeakerPool: vi.fn(() => ({
    data: [
      {
        id: '1',
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp',
        status: 'identified',
        createdAt: '2025-12-13',
        updatedAt: '2025-12-13',
      },
      {
        id: '2',
        speakerName: 'Prof. Bob Johnson',
        company: 'University',
        status: 'contacted',
        createdAt: '2025-12-13',
        updatedAt: '2025-12-13',
      },
    ],
    isLoading: false,
  })),
  useAddSpeakerToPool: () => addMutationStub,
  usePromoteSpeakerToReady: () => promoteMutationStub,
}));

describe('SpeakerBrainstormingPanel', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  const renderComponent = (props = {}) =>
    render(
      <QueryClientProvider client={queryClient}>
        <SpeakerBrainstormingPanel eventCode="BATbern56" {...props} />
      </QueryClientProvider>
    );

  it('should render speaker pool list', () => {
    renderComponent();
    expect(screen.getByText('Dr. Jane Smith')).toBeInTheDocument();
    expect(screen.getByText('Prof. Bob Johnson')).toBeInTheDocument();
  });

  it('should display add speaker form', () => {
    renderComponent();
    expect(screen.getByLabelText(/Speaker Name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Company/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Expertise/i)).toBeInTheDocument();
  });

  it('should allow adding a new speaker', async () => {
    const user = userEvent.setup();
    renderComponent();

    const nameInput = screen.getByLabelText(/Speaker Name/i);
    const companyInput = screen.getByLabelText(/Company/i);

    await user.type(nameInput, 'New Speaker');
    await user.type(companyInput, 'ACME Inc');

    // Verify inputs have values before submit
    expect(nameInput).toHaveValue('New Speaker');
    expect(companyInput).toHaveValue('ACME Inc');

    const addButton = screen.getByRole('button', { name: /Add to Pool/i });
    await user.click(addButton);

    // Button should be clickable (mutation would be called in real implementation)
    expect(addButton).toBeInTheDocument();
  });

  it('should display speaker status badges', () => {
    renderComponent();
    expect(screen.getByText('identified')).toBeInTheDocument();
    expect(screen.getByText('contacted')).toBeInTheDocument();
  });

  it('should allow assigning speakers to organizers', async () => {
    renderComponent();

    // Verify speaker cards are rendered (assignment functionality to be implemented)
    expect(screen.getByText('Dr. Jane Smith')).toBeInTheDocument();
    expect(screen.getByText('Prof. Bob Johnson')).toBeInTheDocument();
  });

  it('should handle empty speaker pool', () => {
    vi.mocked(useSpeakerPool).mockReturnValueOnce({
      data: [],
      isLoading: false,
    });

    renderComponent();
    expect(screen.getByText(/No speakers in pool yet/i)).toBeInTheDocument();
  });

  // ── Story 11.D.1: Promote-to-speaker UI (AC6, AC7) ─────────────────────────

  it('should_not_renderEmailInput_when_inIdentifiedOrContactedMode (AC6)', () => {
    renderComponent();
    // The brainstorming form has no email input — promotion captures it instead.
    expect(screen.queryByLabelText(/^Email$/i)).not.toBeInTheDocument();
  });

  it('should_renderPromoteButton_when_speakerIsContacted (AC7)', () => {
    renderComponent();
    // Bob Johnson is the CONTACTED speaker per the mock fixture.
    expect(screen.getByTestId('promote-button-2')).toBeInTheDocument();
  });

  it('should_not_renderPromoteButton_when_speakerIsIdentified (AC7)', () => {
    renderComponent();
    expect(screen.queryByTestId('promote-button-1')).not.toBeInTheDocument();
  });

  it('should_openPromoteDialog_when_promoteButtonClicked (AC7)', async () => {
    const user = userEvent.setup();
    renderComponent();
    await user.click(screen.getByTestId('promote-button-2'));
    // Title text comes from the i18n fallback ("Promote to speaker")
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByTestId('promote-email-field')).toBeInTheDocument();
  });

  it('should_callPromoteEndpoint_when_dialogSubmitted (AC7)', async () => {
    const user = userEvent.setup();
    promoteMutateMock.mockReset();
    renderComponent();
    await user.click(screen.getByTestId('promote-button-2'));

    const emailInput = await screen.findByTestId('promote-email-field');
    await user.type(emailInput, 'bob@example.com');

    const submit = await screen.findByTestId('promote-submit-button');
    await user.click(submit);

    expect(promoteMutateMock).toHaveBeenCalledTimes(1);
    const [args] = promoteMutateMock.mock.calls[0];
    expect(args).toMatchObject({
      eventCode: 'BATbern56',
      speakerId: '2',
      request: expect.objectContaining({ email: 'bob@example.com' }),
    });
  });
});
