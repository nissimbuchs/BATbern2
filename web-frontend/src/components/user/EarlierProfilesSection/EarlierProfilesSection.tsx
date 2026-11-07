/**
 * #1183 D — earlier profiles under the logged-in person's name (an imported profile or a guest
 * registration without login). "This is me" sends a confirmation link to that profile's address;
 * opening it while logged in merges the profile into the account. Renders nothing when there is
 * no candidate.
 *
 * #1237: someone who can no longer read that address (a former employer's) asks the organizers
 * instead, with an optional note. While the request is open the profile shows "with the organizers"
 * and offers neither path.
 *
 * #1244: a profile imported with a placeholder address (vorname.nachname@batbern.ch) offers only the
 * request; a confirmation link would reach nobody.
 */

import React, { useState } from 'react';
import { Alert, Box, Button, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  listProfileClaimCandidates,
  requestProfileClaim,
  requestProfileMerge,
} from '@/services/api/userAccountApi';

const CANDIDATES_KEY = ['users', 'me', 'profile-claims'];

/** The 409 error code of a refused merge request, if any. */
function conflictCode(error: unknown): string | undefined {
  const data = (error as { response?: { status?: number; data?: { errorCode?: string } } })
    ?.response;
  return data?.status === 409 ? data.data?.errorCode : undefined;
}

export const EarlierProfilesSection: React.FC<{ variant?: 'section' | 'banner' }> = ({
  variant = 'section',
}) => {
  const { t } = useTranslation('userManagement');
  const queryClient = useQueryClient();
  const [sentTo, setSentTo] = useState<Record<string, boolean>>({});
  const [askingFor, setAskingFor] = useState<string | null>(null);
  const [note, setNote] = useState('');

  const candidates = useQuery({
    queryKey: CANDIDATES_KEY,
    queryFn: listProfileClaimCandidates,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const claim = useMutation({
    mutationFn: (candidateId: string) => requestProfileClaim(candidateId),
    onSuccess: (_data, candidateId) => setSentTo((s) => ({ ...s, [candidateId]: true })),
  });

  const ask = useMutation({
    mutationFn: (candidateId: string) => requestProfileMerge(candidateId, note),
    onSuccess: () => {
      setAskingFor(null);
      setNote('');
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY });
    },
  });

  const list = candidates.data ?? [];
  if (list.length === 0) {
    return null;
  }
  const askError = conflictCode(ask.error);

  return (
    <Box sx={variant === 'banner' ? { mb: 3 } : { mt: 4 }} data-testid="earlier-profiles-section">
      <Alert severity="info">
        <Typography variant="subtitle2" gutterBottom>
          {t('claim.sectionTitle')}
        </Typography>
        <Typography variant="body2">{t('claim.sectionHelp')}</Typography>
        <Stack spacing={2} sx={{ mt: 2 }}>
          {list.map((c) => (
            <Box key={c.candidateId} data-testid={`claim-candidate-${c.candidateId}`}>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1}
                alignItems={{ xs: 'flex-start', sm: 'center' }}
                justifyContent="space-between"
              >
                <Box>
                  <Typography variant="body2">{c.maskedEmail}</Typography>
                  {c.companyName && (
                    <Typography variant="caption" color="text.secondary">
                      {c.companyName}
                    </Typography>
                  )}
                </Box>
                {c.requestPending ? (
                  <Typography variant="body2" data-testid={`claim-pending-${c.candidateId}`}>
                    {t('claim.requestPending')}
                  </Typography>
                ) : c.placeholderAddress ? (
                  // #1244: imported without a known address; a link would reach nobody
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    data-testid={`claim-placeholder-${c.candidateId}`}
                  >
                    {t('claim.placeholderAddress')}
                  </Typography>
                ) : sentTo[c.candidateId] ? (
                  <Typography variant="body2" data-testid={`claim-sent-${c.candidateId}`}>
                    {t('claim.sent', { email: c.maskedEmail })}
                  </Typography>
                ) : (
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={claim.isPending}
                    onClick={() => claim.mutate(c.candidateId)}
                    data-testid={`claim-button-${c.candidateId}`}
                  >
                    {t('claim.thisIsMe')}
                  </Button>
                )}
              </Stack>

              {!c.requestPending && askingFor !== c.candidateId && (
                <Button
                  size="small"
                  variant="text"
                  sx={{ px: 0, mt: 0.5, textTransform: 'none' }}
                  onClick={() => {
                    ask.reset();
                    setAskingFor(c.candidateId);
                  }}
                  data-testid={`claim-no-access-${c.candidateId}`}
                >
                  {c.placeholderAddress ? t('claim.askOrganizers') : t('claim.noAccess')}
                </Button>
              )}

              {askingFor === c.candidateId && (
                <Stack
                  spacing={1}
                  sx={{ mt: 1 }}
                  data-testid={`claim-request-form-${c.candidateId}`}
                >
                  <Typography variant="body2">{t('claim.noAccessHelp')}</Typography>
                  <TextField
                    size="small"
                    label={t('claim.noteLabel')}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    multiline
                    minRows={2}
                    inputProps={{ maxLength: 500, 'data-testid': `claim-note-${c.candidateId}` }}
                  />
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      variant="contained"
                      disabled={ask.isPending}
                      onClick={() => ask.mutate(c.candidateId)}
                      data-testid={`claim-send-request-${c.candidateId}`}
                    >
                      {t('claim.sendRequest')}
                    </Button>
                    <Button
                      size="small"
                      onClick={() => setAskingFor(null)}
                      disabled={ask.isPending}
                    >
                      {t('claim.cancel')}
                    </Button>
                  </Stack>
                  {ask.isError && (
                    <Typography variant="body2" color="error" data-testid="claim-request-error">
                      {askError === 'MERGE_REQUEST_DECLINED'
                        ? t('claim.requestDeclinedRecently')
                        : askError === 'MERGE_REQUEST_OPEN'
                          ? t('claim.requestPending')
                          : t('claim.requestFailed')}
                    </Typography>
                  )}
                </Stack>
              )}
            </Box>
          ))}
        </Stack>
        {claim.isError && (
          <Typography variant="body2" color="error">
            {t('claim.sendFailed')}
          </Typography>
        )}
      </Alert>
    </Box>
  );
};

export default EarlierProfilesSection;
