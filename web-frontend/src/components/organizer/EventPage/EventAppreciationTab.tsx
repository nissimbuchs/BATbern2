/**
 * EventAppreciationTab (Story 7.7 — Curated Thank-You Notes)
 *
 * Organizer view of the thank-you notes left for this event (Story 7.4 stored them; this is the
 * surface that finally lets organizers READ them). Each logged-in note can be ★-featured for the
 * public curated marquee; anonymous notes are shown but cannot be featured (no name).
 */

import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Paper,
  Stack,
  Switch,
  Tooltip,
  Typography,
} from '@mui/material';
import { Favorite as FavoriteIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useEventThanks, useSetThanksFeatured } from '@/hooks/useThanks/useThanks';

interface EventAppreciationTabProps {
  eventCode: string;
}

export const EventAppreciationTab = ({ eventCode }: EventAppreciationTabProps) => {
  const { t } = useTranslation('events');
  const { data, isLoading, isError } = useEventThanks(eventCode);
  const setFeatured = useSetThanksFeatured(eventCode);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (isError) {
    return <Alert severity="error">{t('appreciation.loadError')}</Alert>;
  }

  const notes = data?.notes ?? [];
  const count = data?.count ?? 0;

  const authorName = (n: (typeof notes)[number]): string => {
    const full = [n.thankedByFirstName, n.thankedByLastName].filter(Boolean).join(' ').trim();
    if (full) {
      return n.thankedByCompanyName ? `${full} · ${n.thankedByCompanyName}` : full;
    }
    return n.thankedByUsername || t('appreciation.anonymous');
  };

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        {t('appreciation.title')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('appreciation.subtitle', { count })}
      </Typography>

      {notes.length === 0 ? (
        <Alert severity="info" data-testid="appreciation-empty">
          {t('appreciation.empty')}
        </Alert>
      ) : (
        <Stack spacing={1.5}>
          {notes.map((n) => {
            const anonymous = !n.thankedByUsername;
            return (
              <Paper key={n.id} variant="outlined" sx={{ p: 2 }} data-testid="appreciation-note">
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  alignItems="flex-start"
                  spacing={2}
                >
                  <Box sx={{ minWidth: 0 }}>
                    {n.note ? (
                      <Typography variant="body1" sx={{ fontStyle: 'italic' }}>
                        “{n.note}”
                      </Typography>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        {t('appreciation.noNote')}
                      </Typography>
                    )}
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">
                        {authorName(n)}
                      </Typography>
                      {anonymous && (
                        <Chip size="small" label={t('appreciation.anonymous')} variant="outlined" />
                      )}
                      {n.featured && (
                        <Chip
                          size="small"
                          color="success"
                          icon={<FavoriteIcon fontSize="small" />}
                          label={t('appreciation.featured')}
                        />
                      )}
                    </Stack>
                  </Box>

                  <Tooltip
                    title={
                      anonymous
                        ? t('appreciation.cannotFeatureAnonymous')
                        : t('appreciation.featureToggle')
                    }
                  >
                    <span>
                      <Switch
                        checked={!!n.featured}
                        disabled={anonymous || setFeatured.isPending}
                        onChange={(e) =>
                          setFeatured.mutate({ id: n.id!, featured: e.target.checked })
                        }
                        inputProps={{ 'aria-label': t('appreciation.featureToggle') }}
                        data-testid={`appreciation-feature-toggle-${n.id}`}
                      />
                    </span>
                  </Tooltip>
                </Stack>
              </Paper>
            );
          })}
        </Stack>
      )}
    </Box>
  );
};
