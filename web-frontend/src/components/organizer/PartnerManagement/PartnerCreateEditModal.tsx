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
      createMutation.mutate(data as CreatePartnerFormData);
    } else {
      updateMutation.mutate({
        companyName: partnerToEdit!.companyName,
        updates: data as UpdatePartnerFormData,
      });
    }
  };

  const handleClose = () => {
    if (isDirty) {
      const confirmed = window.confirm('You have unsaved changes. Are you sure you want to close?');
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
      <DialogTitle>{isCreate ? 'Create Partnership' : 'Edit Partnership'}</DialogTitle>

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
                  value={field.value}
                  onChange={field.onChange}
                  error={errors.companyName?.message}
                  disabled={isPending}
                  inputRef={firstFieldRef}
                />
              )}
            />
          ) : (
            <Box>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Company
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
                value={field.value}
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
                label="Partnership Start Date"
                value={field.value}
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
                label="Partnership End Date (Optional)"
                value={field.value || null}
                onChange={field.onChange}
                name="partnershipEndDate"
                minDate={watch('partnershipStartDate')}
                error={errors.partnershipEndDate?.message}
                disabled={isPending}
              />
            )}
          />

          {/* Benefits Preview */}
          <TierBenefitsPreview tier={selectedTier} />
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={isPending}>
          Cancel
        </Button>
        <Button
          onClick={handleSubmit(onSubmit)}
          variant="contained"
          disabled={isPending || (!isCreate && !isDirty)}
          startIcon={isPending ? <CircularProgress size={20} role="progressbar" /> : null}
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
};
