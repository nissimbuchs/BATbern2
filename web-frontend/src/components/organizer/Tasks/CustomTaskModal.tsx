/**
 * CustomTaskModal Component (Story 5.5 Phase 6)
 *
 * AC22: Add/edit custom tasks modal
 *
 * Modes:
 * - Create mode (no existingTask): create a new ad-hoc task or template
 * - Edit mode (existingTask provided): update existing task's notes, due date, and assignee
 *
 * When eventId is null (task board context), shows an event selector so the user
 * can pick which event the ad-hoc task belongs to, OR they can check "Save as
 * reusable template" to create a template instead.
 */

import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  FormControl,
  FormLabel,
  RadioGroup,
  Radio,
  FormControlLabel,
  Checkbox,
  Stack,
  Alert,
  CircularProgress,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  taskService,
  type CreateTaskTemplateRequest,
  type CreateEventTaskRequest,
  type EventTaskResponse,
  type UpdateEventTaskRequest,
} from '@/services/taskService';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect';
import { useEvents } from '@/hooks/useEvents';

// Event workflow states — lowercase matches what the backend stores in task_templates
const WORKFLOW_STATE_VALUES = [
  'topic_selection',
  'speaker_brainstorming',
  'speaker_outreach',
  'agenda_published',
  'agenda_finalized',
  'event_live',
  'post_event_wrap_up',
  'archived',
] as const;

const DUE_DATE_TYPE_VALUES = ['immediate', 'relative_to_event', 'absolute'] as const;

interface CustomTaskModalProps {
  open: boolean;
  onClose: () => void;
  eventId: string | null; // If null, shows event selector
  organizerUsername: string;
  existingTask?: EventTaskResponse; // For edit mode
}

export const CustomTaskModal: React.FC<CustomTaskModalProps> = ({
  open,
  onClose,
  eventId,
  organizerUsername,
  existingTask,
}) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const isEditMode = !!existingTask;

  // Form state
  const [taskName, setTaskName] = useState('');
  const [triggerState, setTriggerState] = useState('topic_selection');
  const [dueDateType, setDueDateType] = useState<'immediate' | 'relative_to_event' | 'absolute'>(
    'relative_to_event'
  );
  const [offsetDays, setOffsetDays] = useState<number>(-30);
  const [absoluteDueDate, setAbsoluteDueDate] = useState('');
  const [assignedOrganizer, setAssignedOrganizer] = useState('');
  const [saveAsTemplate, setSaveAsTemplate] = useState(false);
  const [notes, setNotes] = useState('');
  const [selectedEventCode, setSelectedEventCode] = useState<string>('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Fetch events for the event selector (only needed when no eventId provided and not edit mode)
  const showEventSelector = !eventId && !isEditMode;
  const { data: eventsData } = useEvents({ page: 1, limit: 100 }, undefined, undefined);
  const events = eventsData?.data ?? [];

  // Populate form when opening in edit mode
  useEffect(() => {
    if (open && isEditMode && existingTask) {
      setTaskName(existingTask.taskName);
      setTriggerState(existingTask.triggerState);
      setAssignedOrganizer(existingTask.assignedOrganizerUsername ?? '');
      setNotes(existingTask.notes ?? '');
      setSaveAsTemplate(false);
      setErrors({});
    } else if (open && !isEditMode) {
      // Reset to defaults for create mode
      setTaskName('');
      setTriggerState('topic_selection');
      setDueDateType('relative_to_event');
      setOffsetDays(-30);
      setAbsoluteDueDate('');
      setAssignedOrganizer(organizerUsername);
      setSaveAsTemplate(false);
      setNotes('');
      setSelectedEventCode('');
      setErrors({});
    }
  }, [open, isEditMode, existingTask, organizerUsername]);

  // Update task mutation (edit mode)
  const updateTaskMutation = useMutation({
    mutationFn: ({ taskId, request }: { taskId: string; request: UpdateEventTaskRequest }) =>
      taskService.updateTask(taskId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      handleClose();
    },
  });

  // Create task template mutation
  const createTemplateMutation = useMutation({
    mutationFn: (request: CreateTaskTemplateRequest) => taskService.createTemplate(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', 'templates'] });
      handleClose();
    },
  });

  // Create ad-hoc task mutation
  const createAdHocTaskMutation = useMutation({
    mutationFn: ({ eventCode, request }: { eventCode: string; request: CreateEventTaskRequest }) =>
      taskService.createAdHocTask(eventCode, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      handleClose();
    },
  });

  const handleClose = () => {
    setErrors({});
    onClose();
  };

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!isEditMode) {
      if (!taskName.trim()) {
        newErrors.taskName = t('tasks.errors.taskNameRequired');
      }
      if (!triggerState) {
        newErrors.triggerState = t('tasks.errors.triggerStateRequired');
      }
      if (dueDateType === 'relative_to_event' && offsetDays === undefined) {
        newErrors.offsetDays = t('tasks.errors.offsetDaysRequired');
      }
      // If no eventId and not template mode, require event selection
      if (!eventId && !saveAsTemplate && !selectedEventCode) {
        newErrors.event = t('tasks.errors.eventRequired');
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    if (isEditMode && existingTask) {
      // Edit mode: patch existing task
      const request: UpdateEventTaskRequest = {
        notes: notes || null,
        assignedOrganizerUsername: assignedOrganizer || null,
      };
      if (dueDateType === 'absolute' && absoluteDueDate) {
        request.dueDate = new Date(absoluteDueDate).toISOString();
      }
      updateTaskMutation.mutate({ taskId: existingTask.id, request });
      return;
    }

    // Create mode
    const effectiveEventId = eventId ?? (selectedEventCode || null);

    if (saveAsTemplate || !effectiveEventId) {
      // Create as template
      const request: CreateTaskTemplateRequest = {
        name: taskName,
        triggerState,
        dueDateType,
        dueDateOffsetDays: dueDateType === 'relative_to_event' ? offsetDays : undefined,
        saveAsTemplate: true,
      };
      createTemplateMutation.mutate(request);
    } else {
      // Create as ad-hoc task for specific event
      let dueDate: string | undefined;
      if (dueDateType === 'immediate') {
        dueDate = new Date().toISOString();
      } else if (dueDateType === 'absolute' && absoluteDueDate) {
        dueDate = new Date(absoluteDueDate).toISOString();
      }

      const request: CreateEventTaskRequest = {
        taskName,
        triggerState,
        dueDate,
        assignedOrganizerUsername: assignedOrganizer || undefined,
        notes: notes || undefined,
      };
      createAdHocTaskMutation.mutate({ eventCode: effectiveEventId, request });
    }
  };

  const isLoading =
    updateTaskMutation.isPending ||
    createTemplateMutation.isPending ||
    createAdHocTaskMutation.isPending;
  const error =
    updateTaskMutation.error || createTemplateMutation.error || createAdHocTaskMutation.error;

  // Determine effective event ID for button label
  const effectiveEventId = eventId ?? (selectedEventCode || null);
  const isTemplateMode = !isEditMode && (saveAsTemplate || !effectiveEventId);

  const submitLabel = isEditMode
    ? t('common.saveChanges')
    : isTemplateMode
      ? t('tasks.createTemplate')
      : t('tasks.createTask');

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth fullScreen={isMobile}>
      <DialogTitle>{isEditMode ? t('tasks.editTask') : t('tasks.addCustomTask')}</DialogTitle>

      <DialogContent>
        <Stack spacing={3} sx={{ mt: 2 }}>
          {/* Error Alert */}
          {error && (
            <Alert severity="error">
              {isEditMode ? t('tasks.errors.updateTaskFailed') : t('tasks.errors.createTaskFailed')}
              : {error.message}
            </Alert>
          )}

          {/* Edit mode: show read-only task name */}
          {isEditMode ? (
            <Typography variant="subtitle1" fontWeight="bold">
              {existingTask?.taskName}
            </Typography>
          ) : (
            /* Create mode: task name field */
            <TextField
              label={t('tasks.taskName')}
              value={taskName}
              onChange={(e) => setTaskName(e.target.value)}
              error={!!errors.taskName}
              helperText={errors.taskName}
              required
              fullWidth
              placeholder={t('tasks.taskNamePlaceholder')}
            />
          )}

          {/* Event Selector (create mode, no eventId prop) */}
          {showEventSelector && (
            <TextField
              select
              label={t('tasks.selectEvent')}
              value={selectedEventCode}
              onChange={(e) => setSelectedEventCode(e.target.value)}
              error={!!errors.event}
              helperText={errors.event || t('tasks.selectEventHelp')}
              fullWidth
            >
              <MenuItem value="">
                <em>{t('tasks.noEvent')}</em>
              </MenuItem>
              {events.map((event) => (
                <MenuItem key={event.eventCode} value={event.eventCode}>
                  {event.eventCode} — {event.title}
                </MenuItem>
              ))}
            </TextField>
          )}

          {/* Trigger State (create mode only) */}
          {!isEditMode && (
            <TextField
              select
              label={t('tasks.triggerState')}
              value={triggerState}
              onChange={(e) => setTriggerState(e.target.value)}
              error={!!errors.triggerState}
              helperText={errors.triggerState || t('tasks.triggerStateHelp')}
              required
              fullWidth
            >
              {WORKFLOW_STATE_VALUES.map((value) => (
                <MenuItem key={value} value={value}>
                  {t(`tasks.workflowStates.${value}`)}
                </MenuItem>
              ))}
            </TextField>
          )}

          {/* Due Date Type (create mode, or absolute date edit in edit mode) */}
          {!isEditMode && (
            <FormControl component="fieldset">
              <FormLabel component="legend">{t('tasks.dueDateType')}</FormLabel>
              <RadioGroup
                value={dueDateType}
                onChange={(e) =>
                  setDueDateType(e.target.value as 'immediate' | 'relative_to_event' | 'absolute')
                }
              >
                {DUE_DATE_TYPE_VALUES.map((value) => (
                  <FormControlLabel
                    key={value}
                    value={value}
                    control={<Radio />}
                    label={t(`tasks.dueDateTypes.${value}`)}
                  />
                ))}
              </RadioGroup>
            </FormControl>
          )}

          {/* Offset Days (create mode, relative_to_event) */}
          {!isEditMode && dueDateType === 'relative_to_event' && (
            <TextField
              label={t('tasks.offsetDays')}
              type="number"
              value={offsetDays}
              onChange={(e) => setOffsetDays(parseInt(e.target.value, 10))}
              error={!!errors.offsetDays}
              helperText={errors.offsetDays || t('tasks.offsetDaysHelp')}
              required
              fullWidth
              placeholder="-30"
            />
          )}

          {/* Absolute Date picker (absolute due date type) */}
          {dueDateType === 'absolute' && (
            <TextField
              label={t('tasks.absoluteDueDate')}
              type="date"
              value={absoluteDueDate}
              onChange={(e) => setAbsoluteDueDate(e.target.value)}
              fullWidth
              InputLabelProps={{ shrink: true }}
            />
          )}

          {/* Assigned Organizer — dropdown */}
          <OrganizerSelect
            value={assignedOrganizer}
            onChange={(val) => setAssignedOrganizer(val)}
            includeUnassigned={true}
            includeAllOption={false}
            label={t('tasks.assignedOrganizer')}
            fullWidth
          />

          {/* Notes */}
          <TextField
            label={t('tasks.notes')}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            multiline
            rows={3}
            fullWidth
            placeholder={t('tasks.notesPlaceholder')}
          />

          {/* Save as Template (create mode, only when no event selected or eventId) */}
          {!isEditMode && (
            <FormControlLabel
              control={
                <Checkbox
                  checked={saveAsTemplate}
                  onChange={(e) => setSaveAsTemplate(e.target.checked)}
                />
              }
              label={t('tasks.saveAsTemplate')}
            />
          )}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={isLoading}>
          {t('common:actions.cancel')}
        </Button>
        <Button onClick={handleSubmit} variant="contained" disabled={isLoading}>
          {isLoading ? <CircularProgress size={20} /> : submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
