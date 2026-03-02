/**
 * WaitlistSection Component
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC5)
 *
 * Collapsible organizer section showing waitlisted registrations with
 * manual promotion action.
 */

import React, { useState } from 'react';
import {
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Typography,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Button,
  Box,
  Alert,
  Snackbar,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import {
  getEventRegistrations,
  promoteFromWaitlist,
} from '@/services/api/eventRegistrationService';

interface WaitlistSectionProps {
  eventCode: string;
  waitlistCount: number;
}

const WaitlistSection: React.FC<WaitlistSectionProps> = ({ eventCode, waitlistCount }) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const [promotingCode, setPromotingCode] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['event-registrations', eventCode, 'waitlist'],
    queryFn: () =>
      getEventRegistrations(eventCode, {
        filters: { status: ['WAITLIST'] },
        pagination: { page: 1, limit: 100 },
      }),
    enabled: waitlistCount > 0,
  });

  const handlePromote = async (registrationCode: string) => {
    setPromotingCode(registrationCode);
    setErrorMsg(null);
    try {
      await promoteFromWaitlist(eventCode, registrationCode);
      // Invalidate both waitlist and main registration lists, plus event (counts)
      queryClient.invalidateQueries({ queryKey: ['event-registrations', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['events', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      setSuccessMsg(t('eventPage.participantsTab.waitlistPromoteSuccess'));
    } catch {
      setErrorMsg(t('eventPage.participantsTab.waitlistPromoteError'));
    } finally {
      setPromotingCode(null);
    }
  };

  return (
    <>
      <Accordion sx={{ mt: 2 }}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle1" fontWeight="medium">
            {t('eventPage.participantsTab.waitlistSection', { count: waitlistCount })}
          </Typography>
        </AccordionSummary>
        <AccordionDetails sx={{ p: 0 }}>
          {isLoading && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <BATbernLoader size={48} />
            </Box>
          )}
          {error && (
            <Alert severity="error" sx={{ m: 2 }}>
              {String(error)}
            </Alert>
          )}
          {!isLoading && !error && data && data.data.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
              {t('eventPage.participantsTab.waitlistEmpty')}
            </Typography>
          )}
          {!isLoading && !error && data && data.data.length > 0 && (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Company</TableCell>
                  <TableCell>Registered On</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.data.map((reg, idx) => (
                  <TableRow key={reg.registrationCode}>
                    <TableCell>{idx + 1}</TableCell>
                    <TableCell>
                      {reg.firstName} {reg.lastName}
                    </TableCell>
                    <TableCell>{reg.email}</TableCell>
                    <TableCell>{reg.company?.name ?? '—'}</TableCell>
                    <TableCell>
                      {reg.registrationDate
                        ? new Date(reg.registrationDate).toLocaleDateString()
                        : '—'}
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        variant="outlined"
                        color="success"
                        disabled={promotingCode === reg.registrationCode}
                        onClick={() => handlePromote(reg.registrationCode)}
                      >
                        {t('eventPage.participantsTab.waitlistPromote')}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </AccordionDetails>
      </Accordion>

      <Snackbar
        open={!!successMsg}
        autoHideDuration={4000}
        onClose={() => setSuccessMsg(null)}
        message={successMsg}
      />
      {errorMsg && (
        <Alert severity="error" sx={{ mt: 1 }} onClose={() => setErrorMsg(null)}>
          {errorMsg}
        </Alert>
      )}
    </>
  );
};

export default WaitlistSection;
