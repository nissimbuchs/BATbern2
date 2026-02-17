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
import { Alert, Box, Button, CircularProgress, Typography } from '@mui/material';
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
    } catch {
      // If the user has no pairings yet this may 404 in some configs - treat as empty
      setStatus({ pairedWatches: [], pendingCode: null });
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

  const handleUnpair = async (deviceName: string) => {
    setUnpairingDevice(deviceName);
    setError(null);
    try {
      await watchPairingApi.unpairWatch(username, deviceName);
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
        <CircularProgress size={24} />
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
              key={watch.deviceName}
              watch={watch}
              onUnpair={handleUnpair}
              isUnpairing={unpairingDevice === watch.deviceName}
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

      {status?.pairedWatches.length === 0 && !status?.pendingCode && !canGenerateCode && null}
    </Box>
  );
};

export default WatchPairingSection;
