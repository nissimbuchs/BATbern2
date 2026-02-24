/**
 * PartnerDetailHeader Component
 * Story 2.8.2: Partner Detail View - Task 4b
 *
 * Displays comprehensive partner information in the header:
 * - Company logo or initials avatar
 * - Partnership tier badge with emoji
 * - Company information (name, industry, website, location)
 * - Engagement bar (placeholder for Epic 8)
 * - Action buttons (Edit, Add Note, and disabled Epic 8 buttons)
 * - Back button to partner directory
 *
 * AC1: Partner Detail Header displays comprehensive partner information
 */

import React from 'react';
import { Box, Stack, Typography, Chip, Button, Link, Paper } from '@mui/material';
import { Edit, NoteAdd, ArrowBack } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { usePartnerModalStore } from '@/stores/partnerModalStore';
import type { PartnerResponse } from '@/services/api/partnerApi';

type UserRole = 'ORGANIZER' | 'PARTNER' | 'SPEAKER' | 'ATTENDEE';

interface PartnerDetailHeaderProps {
  partner: PartnerResponse;
  role?: UserRole; // Story 8.0: hide edit/action buttons for PARTNER
}

// Tier emoji mapping
const TIER_EMOJIS: Record<string, string> = {
  STRATEGIC: '🏆',
  PLATINUM: '💎',
  GOLD: '🥇',
  SILVER: '🥈',
  BRONZE: '🥉',
};

// Tier colors for chips
const TIER_COLORS: Record<string, 'primary' | 'secondary' | 'success' | 'warning' | 'info'> = {
  STRATEGIC: 'primary',
  PLATINUM: 'info',
  GOLD: 'warning',
  SILVER: 'secondary',
  BRONZE: 'success',
};

/**
 * Get initials from company name
 * @param name - Company name
 * @returns Two-letter initials
 */
const getInitials = (name: string): string => {
  const words = name.split(' ').filter((w) => w.length > 0);
  if (words.length >= 2) {
    return (words[0][0] + words[1][0]).toUpperCase();
  }
  return name.substring(0, 2).toUpperCase();
};

export const PartnerDetailHeader: React.FC<PartnerDetailHeaderProps> = ({ partner, role }) => {
  const { t } = useTranslation('partners');
  const navigate = useNavigate();
  const { openEditModal } = usePartnerModalStore();
  const isPartner = role === 'PARTNER';

  const handleEdit = () => {
    openEditModal(partner);
  };

  const handleBack = () => {
    navigate('/organizer/partners');
  };

  const companyName = partner.company?.name || partner.companyName;
  const initials = getInitials(companyName);
  const tierEmoji = TIER_EMOJIS[partner.partnershipLevel] || '';
  const tierColor = TIER_COLORS[partner.partnershipLevel] || 'default';

  return (
    <Paper elevation={2} sx={{ p: 3, mb: 3 }} data-testid="partner-detail-header">
      <Stack spacing={3}>
        {/* Back Button — hidden for PARTNER (portal layout provides navigation) */}
        {!isPartner && (
          <Box>
            <Button startIcon={<ArrowBack />} onClick={handleBack}>
              {t('detail.header.backButton', 'Back to Partner Directory')}
            </Button>
          </Box>
        )}

        {/* Header Content */}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} alignItems="flex-start">
          {/* Logo / Avatar */}
          <Box
            sx={{
              width: { xs: 80, sm: 120 },
              height: { xs: 80, sm: 120 },
              flexShrink: 0,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
            }}
          >
            {partner.company?.logoUrl ? (
              <Box
                component="img"
                src={partner.company.logoUrl}
                alt={companyName}
                sx={{
                  maxWidth: { xs: 80, sm: 120 },
                  maxHeight: { xs: 80, sm: 120 },
                  objectFit: 'contain',
                }}
              />
            ) : (
              <Box
                sx={{
                  width: { xs: 80, sm: 120 },
                  height: { xs: 80, sm: 120 },
                  borderRadius: 1,
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  fontSize: { xs: '1.75rem', sm: '2.5rem' },
                  fontWeight: 600,
                }}
              >
                {initials}
              </Box>
            )}
          </Box>

          {/* Company Info + Action Buttons */}
          <Stack spacing={1} flex={1} width="100%">
            {/* Company Name, Tier, and Actions row */}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1}
              alignItems={{ xs: 'flex-start', sm: 'center' }}
              justifyContent="space-between"
            >
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                <Typography
                  variant="h4"
                  component="h1"
                  sx={{ fontSize: { xs: '1.5rem', sm: '2.125rem' } }}
                >
                  {companyName}
                </Typography>
                <Chip
                  label={`${tierEmoji} ${t(`tiers.${partner.partnershipLevel.toLowerCase()}`)}`}
                  color={tierColor}
                  size="medium"
                />
              </Stack>

              {/* Action Buttons — hidden for PARTNER role */}
              {!isPartner && (
                <Stack direction="row" spacing={1} flexShrink={0}>
                  <Button variant="outlined" startIcon={<NoteAdd />} size="small">
                    {t('detail.notesTab.addNote')}
                  </Button>
                  <Button
                    variant="contained"
                    startIcon={<Edit />}
                    onClick={handleEdit}
                    size="small"
                    data-testid="edit-partner-button"
                  >
                    {t('modal.editTitle')}
                  </Button>
                </Stack>
              )}
            </Stack>

            {/* Company Details */}
            {partner.company && (
              <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                {partner.company.industry && (
                  <Typography variant="body2" color="text.secondary">
                    {partner.company.industry}
                  </Typography>
                )}
                {partner.company.website && (
                  <>
                    <Typography variant="body2" color="text.secondary">
                      •
                    </Typography>
                    <Link
                      href={partner.company.website}
                      target="_blank"
                      rel="noopener noreferrer"
                      underline="hover"
                    >
                      {partner.company.website.replace(/^https?:\/\/(www\.)?/, '')}
                    </Link>
                  </>
                )}
                {partner.company.location && (
                  <>
                    <Typography variant="body2" color="text.secondary">
                      •
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {partner.company.location}
                    </Typography>
                  </>
                )}
              </Stack>
            )}
          </Stack>
        </Stack>
      </Stack>
    </Paper>
  );
};
