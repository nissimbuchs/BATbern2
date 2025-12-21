/**
 * CustomTaskModal Component (Story 5.5 Phase 6)
 *
 * AC22: Add/edit custom tasks modal
 * Wireframe: docs/wireframes/5.5-content-review-task-system-ux-flow.md
 *
 * Features:
 * - Form fields:
 *   - Task name (text)
 *   - Trigger state (dropdown: all event workflow states)
 *   - Due date type (radio: relative_to_event | immediate | absolute)
 *   - Days offset (number, if relative)
 *   - Assigned organizer (dropdown)
 *   - "Save as template" checkbox
 * - Validation: required fields, positive offset for "before event"
 * - Creates task linked to event OR saves as reusable template
 */

import React, { useState } from 'react';
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
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  taskService,
  type CreateTaskTemplateRequest,
  type CreateEventTaskRequest,
} from '@/services/taskService';

// Event workflow states (from EventWorkflowState enum)
const WORKFLOW_STATES = [
  { value: 'event_draft', label: 'Event Draft' },
  { value: 'topic_selection', label: 'Topic Selection' },
  { value: 'speaker_brainstorming', label: 'Speaker Brainstorming' },
  { value: 'speaker_outreach', label: 'Speaker Outreach' },
  { value: 'agenda_published', label: 'Agenda Published' },
  { value: 'agenda_finalized', label: 'Agenda Finalized' },
  { value: 'event_live', label: 'Event Live' },
  { value: 'post_event_wrap_up', label: 'Post-Event Wrap Up' },
  { value: 'archived', label: 'Archived' },
];

const DUE_DATE_TYPES = [
  { value: 'immediate', label: 'Immediate' },
  { value: 'relative_to_event', label: 'Relative to Event Date' },
  { value: 'absolute', label: 'Absolute Date' },
];

interface CustomTaskModalProps {
  open: boolean;
  onClose: () => void;
  eventId: string | null; // If null, creates template only
  organizerUsername: string;
  existingTask?: EventTaskResponse; // For edit mode (future enhancement)
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

  // Form state
  const [taskName, setTaskName] = useState('');
  const [triggerState, setTriggerState] = useState('topic_selection');
  const [dueDateType, setDueDateType] = useState<'immediate' | 'relative_to_event' | 'absolute'>(
    'relative_to_event'
  );
  const [offsetDays, setOffsetDays] = useState<number>(-30);
  const [assignedOrganizer, setAssignedOrganizer] = useState(organizerUsername);
  const [saveAsTemplate, setSaveAsTemplate] = useState(false);
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

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
    // Reset form
    setTaskName('');
    setTriggerState('topic_selection');
    setDueDateType('relative_to_event');
    setOffsetDays(-30);
    setAssignedOrganizer(organizerUsername);
    setSaveAsTemplate(false);
    setNotes('');
    setErrors({});
    onClose();
  };

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!taskName.trim()) {
      newErrors.taskName = t('tasks.errors.taskNameRequired', 'Task name is required');
    }

    if (!triggerState) {
      newErrors.triggerState = t('tasks.errors.triggerStateRequired', 'Trigger state is required');
    }

    if (dueDateType === 'relative_to_event' && offsetDays === undefined) {
      newErrors.offsetDays = t('tasks.errors.offsetDaysRequired', 'Offset days is required');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    if (saveAsTemplate || !eventId) {
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
      const request: CreateEventTaskRequest = {
        taskName,
        triggerState,
        dueDate: dueDateType === 'immediate' ? new Date().toISOString() : undefined,
        assignedOrganizerUsername: assignedOrganizer || undefined,
        notes: notes || undefined,
      };

      createAdHocTaskMutation.mutate({ eventCode: eventId, request });
    }
  };

  const isLoading = createTemplateMutation.isPending || createAdHocTaskMutation.isPending;
  const error = createTemplateMutation.error || createAdHocTaskMutation.error;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {existingTask
          ? t('tasks.editCustomTask', 'Edit Custom Task')
          : t('tasks.addCustomTask', 'Add Custom Task')}
      </DialogTitle>

      <DialogContent>
        <Stack spacing={3} sx={{ mt: 2 }}>
          {/* Error Alert */}
          {error && (
            <Alert severity="error">
              {t('tasks.errors.createTaskFailed', 'Failed to create task')}: {error.message}
            </Alert>
          )}

          {/* Task Name */}
          <TextField
            label={t('tasks.taskName', 'Task Name')}
            value={taskName}
            onChange={(e) => setTaskName(e.target.value)}
            error={!!errors.taskName}
            helperText={errors.taskName}
            required
            fullWidth
            placeholder={t('tasks.taskNamePlaceholder', 'e.g., Send venue confirmation email')}
          />

          {/* Trigger State */}
          <TextField
            select
            label={t('tasks.triggerState', 'Trigger State')}
            value={triggerState}
            onChange={(e) => setTriggerState(e.target.value)}
            error={!!errors.triggerState}
            helperText={
              errors.triggerState ||
              t('tasks.triggerStateHelp', 'The event state that activates this task')
            }
            required
            fullWidth
          >
            {WORKFLOW_STATES.map((state) => (
              <MenuItem key={state.value} value={state.value}>
                {state.label}
              </MenuItem>
            ))}
          </TextField>

          {/* Due Date Type */}
          <FormControl component="fieldset">
            <FormLabel component="legend">{t('tasks.dueDateType', 'Due Date Type')}</FormLabel>
            <RadioGroup
              value={dueDateType}
              onChange={(e) =>
                setDueDateType(e.target.value as 'immediate' | 'relative_to_event' | 'absolute')
              }
            >
              {DUE_DATE_TYPES.map((type) => (
                <FormControlLabel
                  key={type.value}
                  value={type.value}
                  control={<Radio />}
                  label={type.label}
                />
              ))}
            </RadioGroup>
          </FormControl>

          {/* Offset Days (if relative) */}
          {dueDateType === 'relative_to_event' && (
            <TextField
              label={t('tasks.offsetDays', 'Offset Days')}
              type="number"
              value={offsetDays}
              onChange={(e) => setOffsetDays(parseInt(e.target.value, 10))}
              error={!!errors.offsetDays}
              helperText={
                errors.offsetDays ||
                t('tasks.offsetDaysHelp', 'Negative = before event, Positive = after event')
              }
              required
              fullWidth
              placeholder="-30"
            />
          )}

          {/* Assigned Organizer */}
          <TextField
            label={t('tasks.assignedOrganizer', 'Assigned Organizer')}
            value={assignedOrganizer}
            onChange={(e) => setAssignedOrganizer(e.target.value)}
            helperText={t('tasks.assignedOrganizerHelp', 'Leave empty to assign later')}
            fullWidth
            placeholder={organizerUsername}
          />

          {/* Notes */}
          <TextField
            label={t('tasks.notes', 'Notes (Optional)')}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            multiline
            rows={3}
            fullWidth
            placeholder={t('tasks.notesPlaceholder', 'Add any instructions or context...')}
          />

          {/* Save as Template */}
          <FormControlLabel
            control={
              <Checkbox
                checked={saveAsTemplate}
                onChange={(e) => setSaveAsTemplate(e.target.checked)}
              />
            }
            label={t('tasks.saveAsTemplate', 'Save as reusable template')}
          />
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={isLoading}>
          {t('common.cancel', 'Cancel')}
        </Button>
        <Button onClick={handleSubmit} variant="contained" disabled={isLoading}>
          {isLoading ? (
            <CircularProgress size={20} />
          ) : saveAsTemplate || !eventId ? (
            t('tasks.createTemplate', 'Create Template')
          ) : (
            t('tasks.createTask', 'Create Task')
          )}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
