/**
 * #1251 story 6 — an organizer merges another company into this one.
 *
 * Pick the other company (or open it preset from the duplicates list), read the preview (both
 * sides, what moves, what collides, which empty fields are taken over, why it may be refused),
 * swap the direction if the preview allows, confirm. Mirrors the profile merge dialog.
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
import { CompanyAutocomplete } from '@/components/organizer/PartnerManagement/CompanyAutocomplete';
import {
  apiErrorCode,
  getCompanyMergePreview,
  mergeCompany,
  type CompanyMergePreview,
} from '@/services/api/companyMergeApi';
import type { components } from '@/types/generated/company-api.types';

type Picked = components['schemas']['CompanySearchResponse'];
type Side = CompanyMergePreview['source'];

const MERGE_ERRORS = [
  'MERGE_BLOCKED_BOTH_PARTNERS',
  'MERGE_BLOCKED',
  'MERGE_NOT_ALLOWED',
  'MERGE_INCOMPLETE',
];

interface MergeCompanyDialogProps {
  open: boolean;
  onClose: () => void;
  /** The company whose page is open, or the suggested company to keep (key). */
  companyName: string;
  /** Preset other company (from the duplicates list); hides the picker. */
  otherName?: string;
  /** Stay on the current page after the merge instead of opening the company that stays. */
  stayOnPage?: boolean;
  /** Called after a successful merge with the key of the company that stays. */
  onMerged?: (targetName: string) => void;
}

const SideColumn: React.FC<{ title: string; side: Side; testId: string }> = ({
  title,
  side,
  testId,
}) => {
  const { t } = useTranslation('common');
  return (
    <Box data-testid={testId}>
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
        {title}
      </Typography>
      <Typography variant="body1">{side.displayName || side.name}</Typography>
      <Typography variant="body2" color="text.secondary">
        {side.name}
      </Typography>
      {side.website && <Typography variant="body2">{side.website}</Typography>}
      <Box sx={{ mt: 1, display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
        <Chip size="small" label={t('company.duplicates.people', { count: side.userCount })} />
        {side.logo && (
          <Chip size="small" variant="outlined" label={t('company.merge.fields.logo')} />
        )}
      </Box>
    </Box>
  );
};

export const MergeCompanyDialog: React.FC<MergeCompanyDialogProps> = ({
  open,
  onClose,
  companyName,
  otherName,
  stayOnPage = false,
  onMerged,
}) => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [other, setOther] = useState<Picked | null>(null);
  const [swapped, setSwapped] = useState(false);
  const [merged, setMerged] = useState(false);

  const otherKey = otherName ?? other?.name;
  const target = swapped && otherKey ? otherKey : companyName;
  const source = swapped ? companyName : otherKey;

  const preview = useQuery({
    queryKey: ['companies', 'merge-preview', target, source],
    queryFn: () => getCompanyMergePreview(target, source as string),
    // not after the merge: the source company is gone, a refetch would only produce a 404
    enabled: open && !!source && !merged,
    retry: false,
  });

  const merge = useMutation({
    mutationFn: () => mergeCompany(target, source as string),
    onSuccess: async (result) => {
      setMerged(true);
      // the company that was open is gone after a swapped merge: leave its page first
      if (!stayOnPage && result.targetName !== companyName) {
        navigate(`/organizer/companies/${encodeURIComponent(result.targetName)}`);
      }
      // every company list and detail, except what belongs to the company that no longer exists
      await queryClient.invalidateQueries({
        predicate: (q) =>
          (q.queryKey[0] === 'companies' || q.queryKey[0] === 'company') &&
          q.queryKey[1] !== 'merge-preview' &&
          q.queryKey[1] !== result.sourceName,
      });
      onMerged?.(result.targetName);
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
  const tableLabel = (key: string) => {
    const table = key.split('.')[0];
    return t(`company.merge.tables.${table}`, { defaultValue: table });
  };
  const moves = p ? Object.entries(p.moves).filter(([, n]) => n > 0) : [];
  const conflicts = p ? Object.entries(p.conflicts).filter(([, n]) => n > 0) : [];
  const fieldLabel = (f: string) => t(`company.merge.fields.${f}`, { defaultValue: f });
  const mergeError = apiErrorCode(merge.error);

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
      data-testid="merge-company-dialog"
    >
      <DialogTitle>{t('company.merge.title')}</DialogTitle>
      <DialogContent>
        {merge.isSuccess ? (
          <Alert severity="success" data-testid="merge-company-success">
            {t('company.merge.success', {
              source: merge.data.sourceName,
              target: merge.data.targetName,
            })}
          </Alert>
        ) : (
          <>
            <Typography variant="body2" sx={{ mb: 2 }}>
              {t('company.merge.intro')}
            </Typography>
            {!otherName && (
              <CompanyAutocomplete
                value={other}
                onChange={(c) => {
                  setOther(c);
                  setSwapped(false);
                }}
                label={t('company.merge.pickOther')}
                allowCreate={false}
              />
            )}

            {preview.isLoading && (
              <Box sx={{ mt: 2 }}>
                <CircularProgress size={24} />
              </Box>
            )}
            {preview.isError && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {apiErrorCode(preview.error) === 'MERGE_PREVIEW_INCOMPLETE'
                  ? t('company.merge.previewIncomplete')
                  : t('company.merge.previewFailed')}
              </Alert>
            )}

            {p && (
              <Box sx={{ mt: 3 }}>
                <Grid container spacing={3}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <SideColumn
                      title={t('company.merge.keeps')}
                      side={p.target}
                      testId="merge-company-target"
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <SideColumn
                      title={t('company.merge.deletes')}
                      side={p.source}
                      testId="merge-company-source"
                    />
                  </Grid>
                </Grid>

                <Typography variant="subtitle2" sx={{ mt: 3 }}>
                  {t('company.merge.moves')}
                </Typography>
                {moves.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    {t('company.merge.nothingToMove')}
                  </Typography>
                ) : (
                  <List dense data-testid="merge-company-moves">
                    {moves.map(([key, n]) => (
                      <ListItem key={key} disableGutters>
                        <ListItemText primary={`${tableLabel(key)}: ${n}`} />
                      </ListItem>
                    ))}
                  </List>
                )}

                {conflicts.length > 0 && (
                  <Alert severity="info" sx={{ mt: 1 }} data-testid="merge-company-conflicts">
                    {t('company.merge.conflicts', {
                      list: conflicts.map(([key, n]) => `${tableLabel(key)}: ${n}`).join(', '),
                    })}
                  </Alert>
                )}
                {p.fills.length > 0 && (
                  <Alert severity="info" sx={{ mt: 1 }} data-testid="merge-company-fills">
                    {t('company.merge.fills', { list: p.fills.map(fieldLabel).join(', ') })}
                  </Alert>
                )}

                {p.reasons.map((r) => (
                  <Alert key={r} severity="warning" sx={{ mt: 2 }}>
                    {t(`company.merge.reason.${r}`, {
                      defaultValue: r,
                      blockers: p.blockers.join(', '),
                    })}
                  </Alert>
                ))}
                {p.canSwap && (
                  <Box sx={{ mt: 2 }}>
                    <Button
                      size="small"
                      onClick={() => setSwapped(!swapped)}
                      data-testid="merge-company-swap"
                    >
                      {t('company.merge.swap')}
                    </Button>
                  </Box>
                )}
                {p.canMerge && (
                  <Alert severity="warning" sx={{ mt: 2 }}>
                    {t('company.merge.warning', { source: p.source.name })}
                  </Alert>
                )}
              </Box>
            )}

            {merge.isError && (
              <Alert severity="error" sx={{ mt: 2 }} data-testid="merge-company-error">
                {t(
                  `company.merge.error.${mergeError && MERGE_ERRORS.includes(mergeError) ? mergeError : 'default'}`
                )}
              </Alert>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>
          {merge.isSuccess ? t('actions.close') : t('actions.cancel')}
        </Button>
        {!merge.isSuccess && (
          <Button
            variant="contained"
            color="error"
            disabled={!p?.canMerge || merge.isPending || preview.isFetching}
            onClick={() => merge.mutate()}
            data-testid="merge-company-confirm"
          >
            {merge.isPending ? <CircularProgress size={20} /> : t('company.merge.confirm')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default MergeCompanyDialog;
