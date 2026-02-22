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
import { Edit, NoteAdd, Email, CalendarMonth, Analytics, ArrowBack } from '@mui/icons-material';
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
        <Stack direction="row" spacing={3} alignItems="flex-start">
          {/* Logo / Avatar */}
          <Box
            sx={{
              width: 120,
              height: 120,
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
                  maxWidth: 120,
                  maxHeight: 120,
                  objectFit: 'contain',
                }}
              />
            ) : (
              <Box
                sx={{
                  width: 120,
                  height: 120,
                  borderRadius: 1,
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  fontSize: '2.5rem',
                  fontWeight: 600,
                }}
              >
                {initials}
              </Box>
            )}
          </Box>

          {/* Company Info */}
          <Stack spacing={1} flex={1}>
            {/* Company Name and Tier */}
            <Stack direction="row" spacing={2} alignItems="center">
              <Typography variant="h4" component="h1">
                {companyName}
              </Typography>
              <Chip
                label={`${tierEmoji} ${t(`tiers.${partner.partnershipLevel.toLowerCase()}`)}`}
                color={tierColor}
                size="medium"
              />
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

            {/* Engagement Bar Placeholder */}
            <Box
              sx={{
                mt: 2,
                p: 2,
                bgcolor: 'grey.100',
                borderRadius: 1,
                textAlign: 'center',
              }}
            >
              <Typography variant="body2" color="text.secondary">
                📊 {t('detail.header.comingSoon')}
              </Typography>
            </Box>
          </Stack>

          {/* Action Buttons — hidden for PARTNER role */}
          {!isPartner && (
            <Stack direction="row" spacing={1}>
              <Button
                variant="outlined"
                startIcon={<Email />}
                disabled
                size="small"
                title={t('detail.header.comingSoon')}
              >
                {t('detail.header.sendEmail')}
              </Button>
              <Button
                variant="outlined"
                startIcon={<CalendarMonth />}
                disabled
                size="small"
                title={t('detail.header.comingSoon')}
              >
                {t('detail.header.scheduleMeeting')}
              </Button>
              <Button
                variant="outlined"
                startIcon={<Analytics />}
                disabled
                size="small"
                title={t('detail.header.comingSoon')}
              >
                {t('detail.header.exportData')}
              </Button>
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
      </Stack>
    </Paper>
  );
};
