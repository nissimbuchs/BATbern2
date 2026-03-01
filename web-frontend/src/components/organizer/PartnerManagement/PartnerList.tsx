import React from 'react';
import {
  Box,
  Stack,
  Typography,
  Button,
  ButtonGroup,
  Skeleton,
  Alert,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { FirstPage, NavigateBefore, NavigateNext, LastPage } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { PartnerCard } from './PartnerCard';
import { usePartners } from '@/hooks/usePartners';
import { usePartnerStore } from '@/stores/partnerStore';

export const PartnerList: React.FC = () => {
  const { t } = useTranslation('partners');
  const { viewMode, filters, searchQuery, sortBy, sortOrder, page, setPage } = usePartnerStore();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const effectiveViewMode = isMobile ? 'list' : viewMode;

  // Fetch partners using React Query
  const { data, isLoading, isError, error } = usePartners(
    filters,
    { sortBy, sortOrder },
    { page, size: 20 }
  );

  const allPartners = data?.data || [];
  const partners = searchQuery
    ? allPartners.filter((partner) => {
        const q = searchQuery.toLowerCase();
        return (
          partner.companyName.toLowerCase().includes(q) ||
          partner.company?.displayName?.toLowerCase().includes(q) ||
          partner.company?.name?.toLowerCase().includes(q)
        );
      })
    : allPartners;
  const pagination = data?.metadata;

  // Loading state
  if (isLoading) {
    return (
      <Box data-testid="partner-list-loading">
        <Grid container spacing={2} data-testid="partner-list-grid">
          {[...Array(6)].map((_, index) => (
            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={index}>
              <Skeleton
                variant="rectangular"
                height={300}
                data-testid="partner-skeleton"
                sx={{ borderRadius: 1 }}
              />
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  }

  // Error state
  if (isError) {
    const errorObj = error as Error & { correlationId?: string };
    const errorMessage = errorObj?.message || t('error.failedToLoad');
    const correlationId = errorObj?.correlationId || 'N/A';

    return (
      <Box data-testid="partner-list-error">
        <Alert severity="error">
          <Typography variant="body1">{errorMessage}</Typography>
          <Typography variant="caption" color="text.secondary">
            {t('error.correlationId')}: {correlationId}
          </Typography>
        </Alert>
      </Box>
    );
  }

  // Empty state
  if (partners.length === 0) {
    return (
      <Box
        data-testid="partner-list-empty"
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: 300,
        }}
      >
        <Typography variant="h6" color="text.secondary">
          {t('empty.noPartners')}
        </Typography>
      </Box>
    );
  }

  // Pagination controls
  const showPagination = pagination && pagination.totalPages > 1;
  const isFirstPage = pagination?.page === 0;
  const isLastPage = pagination && pagination.page === pagination.totalPages - 1;

  const handleFirstPage = () => setPage(0);
  const handlePrevPage = () => setPage(Math.max(0, page - 1));
  const handleNextPage = () => {
    if (pagination) {
      setPage(Math.min(pagination.totalPages - 1, page + 1));
    }
  };
  const handleLastPage = () => {
    if (pagination) {
      setPage(pagination.totalPages - 1);
    }
  };

  // Grid view
  if (effectiveViewMode === 'grid') {
    return (
      <Box>
        <Grid container spacing={2} data-testid="partner-grid" data-columns="3" data-spacing="3">
          {partners.map((partner) => (
            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={partner.companyName}>
              <PartnerCard partner={partner} />
            </Grid>
          ))}
        </Grid>

        {showPagination && (
          <Box
            component="nav"
            role="navigation"
            aria-label={t('pagination.ariaLabel')}
            data-testid="partner-pagination"
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              mt: 3,
              pt: 2,
              borderTop: 1,
              borderColor: 'divider',
            }}
          >
            <Typography variant="body2" color="text.secondary" data-testid="pagination-info">
              {t('pagination.page', {
                current: (pagination?.page || 0) + 1,
                total: pagination?.totalPages || 1,
              })}{' '}
              • {t('pagination.partners', { count: pagination?.totalElements || 0 })}
            </Typography>

            <ButtonGroup variant="outlined" size="small">
              <Button
                onClick={handleFirstPage}
                disabled={isFirstPage}
                aria-label={t('pagination.firstPage')}
                data-testid="first-page-button"
              >
                <FirstPage />
              </Button>
              <Button
                onClick={handlePrevPage}
                disabled={isFirstPage}
                aria-label={t('pagination.previousPage')}
                data-testid="prev-page-button"
              >
                <NavigateBefore />
              </Button>
              <Button
                onClick={handleNextPage}
                disabled={isLastPage}
                aria-label={t('pagination.nextPage')}
                data-testid="next-page-button"
              >
                <NavigateNext />
              </Button>
              <Button
                onClick={handleLastPage}
                disabled={isLastPage}
                aria-label={t('pagination.lastPage')}
                data-testid="last-page-button"
              >
                <LastPage />
              </Button>
            </ButtonGroup>
          </Box>
        )}
      </Box>
    );
  }

  // List view (single column)
  return (
    <Box>
      <Stack spacing={2} data-testid="partner-list-list">
        {partners.map((partner) => (
          <PartnerCard key={partner.companyName} partner={partner} />
        ))}
      </Stack>

      {showPagination && (
        <Box
          component="nav"
          role="navigation"
          aria-label={t('pagination.ariaLabel')}
          data-testid="partner-pagination"
          sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            mt: 3,
            pt: 2,
            borderTop: 1,
            borderColor: 'divider',
          }}
        >
          <Typography variant="body2" color="text.secondary" data-testid="pagination-info">
            {t('pagination.page', {
              current: (pagination?.page || 0) + 1,
              total: pagination?.totalPages || 1,
            })}{' '}
            • {t('pagination.partners', { count: pagination?.totalElements || 0 })}
          </Typography>

          <ButtonGroup variant="outlined" size="small">
            <Button
              onClick={handleFirstPage}
              disabled={isFirstPage}
              aria-label={t('pagination.firstPage')}
              data-testid="first-page-button"
            >
              <FirstPage />
            </Button>
            <Button
              onClick={handlePrevPage}
              disabled={isFirstPage}
              aria-label={t('pagination.previousPage')}
              data-testid="prev-page-button"
            >
              <NavigateBefore />
            </Button>
            <Button
              onClick={handleNextPage}
              disabled={isLastPage}
              aria-label={t('pagination.nextPage')}
              data-testid="next-page-button"
            >
              <NavigateNext />
            </Button>
            <Button
              onClick={handleLastPage}
              disabled={isLastPage}
              aria-label={t('pagination.lastPage')}
              data-testid="last-page-button"
            >
              <LastPage />
            </Button>
          </ButtonGroup>
        </Box>
      )}
    </Box>
  );
};
