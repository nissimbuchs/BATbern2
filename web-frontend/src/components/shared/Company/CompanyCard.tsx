/**
 * CompanyCard Component
 *
 * Individual company card display with:
 * - Company logo (CloudFront CDN via ?include=logo)
 * - Verified status (✅)
 * - Company details (name, industry)
 *
 * Story: 2.5.1 - Company Management Frontend
 */

import React from 'react';
import { Card, CardContent, CardActionArea, Typography, Box, Chip, Link } from '@mui/material';
import BusinessIcon from '@mui/icons-material/Business';
import LanguageIcon from '@mui/icons-material/Language';
import CategoryIcon from '@mui/icons-material/Category';
import { useTranslation } from 'react-i18next';
import type { CompanyListItem } from '@/types/company.types';

export interface CompanyCardProps {
  company: CompanyListItem;
  onClick: (companyId: string) => void;
  viewMode?: 'grid' | 'list';
}

export const CompanyCard: React.FC<CompanyCardProps> = ({
  company,
  onClick,
  viewMode = 'grid',
}) => {
  const { t } = useTranslation('common');

  const handleClick = () => {
    onClick(company.name);
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick(company.name);
    }
  };

  return (
    <Card
      data-testid={`company-card-${company.name}`}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <CardActionArea
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        role="button"
        aria-label={t('company.viewDetails', { name: company.displayName || company.name })}
        tabIndex={0}
        sx={{
          height: '100%',
          display: 'flex',
          flexDirection: viewMode === 'grid' ? 'column' : 'row',
          alignItems: 'flex-start',
        }}
      >
        {/* Logo Section */}
        <Box
          sx={{
            p: 2,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: viewMode === 'grid' ? 180 : 120,
          }}
        >
          {company.logo?.url ? (
            <Box
              component="img"
              src={company.logo.url}
              alt={`${company.name} logo`}
              loading="lazy"
              data-testid="company-logo"
              sx={{
                maxWidth: viewMode === 'grid' ? 150 : 100,
                maxHeight: viewMode === 'grid' ? 150 : 100,
                objectFit: 'contain',
              }}
            />
          ) : (
            <Box
              data-testid="company-logo-fallback"
              sx={{
                width: viewMode === 'grid' ? 150 : 100,
                height: viewMode === 'grid' ? 150 : 100,
                bgcolor: 'grey.200',
                borderRadius: 1,
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
              }}
            >
              <BusinessIcon sx={{ fontSize: 60, color: 'grey.500' }} />
            </Box>
          )}
        </Box>

        {/* Content Section */}
        <CardContent sx={{ flexGrow: 1, width: '100%' }}>
          {/* Badges Row */}
          {company.isVerified && (
            <Box sx={{ mb: 1 }}>
              <Chip
                label={`✅ ${t('company.badges.verified')}`}
                size="small"
                aria-label={t('company.badges.verified')}
                sx={{ height: 20, fontSize: '0.75rem' }}
              />
            </Box>
          )}

          {/* Company Name (display name preferred) */}
          <Typography
            variant="h6"
            component="h3"
            gutterBottom
            sx={{
              fontSize: '1rem',
              fontWeight: 600,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {company.displayName || company.name}
          </Typography>

          {/* Legal Name (if different from display name) */}
          {company.displayName && company.displayName !== company.name && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                fontSize: '0.875rem',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                mb: 1,
              }}
            >
              {t('company.fields.legalName', { name: company.name })}
            </Typography>
          )}

          {/* Industry */}
          {company.industry && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
              <CategoryIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
              <Typography variant="body2" color="text.secondary">
                {company.industry}
              </Typography>
            </Box>
          )}

          {/* Website */}
          {company.website && (
            <Box
              sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}
              onClick={(e) => e.stopPropagation()}
            >
              <LanguageIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
              <Link
                href={company.website}
                target="_blank"
                rel="noopener noreferrer"
                variant="body2"
                sx={{
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                onClick={(e) => e.stopPropagation()}
              >
                {company.website.replace(/^https?:\/\/(www\.)?/, '')}
              </Link>
            </Box>
          )}
        </CardContent>
      </CardActionArea>
    </Card>
  );
};
