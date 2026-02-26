/**
 * Accept Topic Dialog
 * Story 10.4: Blob Topic Selector (Task 14)
 * Shows on double-click of a blue blob; confirms topic selection for event.
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Avatar,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { BlueBlobNode } from './types';

interface AcceptTopicDialogProps {
  open: boolean;
  blob: BlueBlobNode | null;
  hasOrbitingRed: boolean;
  mostRecentEventNumber: number;
  onConfirm: (topicCode: string, note: string) => void;
  onCancel: () => void;
}

const AcceptTopicDialog: React.FC<AcceptTopicDialogProps> = ({
  open,
  blob,
  hasOrbitingRed,
  mostRecentEventNumber,
  onConfirm,
  onCancel,
}) => {
  const { t } = useTranslation('organizer');
  const [overrideReason, setOverrideReason] = useState('');

  const handleConfirm = () => {
    if (!blob) return;
    const lastEventNum = blob.relatedPastEventNumbers?.[0];
    const eventsAgo = lastEventNum != null ? mostRecentEventNumber - lastEventNum : null;
    const note = buildNote(blob, overrideReason, lastEventNum ?? null, eventsAgo);
    onConfirm(blob.name, note);
    setOverrideReason('');
  };

  const handleCancel = () => {
    setOverrideReason('');
    onCancel();
  };

  const buildNote = (
    b: BlueBlobNode,
    reason: string,
    lastEventNum: number | null,
    eventsAgo: number | null
  ): string => {
    const lines: string[] = [
      `✅ Selected Topic: ${b.name}`,
      `   Partner alignment: ${b.absorbedLogos.map((l) => l.companyName).join(', ')} (${b.absorbedLogos.length}/9 partners)`,
    ];
    if (lastEventNum != null && eventsAgo != null) {
      const cautionNote = eventsAgo <= 6 ? ' — within caution zone' : '';
      lines.push(
        `   Last covered: BATbern #${lastEventNum} (${eventsAgo} events ago${cautionNote})`
      );
    }
    if (reason) {
      lines.push(`   Override reason: ${reason}`);
    }
    return lines.join('\n');
  };

  if (!blob) return null;

  const lastEventNum = blob.relatedPastEventNumbers?.[0];
  const eventsAgo = lastEventNum != null ? mostRecentEventNumber - lastEventNum : null;

  return (
    <Dialog open={open} onClose={handleCancel} maxWidth="sm" fullWidth>
      <DialogTitle>
        {t('blobSelector.accept.title', {
          topic: blob.name,
          defaultValue: `Select Topic: ${blob.name}`,
        })}
      </DialogTitle>
      <DialogContent>
        {blob.absorbedLogos.length > 0 && (
          <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
            {blob.absorbedLogos.map((logo) => (
              <Avatar
                key={logo.companyName}
                src={logo.logoUrl}
                alt={logo.companyName}
                sx={{ width: 32, height: 32 }}
              />
            ))}
          </Box>
        )}
        <Typography variant="body2" sx={{ mb: 1 }}>
          {t('blobSelector.accept.partnerAlignment', {
            count: blob.absorbedLogos.length,
            defaultValue: `${blob.absorbedLogos.length} of 9 partners aligned`,
          })}
        </Typography>
        {eventsAgo != null && lastEventNum != null && (
          <Typography variant="body2" color="warning.main" sx={{ mb: 2 }}>
            {t('blobSelector.accept.lastCovered', {
              num: lastEventNum,
              count: eventsAgo,
              defaultValue: `Last covered: BATbern #${lastEventNum} (${eventsAgo} events ago)`,
            })}
          </Typography>
        )}
        {hasOrbitingRed && (
          <TextField
            fullWidth
            multiline
            rows={2}
            label={t('blobSelector.accept.overrideReason', {
              defaultValue: 'Why are we revisiting this topic? (optional)',
            })}
            value={overrideReason}
            onChange={(e) => setOverrideReason(e.target.value)}
            variant="outlined"
            size="small"
            sx={{ mt: 1 }}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel}>
          {t('blobSelector.accept.cancel', { defaultValue: 'Keep exploring' })}
        </Button>
        <Button onClick={handleConfirm} variant="contained" color="primary">
          {t('blobSelector.accept.confirm', { defaultValue: 'Select this topic' })}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default AcceptTopicDialog;
