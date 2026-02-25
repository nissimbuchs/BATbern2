/**
 * TaskTemplatesTab (Story 10.1 - Task 4)
 *
 * Tab 2 of the Admin page.
 * Shows Default Templates (read-only) and Custom Templates (create/edit/delete).
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
  Typography,
} from '@mui/material';
import { Add as AddIcon, Delete as DeleteIcon, Edit as EditIcon } from '@mui/icons-material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { taskService, TaskTemplateResponse } from '@/services/taskService';
import { CustomTaskModal } from '@/components/organizer/Tasks/CustomTaskModal';
import { TaskTemplateEditModal } from './TaskTemplateEditModal';
import { BATbernLoader } from '@components/shared/BATbernLoader';

const dueDateSummary = (template: TaskTemplateResponse): string => {
  if (template.dueDateType === 'relative_to_event' && template.dueDateOffsetDays !== null) {
    return `${template.dueDateOffsetDays}d before event`;
  }
  return template.dueDateType ?? '—';
};

export const TaskTemplatesTab: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const {
    data: templates = [],
    isLoading,
    error,
  } = useQuery({
    queryKey: ['tasks', 'templates'],
    queryFn: () => taskService.listAllTemplates(),
  });

  const [createOpen, setCreateOpen] = useState(false);
  const [editTemplate, setEditTemplate] = useState<TaskTemplateResponse | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const defaultTemplates = templates.filter((tmpl) => tmpl.isDefault);
  const customTemplates = templates.filter((tmpl) => !tmpl.isDefault);

  const handleDelete = async (template: TaskTemplateResponse) => {
    if (
      !window.confirm(t('admin.taskTemplates.confirmDelete', `Delete template "${template.name}"?`))
    ) {
      return;
    }
    try {
      await taskService.deleteTemplate(template.id);
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'templates'] });
    } catch {
      setDeleteError(
        t('admin.taskTemplates.deleteFailed', 'Failed to delete template. Please try again.')
      );
    }
  };

  if (isLoading) {
    return (
      <Box sx={{ textAlign: 'center', mt: 4 }}>
        <BATbernLoader size={96} />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">{t('errors.loadFailed', 'Failed to load templates.')}</Alert>;
  }

  return (
    <Box>
      {/* Default Templates */}
      <Typography variant="h6" gutterBottom>
        {t('admin.taskTemplates.defaultSection', 'Default Templates')}
      </Typography>
      <List dense data-testid="default-templates-list">
        {defaultTemplates.map((template) => (
          <ListItem key={template.id} divider>
            <ListItemText primary={template.name} secondary={dueDateSummary(template)} />
            <ListItemSecondaryAction>
              <Chip label={template.triggerState} size="small" variant="outlined" />
            </ListItemSecondaryAction>
          </ListItem>
        ))}
        {defaultTemplates.length === 0 && (
          <ListItem>
            <ListItemText
              secondary={t('admin.taskTemplates.noDefaults', 'No default templates.')}
            />
          </ListItem>
        )}
      </List>

      <Divider sx={{ my: 3 }} />

      {/* Custom Templates */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="h6">
          {t('admin.taskTemplates.customSection', 'Custom Templates')}
        </Typography>
        <Button
          startIcon={<AddIcon />}
          variant="outlined"
          onClick={() => setCreateOpen(true)}
          data-testid="add-template-btn"
        >
          {t('admin.taskTemplates.addTemplate', '+ Add Template')}
        </Button>
      </Box>

      <List dense data-testid="custom-templates-list">
        {customTemplates.map((template) => (
          <ListItem key={template.id} divider>
            <ListItemText primary={template.name} secondary={dueDateSummary(template)} />
            <ListItemSecondaryAction sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Chip label={template.triggerState} size="small" variant="outlined" />
              <IconButton
                size="small"
                onClick={() => setEditTemplate(template)}
                aria-label={`Edit ${template.name}`}
                data-testid={`edit-template-${template.id}`}
              >
                <EditIcon fontSize="small" />
              </IconButton>
              <IconButton
                size="small"
                onClick={() => handleDelete(template)}
                aria-label={`Delete ${template.name}`}
                data-testid={`delete-template-${template.id}`}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </ListItemSecondaryAction>
          </ListItem>
        ))}
        {customTemplates.length === 0 && (
          <ListItem>
            <ListItemText
              secondary={t('admin.taskTemplates.noCustom', 'No custom templates yet.')}
            />
          </ListItem>
        )}
      </List>

      {/* Create via CustomTaskModal (eventId=null → template mode) */}
      <CustomTaskModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        eventId={null}
        organizerUsername={user?.username ?? ''}
      />

      {/* Edit modal */}
      {editTemplate && (
        <TaskTemplateEditModal
          open={Boolean(editTemplate)}
          onClose={() => setEditTemplate(null)}
          template={editTemplate}
        />
      )}

      <Snackbar
        open={Boolean(deleteError)}
        autoHideDuration={4000}
        onClose={() => setDeleteError(null)}
        message={deleteError}
      />
    </Box>
  );
};
