/**
 * EditEventTypeDialog (Story 15.2)
 *
 * Organizer dialog to edit a PER-EVENT copy of the agenda/event-type config (copy-on-edit) —
 * the shared template is never modified. Pre-fills from the resolved config (override or
 * template) and writes the per-event override on save, then invalidates the timetable.
 *
 * Includes the apéro knobs (on/off count, duration, position) and a count-driven break count.
 * Warns (does not block) when speaker slots are already assigned, since re-timing can move them
 * (15.3 introduces stable slot keys to remove that hazard).
 */

import React, { useEffect } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useAgendaConfig, useUpdateAgendaConfig } from '@/hooks/useAgendaConfig/useAgendaConfig';
import type { UpdateEventAgendaConfigRequest } from '@/hooks/useAgendaConfig/useAgendaConfig';

interface EditEventTypeDialogProps {
  eventCode: string;
  open: boolean;
  onClose: () => void;
  /** True when at least one speaker slot is already assigned — shows a non-blocking warning. */
  hasAssignments?: boolean;
}

const schema = z.object({
  minSlots: z.number().int().min(1),
  maxSlots: z.number().int().min(1),
  slotDuration: z.number().int().min(15),
  theoreticalSlotsAM: z.boolean(),
  breakSlots: z.number().int().min(0),
  lunchSlots: z.number().int().min(0),
  defaultCapacity: z.number().int().min(1),
  moderationStartDuration: z.number().int().min(1),
  moderationEndDuration: z.number().int().min(1),
  breakDuration: z.number().int().min(1),
  lunchDuration: z.number().int().min(1),
  aperitifSlots: z.number().int().min(0),
  aperitifDuration: z.number().int().min(1),
  aperitifPosition: z.enum(['start', 'end']),
  typicalStartTime: z.string().optional(),
  typicalEndTime: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

const NUMBER_FIELDS: { name: keyof FormValues; labelKey: string }[] = [
  { name: 'minSlots', labelKey: 'slotAssignment.editEventType.minSlots' },
  { name: 'maxSlots', labelKey: 'slotAssignment.editEventType.maxSlots' },
  { name: 'slotDuration', labelKey: 'slotAssignment.editEventType.slotDuration' },
  { name: 'breakSlots', labelKey: 'slotAssignment.editEventType.breakSlots' },
  { name: 'breakDuration', labelKey: 'slotAssignment.editEventType.breakDuration' },
  { name: 'lunchSlots', labelKey: 'slotAssignment.editEventType.lunchSlots' },
  { name: 'lunchDuration', labelKey: 'slotAssignment.editEventType.lunchDuration' },
  {
    name: 'moderationStartDuration',
    labelKey: 'slotAssignment.editEventType.moderationStartDuration',
  },
  { name: 'moderationEndDuration', labelKey: 'slotAssignment.editEventType.moderationEndDuration' },
  { name: 'aperitifDuration', labelKey: 'slotAssignment.editEventType.aperitifDuration' },
  { name: 'defaultCapacity', labelKey: 'slotAssignment.editEventType.defaultCapacity' },
];

export const EditEventTypeDialog: React.FC<EditEventTypeDialogProps> = ({
  eventCode,
  open,
  onClose,
  hasAssignments = false,
}) => {
  const { t } = useTranslation('events');
  const { data: config, isLoading } = useAgendaConfig(eventCode, open);
  const updateMutation = useUpdateAgendaConfig(eventCode);

  const {
    control,
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      minSlots: 6,
      maxSlots: 8,
      slotDuration: 45,
      theoreticalSlotsAM: false,
      breakSlots: 1,
      lunchSlots: 0,
      defaultCapacity: 200,
      moderationStartDuration: 5,
      moderationEndDuration: 5,
      breakDuration: 20,
      lunchDuration: 60,
      aperitifSlots: 0,
      aperitifDuration: 90,
      aperitifPosition: 'end',
      typicalStartTime: '',
      typicalEndTime: '',
    },
  });

  // Pre-fill from the resolved config when it loads / dialog opens.
  useEffect(() => {
    if (config) {
      reset({
        minSlots: config.minSlots,
        maxSlots: config.maxSlots,
        slotDuration: config.slotDuration,
        theoreticalSlotsAM: config.theoreticalSlotsAM,
        breakSlots: config.breakSlots,
        lunchSlots: config.lunchSlots,
        defaultCapacity: config.defaultCapacity,
        moderationStartDuration: config.moderationStartDuration,
        moderationEndDuration: config.moderationEndDuration,
        breakDuration: config.breakDuration,
        lunchDuration: config.lunchDuration,
        aperitifSlots: config.aperitifSlots,
        aperitifDuration: config.aperitifDuration,
        aperitifPosition: (config.aperitifPosition as 'start' | 'end') ?? 'end',
        typicalStartTime: config.typicalStartTime ?? '',
        typicalEndTime: config.typicalEndTime ?? '',
      });
    }
  }, [config, reset]);

  const aperitifSlots = watch('aperitifSlots');

  const onSubmit = (values: FormValues) => {
    const request: UpdateEventAgendaConfigRequest = {
      ...values,
      typicalStartTime: values.typicalStartTime || undefined,
      typicalEndTime: values.typicalEndTime || undefined,
    };
    updateMutation.mutate(request, { onSuccess: () => onClose() });
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      data-testid="edit-event-type-dialog"
    >
      <DialogTitle>{t('slotAssignment.editEventType.title')}</DialogTitle>
      <DialogContent dividers>
        {hasAssignments && (
          <Alert severity="warning" sx={{ mb: 2 }} data-testid="edit-event-type-assignment-warning">
            {t('slotAssignment.editEventType.assignmentWarning')}
          </Alert>
        )}
        {isLoading ? (
          <Typography>{t('slotAssignment.editEventType.loading')}</Typography>
        ) : (
          <Box
            component="form"
            id="edit-event-type-form"
            onSubmit={handleSubmit(onSubmit)}
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
              gap: 2,
              mt: 1,
            }}
          >
            {NUMBER_FIELDS.map((f) => (
              <TextField
                key={f.name}
                type="number"
                size="small"
                label={t(f.labelKey)}
                inputProps={{ 'data-testid': `field-${f.name}` }}
                error={!!errors[f.name]}
                {...register(f.name, { valueAsNumber: true })}
              />
            ))}

            <Controller
              name="theoreticalSlotsAM"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={
                    <Switch
                      checked={field.value}
                      onChange={(e) => field.onChange(e.target.checked)}
                      data-testid="field-theoreticalSlotsAM"
                    />
                  }
                  label={t('slotAssignment.editEventType.theoreticalSlotsAM')}
                />
              )}
            />

            <TextField
              type="number"
              size="small"
              label={t('slotAssignment.editEventType.aperitifSlots')}
              helperText={t('slotAssignment.editEventType.aperitifSlotsHelp')}
              inputProps={{ 'data-testid': 'field-aperitifSlots' }}
              error={!!errors.aperitifSlots}
              {...register('aperitifSlots', { valueAsNumber: true })}
            />

            <Controller
              name="aperitifPosition"
              control={control}
              render={({ field }) => (
                <TextField
                  select
                  size="small"
                  label={t('slotAssignment.editEventType.aperitifPosition')}
                  disabled={Number(aperitifSlots) <= 0}
                  inputProps={{ 'data-testid': 'field-aperitifPosition' }}
                  {...field}
                >
                  <MenuItem value="start">
                    {t('slotAssignment.editEventType.positionStart')}
                  </MenuItem>
                  <MenuItem value="end">{t('slotAssignment.editEventType.positionEnd')}</MenuItem>
                </TextField>
              )}
            />

            <TextField
              size="small"
              label={t('slotAssignment.editEventType.typicalStartTime')}
              placeholder="13:00"
              inputProps={{ 'data-testid': 'field-typicalStartTime' }}
              {...register('typicalStartTime')}
            />
            <TextField
              size="small"
              label={t('slotAssignment.editEventType.typicalEndTime')}
              placeholder="19:00"
              inputProps={{ 'data-testid': 'field-typicalEndTime' }}
              {...register('typicalEndTime')}
            />
          </Box>
        )}
        {updateMutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {t('slotAssignment.editEventType.saveError')}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} data-testid="edit-event-type-cancel">
          {t('slotAssignment.actions.cancel')}
        </Button>
        <Button
          type="submit"
          form="edit-event-type-form"
          variant="contained"
          disabled={isLoading || updateMutation.isPending}
          data-testid="edit-event-type-save"
        >
          {t('slotAssignment.editEventType.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default EditEventTypeDialog;
