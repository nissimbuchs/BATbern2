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

import React, { useCallback, useEffect, useRef, useState } from 'react';
import Editor from '@monaco-editor/react';
// Self-hosted TinyMCE: import core engine before the React wrapper
// so the wrapper uses the local bundle instead of TinyMCE Cloud.
import 'tinymce/tinymce';
import 'tinymce/models/dom/model';
import 'tinymce/themes/silver/theme';
import 'tinymce/icons/default/icons';
import 'tinymce/plugins/code';
import 'tinymce/plugins/table';
import 'tinymce/plugins/lists';
import 'tinymce/plugins/link';
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
  /** Category to assign when creating a new template (defaults to 'SPEAKER'). */
  initialCategory?: string;
  /** When set, pre-fills content/subject/layout from this template (create mode only). */
  cloneFrom?: EmailTemplateResponse;
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

export const EmailTemplateEditModal: React.FC<Props> = ({
  template,
  isLayoutMode,
  initialCategory,
  cloneFrom,
  onClose,
}) => {
  const { t } = useTranslation();
  const isEdit = Boolean(template);

  // Create-mode fields
  const [newTemplateKey, setNewTemplateKey] = useState('');
  const [newLocale, setNewLocale] = useState<'de' | 'en'>(
    (cloneFrom?.locale as 'de' | 'en') ?? 'de'
  );

  // Shared fields (pre-fill from cloneFrom in create mode)
  const [subject, setSubject] = useState(template?.subject ?? cloneFrom?.subject ?? '');
  const [htmlBody, setHtmlBody] = useState(template?.htmlBody ?? cloneFrom?.htmlBody ?? '');
  const [layoutKey, setLayoutKey] = useState(
    template?.layoutKey ?? cloneFrom?.layoutKey ?? 'batbern-default'
  );
  const [error, setError] = useState<string | null>(null);

  // Track TinyMCE content in a ref to avoid re-renders on every keystroke
  // (re-renders cause cursor to jump to top). Sync to state on blur for validation.
  const tinymceContentRef = useRef(htmlBody);

  const syncHtmlBodyFromEditor = useCallback(() => {
    setHtmlBody(tinymceContentRef.current);
  }, []);

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

    // Sync latest TinyMCE content before validation (ref may be ahead of state)
    const currentHtmlBody = isLayoutMode ? htmlBody : tinymceContentRef.current;
    if (!isLayoutMode) {
      setHtmlBody(currentHtmlBody);
    }

    if (!currentHtmlBody.trim()) {
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
            htmlBody: currentHtmlBody,
            layoutKey: isLayoutMode ? undefined : layoutKey || undefined,
          },
        });
      } else {
        if (!newTemplateKey.trim()) {
          setError(t('emailTemplates.validation.templateKeyRequired', 'Template key is required.'));
          return;
        }
        if (/\s/.test(newTemplateKey)) {
          setError(
            t(
              'emailTemplates.validation.templateKeyNoSpaces',
              'Template key must not contain spaces.'
            )
          );
          return;
        }
        await createMutation.mutateAsync({
          templateKey: newTemplateKey.trim(),
          locale: newLocale,
          category: (initialCategory ?? 'SPEAKER') as
            | 'SPEAKER'
            | 'REGISTRATION'
            | 'TASK_REMINDER'
            | 'LAYOUT'
            | 'NEWSLETTER',
          isLayout: false,
          subject,
          htmlBody: currentHtmlBody,
          layoutKey: layoutKey || undefined,
        });
      }
      onClose();
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        setError(
          t(
            'emailTemplates.validation.duplicateKey',
            'A template with this key already exists for this locale. Please choose a different key.'
          )
        );
      } else {
        setError(t('emailTemplates.saveFailed', 'Failed to save template. Please try again.'));
      }
    }
  };

  const title = isEdit
    ? t('emailTemplates.editTitle', 'Edit Template')
    : cloneFrom
      ? t('emailTemplates.duplicateTitle', 'Duplicate Template')
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
            <TinyMCEEditor
              initialValue={htmlBody}
              onEditorChange={(val: string) => {
                tinymceContentRef.current = val;
              }}
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
                skin_url: '/tinymce/skins/ui/oxide',
                content_css: '/tinymce/skins/content/default/content.min.css',
                setup: (editor) => {
                  editor.on('blur', syncHtmlBodyFromEditor);
                },
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
