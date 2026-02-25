/**
 * EmailTemplatePreviewModal (Story 10.2)
 *
 * Renders a preview of the email template in an iframe.
 * For content templates with layoutKey: fetches layout and merges client-side.
 * For standalone / layout templates: renders htmlBody directly.
 */

import React, { useEffect, useState } from 'react';
import {
  Box,
  Chip,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Typography,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { emailTemplateService } from '@/services/emailTemplateService';
import type { EmailTemplateResponse } from '@/hooks/useEmailTemplates';

const CONTENT_PLACEHOLDER = `
<div style="background:#f0f0f0;border:2px dashed #999;padding:24px;margin:16px;text-align:center;color:#666;font-family:sans-serif;border-radius:4px;">
  [CONTENT AREA]
</div>`;

/** Replace system/layout variables with sensible preview values. */
function substitutePreviewVariables(html: string): string {
  const previewValues: Record<string, string> = {
    logoUrl: '/BATbern_white_logo.svg',
    currentYear: String(new Date().getFullYear()),
    dashboardLink: '#',
    eventUrl: '#',
    supportUrl: '#',
  };
  return html.replace(
    /\{\{(\w+)\}\}/g,
    (_match, key: string) => previewValues[key] ?? `{{${key}}}`
  );
}

interface Props {
  template: EmailTemplateResponse;
  onClose: () => void;
}

export const EmailTemplatePreviewModal: React.FC<Props> = ({ template, onClose }) => {
  const { t } = useTranslation();
  const [mergedHtml, setMergedHtml] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function buildPreview() {
      setLoading(true);
      try {
        if (template.isLayout) {
          // Replace {{content}} with a visible placeholder, then substitute system vars
          setMergedHtml(
            substitutePreviewVariables(
              template.htmlBody.replace('{{content}}', CONTENT_PLACEHOLDER)
            )
          );
        } else if (template.layoutKey) {
          // Fetch layout and merge
          try {
            const layout = await emailTemplateService.getTemplate(
              template.layoutKey,
              template.locale
            );
            setMergedHtml(
              substitutePreviewVariables(layout.htmlBody.replace('{{content}}', template.htmlBody))
            );
          } catch {
            // Layout not found — show content directly
            setMergedHtml(substitutePreviewVariables(template.htmlBody));
          }
        } else {
          setMergedHtml(substitutePreviewVariables(template.htmlBody));
        }
      } finally {
        setLoading(false);
      }
    }
    buildPreview();
  }, [template]);

  const variables = template.variables ? Object.keys(template.variables) : [];

  return (
    <Dialog open onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle
        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}
      >
        <Box>
          <Typography variant="h6">{t('emailTemplates.preview', 'Preview')}</Typography>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 0.5 }}>
            <Chip label={template.templateKey} size="small" variant="outlined" />
            <Chip label={template.locale.toUpperCase()} size="small" color="primary" />
            {template.layoutKey && (
              <Chip
                label={`${t('emailTemplates.layoutKey', 'Layout')}: ${template.layoutKey}`}
                size="small"
                color="secondary"
              />
            )}
          </Box>
        </Box>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>
        {template.subject && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="caption" color="text.secondary">
              {t('emailTemplates.subject', 'Subject')}:
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {template.subject}
            </Typography>
          </Box>
        )}

        {variables.length > 0 && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="caption" color="text.secondary">
              {t('emailTemplates.variables', 'Available Variables')}:
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
              {variables.map((v) => (
                <Chip key={v} label={`{{${v}}}`} size="small" variant="outlined" />
              ))}
            </Box>
          </Box>
        )}

        <Divider sx={{ mb: 2 }} />

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <iframe
            srcDoc={mergedHtml}
            sandbox="allow-same-origin"
            title="Email Preview"
            style={{
              width: '100%',
              height: '600px',
              border: '1px solid #e0e0e0',
              borderRadius: 4,
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
};
