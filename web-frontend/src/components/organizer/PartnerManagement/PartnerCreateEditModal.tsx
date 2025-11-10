import { useEffect, useRef } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  CircularProgress,
  Typography,
  Box,
  Alert,
  Fade,
} from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import { usePartnerModalStore } from '@/stores/partnerModalStore';
import {
  useCreatePartner,
  useUpdatePartner,
} from '@/hooks/usePartnerMutations/usePartnerMutations';
import { CreatePartnerSchema, UpdatePartnerSchema } from '@/schemas/partnerSchema';
import { CompanyAutocomplete } from './CompanyAutocomplete';
import { PartnershipTierSelect } from './PartnershipTierSelect';
import { PartnershipDatePicker } from './PartnershipDatePicker';
import { TierBenefitsPreview } from './TierBenefitsPreview';
import type { CreatePartnerFormData, UpdatePartnerFormData } from '@/schemas/partnerSchema';

export const PartnerCreateEditModal: React.FC = () => {
  const { t } = useTranslation('partners');
  const { isOpen, mode, partnerToEdit, closeModal } = usePartnerModalStore();
  const createMutation = useCreatePartner();
  const updateMutation = useUpdatePartner();
  const firstFieldRef = useRef<HTMLInputElement>(null);

  const isCreate = mode === 'create';
  const isPending = createMutation.isPending || updateMutation.isPending;
  const error = createMutation.error || updateMutation.error;

  const {
    control,
    handleSubmit,
    formState: { errors, isDirty },
    reset,
    watch,
  } = useForm<CreatePartnerFormData | UpdatePartnerFormData>({
    resolver: zodResolver(isCreate ? CreatePartnerSchema : UpdatePartnerSchema),
    defaultValues: isCreate
      ? {
          companyName: '',
          partnershipLevel: 'BRONZE',
          partnershipStartDate: new Date(),
          partnershipEndDate: undefined,
        }
      : {
          partnershipLevel: partnerToEdit?.partnershipLevel || 'BRONZE',
          partnershipStartDate: partnerToEdit?.partnershipStartDate
            ? new Date(partnerToEdit.partnershipStartDate)
            : new Date(),
          partnershipEndDate: partnerToEdit?.partnershipEndDate
            ? new Date(partnerToEdit.partnershipEndDate)
            : undefined,
        },
  });

  // Watch tier for benefits preview
  const selectedTier = watch('partnershipLevel');

  // Reset form when modal opens/closes or mode changes
  useEffect(() => {
    if (isOpen) {
      reset(
        isCreate
          ? {
              companyName: '',
              partnershipLevel: 'BRONZE',
              partnershipStartDate: new Date(),
              partnershipEndDate: undefined,
            }
          : {
              partnershipLevel: partnerToEdit?.partnershipLevel || 'BRONZE',
              partnershipStartDate: partnerToEdit?.partnershipStartDate
                ? new Date(partnerToEdit.partnershipStartDate)
                : new Date(),
              partnershipEndDate: partnerToEdit?.partnershipEndDate
                ? new Date(partnerToEdit.partnershipEndDate)
                : undefined,
            }
      );

      // Autofocus first field when modal opens
      setTimeout(() => {
        firstFieldRef.current?.focus();
      }, 100);
    }
  }, [isOpen, mode, partnerToEdit, reset, isCreate]);

  const onSubmit = (data: CreatePartnerFormData | UpdatePartnerFormData) => {
    if (isCreate) {
      const createData = data as CreatePartnerFormData;
      // Convert Date objects to ISO strings for API
      createMutation.mutate({
        companyName: createData.companyName,
        partnershipLevel: createData.partnershipLevel,
        partnershipStartDate: createData.partnershipStartDate.toISOString().split('T')[0],
        partnershipEndDate: createData.partnershipEndDate
          ? createData.partnershipEndDate.toISOString().split('T')[0]
          : undefined,
      });
    } else {
      const updateData = data as UpdatePartnerFormData;
      // Convert Date objects to ISO strings for API
      updateMutation.mutate({
        companyName: partnerToEdit!.companyName,
        data: {
          partnershipLevel: updateData.partnershipLevel,
          partnershipEndDate: updateData.partnershipEndDate
            ? updateData.partnershipEndDate.toISOString().split('T')[0]
            : undefined,
        },
      });
    }
  };

  const handleClose = () => {
    if (isDirty) {
      const confirmed = window.confirm(t('modal.messages.unsavedChanges'));
      if (!confirmed) return;
    }
    closeModal();
  };

  // Custom Transition component with 200ms duration
  const TransitionComponent = (props: any) => <Fade {...props} timeout={200} />;

  if (!isOpen) return null;

  return (
    <Dialog
      open={isOpen}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
      fullScreen={window.innerWidth < 640}
      TransitionComponent={TransitionComponent}
    >
      <DialogTitle>{isCreate ? t('modal.createTitle') : t('modal.editTitle')}</DialogTitle>

      <DialogContent>
        <Box component="form" sx={{ mt: 2, display: 'flex', flexDirection: 'column', gap: 3 }}>
          {error && (
            <Alert severity="error">
              {error instanceof Error ? error.message : 'An error occurred'}
            </Alert>
          )}

          {/* Company Selection/Display */}
          {isCreate ? (
            <Controller
              name="companyName"
              control={control}
              render={({ field }) => (
                <CompanyAutocomplete
                  value={null}
                  onChange={(company) => field.onChange(company?.name || '')}
                  error={'companyName' in errors ? errors.companyName?.message : undefined}
                  disabled={isPending}
                  inputRef={firstFieldRef}
                />
              )}
            />
          ) : (
            <Box>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('modal.fields.company')}
              </Typography>
              <Typography variant="body1">{partnerToEdit?.companyName}</Typography>
            </Box>
          )}

          {/* Partnership Tier */}
          <Controller
            name="partnershipLevel"
            control={control}
            render={({ field }) => (
              <PartnershipTierSelect
                value={(field.value ?? 'BRONZE') as string}
                onChange={field.onChange}
                error={errors.partnershipLevel?.message}
                disabled={isPending}
              />
            )}
          />

          {/* Start Date */}
          <Controller
            name="partnershipStartDate"
            control={control}
            render={({ field }) => (
              <PartnershipDatePicker
                label={t('modal.fields.startDate')}
                value={field.value ?? null}
                onChange={field.onChange}
                name="partnershipStartDate"
                maxDate={new Date()}
                error={errors.partnershipStartDate?.message}
                disabled={isPending}
              />
            )}
          />

          {/* End Date */}
          <Controller
            name="partnershipEndDate"
            control={control}
            render={({ field }) => (
              <PartnershipDatePicker
                label={t('modal.fields.endDateOptional')}
                value={field.value ?? null}
                onChange={(date) => field.onChange(date ?? undefined)}
                name="partnershipEndDate"
                minDate={(watch('partnershipStartDate') as Date | undefined) ?? undefined}
                error={errors.partnershipEndDate?.message}
                disabled={isPending}
              />
            )}
          />

          {/* Benefits Preview */}
          {selectedTier && <TierBenefitsPreview tier={selectedTier} />}
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={isPending}>
          {t('modal.actions.cancel')}
        </Button>
        <Button
          onClick={handleSubmit(onSubmit)}
          variant="contained"
          disabled={isPending || (!isCreate && !isDirty)}
          startIcon={isPending ? <CircularProgress size={20} role="progressbar" /> : null}
        >
          {isPending ? t('modal.actions.saving') : t('modal.actions.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
