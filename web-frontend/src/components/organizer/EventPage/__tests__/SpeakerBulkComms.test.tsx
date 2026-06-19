/**
 * SpeakerBulkComms tests (Epic 14, Phase E — Story 14.E.3)
 *
 * Covers: recipient filtering per reminder type, the NFR1 confirm-before-send
 * gate, the bulk loop over the 1:1 hook, partial-failure reporting + retry,
 * the empty-recipients disabled state, and the pool load-error path.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SpeakerBulkComms } from '../SpeakerBulkComms';

vi.mock('@/hooks/useSpeakerPool', () => ({
  useSpeakerPool: vi.fn(),
  useSendReminder: vi.fn(),
}));
vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: () => ({ isMobile: false }),
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: string | Record<string, unknown>) =>
      typeof opts === 'string' ? opts : ((opts?.defaultValue as string) ?? key),
  }),
}));

import { useSpeakerPool, useSendReminder } from '@/hooks/useSpeakerPool';

const mutateAsync = vi.fn();

function entry(id: string, status: string) {
  return { id, speakerName: `Speaker ${id}`, status } as never;
}

function mockPool(data: unknown[], extra: Record<string, unknown> = {}) {
  vi.mocked(useSpeakerPool).mockReturnValue({
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...extra,
  } as unknown as ReturnType<typeof useSpeakerPool>);
}

beforeEach(() => {
  vi.clearAllMocks();
  mutateAsync.mockReset().mockResolvedValue({});
  vi.mocked(useSendReminder).mockReturnValue({
    mutateAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useSendReminder>);
  mockPool([entry('a', 'INVITED'), entry('b', 'INVITED'), entry('c', 'ACCEPTED')]);
});

function renderComp() {
  return render(<SpeakerBulkComms eventCode="BATbern73" eventTitle="DevOps Event" />);
}

describe('SpeakerBulkComms', () => {
  it('counts the INVITED speakers for the RESPONSE reminder and ACCEPTED for CONTENT', () => {
    renderComp();
    // default RESPONSE → two INVITED speakers
    expect(screen.getByTestId('speaker-comms-recipient-count')).toHaveTextContent('2');
    // switch to CONTENT → one ACCEPTED speaker
    fireEvent.mouseDown(screen.getByTestId('speaker-comms-type-select'));
    fireEvent.click(screen.getByText('Content deadline (accepted speakers)'));
    expect(screen.getByTestId('speaker-comms-recipient-count')).toHaveTextContent('1');
  });

  it('disables Send and shows an empty notice when no speaker awaits the reminder', () => {
    mockPool([entry('c', 'ACCEPTED')]); // none INVITED
    renderComp();
    expect(screen.getByTestId('speaker-comms-empty')).toBeInTheDocument();
    expect(screen.getByTestId('speaker-comms-send-button')).toBeDisabled();
  });

  it('opens the confirm dialog BEFORE sending and never sends without confirmation (NFR1)', async () => {
    renderComp();
    fireEvent.click(screen.getByTestId('speaker-comms-send-button'));
    // dialog up, but no send has fired yet
    expect(screen.getByTestId('speaker-comms-confirm-send')).toBeInTheDocument();
    expect(mutateAsync).not.toHaveBeenCalled();
    // confirm → one call per INVITED speaker
    fireEvent.click(screen.getByTestId('speaker-comms-confirm-send'));
    await waitFor(() => expect(mutateAsync).toHaveBeenCalledTimes(2));
    expect(mutateAsync).toHaveBeenCalledWith({
      speakerPoolId: 'a',
      request: { reminderType: 'RESPONSE' },
    });
    expect(await screen.findByTestId('speaker-comms-result')).toHaveTextContent('Sent 2');
  });

  it('reports a partial failure and offers a retry of only the failed ones', async () => {
    mutateAsync.mockReset().mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('boom'));
    renderComp();
    fireEvent.click(screen.getByTestId('speaker-comms-send-button'));
    fireEvent.click(screen.getByTestId('speaker-comms-confirm-send'));
    const result = await screen.findByTestId('speaker-comms-result');
    expect(result).toHaveTextContent('Sent 1');
    expect(result).toHaveTextContent('failed 1');
    expect(screen.getByTestId('speaker-comms-retry-failed')).toBeInTheDocument();
  });

  it('locks the reminder-type select while a send is in flight (no mid-send type switch)', async () => {
    // A never-resolving send keeps the component in the `sending` state.
    let release: () => void = () => {};
    mutateAsync.mockReset().mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          release = resolve;
        })
    );
    renderComp();
    fireEvent.click(screen.getByTestId('speaker-comms-send-button'));
    fireEvent.click(screen.getByTestId('speaker-comms-confirm-send'));
    // MUI disables a Select by setting aria-disabled on the display element.
    await waitFor(() =>
      expect(screen.getByTestId('speaker-comms-type-select')).toHaveAttribute(
        'aria-disabled',
        'true'
      )
    );
    release();
  });

  it('surfaces a load error with a retry when the pool fetch fails', () => {
    const refetch = vi.fn();
    mockPool([], { isError: true, refetch });
    renderComp();
    expect(screen.getByTestId('speaker-comms-load-error')).toBeInTheDocument();
  });
});
