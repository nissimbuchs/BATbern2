/**
 * PartnerOverviewTab Component
 *
 * Displays partnership details, engagement metrics, and recent activity
 * Story 2.8.2: Partner Detail View - AC4
 */

import React from 'react';
import { Box, Card, CardContent, Typography, Button, Divider, Stack } from '@mui/material';
import { CheckCircle } from '@mui/icons-material';
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

// Partnership benefits by tier
const TIER_BENEFITS: Record<string, string[]> = {
  STRATEGIC: [
    'Logo placement on website',
    'Newsletter mentions',
    'Priority event access',
    'Quarterly strategic meetings',
    'ROI analytics dashboard',
    'Dedicated account manager',
  ],
  PLATINUM: [
    'Logo placement on website',
    'Newsletter mentions',
    'Priority event access',
    'Quarterly strategic meetings',
  ],
  GOLD: ['Logo placement on website', 'Newsletter mentions', 'Priority event access'],
  SILVER: ['Newsletter mentions', 'Event access'],
  BRONZE: ['Event access'],
};

// Format date to "Jan 1, 2024" format
const formatShortDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
};

export const PartnerOverviewTab: React.FC<PartnerOverviewTabProps> = ({ partner }) => {
  const { setShowEditModal } = usePartnerDetailStore();

  const tierEmoji = TIER_EMOJIS[partner.partnershipLevel] || '';
  const benefits = TIER_BENEFITS[partner.partnershipLevel] || [];
  // TODO: previousTier requires backend implementation
  // const previousTierEmoji = partner.previousTier ? TIER_EMOJIS[partner.previousTier] : '';

  return (
    <Box>
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Partnership Details
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Stack spacing={2}>
            <Box>
              <Typography variant="body2" color="text.secondary">
                Partnership Tier
              </Typography>
              <Typography variant="h6">
                {tierEmoji} {partner.partnershipLevel}
              </Typography>
            </Box>

            <Box>
              <Typography variant="body2" color="text.secondary">
                Partnership Start Date
              </Typography>
              <Typography variant="body1">
                {formatShortDate(partner.partnershipStartDate)}
              </Typography>
            </Box>

            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Benefits
              </Typography>
              <Stack spacing={1}>
                {benefits.map((benefit, index) => (
                  <Box key={index} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <CheckCircle color="success" fontSize="small" />
                    <Typography variant="body2">{benefit}</Typography>
                  </Box>
                ))}
              </Stack>
            </Box>

            <Button variant="outlined" onClick={() => setShowEditModal(true)} fullWidth>
              Change Tier
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};
