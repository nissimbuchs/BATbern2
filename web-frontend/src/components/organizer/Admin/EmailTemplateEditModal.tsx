/**
 * EmailTemplateEditModal (Story 10.2)
 *
 * Two modes:
 * - Edit mode (template prop provided): edit existing template
 * - Create mode (template undefined): create new content template
 *
 * Two editor modes:
 * - Layout mode (isLayoutMode=true): Monaco Editor, no subject field
 * - Content mode (isLayoutMode=false): TinyMCE WYSIWYG, subject field, layoutKey selector
 */

import React, { useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Editor as TinyMCEEditor } from '@tinymce/tinymce-react';
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
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  useCreateEmailTemplate,
  useLayoutTemplates,
  useUpdateEmailTemplate,
} from '@/hooks/useEmailTemplates';
import type { EmailTemplateResponse } from '@/hooks/useEmailTemplates';

interface Props {
  /** When provided: edit mode. When undefined: create mode. */
  template?: EmailTemplateResponse;
  isLayoutMode: boolean;
  onClose: () => void;
}

function extractVariables(html: string): string[] {
  const matches = new Set<string>();
  const re = /\{\{(\w+)\}\}/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    matches.add(m[1]);
  }
  return Array.from(matches);
}

export const EmailTemplateEditModal: React.FC<Props> = ({ template, isLayoutMode, onClose }) => {
  const { t } = useTranslation();
  const isEdit = Boolean(template);

  // Create-mode fields
  const [newTemplateKey, setNewTemplateKey] = useState('');
  const [newLocale, setNewLocale] = useState<'de' | 'en'>('de');

  // Shared fields
  const [subject, setSubject] = useState(template?.subject ?? '');
  const [htmlBody, setHtmlBody] = useState(template?.htmlBody ?? '');
  const [layoutKey, setLayoutKey] = useState(template?.layoutKey ?? 'batbern-default');
  const [error, setError] = useState<string | null>(null);

  const { data: layouts = [] } = useLayoutTemplates();
  const createMutation = useCreateEmailTemplate();
  const updateMutation = useUpdateEmailTemplate();

  const isSaving = createMutation.isPending || updateMutation.isPending;

  useEffect(() => {
    if (template) {
      setSubject(template.subject ?? '');
      setHtmlBody(template.htmlBody);
      setLayoutKey(template.layoutKey ?? 'batbern-default');
    }
  }, [template]);

  const missingContentPlaceholder = isLayoutMode && !htmlBody.includes('{{content}}');
  const detectedVars = extractVariables(htmlBody);

  const handleSave = async () => {
    setError(null);

    if (!htmlBody.trim()) {
      setError(t('emailTemplates.validation.htmlBodyRequired', 'HTML body is required.'));
      return;
    }
    if (!isLayoutMode && !subject.trim()) {
      setError(
        t('emailTemplates.validation.subjectRequired', 'Subject is required for content templates.')
      );
      return;
    }

    try {
      if (isEdit && template) {
        await updateMutation.mutateAsync({
          templateKey: template.templateKey,
          locale: template.locale,
          req: {
            subject: isLayoutMode ? undefined : subject,
            htmlBody,
            layoutKey: isLayoutMode ? undefined : layoutKey || undefined,
          },
        });
      } else {
        if (!newTemplateKey.trim()) {
          setError(t('emailTemplates.validation.templateKeyRequired', 'Template key is required.'));
          return;
        }
        await createMutation.mutateAsync({
          templateKey: newTemplateKey.trim(),
          locale: newLocale,
          category: 'SPEAKER',
          isLayout: false,
          subject,
          htmlBody,
          layoutKey: layoutKey || undefined,
        });
      }
      onClose();
    } catch {
      setError(t('emailTemplates.saveFailed', 'Failed to save template. Please try again.'));
    }
  };

  const title = isEdit
    ? t('emailTemplates.editTitle', 'Edit Template')
    : t('emailTemplates.createTitle', 'New Template');

  return (
    <Dialog open onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent dividers>
        {/* Metadata chips (edit mode) */}
        {template && (
          <Box sx={{ mb: 2, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Chip label={template.templateKey} size="small" variant="outlined" />
            <Chip label={template.locale.toUpperCase()} size="small" color="primary" />
            <Chip label={template.category} size="small" />
            {template.isSystemTemplate && (
              <Chip
                label={t('emailTemplates.systemTemplate', 'System Template')}
                size="small"
                color="warning"
              />
            )}
          </Box>
        )}

        {/* Create-mode: templateKey + locale */}
        {!isEdit && (
          <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
            <TextField
              label={t('emailTemplates.templateKey', 'Template Key')}
              value={newTemplateKey}
              onChange={(e) => setNewTemplateKey(e.target.value)}
              fullWidth
              required
              helperText="e.g. my-custom-invitation"
            />
            <FormControl sx={{ minWidth: 100 }}>
              <InputLabel>{t('emailTemplates.locale', 'Locale')}</InputLabel>
              <Select
                value={newLocale}
                label={t('emailTemplates.locale', 'Locale')}
                onChange={(e) => setNewLocale(e.target.value as 'de' | 'en')}
              >
                <MenuItem value="de">DE</MenuItem>
                <MenuItem value="en">EN</MenuItem>
              </Select>
            </FormControl>
          </Box>
        )}

        {/* Subject (content mode only) */}
        {!isLayoutMode && (
          <TextField
            label={t('emailTemplates.subject', 'Subject')}
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            fullWidth
            required
            inputProps={{ maxLength: 500 }}
            sx={{ mb: 2 }}
          />
        )}

        <Typography variant="subtitle2" gutterBottom>
          {t('emailTemplates.htmlBody', 'HTML Content')}
        </Typography>

        {missingContentPlaceholder && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            {t(
              'emailTemplates.missingContentPlaceholder',
              '{{content}} placeholder missing — email body will be empty'
            )}
          </Alert>
        )}

        {/* Editor */}
        {isLayoutMode ? (
          <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
            <Editor
              height="500px"
              language="html"
              value={htmlBody}
              onChange={(val) => setHtmlBody(val ?? '')}
              options={{
                wordWrap: 'on',
                minimap: { enabled: false },
                fontSize: 13,
              }}
            />
          </Box>
        ) : (
          <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
            {/* Security note: TinyMCE API key is intentionally committed.
               It is a domain-locked free-tier key restricted to BATbern domains.
               No sensitive data is processed by TinyMCE cloud — it only loads the editor JS bundle.
               Risk accepted (code review 2026-02-28). */}
            <TinyMCEEditor
              apiKey="vfen2deuuzo9vxkqtwegdhngiujb74mu2pb3l5fg9o31ekvf"
              value={htmlBody}
              onEditorChange={(val: string) => setHtmlBody(val)}
              init={{
                height: 400,
                menubar: false,
                plugins: 'code table lists link',
                toolbar:
                  'code | blocks | bold italic underline' +
                  ' | alignleft aligncenter alignright' +
                  ' | bullist numlist | outdent indent | hr | link | table',
                entity_encoding: 'raw',
                valid_elements: '*[*]',
                branding: false,
              }}
            />
          </Box>
        )}

        {/* Layout key + variable chips (content mode only) */}
        {!isLayoutMode && (
          <Box sx={{ mt: 2 }}>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel>{t('emailTemplates.layoutKey', 'Layout')}</InputLabel>
              <Select
                value={layoutKey}
                label={t('emailTemplates.layoutKey', 'Layout')}
                onChange={(e) => setLayoutKey(e.target.value)}
              >
                <MenuItem value="">{t('emailTemplates.standalone', 'None (standalone)')}</MenuItem>
                {layouts.map((l) => (
                  <MenuItem key={`${l.templateKey}-${l.locale}`} value={l.templateKey}>
                    {l.templateKey} ({l.locale})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {detectedVars.length > 0 && (
              <Box>
                <Typography variant="caption" color="text.secondary">
                  {t('emailTemplates.variables', 'Available Variables')}:
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
                  {detectedVars.map((v) => (
                    <Chip key={v} label={`{{${v}}}`} size="small" variant="outlined" />
                  ))}
                </Box>
              </Box>
            )}
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={isSaving}>
          {t('actions.cancel', 'Cancel')}
        </Button>
        <Button
          onClick={handleSave}
          variant="contained"
          disabled={isSaving}
          startIcon={isSaving ? <CircularProgress size={16} /> : undefined}
        >
          {t('actions.save', 'Save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
