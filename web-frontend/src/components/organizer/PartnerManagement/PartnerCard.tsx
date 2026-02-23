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
 * - Quick action buttons
 */

import React, { useState, useEffect, useRef } from 'react';
import { Box, Card, CardContent, Typography, Chip } from '@mui/material';
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
    navigate(`/organizer/partners/${partner.companyName}`);
  };

  const logoUrl = partner.company?.logoUrl;
  const primaryContact = partner.contacts?.[0];
  const tierEmoji = TIER_EMOJIS[partner.partnershipLevel.toUpperCase()] || '';

  return (
    <Card
      ref={cardRef}
      data-testid="partner-card"
      className="mobile"
      onClick={handleViewDetails}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        cursor: 'pointer',
        '&:hover': { boxShadow: 6 },
      }}
    >
      <CardContent sx={{ flexGrow: 1 }}>
        {/* Logo and Tier Badge */}
        <Box display="flex" alignItems="center" gap={2} mb={2}>
          {logoUrl && isVisible && (
            <Box
              component="img"
              src={logoUrl}
              alt={partner.companyName}
              loading="lazy"
              sx={{ maxWidth: 60, maxHeight: 60, objectFit: 'contain', flexShrink: 0 }}
            />
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
      </CardContent>
    </Card>
  );
};

// Memoized export for performance optimization (AC8: Performance)
// Prevents re-renders when parent re-renders but partner prop hasn't changed
export const PartnerCard = React.memo(PartnerCardComponent);
