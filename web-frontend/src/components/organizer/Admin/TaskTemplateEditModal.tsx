/**
 * TaskTemplateEditModal (Story 10.1 - Task 4)
 *
 * Modal for editing an existing custom task template.
 * Fields: name, triggerState, dueDateType, dueDateOffsetDays.
 */

import React, { useEffect, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
} from '@mui/material';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  taskService,
  TaskTemplateResponse,
  UpdateTaskTemplateRequest,
} from '@/services/taskService';

const TRIGGER_STATES = [
  'DRAFT',
  'SCHEDULED',
  'PUBLISHED',
  'REGISTRATION_OPEN',
  'REGISTRATION_CLOSED',
  'SPEAKER_INVITED',
  'EVENT_LIVE',
  'EVENT_COMPLETED',
  'ARCHIVED',
];

const DUE_DATE_TYPES = ['immediate', 'relative_to_event', 'absolute'];

interface TaskTemplateEditModalProps {
  open: boolean;
  onClose: () => void;
  template: TaskTemplateResponse;
}

export const TaskTemplateEditModal: React.FC<TaskTemplateEditModalProps> = ({
  open,
  onClose,
  template,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [name, setName] = useState(template.name);
  const [triggerState, setTriggerState] = useState(template.triggerState);
  const [dueDateType, setDueDateType] = useState(template.dueDateType);
  const [dueDateOffsetDays, setDueDateOffsetDays] = useState<number | ''>(
    template.dueDateOffsetDays ?? ''
  );
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setName(template.name);
      setTriggerState(template.triggerState);
      setDueDateType(template.dueDateType);
      setDueDateOffsetDays(template.dueDateOffsetDays ?? '');
    }
  }, [open, template]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const request: UpdateTaskTemplateRequest = {
        name,
        triggerState,
        dueDateType,
        dueDateOffsetDays:
          dueDateType === 'relative_to_event' && dueDateOffsetDays !== ''
            ? Number(dueDateOffsetDays)
            : undefined,
      };
      await taskService.updateTemplate(template.id, request);
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'templates'] });
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('admin.taskTemplates.editTitle', 'Edit Task Template')}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
        <TextField
          label={t('admin.taskTemplates.name', 'Name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          fullWidth
          inputProps={{ 'data-testid': 'template-name-input' }}
        />

        <FormControl fullWidth>
          <InputLabel>{t('admin.taskTemplates.triggerState', 'Trigger State')}</InputLabel>
          <Select
            value={triggerState}
            label={t('admin.taskTemplates.triggerState', 'Trigger State')}
            onChange={(e) => setTriggerState(e.target.value)}
            inputProps={{ 'data-testid': 'template-trigger-state-select' }}
          >
            {TRIGGER_STATES.map((state) => (
              <MenuItem key={state} value={state}>
                {state}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl fullWidth>
          <InputLabel>{t('admin.taskTemplates.dueDateType', 'Due Date Type')}</InputLabel>
          <Select
            value={dueDateType}
            label={t('admin.taskTemplates.dueDateType', 'Due Date Type')}
            onChange={(e) => setDueDateType(e.target.value)}
            inputProps={{ 'data-testid': 'template-due-date-type-select' }}
          >
            {DUE_DATE_TYPES.map((type) => (
              <MenuItem key={type} value={type}>
                {type}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {dueDateType === 'relative_to_event' && (
          <TextField
            label={t('admin.taskTemplates.dueDateOffsetDays', 'Offset Days')}
            type="number"
            value={dueDateOffsetDays}
            onChange={(e) =>
              setDueDateOffsetDays(e.target.value === '' ? '' : Number(e.target.value))
            }
            fullWidth
            inputProps={{ 'data-testid': 'template-offset-days-input', min: 0 }}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          {t('common.cancel', 'Cancel')}
        </Button>
        <Button variant="contained" onClick={handleSave} disabled={saving || !name.trim()}>
          {t('common.save', 'Save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
