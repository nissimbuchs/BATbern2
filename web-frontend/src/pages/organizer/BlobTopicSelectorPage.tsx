/**
 * Blob Topic Selector Page
 * Story 10.4: Blob Topic Selector (Task 5)
 *
 * Full-screen page WITHOUT AuthLayout (no sidebar, no nav).
 * Route: /organizer/events/:eventCode/topic-blob
 */

import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Box,
  Button,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import { BATbernLoader } from '@/components/shared/BATbernLoader';
import { ArrowBack } from '@mui/icons-material';
import { useTopicSessionData } from '@/components/BlobTopicSelector/useTopicSessionData';
import BlobTopicSelector from '@/components/BlobTopicSelector/BlobTopicSelector';
import OnboardingOverlay, {
  ONBOARDING_KEY,
} from '@/components/BlobTopicSelector/OnboardingOverlay';

const BlobTopicSelectorPage: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation('organizer');

  const [showBackDialog, setShowBackDialog] = useState(false);
  const [showOnboarding, setShowOnboarding] = useState(() => !localStorage.getItem(ONBOARDING_KEY));

  const { data: sessionData, isLoading, error } = useTopicSessionData(eventCode ?? '');

  const handleBackClick = () => {
    setShowBackDialog(true);
  };

  const handleBackConfirm = () => {
    setShowBackDialog(false);
    navigate(`/organizer/topics?eventCode=${eventCode ?? ''}`);
  };

  const handleBackCancel = () => {
    setShowBackDialog(false);
  };

  const handleOnboardingComplete = () => {
    setShowOnboarding(false);
  };

  if (!eventCode) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          bgcolor: '#0d1b2a',
          color: 'white',
        }}
      >
        <Typography>No event code provided</Typography>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        width: '100vw',
        height: '100vh',
        overflow: 'hidden',
        bgcolor: '#0d1b2a',
        position: 'relative',
      }}
    >
      {/* Fixed back button — top-left, above canvas (AC: 4) */}
      <Button
        data-testid="back-to-topics"
        startIcon={<ArrowBack />}
        onClick={handleBackClick}
        sx={{
          position: 'fixed',
          top: 16,
          left: 16,
          zIndex: 1000,
          color: 'rgba(255,255,255,0.85)',
          bgcolor: 'rgba(13,27,42,0.8)',
          border: '1px solid rgba(255,255,255,0.2)',
          '&:hover': { bgcolor: 'rgba(255,255,255,0.1)' },
        }}
      >
        {t('blobSelector.backButton', { defaultValue: '← Back to Topic List' })}
      </Button>

      {/* Loading state */}
      {isLoading && (
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            height: '100vh',
          }}
        >
          <BATbernLoader size={80} />
        </Box>
      )}

      {/* Error state */}
      {error && !isLoading && (
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            height: '100vh',
            flexDirection: 'column',
            gap: 2,
          }}
        >
          <Typography color="error">Failed to load session data</Typography>
          <Button onClick={() => navigate(-1)} variant="outlined" sx={{ color: 'white' }}>
            Go Back
          </Button>
        </Box>
      )}

      {/* Main canvas — only shown when data is ready */}
      {sessionData && !isLoading && (
        <BlobTopicSelector eventCode={eventCode} sessionData={sessionData} />
      )}

      {/* Onboarding overlay (AC: 28) */}
      {showOnboarding && sessionData && <OnboardingOverlay onComplete={handleOnboardingComplete} />}

      {/* Unsaved changes warning dialog (AC: 4) */}
      <Dialog open={showBackDialog} onClose={handleBackCancel} maxWidth="xs">
        <DialogTitle>
          {t('blobSelector.unsavedWarning', {
            defaultValue: 'Unsaved session — all changes will be lost. Go back?',
          })}
        </DialogTitle>
        <DialogContent />
        <DialogActions>
          <Button onClick={handleBackCancel}>
            {t('common.cancel', { defaultValue: 'Cancel', ns: 'events' })}
          </Button>
          <Button onClick={handleBackConfirm} variant="contained" color="primary">
            {t('common.confirm', { defaultValue: 'Go back', ns: 'events' })}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default BlobTopicSelectorPage;
