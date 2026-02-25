/**
 * EmailTemplatesTab (Story 10.2)
 *
 * Tab 3 of the Admin page.
 * - Layout Templates section (top): editable with Monaco Editor
 * - Content Templates section: filterable by category/locale, editable with Monaco Editor
 */

import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemSecondaryAction,
  ListItemText,
  Snackbar,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  InfoOutlined as InfoIcon,
  Visibility as PreviewIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import {
  useDeleteEmailTemplate,
  useEmailTemplates,
  useLayoutTemplates,
} from '@/hooks/useEmailTemplates';
import type { EmailTemplateResponse } from '@/hooks/useEmailTemplates';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { EmailTemplateEditModal } from './EmailTemplateEditModal';
import { EmailTemplatePreviewModal } from './EmailTemplatePreviewModal';

type Category = 'SPEAKER' | 'REGISTRATION' | 'TASK_REMINDER';

const formatDate = (dateStr: string) => {
  try {
    return format(new Date(dateStr), 'dd.MM.yyyy');
  } catch {
    return dateStr;
  }
};

export const EmailTemplatesTab: React.FC = () => {
  const { t } = useTranslation();

  const { data: layoutTemplates = [], isLoading: loadingLayouts } = useLayoutTemplates();
  const {
    data: allContentTemplates = [],
    isLoading: loadingContent,
    error: contentError,
  } = useEmailTemplates({ isLayout: false });
  const deleteMutation = useDeleteEmailTemplate();

  const [categoryFilter, setCategoryFilter] = useState<Category>('SPEAKER');
  const [localeFilter, setLocaleFilter] = useState<'de' | 'en'>('de');

  const [editTemplate, setEditTemplate] = useState<EmailTemplateResponse | undefined>(undefined);
  const [isLayoutEdit, setIsLayoutEdit] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [previewTemplate, setPreviewTemplate] = useState<EmailTemplateResponse | null>(null);
  const [snackbar, setSnackbar] = useState<string | null>(null);

  const filteredContent = allContentTemplates.filter(
    (tmpl) => tmpl.category === categoryFilter && tmpl.locale === localeFilter
  );

  const handleEdit = (tmpl: EmailTemplateResponse, layoutMode: boolean) => {
    setEditTemplate(tmpl);
    setIsLayoutEdit(layoutMode);
    setEditOpen(true);
    setCreateOpen(false);
  };

  const handleDelete = async (tmpl: EmailTemplateResponse) => {
    if (!window.confirm(`Delete template "${tmpl.templateKey}" (${tmpl.locale})?`)) {
      return;
    }
    try {
      await deleteMutation.mutateAsync({ templateKey: tmpl.templateKey, locale: tmpl.locale });
    } catch {
      setSnackbar(t('emailTemplates.deleteFailed', 'Failed to delete template.'));
    }
  };

  if (loadingLayouts || loadingContent) {
    return (
      <Box sx={{ textAlign: 'center', mt: 4 }}>
        <BATbernLoader size={96} />
      </Box>
    );
  }

  if (contentError) {
    return <Alert severity="error">{t('errors.loadFailed', 'Failed to load templates.')}</Alert>;
  }

  return (
    <Box>
      {/* ── Layout Templates ── */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
        <Typography variant="h6">
          {t('emailTemplates.layoutTemplates', 'Layout Templates')}
        </Typography>
        <Tooltip
          title={t(
            'emailTemplates.layoutTemplatesInfo',
            'Defines the BATbern look & feel for all emails. Changes here affect all templates using this layout.'
          )}
        >
          <InfoIcon fontSize="small" color="action" />
        </Tooltip>
      </Box>

      <Box sx={{ bgcolor: 'action.hover', borderRadius: 1, px: 2, py: 1, mb: 1 }}>
        <List dense disablePadding>
          {layoutTemplates.map((tmpl) => (
            <ListItem key={`${tmpl.templateKey}-${tmpl.locale}`} divider>
              <ListItemText
                primary={
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <Typography variant="body2" fontWeight={600}>
                      {tmpl.templateKey}
                    </Typography>
                    <Chip label={tmpl.locale.toUpperCase()} size="small" color="primary" />
                  </Box>
                }
                secondary={`Updated: ${formatDate(tmpl.updatedAt)}`}
              />
              <ListItemSecondaryAction sx={{ display: 'flex', gap: 0.5 }}>
                <IconButton
                  size="small"
                  onClick={() => setPreviewTemplate(tmpl)}
                  aria-label={`Preview ${tmpl.templateKey}`}
                >
                  <PreviewIcon fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  onClick={() => handleEdit(tmpl, true)}
                  aria-label={`Edit ${tmpl.templateKey}`}
                  data-testid={`edit-layout-${tmpl.templateKey}-${tmpl.locale}`}
                >
                  <EditIcon fontSize="small" />
                </IconButton>
              </ListItemSecondaryAction>
            </ListItem>
          ))}
          {layoutTemplates.length === 0 && (
            <ListItem>
              <ListItemText
                secondary={t('emailTemplates.noLayouts', 'No layout templates found.')}
              />
            </ListItem>
          )}
        </List>
      </Box>

      <Divider sx={{ my: 3 }} />

      {/* ── Content Templates ── */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          mb: 2,
          flexWrap: 'wrap',
          gap: 1,
        }}
      >
        <Typography variant="h6">
          {t('emailTemplates.contentTemplates', 'Email Content')}
        </Typography>
        <Button
          startIcon={<AddIcon />}
          variant="outlined"
          onClick={() => {
            setEditTemplate(undefined);
            setIsLayoutEdit(false);
            setCreateOpen(true);
            setEditOpen(false);
          }}
          data-testid="add-email-template-btn"
        >
          {t('emailTemplates.newTemplate', '+ New Template')}
        </Button>
      </Box>

      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <ToggleButtonGroup
          value={categoryFilter}
          exclusive
          onChange={(_, v) => {
            if (v) setCategoryFilter(v);
          }}
          size="small"
          aria-label="Category filter"
        >
          <ToggleButton value="SPEAKER">
            {t('emailTemplates.categories.SPEAKER', 'Speakers')}
          </ToggleButton>
          <ToggleButton value="REGISTRATION">
            {t('emailTemplates.categories.REGISTRATION', 'Registration')}
          </ToggleButton>
          <ToggleButton value="TASK_REMINDER">
            {t('emailTemplates.categories.TASK_REMINDER', 'Task Reminders')}
          </ToggleButton>
        </ToggleButtonGroup>

        <ToggleButtonGroup
          value={localeFilter}
          exclusive
          onChange={(_, v) => {
            if (v) setLocaleFilter(v);
          }}
          size="small"
          aria-label="Language filter"
        >
          <ToggleButton value="de">DE</ToggleButton>
          <ToggleButton value="en">EN</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      <List dense data-testid="content-templates-list">
        {filteredContent.map((tmpl) => (
          <ListItem key={`${tmpl.templateKey}-${tmpl.locale}`} divider>
            <ListItemText
              primary={
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="body2" fontWeight={600}>
                    {tmpl.templateKey}
                  </Typography>
                  {tmpl.isSystemTemplate ? (
                    <Chip
                      label={t('emailTemplates.systemTemplate', 'system')}
                      size="small"
                      color="warning"
                      variant="outlined"
                    />
                  ) : tmpl.layoutKey ? (
                    <Chip
                      label={tmpl.layoutKey}
                      size="small"
                      color="secondary"
                      variant="outlined"
                    />
                  ) : (
                    <Chip
                      label={t('emailTemplates.standalone', 'standalone')}
                      size="small"
                      variant="outlined"
                    />
                  )}
                </Box>
              }
              secondary={
                <Box component="span">
                  {tmpl.subject && (
                    <Typography
                      variant="caption"
                      component="span"
                      display="block"
                      noWrap
                      sx={{ maxWidth: 400 }}
                    >
                      {tmpl.subject}
                    </Typography>
                  )}
                  <Typography variant="caption" component="span" color="text.secondary">
                    Updated: {formatDate(tmpl.updatedAt)}
                  </Typography>
                </Box>
              }
            />
            <ListItemSecondaryAction sx={{ display: 'flex', gap: 0.5 }}>
              <IconButton
                size="small"
                onClick={() => setPreviewTemplate(tmpl)}
                aria-label={`Preview ${tmpl.templateKey}`}
              >
                <PreviewIcon fontSize="small" />
              </IconButton>
              <IconButton
                size="small"
                onClick={() => handleEdit(tmpl, false)}
                aria-label={`Edit ${tmpl.templateKey}`}
                data-testid={`edit-email-template-${tmpl.templateKey}`}
              >
                <EditIcon fontSize="small" />
              </IconButton>
              {!tmpl.isSystemTemplate && (
                <IconButton
                  size="small"
                  onClick={() => handleDelete(tmpl)}
                  aria-label={`Delete ${tmpl.templateKey}`}
                  data-testid={`delete-email-template-${tmpl.templateKey}`}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </ListItemSecondaryAction>
          </ListItem>
        ))}
        {filteredContent.length === 0 && (
          <ListItem>
            <ListItemText
              secondary={t('emailTemplates.noTemplates', 'No templates found for this filter.')}
            />
          </ListItem>
        )}
      </List>

      {/* Edit Modal */}
      {editOpen && (
        <EmailTemplateEditModal
          template={editTemplate}
          isLayoutMode={isLayoutEdit}
          onClose={() => setEditOpen(false)}
        />
      )}

      {/* Create Modal */}
      {createOpen && (
        <EmailTemplateEditModal
          template={undefined}
          isLayoutMode={false}
          onClose={() => setCreateOpen(false)}
        />
      )}

      {/* Preview Modal */}
      {previewTemplate && (
        <EmailTemplatePreviewModal
          template={previewTemplate}
          onClose={() => setPreviewTemplate(null)}
        />
      )}

      <Snackbar
        open={Boolean(snackbar)}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
      />
    </Box>
  );
};
