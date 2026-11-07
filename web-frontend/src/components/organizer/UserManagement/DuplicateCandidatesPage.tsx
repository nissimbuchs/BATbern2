/**
 * Probable duplicate profiles (organizers): pairs computed on the live data by the user service
 * (`GET /users/duplicate-candidates`: same name, swapped name, same address name at another
 * domain). Per pair the organizer compares and merges with the existing merge dialog, preset with
 * the pair and the suggested profile to keep, or marks it as "not a duplicate". Pairs where both
 * profiles have a login cannot be merged by organizers; only the person can (profile → addresses).
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
  dismissDuplicateCandidate,
  listDuplicateCandidates,
  type DuplicateCandidate,
} from '@/services/api/userManagementApi';
import { MergeProfileDialog } from './MergeProfileDialog';

type Profile = DuplicateCandidate['target'];
type ConfidenceFilter = 'ALL' | DuplicateCandidate['confidence'];

const QUERY_KEY = ['users', 'duplicate-candidates'];

const confidenceColor = (c: DuplicateCandidate['confidence']) =>
  c === 'HIGH' ? 'success' : c === 'MEDIUM' ? 'warning' : 'default';

const ProfileBox: React.FC<{ title: string; profile: Profile }> = ({ title, profile }) => {
  const { t } = useTranslation('userManagement');
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {title}
      </Typography>
      <Typography variant="body1">
        <RouterLink to={`/organizer/users/${encodeURIComponent(profile.username)}`}>
          {profile.firstName} {profile.lastName}
        </RouterLink>
      </Typography>
      <Typography variant="body2">{profile.email}</Typography>
      <Typography variant="body2" color="text.secondary">
        {profile.companyId ?? '–'}
      </Typography>
      <Stack direction="row" spacing={0.5} sx={{ mt: 0.5, flexWrap: 'wrap' }}>
        <Chip
          size="small"
          color={profile.hasLogin ? 'info' : 'default'}
          label={profile.hasLogin ? t('merge.hasLogin') : t('merge.noLogin')}
        />
        {profile.placeholderAddress && (
          <Chip size="small" variant="outlined" label={t('duplicates.placeholder')} />
        )}
      </Stack>
    </Box>
  );
};

export const DuplicateCandidatesPage: React.FC = () => {
  const { t } = useTranslation('userManagement');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<ConfidenceFilter>('ALL');
  const [merging, setMerging] = useState<DuplicateCandidate | null>(null);

  const query = useQuery({ queryKey: QUERY_KEY, queryFn: listDuplicateCandidates, retry: false });

  const dismiss = useMutation({
    mutationFn: (pair: DuplicateCandidate) =>
      dismissDuplicateCandidate(pair.target.username, pair.source.username),
    onSuccess: (_data, pair) => {
      queryClient.setQueryData<DuplicateCandidate[]>(QUERY_KEY, (old) =>
        (old ?? []).filter(
          (p) =>
            !(
              p.target.username === pair.target.username &&
              p.source.username === pair.source.username
            )
        )
      );
    },
  });

  const pairs = useMemo(
    () => (query.data ?? []).filter((p) => filter === 'ALL' || p.confidence === filter),
    [query.data, filter]
  );

  return (
    <Box sx={{ flexGrow: 1, p: 3 }}>
      <Container maxWidth="xl">
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/organizer/users')}
          sx={{ mb: 2 }}
        >
          {t('duplicates.back')}
        </Button>
        <Typography variant="h4" component="h1" gutterBottom>
          {t('duplicates.title')}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('duplicates.intro')}
        </Typography>

        {query.isLoading && <Skeleton variant="rectangular" height={200} />}
        {query.isError && <Alert severity="error">{t('duplicates.loadError')}</Alert>}

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
                <ToggleButton value="ALL">{t('duplicates.all')}</ToggleButton>
                <ToggleButton value="HIGH">{t('duplicates.confidence.HIGH')}</ToggleButton>
                <ToggleButton value="MEDIUM">{t('duplicates.confidence.MEDIUM')}</ToggleButton>
                <ToggleButton value="LOW">{t('duplicates.confidence.LOW')}</ToggleButton>
              </ToggleButtonGroup>
              <Typography variant="body2" color="text.secondary" data-testid="duplicates-count">
                {t('duplicates.count', { count: pairs.length })}
              </Typography>
            </Stack>

            {query.data.length === 0 && (
              <Alert severity="success" data-testid="duplicates-empty">
                {t('duplicates.empty')}
              </Alert>
            )}

            <Stack spacing={2}>
              {pairs.map((pair) => (
                <Paper
                  key={`${pair.target.username}|${pair.source.username}`}
                  variant="outlined"
                  sx={{ p: 2 }}
                  data-testid="duplicate-pair"
                >
                  <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
                    <Chip
                      size="small"
                      color={confidenceColor(pair.confidence)}
                      label={t(`duplicates.confidence.${pair.confidence}`)}
                    />
                    <Chip
                      size="small"
                      variant="outlined"
                      label={t(`duplicates.rule.${pair.rule}`)}
                    />
                  </Stack>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 5 }}>
                      <ProfileBox title={t('duplicates.keep')} profile={pair.target} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 5 }}>
                      <ProfileBox title={t('duplicates.mergeIn')} profile={pair.source} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <Stack spacing={1}>
                        {pair.organizerCanMerge ? (
                          <Button
                            variant="contained"
                            size="small"
                            onClick={() => setMerging(pair)}
                            data-testid="duplicate-merge"
                          >
                            {t('duplicates.compare')}
                          </Button>
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            {t('duplicates.bothLogin')}
                          </Typography>
                        )}
                        <Button
                          size="small"
                          disabled={dismiss.isPending}
                          onClick={() => dismiss.mutate(pair)}
                          data-testid="duplicate-dismiss"
                        >
                          {t('duplicates.dismiss')}
                        </Button>
                      </Stack>
                    </Grid>
                  </Grid>
                </Paper>
              ))}
            </Stack>
            {dismiss.isError && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {t('duplicates.dismissError')}
              </Alert>
            )}
          </>
        )}

        {merging && (
          <MergeProfileDialog
            open
            onClose={() => setMerging(null)}
            username={merging.target.username}
            otherUsername={merging.source.username}
            stayOnPage
            onMerged={() => void queryClient.invalidateQueries({ queryKey: QUERY_KEY })}
          />
        )}
      </Container>
    </Box>
  );
};

export default DuplicateCandidatesPage;
