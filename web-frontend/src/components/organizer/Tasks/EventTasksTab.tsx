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
  IconButton,
  Button,
  Divider,
  List,
  ListItem,
  ListItemText,
  Alert,
  Tooltip,
  Chip,
} from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
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
import { OrganizerSelect, useOrganizers } from '@/components/shared/OrganizerSelect';

interface EventTasksTabProps {
  eventId: string | null; // Null for new event creation
  organizerUsername: string;
  selectedTemplates: string[]; // Array of template IDs
  templateAssignees: Record<string, string>; // Map of templateId -> username
  customTasks: EventTaskResponse[];
  disabledTemplates?: string[]; // Templates that have task instances (can't be unchecked)
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
  disabledTemplates = [],
  onTemplateToggle,
  onAssigneeChange,
  onEditCustomTask,
  onDeleteCustomTask,
}) => {
  const { t } = useTranslation('events');
  const [isCustomTaskModalOpen, setIsCustomTaskModalOpen] = useState(false);

  // Fetch organizers
  const { organizers } = useOrganizers();

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
  const defaultTemplates = allTemplates.filter((tmpl) => tmpl.isDefault);
  const customTemplates = allTemplates.filter((tmpl) => !tmpl.isDefault);

  const handleAddCustomTask = () => {
    setIsCustomTaskModalOpen(true);
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={300}>
        <BATbernLoader size={96} />
      </Box>
    );
  }

  if (isError) {
    return <Alert severity="error">{t('tasks.errors.loadTemplatesFailed')}</Alert>;
  }

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        {t('tasks.eventTasksDescription')}
      </Typography>

      <Divider sx={{ my: 3 }} />

      {/* Default Templates Section */}
      <Stack spacing={2}>
        <Typography variant="h6">{t('tasks.defaultTemplates')}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t('tasks.defaultTemplatesDescription')}
        </Typography>

        <Paper variant="outlined" sx={{ p: 2 }}>
          <List>
            {defaultTemplates.map((template) => {
              const isSelected = selectedTemplates.includes(template.id);
              const assignee = templateAssignees[template.id] || '';
              const hasTaskInstance = disabledTemplates.includes(template.id);

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
                    backgroundColor: hasTaskInstance ? 'action.disabledBackground' : 'inherit',
                  }}
                >
                  <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={1}
                    alignItems={{ xs: 'stretch', sm: 'center' }}
                    width="100%"
                  >
                    {/* Checkbox + Info icon (row on all sizes) */}
                    <Stack direction="row" alignItems="center" sx={{ flex: 1 }}>
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={isSelected}
                            onChange={(e) => onTemplateToggle(template.id, e.target.checked)}
                            disabled={hasTaskInstance}
                          />
                        }
                        label={
                          hasTaskInstance ? (
                            <>
                              {template.name}
                              <Chip
                                label={t('tasks.taskExists')}
                                size="small"
                                color="info"
                                sx={{ ml: 1 }}
                              />
                            </>
                          ) : (
                            template.name
                          )
                        }
                        sx={{ flex: 1 }}
                      />
                      <Tooltip
                        title={
                          <Stack spacing={0.5}>
                            <Typography variant="caption">
                              <strong>{t('tasks.triggerState')}:</strong> {template.triggerState}
                            </Typography>
                            <Typography variant="caption">
                              <strong>{t('tasks.dueDate')}:</strong>{' '}
                              {template.dueDateType === 'immediate'
                                ? t('tasks.immediate')
                                : `${template.dueDateOffsetDays} ${t('tasks.days')}`}
                            </Typography>
                          </Stack>
                        }
                      >
                        <IconButton size="small">
                          <InfoIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Stack>

                    {/* Assignee Dropdown */}
                    <OrganizerSelect
                      value={assignee}
                      onChange={(organizerId) => onAssigneeChange(template.id, organizerId)}
                      organizers={organizers}
                      label={t('tasks.assignTo')}
                      sx={{ width: { xs: '100%', sm: 200 } }}
                      disabled={!isSelected || hasTaskInstance}
                      includeUnassigned={true}
                      includeAllOption={false}
                    />
                  </Stack>
                </ListItem>
              );
            })}
          </List>
        </Paper>
      </Stack>

      {/* Custom Template Tasks Section (organizer-created templates) */}
      {customTemplates.length > 0 && (
        <>
          <Divider sx={{ my: 3 }} />
          <Stack spacing={2}>
            <Typography variant="h6">{t('tasks.customTemplates')}</Typography>
            <Typography variant="body2" color="text.secondary">
              {t('tasks.customTemplatesDescription')}
            </Typography>

            <Paper variant="outlined" sx={{ p: 2 }}>
              <List>
                {customTemplates.map((template) => {
                  const isSelected = selectedTemplates.includes(template.id);
                  const assignee = templateAssignees[template.id] || '';
                  const hasTaskInstance = disabledTemplates.includes(template.id);

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
                        backgroundColor: hasTaskInstance ? 'action.disabledBackground' : 'inherit',
                      }}
                    >
                      <Stack
                        direction={{ xs: 'column', sm: 'row' }}
                        spacing={1}
                        alignItems={{ xs: 'stretch', sm: 'center' }}
                        width="100%"
                      >
                        {/* Checkbox + Info icon (row on all sizes) */}
                        <Stack direction="row" alignItems="center" sx={{ flex: 1 }}>
                          <FormControlLabel
                            control={
                              <Checkbox
                                checked={isSelected}
                                onChange={(e) => onTemplateToggle(template.id, e.target.checked)}
                                disabled={hasTaskInstance}
                              />
                            }
                            label={
                              hasTaskInstance ? (
                                <>
                                  {template.name}
                                  <Chip
                                    label={t('tasks.taskExists')}
                                    size="small"
                                    color="info"
                                    sx={{ ml: 1 }}
                                  />
                                </>
                              ) : (
                                template.name
                              )
                            }
                            sx={{ flex: 1 }}
                          />
                          <Tooltip
                            title={
                              <Stack spacing={0.5}>
                                <Typography variant="caption">
                                  <strong>{t('tasks.triggerState')}:</strong>{' '}
                                  {template.triggerState}
                                </Typography>
                                <Typography variant="caption">
                                  <strong>{t('tasks.dueDate')}:</strong>{' '}
                                  {template.dueDateType === 'immediate'
                                    ? t('tasks.immediate')
                                    : `${template.dueDateOffsetDays} ${t('tasks.days')}`}
                                </Typography>
                              </Stack>
                            }
                          >
                            <IconButton size="small">
                              <InfoIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Stack>

                        {/* Assignee Dropdown */}
                        <OrganizerSelect
                          value={assignee}
                          onChange={(organizerId) => onAssigneeChange(template.id, organizerId)}
                          organizers={organizers}
                          label={t('tasks.assignTo')}
                          sx={{ width: { xs: '100%', sm: 200 } }}
                          disabled={!isSelected || hasTaskInstance}
                          includeUnassigned={true}
                          includeAllOption={false}
                        />
                      </Stack>
                    </ListItem>
                  );
                })}
              </List>
            </Paper>
          </Stack>
        </>
      )}

      <Divider sx={{ my: 3 }} />

      {/* Custom Tasks Section */}
      <Stack spacing={2}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">{t('tasks.customTasks')}</Typography>
          <Button variant="outlined" startIcon={<AddIcon />} onClick={handleAddCustomTask}>
            {t('tasks.addCustomTask')}
          </Button>
        </Stack>

        {customTasks.length === 0 ? (
          <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              {t('tasks.noCustomTasks')}
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
                        aria-label={t('tasks.editTask')}
                        onClick={() => onEditCustomTask(task)}
                      >
                        <EditIcon />
                      </IconButton>
                      <IconButton
                        edge="end"
                        aria-label={t('tasks.deleteTask')}
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
                          {t('tasks.trigger')}: {task.triggerState}
                        </Typography>
                        <Typography variant="caption">
                          {t('tasks.assignedTo')}:{' '}
                          {task.assignedOrganizerUsername || t('tasks.unassigned')}
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
