import React from 'react';
import {
  Box,
  Typography,
  Alert,
  FormControlLabel,
  Switch,
  Button,
  Divider,
  Stack,
} from '@mui/material';
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
  isActive?: boolean;
  autoRenewal?: boolean;
  renewalDate?: string;
}

interface PartnerSettingsTabProps {
  partner: PartnerDetail;
  currentUser: User;
  onUpdateStatus?: (active: boolean) => void;
  onUpdateAutoRenewal?: (autoRenewal: boolean) => void;
}

/**
 * PartnerSettingsTab Component
 * Organizer-only settings tab for partner configuration
 */
export const PartnerSettingsTab: React.FC<PartnerSettingsTabProps> = ({
  partner,
  currentUser,
  onUpdateStatus,
  onUpdateAutoRenewal,
}) => {
  const { t } = useTranslation('partners');
  const isOrganizer = currentUser.role === 'ORGANIZER';

  if (!isOrganizer) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" icon={<BlockIcon />}>
          <Typography variant="h6">{t('detail.settingsTab.accessDenied')}</Typography>
          <Typography variant="body2">{t('detail.settingsTab.accessDeniedMessage')}</Typography>
        </Alert>
      </Box>
    );
  }

  const renewalDateFormatted = partner.renewalDate
    ? new Date(partner.renewalDate).toLocaleDateString('en', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    : null;

  const epicDeferredTitle = 'Epic 8 feature — not yet implemented';

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        {t('detail.settingsTab.title', 'Settings')}
      </Typography>

      <Stack spacing={3} sx={{ mt: 2 }}>
        <Box>
          <Typography variant="h6" gutterBottom>
            {t('detail.settingsTab.activePartnership', 'Active Partnership')}
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={partner.isActive ?? false}
                onChange={(e) => onUpdateStatus?.(e.target.checked)}
              />
            }
            label={t('common:filters.status.active')}
          />
        </Box>

        <Divider />

        <Box>
          <Typography variant="h6" gutterBottom>
            {t('detail.settingsTab.autoRenewal', 'Auto-Renewal')}
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={partner.autoRenewal ?? false}
                onChange={(e) => onUpdateAutoRenewal?.(e.target.checked)}
              />
            }
            label="Auto-Renewal"
          />
          {renewalDateFormatted && (
            <Typography variant="body2" sx={{ mt: 1 }}>
              {t('detail.settingsTab.renewalDate', 'Renewal Date')}: {renewalDateFormatted}
            </Typography>
          )}
        </Box>

        <Divider />

        <Stack direction="row" spacing={2}>
          <Button variant="outlined" disabled title={epicDeferredTitle}>
            {t('detail.settingsTab.exportData', 'Export')}
          </Button>
          <Button variant="outlined" color="error" disabled title={epicDeferredTitle}>
            {t('detail.settingsTab.deletePartner', 'Delete')}
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
};
