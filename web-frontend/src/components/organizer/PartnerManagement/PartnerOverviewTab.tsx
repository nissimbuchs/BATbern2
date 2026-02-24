/**
 * PartnerOverviewTab Component
 *
 * Displays partnership details, engagement metrics, and recent activity
 * Story 2.8.2: Partner Detail View - AC4
 */

import React from 'react';
import { Box, Card, CardContent, Typography, Button, Divider, Stack } from '@mui/material';
import { CheckCircle } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePartnerDetailStore } from '@/stores/partnerDetailStore';
import type { PartnerResponse } from '@/services/api/partnerApi';

interface PartnerOverviewTabProps {
  partner: PartnerResponse;
}

// Partnership tier emojis
const TIER_EMOJIS: Record<string, string> = {
  STRATEGIC: '🏆',
  PLATINUM: '💎',
  GOLD: '🥇',
  SILVER: '🥈',
  BRONZE: '🥉',
};

// Tier benefits as translation key arrays
const TIER_BENEFIT_KEYS: Record<string, string[]> = {
  STRATEGIC: [
    'logoPlacement',
    'newsletterMentions',
    'priorityAccess',
    'quarterlyMeetings',
    'roiAnalytics',
    'accountManager',
  ],
  PLATINUM: ['logoPlacement', 'newsletterMentions', 'priorityAccess', 'quarterlyMeetings'],
  GOLD: ['logoPlacement', 'newsletterMentions', 'priorityAccess'],
  SILVER: ['newsletterMentions', 'eventAccess'],
  BRONZE: ['eventAccess'],
};

export const PartnerOverviewTab: React.FC<PartnerOverviewTabProps> = ({ partner }) => {
  const { t, i18n } = useTranslation('partners');
  const { setShowEditModal } = usePartnerDetailStore();

  const tierEmoji = TIER_EMOJIS[partner.partnershipLevel] || '';
  const benefitKeys = TIER_BENEFIT_KEYS[partner.partnershipLevel] || [];

  const formattedStartDate = new Date(partner.partnershipStartDate).toLocaleDateString(
    i18n.language,
    { year: 'numeric', month: 'short', day: 'numeric' }
  );

  return (
    <Box>
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {t('detail.overviewTab.title')}
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Stack spacing={2}>
            <Box>
              <Typography variant="body2" color="text.secondary">
                {t('detail.overviewTab.tier')}
              </Typography>
              <Typography variant="h6">
                {tierEmoji} {t(`tiers.${partner.partnershipLevel.toLowerCase()}`)}
              </Typography>
            </Box>

            <Box>
              <Typography variant="body2" color="text.secondary">
                {t('detail.overviewTab.startDate')}
              </Typography>
              <Typography variant="body1">{formattedStartDate}</Typography>
            </Box>

            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {t('detail.overviewTab.benefits')}
              </Typography>
              <Stack spacing={1}>
                {benefitKeys.map((key) => (
                  <Box key={key} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <CheckCircle color="success" fontSize="small" />
                    <Typography variant="body2">
                      {t(`detail.overviewTab.tierBenefits.${key}`)}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            </Box>

            <Button variant="outlined" onClick={() => setShowEditModal(true)} fullWidth>
              {t('detail.overviewTab.changeTier')}
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};
