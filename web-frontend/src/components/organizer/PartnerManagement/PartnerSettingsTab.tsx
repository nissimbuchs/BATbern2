import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Switch,
  FormControlLabel,
  Button,
  Stack,
  Divider,
  Alert,
  Chip,
} from '@mui/material';
import {
  Download as DownloadIcon,
  DeleteForever as DeleteIcon,
  Block as BlockIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

interface User {
  username: string;
  role: 'ORGANIZER' | 'PARTNER' | 'SPEAKER' | 'ATTENDEE';
}

interface PartnerDetail {
  companyName: string; // ADR-003: companyName is the meaningful ID
  partnershipLevel: 'STRATEGIC' | 'PLATINUM' | 'GOLD' | 'SILVER' | 'BRONZE';
  partnershipStartDate: string;
  isActive: boolean;
  autoRenewal?: boolean; // TODO: Add to backend PartnerResponse
  renewalDate?: string;
}

interface PartnerSettingsTabProps {
  partner: PartnerDetail;
  currentUser: User;
  onUpdateStatus: (isActive: boolean) => void;
  onUpdateAutoRenewal: (autoRenewal: boolean) => void;
}

/**
 * Format ISO date to "Month DD, YYYY"
 */
const formatDate = (isoDate: string): string => {
  const date = new Date(isoDate);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
};

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
  // Check if user is organizer
  const isOrganizer = currentUser.role === 'ORGANIZER';

  // Access denied for non-organizers
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

  // Handler for status toggle
  const handleStatusChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onUpdateStatus(event.target.checked);
  };

  // Handler for auto-renewal toggle
  const handleAutoRenewalChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onUpdateAutoRenewal(event.target.checked);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        {t('detail.settingsTab.title')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Configure partnership status and preferences
      </Typography>

      {/* Partnership Status Section */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {t('detail.settingsTab.activePartnership')}
          </Typography>
          <Stack spacing={2}>
            <Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={partner.isActive}
                    onChange={handleStatusChange}
                    name="isActive"
                    inputProps={{ 'aria-label': 'Active' }}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">Active</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {partner.isActive
                        ? 'Partnership is currently active'
                        : 'Partnership is currently inactive'}
                    </Typography>
                  </Box>
                }
              />
            </Box>
            {partner.isActive && (
              <Chip
                label={t('detail.settingsTab.activePartnership')}
                color="success"
                size="small"
                sx={{ width: 'fit-content' }}
              />
            )}
          </Stack>
        </CardContent>
      </Card>

      {/* Auto-Renewal Settings Section */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Auto-Renewal
          </Typography>
          <Stack spacing={2}>
            <Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={partner.autoRenewal}
                    onChange={handleAutoRenewalChange}
                    name="autoRenewal"
                    inputProps={{ 'aria-label': 'Auto-Renewal' }}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">Auto-Renewal</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {partner.autoRenewal
                        ? 'Partnership will auto-renew'
                        : 'Partnership will not auto-renew'}
                    </Typography>
                  </Box>
                }
              />
            </Box>
            {partner.autoRenewal && partner.renewalDate && (
              <Box>
                <Typography variant="body2" color="text.secondary">
                  Renewal Date
                </Typography>
                <Typography variant="body1">{formatDate(partner.renewalDate)}</Typography>
              </Box>
            )}
          </Stack>
        </CardContent>
      </Card>

      {/* Data Management Section */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Data Management
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('detail.header.comingSoon')}
          </Typography>
          <Stack spacing={2} divider={<Divider />}>
            <Box>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                disabled
                title={t('detail.header.comingSoon')}
                fullWidth
              >
                {t('detail.settingsTab.exportData')}
              </Button>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                Download all partner data in JSON format
              </Typography>
            </Box>
            <Box>
              <Button
                variant="outlined"
                color="error"
                startIcon={<DeleteIcon />}
                disabled
                title={t('detail.header.comingSoon')}
                fullWidth
              >
                {t('detail.settingsTab.deletePartner')}
              </Button>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                Permanently delete all partner data (requires confirmation)
              </Typography>
            </Box>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};
