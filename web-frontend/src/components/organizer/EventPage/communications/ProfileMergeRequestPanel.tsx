/**
 * ProfileMergeRequestPanel (#1237): shown above the messages of a Mail Center conversation that is a
 * profile merge request. Someone could not confirm by mail that an earlier profile is theirs (the
 * address belonged to a former employer), so an organizer decides: both profiles side by side, what
 * would move, the person's note. Approve runs the organizer merge without the earlier profile's
 * addresses; decline needs a reason, which is mailed to the person. Either way the person is told.
 */
import React from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import {
  approveProfileMergeRequest,
  declineProfileMergeRequest,
  getProfileMergeRequest,
  type ProfileMergeRequestView,
} from '@/services/api/userManagementApi';

type Summary = NonNullable<ProfileMergeRequestView['preview']>['target'];

interface ProfileMergeRequestPanelProps {
  requestId: string;
  /** Called after a decision, so the conversation can refresh (it is closed by the service). */
  onDecided?: () => void;
}

export const ProfileMergeRequestPanel: React.FC<ProfileMergeRequestPanelProps> = ({
  requestId,
  onDecided,
}) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const [declining, setDeclining] = React.useState(false);
  const [reason, setReason] = React.useState('');
  const queryKey = ['profile-merge-requests', requestId];

  const query = useQuery({
    queryKey,
    queryFn: () => getProfileMergeRequest(requestId),
    retry: false,
  });

  const settle = (result: ProfileMergeRequestView) => {
    queryClient.setQueryData(queryKey, result);
    void queryClient.invalidateQueries({ queryKey: ['mails'] });
    onDecided?.();
  };
  const approve = useMutation({
    mutationFn: () => approveProfileMergeRequest(requestId),
    onSuccess: settle,
  });
  const decline = useMutation({
    mutationFn: () => declineProfileMergeRequest(requestId, reason.trim()),
    onSuccess: settle,
  });

  if (query.isLoading) return <Skeleton variant="rectangular" height={160} />;
  if (query.isError || !query.data) {
    return <Alert severity="error">{t('eventPage.mail.mergeRequest.loadError')}</Alert>;
  }
  const request = query.data;
  const preview = request.preview;
  const busy = approve.isPending || decline.isPending;
  const failed = approve.isError || decline.isError;

  const row = (label: string, value: (s: Summary) => React.ReactNode) =>
    preview && (
      <TableRow>
        <TableCell component="th" scope="row">
          {label}
        </TableCell>
        <TableCell>{value(preview.target)}</TableCell>
        <TableCell>{value(preview.source)}</TableCell>
      </TableRow>
    );

  return (
    <Paper variant="outlined" sx={{ p: 2 }} data-testid="merge-request-panel">
      <Stack spacing={2}>
        <Typography variant="subtitle1">{t('eventPage.mail.mergeRequest.title')}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t('eventPage.mail.mergeRequest.explain')}
        </Typography>
        {request.note && (
          <Box>
            <Typography variant="caption" color="text.secondary">
              {t('eventPage.mail.mergeRequest.note')}
            </Typography>
            <Typography variant="body2" data-testid="merge-request-note">
              {request.note}
            </Typography>
          </Box>
        )}

        {preview && (
          <Table size="small" data-testid="merge-request-profiles">
            <TableHead>
              <TableRow>
                <TableCell />
                <TableCell>{t('eventPage.mail.mergeRequest.target')}</TableCell>
                <TableCell>{t('eventPage.mail.mergeRequest.source')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {row(
                t('eventPage.mail.mergeRequest.name'),
                (s) => `${s.firstName ?? ''} ${s.lastName ?? ''}`
              )}
              {row(t('eventPage.mail.mergeRequest.username'), (s) => s.username)}
              {row(t('eventPage.mail.mergeRequest.email'), (s) => s.email)}
              {row(
                t('eventPage.mail.mergeRequest.additionalEmails'),
                (s) => (s.additionalEmails ?? []).join(', ') || '–'
              )}
              {row(t('eventPage.mail.mergeRequest.company'), (s) => s.companyId ?? '–')}
              {row(t('eventPage.mail.mergeRequest.roles'), (s) => (s.roles ?? []).join(', '))}
            </TableBody>
          </Table>
        )}

        {preview && (
          <Box data-testid="merge-request-moves">
            <Typography variant="caption" color="text.secondary">
              {t('eventPage.mail.mergeRequest.moves')}
            </Typography>
            {Object.keys(preview.moves ?? {}).length === 0 ? (
              <Typography variant="body2">
                {t('eventPage.mail.mergeRequest.nothingMoves')}
              </Typography>
            ) : (
              Object.entries(preview.moves ?? {}).map(([what, count]) => (
                <Typography variant="body2" key={what}>
                  {what}: {count}
                </Typography>
              ))
            )}
            {(preview.blockers ?? []).map((blocker) => (
              <Alert severity="warning" key={blocker} sx={{ mt: 1 }}>
                {blocker}
              </Alert>
            ))}
            {!preview.canMerge && (
              <Alert severity="warning" sx={{ mt: 1 }} data-testid="merge-request-cannot-merge">
                {t('eventPage.mail.mergeRequest.cannotMerge', {
                  reasons: (preview.reasons ?? []).join(', '),
                })}
              </Alert>
            )}
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {t('eventPage.mail.mergeRequest.addressesNotCarried')}
            </Typography>
          </Box>
        )}

        {request.status === 'OPEN' ? (
          <Stack spacing={1}>
            {declining && (
              <TextField
                label={t('eventPage.mail.mergeRequest.reason')}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                multiline
                minRows={2}
                inputProps={{ maxLength: 1000, 'data-testid': 'merge-request-reason' }}
                helperText={t('eventPage.mail.mergeRequest.reasonHelp')}
              />
            )}
            <Stack direction="row" spacing={1}>
              {!declining && (
                <Button
                  variant="contained"
                  onClick={() => approve.mutate()}
                  disabled={busy || !preview?.canMerge}
                  data-testid="merge-request-approve"
                >
                  {t('eventPage.mail.mergeRequest.approve')}
                </Button>
              )}
              <Button
                variant="outlined"
                color="error"
                onClick={() => (declining ? decline.mutate() : setDeclining(true))}
                disabled={busy || (declining && !reason.trim())}
                data-testid="merge-request-decline"
              >
                {declining
                  ? t('eventPage.mail.mergeRequest.confirmDecline')
                  : t('eventPage.mail.mergeRequest.decline')}
              </Button>
              {declining && (
                <Button onClick={() => setDeclining(false)} disabled={busy}>
                  {t('eventPage.mail.mergeRequest.cancel')}
                </Button>
              )}
            </Stack>
            {failed && (
              <Alert severity="error" data-testid="merge-request-error">
                {t('eventPage.mail.mergeRequest.actionFailed')}
              </Alert>
            )}
          </Stack>
        ) : (
          <Alert
            severity={request.status === 'APPROVED' ? 'success' : 'info'}
            data-testid="merge-request-decided"
          >
            {request.status === 'APPROVED'
              ? t('eventPage.mail.mergeRequest.approved', { by: request.decidedBy ?? '' })
              : t('eventPage.mail.mergeRequest.declined', {
                  by: request.decidedBy ?? '',
                  reason: request.reason ?? '',
                })}
          </Alert>
        )}
      </Stack>
    </Paper>
  );
};

export default ProfileMergeRequestPanel;
