import React from 'react';
import { Box, Typography, Alert } from '@mui/material';
import { Block as BlockIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

interface User {
  username: string;
  role: 'ORGANIZER' | 'PARTNER' | 'SPEAKER' | 'ATTENDEE';
}

interface PartnerDetail {
  companyName: string; // ADR-003: companyName is the meaningful ID
  partnershipLevel: 'STRATEGIC' | 'PLATINUM' | 'GOLD' | 'SILVER' | 'BRONZE';
  partnershipStartDate: string;
}

interface PartnerSettingsTabProps {
  partner: PartnerDetail;
  currentUser: User;
}

/**
 * PartnerSettingsTab Component
 * Organizer-only settings tab for partner configuration
 */
export const PartnerSettingsTab: React.FC<PartnerSettingsTabProps> = ({ currentUser }) => {
  const { t } = useTranslation('partners');
  const isOrganizer = currentUser.role === 'ORGANIZER';

  if (!isOrganizer) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" icon={<BlockIcon />}>
          <Typography variant="h6">Access Denied</Typography>
          <Typography variant="body2">This section is only accessible to organizers.</Typography>
        </Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        {t('detail.settingsTab.title')}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Configure partnership status and preferences
      </Typography>
    </Box>
  );
};
