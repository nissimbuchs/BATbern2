/**
 * #1183 D — earlier profiles under the logged-in person's name (an imported profile or a guest
 * registration without login). "This is me" sends a confirmation link to that profile's address;
 * opening it while logged in merges the profile into the account. Renders nothing when there is
 * no candidate.
 */

import React, { useState } from 'react';
import { Alert, Box, Button, List, ListItem, ListItemText, Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listProfileClaimCandidates, requestProfileClaim } from '@/services/api/userAccountApi';

export const EarlierProfilesSection: React.FC<{ variant?: 'section' | 'banner' }> = ({
  variant = 'section',
}) => {
  const { t } = useTranslation('userManagement');
  const [sentTo, setSentTo] = useState<Record<string, boolean>>({});

  const candidates = useQuery({
    queryKey: ['users', 'me', 'profile-claims'],
    queryFn: listProfileClaimCandidates,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const claim = useMutation({
    mutationFn: (candidateId: string) => requestProfileClaim(candidateId),
    onSuccess: (_data, candidateId) => setSentTo((s) => ({ ...s, [candidateId]: true })),
  });

  const list = candidates.data ?? [];
  if (list.length === 0) {
    return null;
  }

  return (
    <Box sx={variant === 'banner' ? { mb: 3 } : { mt: 4 }} data-testid="earlier-profiles-section">
      <Alert severity="info">
        <Typography variant="subtitle2" gutterBottom>
          {t('claim.sectionTitle')}
        </Typography>
        <Typography variant="body2">{t('claim.sectionHelp')}</Typography>
        <List dense>
          {list.map((c) => (
            <ListItem
              key={c.candidateId}
              disableGutters
              secondaryAction={
                sentTo[c.candidateId] ? (
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
                )
              }
            >
              <ListItemText
                primary={c.maskedEmail}
                secondary={c.companyName ?? undefined}
                sx={{ pr: 18 }}
              />
            </ListItem>
          ))}
        </List>
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
