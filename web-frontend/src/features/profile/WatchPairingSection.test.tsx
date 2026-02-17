/**
 * WatchPairingSection Tests
 * Story W2.1: Pairing Code Backend & Web Frontend
 * AC1: Generate pairing code, AC2: Max watches error, AC4: Unpair
 */

import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import WatchPairingSection from './WatchPairingSection';
import type { PairingStatusResponse } from '@/types/watch';

// Mock watchPairingApi
const mockGetPairingStatus = vi.fn();
const mockGeneratePairingCode = vi.fn();
const mockUnpairWatch = vi.fn();

vi.mock('@/services/api/watchPairingApi', () => ({
  default: {
    getPairingStatus: (...args: unknown[]) => mockGetPairingStatus(...args),
    generatePairingCode: (...args: unknown[]) => mockGeneratePairingCode(...args),
    unpairWatch: (...args: unknown[]) => mockUnpairWatch(...args),
  },
}));

const emptyStatus: PairingStatusResponse = { pairedWatches: [], pendingCode: null };

describe('WatchPairingSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows Pair Apple Watch button when no watches paired', async () => {
    mockGetPairingStatus.mockResolvedValue(emptyStatus);

    render(<WatchPairingSection username="john.doe" />);

    await waitFor(() => {
      expect(screen.getByTestId('pair-watch-button')).toBeTruthy();
    });
  });

  it('hides Pair button when 2 watches are already paired', async () => {
    const twoWatches: PairingStatusResponse = {
      pairedWatches: [
        { deviceName: 'Watch 1', pairedAt: '2026-01-01T10:00:00Z' },
        { deviceName: 'Watch 2', pairedAt: '2026-01-02T10:00:00Z' },
      ],
      pendingCode: null,
    };
    mockGetPairingStatus.mockResolvedValue(twoWatches);

    render(<WatchPairingSection username="john.doe" />);

    await waitFor(() => {
      expect(screen.queryByTestId('pair-watch-button')).toBeNull();
    });
    expect(screen.getByTestId('paired-watch-card-Watch 1')).toBeTruthy();
    expect(screen.getByTestId('paired-watch-card-Watch 2')).toBeTruthy();
  });

  it('shows pending code when one exists', async () => {
    const withCode: PairingStatusResponse = {
      pairedWatches: [],
      pendingCode: { code: '123456', expiresAt: new Date(Date.now() + 3600000).toISOString() },
    };
    mockGetPairingStatus.mockResolvedValue(withCode);

    render(<WatchPairingSection username="john.doe" />);

    await waitFor(() => {
      expect(screen.getByTestId('pairing-code-value')).toBeTruthy();
    });
    expect(screen.getByTestId('pairing-code-value').textContent).toBe('123456');
  });

  it('calls generatePairingCode API and refreshes status on button click', async () => {
    mockGetPairingStatus.mockResolvedValue(emptyStatus);
    mockGeneratePairingCode.mockResolvedValue({
      pairingCode: '654321',
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
      hoursUntilExpiry: 24,
    });
    // After generate, status shows pending code
    const afterGenerate: PairingStatusResponse = {
      pairedWatches: [],
      pendingCode: { code: '654321', expiresAt: new Date(Date.now() + 86400000).toISOString() },
    };
    mockGetPairingStatus.mockResolvedValueOnce(emptyStatus).mockResolvedValueOnce(afterGenerate);

    render(<WatchPairingSection username="john.doe" />);

    const btn = await screen.findByTestId('pair-watch-button');
    fireEvent.click(btn);

    await waitFor(() => {
      expect(mockGeneratePairingCode).toHaveBeenCalledWith('john.doe');
    });
  });

  it('shows max watches error message on 409 response', async () => {
    mockGetPairingStatus.mockResolvedValue(emptyStatus);
    mockGeneratePairingCode.mockRejectedValue({ response: { status: 409 } });

    render(<WatchPairingSection username="john.doe" />);

    const btn = await screen.findByTestId('pair-watch-button');
    fireEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByTestId('watch-pairing-error')).toBeTruthy();
    });
    expect(screen.getByTestId('watch-pairing-error').textContent).toContain(
      'Maximum 2 watches paired'
    );
  });

  it('calls unpairWatch API after two-step confirmation', async () => {
    const withWatch: PairingStatusResponse = {
      pairedWatches: [{ deviceName: 'my-watch', pairedAt: '2026-01-01T10:00:00Z' }],
      pendingCode: null,
    };
    mockGetPairingStatus.mockResolvedValueOnce(withWatch).mockResolvedValueOnce(emptyStatus);
    mockUnpairWatch.mockResolvedValue(undefined);

    render(<WatchPairingSection username="john.doe" />);

    // Step 1: click Unpair → shows confirmation buttons
    const unpairBtn = await screen.findByTestId('unpair-button-my-watch');
    fireEvent.click(unpairBtn);

    // Step 2: confirmation button appears
    const confirmBtn = await screen.findByTestId('unpair-confirm-button-my-watch');
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mockUnpairWatch).toHaveBeenCalledWith('john.doe', 'my-watch');
    });
  });

  it('cancels unpair when Cancel is clicked in confirmation', async () => {
    const withWatch: PairingStatusResponse = {
      pairedWatches: [{ deviceName: 'my-watch', pairedAt: '2026-01-01T10:00:00Z' }],
      pendingCode: null,
    };
    mockGetPairingStatus.mockResolvedValueOnce(withWatch);
    mockUnpairWatch.mockResolvedValue(undefined);

    render(<WatchPairingSection username="john.doe" />);

    const unpairBtn = await screen.findByTestId('unpair-button-my-watch');
    fireEvent.click(unpairBtn);

    const cancelBtn = await screen.findByTestId('unpair-cancel-button-my-watch');
    fireEvent.click(cancelBtn);

    // Original unpair button is back, API not called
    await waitFor(() => {
      expect(screen.getByTestId('unpair-button-my-watch')).toBeTruthy();
    });
    expect(mockUnpairWatch).not.toHaveBeenCalled();
  });

  it('shows countdown text when pending code exists', async () => {
    const expiresAt = new Date(Date.now() + 3600000).toISOString(); // 1 hour from now
    const withCode: PairingStatusResponse = {
      pairedWatches: [],
      pendingCode: { code: '123456', expiresAt },
    };
    mockGetPairingStatus.mockResolvedValue(withCode);

    render(<WatchPairingSection username="john.doe" />);

    await waitFor(() => {
      expect(screen.getByTestId('pairing-code-countdown')).toBeTruthy();
    });
    expect(screen.getByTestId('pairing-code-countdown').textContent).toContain('Expires in');
  });

  it('shows load error when getPairingStatus returns a non-404 error', async () => {
    mockGetPairingStatus.mockRejectedValue({ response: { status: 500 } });

    render(<WatchPairingSection username="john.doe" />);

    await waitFor(() => {
      expect(screen.getByTestId('watch-pairing-error')).toBeTruthy();
    });
    expect(screen.getByTestId('watch-pairing-error').textContent).toContain('Failed to load');
  });
});
