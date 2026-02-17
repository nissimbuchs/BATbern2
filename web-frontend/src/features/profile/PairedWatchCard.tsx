/**
 * PairedWatchCard Component
 * Story W2.1: Pairing Code Backend & Web Frontend — AC4
 * Displays a paired watch with device name, paired date, and Unpair button.
 */

import React, { useState } from 'react';
import { Box, Button, Typography } from '@mui/material';
import WatchIcon from '@mui/icons-material/Watch';
import type { PairedWatch } from '@/types/watch';

interface PairedWatchCardProps {
  watch: PairedWatch;
  onUnpair: (deviceName: string) => void;
  isUnpairing?: boolean;
}

const PairedWatchCard: React.FC<PairedWatchCardProps> = ({ watch, onUnpair, isUnpairing }) => {
  const [confirmPending, setConfirmPending] = useState(false);
  const formattedDate = new Date(watch.pairedAt).toLocaleDateString('en-GB', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        p: 1.5,
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        mb: 1,
      }}
      data-testid={`paired-watch-card-${watch.deviceName}`}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <WatchIcon color="action" />
        <Box>
          <Typography variant="body2" fontWeight="medium" data-testid="watch-device-name">
            {watch.deviceName || 'Apple Watch'}
          </Typography>
          <Typography variant="caption" color="text.secondary" data-testid="watch-paired-date">
            Paired {formattedDate}
          </Typography>
        </Box>
      </Box>
      {confirmPending ? (
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            size="small"
            variant="contained"
            color="error"
            disabled={isUnpairing}
            onClick={() => {
              setConfirmPending(false);
              onUnpair(watch.deviceName);
            }}
            data-testid={`unpair-confirm-button-${watch.deviceName}`}
          >
            Confirm
          </Button>
          <Button
            size="small"
            variant="outlined"
            onClick={() => setConfirmPending(false)}
            data-testid={`unpair-cancel-button-${watch.deviceName}`}
          >
            Cancel
          </Button>
        </Box>
      ) : (
        <Button
          size="small"
          variant="outlined"
          color="error"
          disabled={isUnpairing}
          onClick={() => setConfirmPending(true)}
          data-testid={`unpair-button-${watch.deviceName}`}
        >
          Unpair
        </Button>
      )}
    </Box>
  );
};

export default PairedWatchCard;
