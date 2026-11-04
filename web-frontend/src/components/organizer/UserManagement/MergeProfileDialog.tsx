/**
 * #1183 C — an organizer merges another profile of the same person into this one.
 *
 * Pick the other profile, read the preview (both sides, what moves, what collides), confirm.
 * The profile without a login is always the one that is merged in and deleted; if the organizer
 * picked it the other way round, the dialog offers to swap.
 */

import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  List,
  ListItem,
  ListItemText,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { UserAutocomplete } from '@/components/shared/UserAutocomplete';
import {
  getUserMergePreview,
  mergeUserProfile,
  type UserMergePreview,
} from '@/services/api/userManagementApi';
import type { UserSearchResponse } from '@/types/user.types';

interface MergeProfileDialogProps {
  open: boolean;
  onClose: () => void;
  /** The profile whose page is open (username). */
  username: string;
}

type Summary = UserMergePreview['source'];

const ProfileColumn: React.FC<{ title: string; profile: Summary; testId: string }> = ({
  title,
  profile,
  testId,
}) => {
  const { t } = useTranslation('userManagement');
  return (
    <Box data-testid={testId}>
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
        {title}
      </Typography>
      <Typography variant="body1">
        {profile.firstName} {profile.lastName}
      </Typography>
      <Typography variant="body2">{profile.email}</Typography>
      <Typography variant="body2" color="text.secondary">
        {profile.username}
      </Typography>
      <Box sx={{ mt: 1, display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
        <Chip
          size="small"
          color={profile.hasLogin ? 'info' : 'default'}
          label={profile.hasLogin ? t('merge.hasLogin') : t('merge.noLogin')}
        />
        {profile.roles.map((role) => (
          <Chip key={role} size="small" label={role} />
        ))}
      </Box>
      {profile.additionalEmails.length > 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {t('merge.additionalEmails')}: {profile.additionalEmails.join(', ')}
        </Typography>
      )}
    </Box>
  );
};

export const MergeProfileDialog: React.FC<MergeProfileDialogProps> = ({
  open,
  onClose,
  username,
}) => {
  const { t } = useTranslation('userManagement');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [other, setOther] = useState<UserSearchResponse | null>(null);
  const [swapped, setSwapped] = useState(false);

  const target = swapped && other ? other.id : username;
  const source = swapped ? username : other?.id;

  const [merged, setMerged] = useState(false);

  const preview = useQuery({
    queryKey: ['users', 'merge-preview', target, source],
    queryFn: () => getUserMergePreview(target, source as string),
    // not after the merge: the source profile is gone, a refetch would only produce a 404
    enabled: open && !!source && source !== target && !merged,
    retry: false,
  });

  const merge = useMutation({
    mutationFn: () => mergeUserProfile(target, source as string),
    onSuccess: async () => {
      setMerged(true);
      // everything about users, except the preview of the profile that no longer exists
      await queryClient.invalidateQueries({
        queryKey: ['users'],
        predicate: (q) => q.queryKey[1] !== 'merge-preview',
      });
      if (target !== username) {
        navigate(`/organizer/users/${encodeURIComponent(target)}`);
      }
    },
  });

  const handleClose = () => {
    setOther(null);
    setSwapped(false);
    setMerged(false);
    merge.reset();
    onClose();
  };

  const p = preview.data;
  const moves = p ? Object.entries(p.moves).filter(([, n]) => n > 0) : [];
  const conflicts = p ? Object.entries(p.conflicts).filter(([, n]) => n > 0) : [];
  const tableLabel = (key: string) =>
    t(`merge.tables.${key.split('.')[0]}`, { defaultValue: key.split('.')[0] });

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth data-testid="merge-dialog">
      <DialogTitle>{t('merge.title')}</DialogTitle>
      <DialogContent>
        {merge.isSuccess ? (
          <Alert severity="success" data-testid="merge-success">
            {t('merge.success', {
              source: merge.data.sourceUsername,
              target: merge.data.targetUsername,
            })}
          </Alert>
        ) : (
          <>
            <Typography variant="body2" sx={{ mb: 2 }}>
              {t('merge.intro')}
            </Typography>
            <UserAutocomplete
              value={other}
              onChange={(u) => {
                setOther(u);
                setSwapped(false);
              }}
              label={t('merge.pickOther')}
              data-testid="merge-pick-other"
            />

            {preview.isLoading && (
              <Box sx={{ mt: 2 }}>
                <CircularProgress size={24} />
              </Box>
            )}
            {preview.isError && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {t('merge.previewFailed')}
              </Alert>
            )}

            {p && (
              <Box sx={{ mt: 3 }}>
                <Grid container spacing={3}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <ProfileColumn
                      title={t('merge.keeps')}
                      profile={p.target}
                      testId="merge-target"
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <ProfileColumn
                      title={t('merge.deletes')}
                      profile={p.source}
                      testId="merge-source"
                    />
                  </Grid>
                </Grid>

                <Typography variant="subtitle2" sx={{ mt: 3 }}>
                  {t('merge.moves')}
                </Typography>
                {moves.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    {t('merge.nothingToMove')}
                  </Typography>
                ) : (
                  <List dense data-testid="merge-moves">
                    {moves.map(([key, n]) => (
                      <ListItem key={key} disableGutters>
                        <ListItemText primary={`${tableLabel(key)}: ${n}`} />
                      </ListItem>
                    ))}
                  </List>
                )}

                {conflicts.length > 0 && (
                  <Alert severity="info" sx={{ mt: 1 }} data-testid="merge-conflicts">
                    {t('merge.conflicts', {
                      list: conflicts.map(([key, n]) => `${tableLabel(key)}: ${n}`).join(', '),
                    })}
                  </Alert>
                )}

                {p.reasons.includes('SOURCE_HAS_LOGIN') && (
                  <Alert
                    severity="warning"
                    sx={{ mt: 2 }}
                    action={
                      <Button color="inherit" size="small" onClick={() => setSwapped(!swapped)}>
                        {t('merge.swap')}
                      </Button>
                    }
                  >
                    {t('merge.reason.SOURCE_HAS_LOGIN')}
                  </Alert>
                )}
                {p.reasons
                  .filter((r) => r !== 'SOURCE_HAS_LOGIN')
                  .map((r) => (
                    <Alert key={r} severity="warning" sx={{ mt: 2 }}>
                      {t(`merge.reason.${r}`, { events: p.blockers.join(', ') })}
                    </Alert>
                  ))}
                {p.canMerge && (
                  <Alert severity="warning" sx={{ mt: 2 }}>
                    {t('merge.warning', { source: p.source.username })}
                  </Alert>
                )}
              </Box>
            )}

            {merge.isError && (
              <Alert severity="error" sx={{ mt: 2 }} data-testid="merge-error">
                {t('merge.failed')}
              </Alert>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>
          {merge.isSuccess ? t('common:actions.close') : t('common:actions.cancel')}
        </Button>
        {!merge.isSuccess && (
          <Button
            variant="contained"
            color="error"
            disabled={!p?.canMerge || merge.isPending}
            onClick={() => merge.mutate()}
            data-testid="merge-confirm"
          >
            {merge.isPending ? <CircularProgress size={20} /> : t('merge.confirm')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default MergeProfileDialog;
