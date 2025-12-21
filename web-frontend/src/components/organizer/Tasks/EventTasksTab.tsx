/**
 * EventTasksTab Component (Story 5.5 Phase 6)
 *
 * AC21-22: Task configuration tab for EventDetailEdit modal
 * Wireframe: docs/wireframes/5.5-content-review-task-system-ux-flow.md
 *
 * Features:
 * - Lists 7 default templates (all pre-checked)
 * - Assignee dropdown for each template
 * - Custom tasks section with edit/delete actions
 * - "Add Custom Task" button opens CustomTaskModal
 * - Tasks created with status="pending" on event save
 */

import React, { useState } from 'react';
import {
  Box,
  Typography,
  Stack,
  Paper,
  Checkbox,
  FormControlLabel,
  TextField,
  MenuItem,
  IconButton,
  Button,
  Divider,
  List,
  ListItem,
  ListItemText,
  CircularProgress,
  Alert,
  Tooltip,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Info as InfoIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { taskService, type EventTaskResponse } from '@/services/taskService';
import { CustomTaskModal } from './CustomTaskModal';

interface EventTasksTabProps {
  eventId: string | null; // Null for new event creation
  organizerUsername: string;
  selectedTemplates: string[]; // Array of template IDs
  templateAssignees: Record<string, string>; // Map of templateId -> username
  customTasks: EventTaskResponse[];
  onTemplateToggle: (templateId: string, checked: boolean) => void;
  onAssigneeChange: (templateId: string, assignee: string) => void;
  onAddCustomTask: () => void;
  onEditCustomTask: (task: EventTaskResponse) => void;
  onDeleteCustomTask: (taskId: string) => void;
}

export const EventTasksTab: React.FC<EventTasksTabProps> = ({
  eventId,
  organizerUsername,
  selectedTemplates,
  templateAssignees,
  customTasks,
  onTemplateToggle,
  onAssigneeChange,
  onAddCustomTask,
  onEditCustomTask,
  onDeleteCustomTask,
}) => {
  const { t } = useTranslation('events');
  const [isCustomTaskModalOpen, setIsCustomTaskModalOpen] = useState(false);

  // Fetch all task templates
  const {
    data: allTemplates = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['tasks', 'templates'],
    queryFn: () => taskService.listAllTemplates(),
  });

  // Separate default and custom templates
  const defaultTemplates = allTemplates.filter((t) => t.isDefault);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const _customTemplates = allTemplates.filter((t) => !t.isDefault);

  // Mock organizers list (in real app, fetch from users service)
  const organizers = [organizerUsername, 'organizer1@example.com', 'organizer2@example.com'];

  const handleAddCustomTask = () => {
    setIsCustomTaskModalOpen(true);
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const _handleCustomTaskCreated = () => {
    setIsCustomTaskModalOpen(false);
    onAddCustomTask();
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={300}>
        <CircularProgress />
      </Box>
    );
  }

  if (isError) {
    return (
      <Alert severity="error">
        {t('tasks.errors.loadTemplatesFailed', 'Failed to load task templates')}
      </Alert>
    );
  }

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        {t(
          'tasks.eventTasksDescription',
          'Configure tasks that will be automatically created and activated as the event progresses through workflow states.'
        )}
      </Typography>

      <Divider sx={{ my: 3 }} />

      {/* Default Templates Section */}
      <Stack spacing={2}>
        <Typography variant="h6">
          {t('tasks.defaultTemplates', 'Default Task Templates')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t(
            'tasks.defaultTemplatesDescription',
            'These are the standard event planning tasks. All are pre-selected and will be created when you save the event.'
          )}
        </Typography>

        <Paper variant="outlined" sx={{ p: 2 }}>
          <List>
            {defaultTemplates.map((template) => {
              const isSelected = selectedTemplates.includes(template.id);
              const assignee = templateAssignees[template.id] || '';

              return (
                <ListItem
                  key={template.id}
                  sx={{
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    mb: 1,
                    flexDirection: 'column',
                    alignItems: 'stretch',
                  }}
                >
                  <Stack direction="row" spacing={2} alignItems="center" width="100%">
                    {/* Checkbox */}
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={isSelected}
                          onChange={(e) => onTemplateToggle(template.id, e.target.checked)}
                        />
                      }
                      label={template.name}
                      sx={{ flex: 1 }}
                    />

                    {/* Assignee Dropdown */}
                    <TextField
                      select
                      size="small"
                      value={assignee}
                      onChange={(e) => onAssigneeChange(template.id, e.target.value)}
                      label={t('tasks.assignTo', 'Assign To')}
                      sx={{ minWidth: 200 }}
                      disabled={!isSelected}
                    >
                      <MenuItem value="">
                        <em>{t('tasks.unassigned', 'Unassigned')}</em>
                      </MenuItem>
                      {organizers.map((org) => (
                        <MenuItem key={org} value={org}>
                          {org}
                        </MenuItem>
                      ))}
                    </TextField>

                    {/* Info Icon */}
                    <Tooltip
                      title={
                        <Stack spacing={0.5}>
                          <Typography variant="caption">
                            <strong>{t('tasks.triggerState', 'Trigger')}:</strong>{' '}
                            {template.triggerState}
                          </Typography>
                          <Typography variant="caption">
                            <strong>{t('tasks.dueDate', 'Due')}:</strong>{' '}
                            {template.dueDateType === 'immediate'
                              ? t('tasks.immediate', 'Immediate')
                              : `${template.dueDateOffsetDays} ${t('tasks.days', 'days')}`}
                          </Typography>
                        </Stack>
                      }
                    >
                      <IconButton size="small">
                        <InfoIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </ListItem>
              );
            })}
          </List>
        </Paper>
      </Stack>

      <Divider sx={{ my: 3 }} />

      {/* Custom Tasks Section */}
      <Stack spacing={2}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">{t('tasks.customTasks', 'Custom Tasks')}</Typography>
          <Button variant="outlined" startIcon={<AddIcon />} onClick={handleAddCustomTask}>
            {t('tasks.addCustomTask', 'Add Custom Task')}
          </Button>
        </Stack>

        {customTasks.length === 0 ? (
          <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              {t(
                'tasks.noCustomTasks',
                'No custom tasks yet. Click "Add Custom Task" to create one.'
              )}
            </Typography>
          </Paper>
        ) : (
          <Paper variant="outlined" sx={{ p: 2 }}>
            <List>
              {customTasks.map((task) => (
                <ListItem
                  key={task.id}
                  sx={{
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    mb: 1,
                  }}
                  secondaryAction={
                    <Stack direction="row" spacing={1}>
                      <IconButton
                        edge="end"
                        aria-label="edit"
                        onClick={() => onEditCustomTask(task)}
                      >
                        <EditIcon />
                      </IconButton>
                      <IconButton
                        edge="end"
                        aria-label="delete"
                        onClick={() => onDeleteCustomTask(task.id)}
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Stack>
                  }
                >
                  <ListItemText
                    primary={task.taskName}
                    secondary={
                      <Stack spacing={0.5} mt={0.5}>
                        <Typography variant="caption">
                          {t('tasks.trigger', 'Trigger')}: {task.triggerState}
                        </Typography>
                        <Typography variant="caption">
                          {t('tasks.assignedTo', 'Assigned to')}:{' '}
                          {task.assignedOrganizerUsername || t('tasks.unassigned', 'Unassigned')}
                        </Typography>
                        {task.notes && (
                          <Typography variant="caption" sx={{ fontStyle: 'italic' }}>
                            {task.notes}
                          </Typography>
                        )}
                      </Stack>
                    }
                  />
                </ListItem>
              ))}
            </List>
          </Paper>
        )}
      </Stack>

      {/* Custom Task Modal */}
      <CustomTaskModal
        open={isCustomTaskModalOpen}
        onClose={() => setIsCustomTaskModalOpen(false)}
        eventId={eventId}
        organizerUsername={organizerUsername}
      />
    </Box>
  );
};
