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
}

interface PartnerSettingsTabProps {
  partner: PartnerDetail;
  currentUser: User;
  /**
   * Called when the organizer flips the Active switch. Required in practice — see the
   * comment on the switch below; this stayed optional and unpassed, which is exactly how
   * the control became a silent no-op (issue #821).
   */
  onUpdateStatus?: (active: boolean) => void;
  /** True while the activate/deactivate request is in flight. */
  isUpdatingStatus?: boolean;
}

/**
 * PartnerSettingsTab Component
 * Organizer-only settings tab for partner configuration
 */
export const PartnerSettingsTab: React.FC<PartnerSettingsTabProps> = ({
  partner,
  currentUser,
  onUpdateStatus,
  isUpdatingStatus = false,
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
          {/* isActive is DERIVED from partnershipEndDate server-side, never set directly:
              off → DELETE (end date = today), on → POST /reactivate (end date cleared).
              The parent owns those mutations; see PartnerDetailScreen. */}
          <FormControlLabel
            control={
              <Switch
                checked={partner.isActive ?? false}
                onChange={(e) => onUpdateStatus?.(e.target.checked)}
                disabled={isUpdatingStatus}
                data-testid="partner-active-toggle"
                inputProps={
                  {
                    'aria-label': t('common:filters.status.active'),
                  } as React.InputHTMLAttributes<HTMLInputElement>
                }
              />
            }
            label={t('common:filters.status.active')}
          />
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
