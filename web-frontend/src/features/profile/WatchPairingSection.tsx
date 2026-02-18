/**
 * WatchPairingSection Component
 * Story W2.1: Pairing Code Backend & Web Frontend
 *
 * Allows organizers to:
 * - Generate a pairing code (AC1) — visible when <2 watches paired
 * - See paired watches with Unpair button (AC4)
 * - View pending code with countdown (AC1, AC3)
 * - See error when max watches reached (AC2)
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Alert, Box, Button, Typography } from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import WatchIcon from '@mui/icons-material/Watch';
import PairingCodeDisplay from './PairingCodeDisplay';
import PairedWatchCard from './PairedWatchCard';
import watchPairingApi from '@/services/api/watchPairingApi';
import type { PairingStatusResponse } from '@/types/watch';

interface WatchPairingSectionProps {
  username: string;
}

const WatchPairingSection: React.FC<WatchPairingSectionProps> = ({ username }) => {
  const [status, setStatus] = useState<PairingStatusResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isGenerating, setIsGenerating] = useState(false);
  const [unpairingDevice, setUnpairingDevice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadStatus = useCallback(async () => {
    try {
      const data = await watchPairingApi.getPairingStatus(username);
      setStatus(data);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number } };
      if (axiosErr?.response?.status === 404) {
        // No pairings yet — treat as empty
        setStatus({ pairedWatches: [], pendingCode: null });
      } else {
        // Real error (network failure, 500, etc.) — surface it to the user
        setError('Failed to load pairing status. Please refresh the page.');
        setStatus({ pairedWatches: [], pendingCode: null });
      }
    } finally {
      setIsLoading(false);
    }
  }, [username]);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  const handleGenerateCode = async () => {
    setIsGenerating(true);
    setError(null);
    try {
      await watchPairingApi.generatePairingCode(username);
      await loadStatus();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number } };
      if (axiosErr?.response?.status === 409) {
        setError('Maximum 2 watches paired. Unpair a device first.');
      } else {
        setError('Failed to generate pairing code. Please try again.');
      }
    } finally {
      setIsGenerating(false);
    }
  };

  const handleUnpair = async (watchId: string) => {
    setUnpairingDevice(watchId);
    setError(null);
    try {
      await watchPairingApi.unpairWatch(username, watchId);
      await loadStatus();
    } catch {
      setError('Failed to unpair device. Please try again.');
    } finally {
      setUnpairingDevice(null);
    }
  };

  const canGenerateCode = status !== null && !status.pendingCode && status.pairedWatches.length < 2;

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}>
        <BATbernLoader size={48} />
      </Box>
    );
  }

  return (
    <Box data-testid="watch-pairing-section">
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <WatchIcon />
        <Typography variant="h6">Apple Watch Pairing</Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} data-testid="watch-pairing-error">
          {error}
        </Alert>
      )}

      {status?.pendingCode && (
        <Box sx={{ mb: 2 }}>
          <PairingCodeDisplay pendingCode={status.pendingCode} />
        </Box>
      )}

      {status?.pairedWatches && status.pairedWatches.length > 0 && (
        <Box sx={{ mb: 2 }}>
          {status.pairedWatches.map((watch) => (
            <PairedWatchCard
              key={watch.id}
              watch={watch}
              onUnpair={handleUnpair}
              isUnpairing={unpairingDevice === watch.id}
            />
          ))}
        </Box>
      )}

      {canGenerateCode && (
        <Button
          variant="outlined"
          startIcon={<WatchIcon />}
          onClick={handleGenerateCode}
          disabled={isGenerating}
          data-testid="pair-watch-button"
        >
          {isGenerating ? 'Generating...' : 'Pair Apple Watch'}
        </Button>
      )}
    </Box>
  );
};

export default WatchPairingSection;
