/**
 * #1251 story 6 — candidate duplicate companies (organizers): pairs computed on the live data by
 * the company service (`GET /companies/duplicate-candidates`: same website, same name, same name
 * apart from the legal form, one short name starts with the other). Per pair the organizer
 * compares and merges with the merge dialog, preset with the pair and the suggested company to
 * keep, or marks it as "not a duplicate". Two partner companies cannot be merged.
 * Built like the profile duplicates page (`UserManagement/DuplicateCandidatesPage`).
 */
import React, { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  Grid,
  Paper,
  Skeleton,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  apiErrorCode,
  dismissCompanyDuplicateCandidate,
  listCompanyDuplicateCandidates,
  type CompanyDuplicateCandidate,
} from '@/services/api/companyMergeApi';
import { MergeCompanyDialog } from './MergeCompanyDialog';

type Side = CompanyDuplicateCandidate['target'];
type ConfidenceFilter = 'ALL' | CompanyDuplicateCandidate['confidence'];

const QUERY_KEY = ['companies', 'duplicate-candidates'];

const confidenceColor = (c: CompanyDuplicateCandidate['confidence']) =>
  c === 'HIGH' ? 'success' : c === 'MEDIUM' ? 'warning' : 'default';

const samePair = (a: CompanyDuplicateCandidate, b: CompanyDuplicateCandidate) =>
  a.target.name === b.target.name && a.source.name === b.source.name;

const SideBox: React.FC<{ title: string; side: Side }> = ({ title, side }) => {
  const { t } = useTranslation('common');
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {title}
      </Typography>
      <Stack direction="row" spacing={1} alignItems="center">
        {side.logoUrl && (
          <Box
            component="img"
            src={side.logoUrl}
            alt=""
            sx={{ width: 32, height: 32, objectFit: 'contain' }}
          />
        )}
        <Typography variant="body1">
          <RouterLink to={`/organizer/companies/${encodeURIComponent(side.name)}`}>
            {side.displayName || side.name}
          </RouterLink>
        </Typography>
      </Stack>
      <Typography variant="body2" color="text.secondary">
        {side.name}
      </Typography>
      {side.website && <Typography variant="body2">{side.website}</Typography>}
      <Stack direction="row" spacing={0.5} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 0.5 }}>
        <Chip size="small" label={t('company.duplicates.people', { count: side.userCount })} />
        <Chip
          size="small"
          label={t('company.duplicates.registrations', { count: side.registrationCount })}
        />
        {side.isPartner && (
          <Chip size="small" color="info" label={t('company.duplicates.partner')} />
        )}
      </Stack>
    </Box>
  );
};

export const CompanyDuplicatesPage: React.FC = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<ConfidenceFilter>('ALL');
  const [merging, setMerging] = useState<CompanyDuplicateCandidate | null>(null);

  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: listCompanyDuplicateCandidates,
    retry: false,
  });

  const dismiss = useMutation({
    mutationFn: (pair: CompanyDuplicateCandidate) =>
      dismissCompanyDuplicateCandidate(pair.target.name, pair.source.name),
    onSuccess: (_data, pair) => {
      queryClient.setQueryData<CompanyDuplicateCandidate[]>(QUERY_KEY, (old) =>
        (old ?? []).filter((p) => !samePair(p, pair))
      );
    },
  });

  const pairs = useMemo(
    () => (query.data ?? []).filter((p) => filter === 'ALL' || p.confidence === filter),
    [query.data, filter]
  );

  return (
    <Box sx={{ flexGrow: 1 }}>
      <Container maxWidth="xl" disableGutters>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/organizer/companies')}
          sx={{ mb: 2 }}
        >
          {t('company.duplicates.back')}
        </Button>
        <Typography variant="h4" component="h1" gutterBottom>
          {t('company.duplicates.title')}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('company.duplicates.intro')}
        </Typography>

        {query.isLoading && <Skeleton variant="rectangular" height={200} />}
        {query.isError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={() => void query.refetch()}>
                {t('actions.retry')}
              </Button>
            }
          >
            {apiErrorCode(query.error) === 'DUPLICATE_COUNTS_INCOMPLETE'
              ? t('company.duplicates.countsIncomplete')
              : t('company.duplicates.loadError')}
          </Alert>
        )}

        {query.data && (
          <>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={2}
              alignItems={{ xs: 'flex-start', sm: 'center' }}
              sx={{ mb: 2 }}
            >
              <ToggleButtonGroup
                size="small"
                exclusive
                value={filter}
                onChange={(_e, v: ConfidenceFilter | null) => v && setFilter(v)}
              >
                <ToggleButton value="ALL">{t('company.duplicates.all')}</ToggleButton>
                <ToggleButton value="HIGH">{t('company.duplicates.confidence.HIGH')}</ToggleButton>
                <ToggleButton value="MEDIUM">
                  {t('company.duplicates.confidence.MEDIUM')}
                </ToggleButton>
                <ToggleButton value="LOW">{t('company.duplicates.confidence.LOW')}</ToggleButton>
              </ToggleButtonGroup>
              <Typography
                variant="body2"
                color="text.secondary"
                data-testid="company-duplicates-count"
              >
                {t('company.duplicates.count', { count: pairs.length })}
              </Typography>
            </Stack>

            {query.data.length === 0 && (
              <Alert severity="success" data-testid="company-duplicates-empty">
                {t('company.duplicates.empty')}
              </Alert>
            )}

            <Stack spacing={2}>
              {pairs.map((pair) => (
                <Paper
                  key={`${pair.target.name}|${pair.source.name}`}
                  variant="outlined"
                  sx={{ p: 2 }}
                  data-testid="company-duplicate-pair"
                >
                  <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
                    <Chip
                      size="small"
                      color={confidenceColor(pair.confidence)}
                      label={t(`company.duplicates.confidence.${pair.confidence}`)}
                    />
                    <Chip
                      size="small"
                      variant="outlined"
                      label={t(`company.duplicates.rule.${pair.rule}`)}
                    />
                  </Stack>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 5 }}>
                      <SideBox title={t('company.duplicates.keep')} side={pair.target} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 5 }}>
                      <SideBox title={t('company.duplicates.mergeIn')} side={pair.source} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <Stack spacing={1}>
                        {pair.organizerCanMerge ? (
                          <Button
                            variant="contained"
                            size="small"
                            onClick={() => setMerging(pair)}
                            data-testid="company-duplicate-merge"
                          >
                            {t('company.duplicates.compare')}
                          </Button>
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            {t('company.duplicates.bothPartners')}
                          </Typography>
                        )}
                        <Button
                          size="small"
                          disabled={dismiss.isPending}
                          onClick={() => dismiss.mutate(pair)}
                          data-testid="company-duplicate-dismiss"
                        >
                          {t('company.duplicates.dismiss')}
                        </Button>
                      </Stack>
                    </Grid>
                  </Grid>
                </Paper>
              ))}
            </Stack>
            {dismiss.isError && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {t('company.duplicates.dismissError')}
              </Alert>
            )}
          </>
        )}

        {merging && (
          <MergeCompanyDialog
            open
            onClose={() => setMerging(null)}
            companyName={merging.target.name}
            otherName={merging.source.name}
            stayOnPage
          />
        )}
      </Container>
    </Box>
  );
};

export default CompanyDuplicatesPage;
