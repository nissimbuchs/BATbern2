/**
 * PairingCodeDisplay Component
 * Story W2.1: Pairing Code Backend & Web Frontend — AC1
 * Shows 6-digit pairing code with live 24h countdown timer.
 */

import React, { useState, useEffect } from 'react';
import { Alert, Box, Typography } from '@mui/material';
import type { PendingPairingCode } from '@/types/watch';

interface PairingCodeDisplayProps {
  pendingCode: PendingPairingCode;
}

function formatCountdown(expiresAt: string): string {
  const now = new Date();
  const expiry = new Date(expiresAt);
  const diffMs = expiry.getTime() - now.getTime();

  if (diffMs <= 0) return 'Expired';

  const totalMinutes = Math.floor(diffMs / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours > 0) return `Expires in ${hours}h ${minutes}m`;
  return `Expires in ${minutes}m`;
}

const PairingCodeDisplay: React.FC<PairingCodeDisplayProps> = ({ pendingCode }) => {
  const [countdown, setCountdown] = useState(() => formatCountdown(pendingCode.expiresAt));

  useEffect(() => {
    const interval = setInterval(() => {
      setCountdown(formatCountdown(pendingCode.expiresAt));
    }, 1000);
    return () => clearInterval(interval);
  }, [pendingCode.expiresAt]);

  return (
    <Alert severity="info" data-testid="pairing-code-display">
      <Typography variant="subtitle2" gutterBottom>
        Your pairing code
      </Typography>
      <Typography
        variant="h4"
        component="span"
        sx={{ fontFamily: 'monospace', letterSpacing: 4, fontWeight: 'bold' }}
        data-testid="pairing-code-value"
      >
        {pendingCode.code}
      </Typography>
      <Box sx={{ mt: 1 }}>
        <Typography variant="caption" color="text.secondary" data-testid="pairing-code-countdown">
          {countdown}
        </Typography>
      </Box>
      <Typography variant="body2" sx={{ mt: 1 }}>
        Open BATbern Watch app, swipe right, and enter this code.
      </Typography>
    </Alert>
  );
};

export default PairingCodeDisplay;
