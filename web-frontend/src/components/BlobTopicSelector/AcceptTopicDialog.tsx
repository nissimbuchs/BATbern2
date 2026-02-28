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
  MenuItem,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { BlueBlobNode } from './types';

const TOPIC_CATEGORIES = [
  { value: 'technical', label: 'Technical' },
  { value: 'management', label: 'Management' },
  { value: 'soft_skills', label: 'Soft Skills' },
  { value: 'industry_trends', label: 'Industry Trends' },
  { value: 'tools_platforms', label: 'Tools & Platforms' },
];

interface AcceptTopicDialogProps {
  open: boolean;
  blob: BlueBlobNode | null;
  hasOrbitingRed: boolean;
  mostRecentEventNumber: number;
  /** Names of other blue blobs present at decision time — included in session note (spec AC25) */
  competingCandidates: string[];
  onConfirm: (note: string, newTopicFields?: { description: string; category: string }) => void;
  onCancel: () => void;
}

const AcceptTopicDialog: React.FC<AcceptTopicDialogProps> = ({
  open,
  blob,
  hasOrbitingRed,
  mostRecentEventNumber,
  competingCandidates,
  onConfirm,
  onCancel,
}) => {
  const { t } = useTranslation('organizer');
  const [overrideReason, setOverrideReason] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('technical');

  const isNewTopic = !blob?.topicCode;

  const handleConfirm = () => {
    if (!blob) return;
    const lastEventNum = blob.relatedPastEventNumbers?.[0];
    const eventsAgo = lastEventNum != null ? mostRecentEventNumber - lastEventNum : null;
    const note = buildNote(
      blob,
      overrideReason,
      lastEventNum ?? null,
      eventsAgo,
      competingCandidates
    );
    onConfirm(note, isNewTopic ? { description, category } : undefined);
    setOverrideReason('');
    setDescription('');
    setCategory('technical');
  };

  const handleCancel = () => {
    setOverrideReason('');
    setDescription('');
    setCategory('technical');
    onCancel();
  };

  const buildNote = (
    b: BlueBlobNode,
    reason: string,
    lastEventNum: number | null,
    eventsAgo: number | null,
    competitors: string[]
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
    if (competitors.length > 0) {
      lines.push(`   Competing candidates: ${competitors.join(', ')}`);
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
        {isNewTopic && (
          <Box sx={{ mt: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            <Typography variant="body2" color="text.secondary">
              This is a new topic — it will be added to the topic backlog.
            </Typography>
            <TextField
              fullWidth
              multiline
              rows={2}
              label={t('acceptTopic.descriptionOptional')}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              variant="outlined"
              size="small"
            />
            <TextField
              select
              fullWidth
              label={t('topicBacklog.createModal.fields.category')}
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              variant="outlined"
              size="small"
            >
              {TOPIC_CATEGORIES.map((c) => (
                <MenuItem key={c.value} value={c.value}>
                  {c.label}
                </MenuItem>
              ))}
            </TextField>
          </Box>
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
            sx={{ mt: 1.5 }}
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
