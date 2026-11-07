/**
 * CompanyLogoRequestPanel (#1246): shown above the messages of a Mail Center conversation that is a
 * company logo request. A participant sent a new logo for their company; an organizer sees the
 * current and the new logo side by side and accepts (the company gets the new logo) or declines,
 * with an optional reason that is mailed to the participant. Either way the participant is told.
 */
import React from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Paper, Skeleton, Stack, TextField, Typography } from '@mui/material';
import {
  approveCompanyLogoRequest,
  declineCompanyLogoRequest,
  getCompanyLogoRequest,
  type CompanyLogoRequestView,
} from '@/services/api/userManagementApi';

interface CompanyLogoRequestPanelProps {
  requestId: string;
  /** Called after a decision, so the conversation can refresh (it is closed by the service). */
  onDecided?: () => void;
}

const logoBox = {
  width: 200,
  height: 100,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  border: 1,
  borderColor: 'divider',
  borderRadius: 1,
  bgcolor: '#fff',
  p: 1,
} as const;

export const CompanyLogoRequestPanel: React.FC<CompanyLogoRequestPanelProps> = ({
  requestId,
  onDecided,
}) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const [declining, setDeclining] = React.useState(false);
  const [reason, setReason] = React.useState('');
  const queryKey = ['company-logo-requests', requestId];

  const query = useQuery({
    queryKey,
    queryFn: () => getCompanyLogoRequest(requestId),
    retry: false,
  });

  const settle = (result: CompanyLogoRequestView) => {
    queryClient.setQueryData(queryKey, result);
    void queryClient.invalidateQueries({ queryKey: ['mails'] });
    onDecided?.();
  };
  const approve = useMutation({
    mutationFn: () => approveCompanyLogoRequest(requestId),
    onSuccess: settle,
  });
  const decline = useMutation({
    mutationFn: () => declineCompanyLogoRequest(requestId, reason.trim()),
    onSuccess: settle,
  });

  if (query.isLoading) return <Skeleton variant="rectangular" height={160} />;
  if (query.isError || !query.data) {
    return <Alert severity="error">{t('eventPage.mail.logoRequest.loadError')}</Alert>;
  }
  const request = query.data;
  const company = request.companyDisplayName || request.companyName;
  const busy = approve.isPending || decline.isPending;
  const failed = approve.isError || decline.isError;

  return (
    <Paper variant="outlined" sx={{ p: 2 }} data-testid="logo-request-panel">
      <Stack spacing={2}>
        <Typography variant="subtitle1">
          {t('eventPage.mail.logoRequest.title', { company })}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('eventPage.mail.logoRequest.explain', { name: request.requesterName ?? '' })}
        </Typography>
        {request.note && (
          <Box>
            <Typography variant="caption" color="text.secondary">
              {t('eventPage.mail.logoRequest.note')}
            </Typography>
            <Typography variant="body2" data-testid="logo-request-note">
              {request.note}
            </Typography>
          </Box>
        )}

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
          <Box>
            <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
              {t('eventPage.mail.logoRequest.current')}
            </Typography>
            <Box sx={logoBox}>
              {request.oldLogoUrl ? (
                <Box
                  component="img"
                  src={request.oldLogoUrl}
                  alt={t('eventPage.mail.logoRequest.current')}
                  sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                  data-testid="logo-request-old"
                />
              ) : (
                <Typography
                  variant="body2"
                  color="text.secondary"
                  data-testid="logo-request-no-old"
                >
                  {t('eventPage.mail.logoRequest.noLogo')}
                </Typography>
              )}
            </Box>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
              {t('eventPage.mail.logoRequest.new')}
            </Typography>
            <Box sx={logoBox}>
              <Box
                component="img"
                src={request.newLogoUrl}
                alt={t('eventPage.mail.logoRequest.new')}
                sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                data-testid="logo-request-new"
              />
            </Box>
          </Box>
        </Stack>

        {request.status === 'OPEN' ? (
          <Stack spacing={1}>
            {declining && (
              <TextField
                label={t('eventPage.mail.logoRequest.reason')}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                multiline
                minRows={2}
                inputProps={{ maxLength: 1000, 'data-testid': 'logo-request-reason' }}
                helperText={t('eventPage.mail.logoRequest.reasonHelp')}
              />
            )}
            <Stack direction="row" spacing={1}>
              {!declining && (
                <Button
                  variant="contained"
                  onClick={() => approve.mutate()}
                  disabled={busy}
                  data-testid="logo-request-approve"
                >
                  {t('eventPage.mail.logoRequest.approve')}
                </Button>
              )}
              <Button
                variant="outlined"
                color="error"
                onClick={() => (declining ? decline.mutate() : setDeclining(true))}
                disabled={busy}
                data-testid="logo-request-decline"
              >
                {declining
                  ? t('eventPage.mail.logoRequest.confirmDecline')
                  : t('eventPage.mail.logoRequest.decline')}
              </Button>
              {declining && (
                <Button onClick={() => setDeclining(false)} disabled={busy}>
                  {t('eventPage.mail.logoRequest.cancel')}
                </Button>
              )}
            </Stack>
            {failed && (
              <Alert severity="error" data-testid="logo-request-error">
                {t('eventPage.mail.logoRequest.actionFailed')}
              </Alert>
            )}
          </Stack>
        ) : (
          <Alert
            severity={request.status === 'APPROVED' ? 'success' : 'info'}
            data-testid="logo-request-decided"
          >
            {request.status === 'APPROVED'
              ? t('eventPage.mail.logoRequest.approved', { by: request.decidedBy ?? '' })
              : request.reason
                ? t('eventPage.mail.logoRequest.declinedWithReason', {
                    by: request.decidedBy ?? '',
                    reason: request.reason,
                  })
                : t('eventPage.mail.logoRequest.declined', { by: request.decidedBy ?? '' })}
          </Alert>
        )}
      </Stack>
    </Paper>
  );
};

export default CompanyLogoRequestPanel;
