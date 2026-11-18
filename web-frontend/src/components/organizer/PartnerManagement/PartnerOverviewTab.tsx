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
import { usePartnerModalStore } from '@/stores/partnerModalStore';
import type { PartnerResponse } from '@/services/api/partnerApi';
import { intlLocale } from '@/utils/date/intlLocale';

interface PartnerOverviewTabProps {
  partner: PartnerResponse;
  /**
   * Organizers change the tier; a partner sees it read-only (owner 2026-11-15, #1223 item 12).
   * The API agrees: `PATCH /partners/{companyName}` is organizer-only.
   */
  canChangeTier: boolean;
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

export const PartnerOverviewTab: React.FC<PartnerOverviewTabProps> = ({
  partner,
  canChangeTier,
}) => {
  const { t, i18n } = useTranslation('partners');
  const openEditModal = usePartnerModalStore((state) => state.openEditModal);

  const tierEmoji = TIER_EMOJIS[partner.partnershipLevel] || '';
  const benefitKeys = TIER_BENEFIT_KEYS[partner.partnershipLevel] || [];

  const formattedStartDate = new Date(partner.partnershipStartDate).toLocaleDateString(
    intlLocale(i18n.language),
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

            {/* The edit modal holds the tier; the button set a flag nothing read before. */}
            {canChangeTier && (
              <Button variant="outlined" onClick={() => openEditModal(partner)} fullWidth>
                {t('detail.overviewTab.changeTier')}
              </Button>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};
