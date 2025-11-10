/**
 * PartnerCard Component
 *
 * Story 2.8.1 - Task 4b (GREEN Phase)
 * AC: 3 (Partner Cards)
 *
 * Displays individual partner card with:
 * - Company logo (with fallback to initials avatar)
 * - Partnership tier badge with emoji
 * - Company information (name, industry)
 * - Primary contact information
 * - Last event attendance
 * - Topic votes count
 * - Next meeting date
 * - Engagement bar (placeholder for Epic 8)
 * - Quick action buttons
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  CardContent,
  CardActions,
  Typography,
  Avatar,
  Chip,
  Button,
  Box,
  LinearProgress,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { PartnerResponse } from '@/services/api/partnerApi';

interface PartnerCardProps {
  partner: PartnerResponse;
}

// Tier emoji mapping - UPPER_CASE per coding standards
const TIER_EMOJIS: Record<string, string> = {
  STRATEGIC: '🏆',
  PLATINUM: '💎',
  GOLD: '🥇',
  SILVER: '🥈',
  BRONZE: '🥉',
};

const PartnerCardComponent: React.FC<PartnerCardProps> = ({ partner }) => {
  const { t } = useTranslation('partners');
  const navigate = useNavigate();
  const [isVisible, setIsVisible] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  // Intersection Observer for lazy loading
  useEffect(() => {
    // In test environment or if IntersectionObserver is not available, load immediately
    if (
      typeof window === 'undefined' ||
      !('IntersectionObserver' in window) ||
      import.meta.env.MODE === 'test'
    ) {
      setIsVisible(true);
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setIsVisible(true);
            observer.disconnect();
          }
        });
      },
      { rootMargin: '50px' } // Load 50px before entering viewport
    );

    if (cardRef.current) {
      observer.observe(cardRef.current);
    }

    return () => observer.disconnect();
  }, []);

  const handleViewDetails = () => {
    navigate(`/partners/${partner.companyName}`);
  };

  const logoUrl = partner.company?.logoUrl;
  const primaryContact = partner.contacts?.find((c) => c.isPrimary);
  const tierEmoji = TIER_EMOJIS[partner.partnershipLevel.toUpperCase()] || '';

  return (
    <Card
      ref={cardRef}
      data-testid="partner-card"
      className="mobile"
      sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}
    >
      <CardContent sx={{ flexGrow: 1 }}>
        {/* Logo and Tier Badge */}
        <Box display="flex" alignItems="center" gap={2} mb={2}>
          {logoUrl && isVisible ? (
            <Avatar
              src={logoUrl}
              alt={partner.companyName}
              sx={{ width: 60, height: 60 }}
              imgProps={{
                loading: 'lazy',
                // Image loaded callback removed (imageLoaded state unused)
              }}
            />
          ) : (
            <Avatar sx={{ width: 60, height: 60 }}>
              {partner.companyName.substring(0, 2).toUpperCase()}
            </Avatar>
          )}
          <Box flexGrow={1}>
            <Chip
              label={`${tierEmoji} ${t(`tiers.${partner.partnershipLevel.toLowerCase()}`)}`}
              color="primary"
              size="small"
              sx={{ mb: 0.5 }}
            />
            <Typography variant="h6">{partner.companyName}</Typography>
          </Box>
        </Box>

        {/* Company Industry */}
        <Typography variant="body2" color="text.secondary" gutterBottom>
          🏢 {partner.company?.industry || 'N/A'}
        </Typography>

        {/* Primary Contact */}
        {primaryContact && (
          <Typography variant="body2" color="text.secondary" gutterBottom>
            👤 {primaryContact.firstName} {primaryContact.lastName} ({primaryContact.email})
          </Typography>
        )}

        {/* TODO: Future feature - Last Event (requires backend implementation) */}
        {/* <Typography variant="body2" color="text.secondary" gutterBottom>
          📊 Last Event: {partner.lastEventName || 'N/A'}
        </Typography> */}

        {/* TODO: Future feature - Topic Votes (requires backend implementation) */}
        {/* <Typography variant="body2" color="text.secondary" gutterBottom>
          🗳️ Topic Votes: {partner.votesCount || 0} active
        </Typography> */}

        {/* TODO: Future feature - Next Meeting (requires backend implementation) */}
        {/* <Typography variant="body2" color="text.secondary" gutterBottom>
          📅 Next Meeting: {partner.nextMeetingDate || 'Pending'}
        </Typography> */}

        {/* Engagement Bar - Placeholder for Epic 8 */}
        <Box mt={2}>
          <Typography variant="caption" color="text.secondary">
            {t('card.engagement')}: {t('card.engagementComingSoon')}
          </Typography>
          <LinearProgress variant="determinate" value={0} sx={{ mt: 0.5 }} role="progressbar" />
        </Box>
      </CardContent>

      <CardActions>
        <Button size="small" onClick={handleViewDetails}>
          {t('card.viewDetails')}
        </Button>
        <Button size="small" disabled title={t('card.comingSoon')}>
          {t('card.sendEmail')}
        </Button>
        <Button size="small" disabled title={t('card.comingSoon')}>
          {t('card.analytics')}
        </Button>
      </CardActions>
    </Card>
  );
};

// Memoized export for performance optimization (AC8: Performance)
// Prevents re-renders when parent re-renders but partner prop hasn't changed
export const PartnerCard = React.memo(PartnerCardComponent);
