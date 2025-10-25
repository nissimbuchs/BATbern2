/**
 * ReleaseNotesInfoBox Component
 * Displays release notes from a static text file on login screen
 * Only visible in development and staging environments
 */

import React, { useState, useEffect } from 'react';
import { Alert, AlertTitle, Box, CircularProgress, Collapse, IconButton } from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useEnvironment } from '@/contexts/useEnvironment';

export const ReleaseNotesInfoBox: React.FC = () => {
  const environment = useEnvironment();
  const [notes, setNotes] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<boolean>(false);
  const [dismissed, setDismissed] = useState<boolean>(false);

  // Only show in development and staging
  const shouldShow = environment === 'development' || environment === 'staging';

  useEffect(() => {
    if (!shouldShow) {
      setLoading(false);
      return;
    }

    const fetchReleaseNotes = async () => {
      try {
        const response = await fetch('/release-notes.txt');

        if (!response.ok) {
          // File doesn't exist or fetch failed - not an error, just no notes
          setLoading(false);
          return;
        }

        const text = await response.text();

        // Only set notes if there's actual content (not empty or whitespace)
        if (text.trim()) {
          setNotes(text.trim());
        }

        setLoading(false);
      } catch (err) {
        console.warn('[ReleaseNotesInfoBox] Failed to fetch release notes:', err);
        setError(true);
        setLoading(false);
      }
    };

    fetchReleaseNotes();
  }, [shouldShow]);

  // Don't render anything if:
  // - Not in dev/staging environment
  // - Still loading
  // - Error occurred
  // - No notes content
  // - User dismissed the box
  if (!shouldShow || loading || error || !notes || dismissed) {
    return null;
  }

  return (
    <Collapse in={!dismissed}>
      <Box sx={{ mb: 2 }}>
        <Alert
          severity="info"
          action={
            <IconButton
              aria-label="close"
              color="inherit"
              size="small"
              onClick={() => setDismissed(true)}
            >
              <CloseIcon fontSize="inherit" />
            </IconButton>
          }
          sx={{
            '& .MuiAlert-message': {
              width: '100%',
            },
          }}
        >
          <AlertTitle sx={{ fontWeight: 'bold' }}>Release Notes</AlertTitle>
          <Box
            component="pre"
            sx={{
              fontFamily: 'inherit',
              fontSize: 'inherit',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              margin: 0,
              marginTop: 1,
            }}
          >
            {notes}
          </Box>
        </Alert>
      </Box>
    </Collapse>
  );
};
