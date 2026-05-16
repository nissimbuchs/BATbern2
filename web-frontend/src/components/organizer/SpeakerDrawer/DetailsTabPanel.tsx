import React from 'react';
import { Box, Typography, Chip, Button } from '@mui/material';
import { AttachFile as AttachFileIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { formatDateTime } from '@/utils/date';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface DetailsTabPanelProps {
  speaker: SpeakerPoolEntry;
}

export const DetailsTabPanel: React.FC<DetailsTabPanelProps> = ({ speaker }) => {
  const { t, i18n } = useTranslation('organizer');
  const fmt = (dateString: string) => formatDateTime(new Date(dateString), i18n.language);

  return (
    <Box sx={{ p: 2, overflow: 'auto' }}>
      {/* Response Details */}
      {speaker.acceptedAt && (
        <Box sx={{ bgcolor: '#e8f5e9', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#1b5e20' }} gutterBottom>
            {t('speakers.responseDetails')}
          </Typography>
          <Typography variant="body2" sx={{ color: '#212121' }}>
            {t('speakers.acceptedAt')}: {fmt(speaker.acceptedAt)}
          </Typography>
          {speaker.preferredTimeSlot && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.preferredTimeSlot')}: {speaker.preferredTimeSlot}
            </Typography>
          )}
          {speaker.travelRequirements && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.travelRequirements')}: {speaker.travelRequirements}
            </Typography>
          )}
          {speaker.technicalRequirements && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.technicalRequirements')}: {speaker.technicalRequirements}
            </Typography>
          )}
          {speaker.initialPresentationTitle && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.initialTitle')}: {speaker.initialPresentationTitle}
            </Typography>
          )}
          {speaker.preferenceComments && (
            <Typography variant="body2" sx={{ color: '#212121', mt: 1, fontStyle: 'italic' }}>
              {t('speakers.comments')}: {speaker.preferenceComments}
            </Typography>
          )}
        </Box>
      )}

      {/* Decline Details */}
      {speaker.status === 'DECLINED' && speaker.declineReason && (
        <Box sx={{ bgcolor: '#ffebee', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#b71c1c' }} gutterBottom>
            {t('speakers.declineDetails')}
          </Typography>
          {speaker.declinedAt && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.declinedAt')}: {fmt(speaker.declinedAt)}
            </Typography>
          )}
          <Typography variant="body2" sx={{ color: '#212121' }}>
            {t('speakers.declineReason')}: {speaker.declineReason}
          </Typography>
        </Box>
      )}

      {/* Revision Feedback */}
      {speaker.contentStatus === 'REVISION_NEEDED' && speaker.notes && (
        <Box sx={{ bgcolor: '#ffebee', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#b71c1c' }} gutterBottom>
            {t('speakers.revisionRequested', 'Revision Requested')}
          </Typography>
          <Typography variant="body2" sx={{ color: '#212121', whiteSpace: 'pre-wrap' }}>
            {speaker.notes}
          </Typography>
        </Box>
      )}

      {/* Submitted Content */}
      {speaker.submittedTitle && (
        <Box sx={{ bgcolor: '#e8f5e9', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#1b5e20' }} gutterBottom>
            {t('speakers.submittedContent', 'Submitted Content')}
            {speaker.contentStatus && (
              <Chip
                label={speaker.contentStatus}
                size="small"
                color={
                  speaker.contentStatus === 'APPROVED'
                    ? 'success'
                    : speaker.contentStatus === 'SUBMITTED'
                      ? 'info'
                      : 'warning'
                }
                sx={{ ml: 1 }}
              />
            )}
          </Typography>
          <Typography variant="body2" sx={{ color: '#212121' }} fontWeight="medium" gutterBottom>
            {speaker.submittedTitle}
          </Typography>
          {speaker.submittedAbstract && (
            <Typography
              variant="body2"
              sx={{
                color: '#212121',
                mt: 1,
                whiteSpace: 'pre-wrap',
              }}
            >
              {speaker.submittedAbstract}
            </Typography>
          )}
          {speaker.materialFileName && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
              <AttachFileIcon sx={{ fontSize: 16, color: '#1b5e20' }} />
              {speaker.materialCloudFrontUrl ? (
                <Button
                  variant="text"
                  href={speaker.materialCloudFrontUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  size="small"
                  sx={{ textTransform: 'none', color: '#1b5e20', p: 0, minWidth: 0 }}
                >
                  {speaker.materialFileName}
                </Button>
              ) : (
                <Typography variant="body2" sx={{ color: '#212121' }}>
                  {speaker.materialFileName}
                </Typography>
              )}
            </Box>
          )}
          {speaker.contentSubmittedAt && (
            <Typography variant="caption" sx={{ color: '#424242', mt: 1, display: 'block' }}>
              {t('speakers.submittedAt', 'Submitted')}: {fmt(speaker.contentSubmittedAt)}
            </Typography>
          )}
        </Box>
      )}

      {/* No details message if nothing to show */}
      {!speaker.acceptedAt &&
        !(speaker.status === 'DECLINED' && speaker.declineReason) &&
        !(speaker.contentStatus === 'REVISION_NEEDED' && speaker.notes) &&
        !speaker.submittedTitle && (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <Typography variant="body2" color="text.secondary">
              {t('speakers.noDetails')}
            </Typography>
          </Box>
        )}
    </Box>
  );
};
