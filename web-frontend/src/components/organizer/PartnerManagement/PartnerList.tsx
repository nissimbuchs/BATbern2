import React from 'react';
import { Box, Stack, Typography, Button, ButtonGroup, Skeleton, Alert } from '@mui/material';
import Grid from '@mui/material/Grid';
import { FirstPage, NavigateBefore, NavigateNext, LastPage } from '@mui/icons-material';
import { PartnerCard } from './PartnerCard';
import { usePartners } from '@/hooks/usePartners';
import { usePartnerStore } from '@/stores/partnerStore';

export const PartnerList: React.FC = () => {
  const { viewMode, filters, sortBy, sortOrder, page, setPage } = usePartnerStore();

  // Fetch partners using React Query
  const { data, isLoading, isError, error } = usePartners(
    filters,
    { sortBy, sortOrder },
    { page, size: 20 }
  );

  const partners = data?.data || [];
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
    const errorMessage = errorObj?.message || 'Failed to fetch partners';
    const correlationId = errorObj?.correlationId || 'N/A';

    return (
      <Box data-testid="partner-list-error">
        <Alert severity="error">
          <Typography variant="body1">{errorMessage}</Typography>
          <Typography variant="caption" color="text.secondary">
            Correlation ID: {correlationId}
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
          No partners found
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
  if (viewMode === 'grid') {
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
            aria-label="Partner list pagination"
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
            <Typography variant="body2" color="text.secondary">
              Page {(pagination?.page || 0) + 1} of {pagination?.totalPages || 1} •{' '}
              {pagination?.totalElements || 0} partners
            </Typography>

            <ButtonGroup variant="outlined" size="small">
              <Button onClick={handleFirstPage} disabled={isFirstPage} aria-label="First page">
                <FirstPage />
              </Button>
              <Button onClick={handlePrevPage} disabled={isFirstPage} aria-label="Previous page">
                <NavigateBefore />
              </Button>
              <Button onClick={handleNextPage} disabled={isLastPage} aria-label="Next page">
                <NavigateNext />
              </Button>
              <Button onClick={handleLastPage} disabled={isLastPage} aria-label="Last page">
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
          aria-label="Partner list pagination"
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
          <Typography variant="body2" color="text.secondary">
            Page {(pagination?.page || 0) + 1} of {pagination?.totalPages || 1} •{' '}
            {pagination?.totalElements || 0} partners
          </Typography>

          <ButtonGroup variant="outlined" size="small">
            <Button onClick={handleFirstPage} disabled={isFirstPage} aria-label="First page">
              <FirstPage />
            </Button>
            <Button onClick={handlePrevPage} disabled={isFirstPage} aria-label="Previous page">
              <NavigateBefore />
            </Button>
            <Button onClick={handleNextPage} disabled={isLastPage} aria-label="Next page">
              <NavigateNext />
            </Button>
            <Button onClick={handleLastPage} disabled={isLastPage} aria-label="Last page">
              <LastPage />
            </Button>
          </ButtonGroup>
        </Box>
      )}
    </Box>
  );
};
