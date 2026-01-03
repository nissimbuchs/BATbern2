# Form Validation Pattern

**Category**: Frontend - Form Handling
**Used in Stories**: 2.5.3 (Event Management Frontend)
**Last Updated**: 2025-12-20
**Source**: Extracted from Story 2.5.3

## Overview

Type-safe form validation using React Hook Form + Zod schema validation + Material-UI components with support for auto-save, optimistic updates, and unsaved changes detection.

**Use this pattern when**:
- Building complex forms with validation
- Implementing auto-save functionality (debounced)
- Supporting create/edit modes in same component
- Handling unsaved changes warnings
- Integrating with Material-UI dialog forms
- Implementing partial updates (PATCH)

## Prerequisites

```bash
npm install react-hook-form zod @hookform/resolvers/zod
npm install @mui/material @mui/x-date-pickers
npm install date-fns  # for date manipulation
```

## Implementation Steps

### Step 1: Define Zod Validation Schema

```typescript
import * as z from 'zod';

// Define validation schema with Zod
const entitySchema = z.object({
  title: z.string()
    .min(10, 'Title must be at least 10 characters')
    .max(200, 'Title cannot exceed 200 characters'),

  description: z.string()
    .min(50, 'Description must be at least 50 characters')
    .max(2000, 'Description cannot exceed 2000 characters'),

  eventDate: z.date()
    .refine((date) => {
      const minDate = new Date();
      minDate.setDate(minDate.getDate() + 30);
      return date >= minDate;
    }, 'Event date must be at least 30 days in the future'),

  eventType: z.enum(['full_day', 'afternoon', 'evening']),

  capacity: z.number()
    .min(20, 'Capacity must be at least 20')
    .max(1000, 'Capacity cannot exceed 1000'),

  registrationDeadline: z.date(),

  // Optional fields
  theme: z.string().max(100).optional(),
  venueId: z.string().uuid().optional(),
})
.refine((data) => {
  // Cross-field validation
  const eventDate = new Date(data.eventDate);
  const deadline = new Date(data.registrationDeadline);
  const minDeadline = new Date(eventDate);
  minDeadline.setDate(minDeadline.getDate() - 7);
  return deadline <= minDeadline;
}, {
  message: 'Registration deadline must be at least 7 days before event date',
  path: ['registrationDeadline'],  // Show error on this field
});

type EntityFormData = z.infer<typeof entitySchema>;
```

### Step 2: Create Form Component

```typescript
import React, { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Grid,
  Typography,
  Alert,
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers';
import { useDebounce } from '@/hooks/useDebounce';

interface EntityFormProps {
  open: boolean;
  mode: 'create' | 'edit';
  initialData?: Entity;
  onClose: () => void;
  onSubmit: (data: CreateEntityRequest | UpdateEntityRequest) => void;
}

export const EntityForm: React.FC<EntityFormProps> = ({
  open,
  mode,
  initialData,
  onClose,
  onSubmit,
}) => {
  const [lastSaved, setLastSaved] = useState<Date | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // Initialize form with React Hook Form + Zod
  const {
    control,
    handleSubmit,
    formState: { errors, isDirty },
    watch,
    reset,
  } = useForm<EntityFormData>({
    resolver: zodResolver(entitySchema),
    defaultValues: initialData || {},
  });

  // Auto-save logic (5-second debounce, edit mode only)
  const formValues = watch();
  const debouncedFormValues = useDebounce(formValues, 5000);

  useEffect(() => {
    if (mode === 'edit' && isDirty && debouncedFormValues) {
      setIsSaving(true);
      onSubmit({
        ...debouncedFormValues,
        version: initialData?.version || 0,  // For optimistic concurrency control
      });
      setLastSaved(new Date());
      setIsSaving(false);
    }
  }, [debouncedFormValues, mode, isDirty]);

  // Unsaved changes warning
  const handleClose = () => {
    if (isDirty) {
      const confirmed = window.confirm('You have unsaved changes. Are you sure you want to close?');
      if (!confirmed) return;
    }
    reset();  // Reset form on close
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {mode === 'create' ? 'Create New Entity' : 'Edit Entity'}
      </DialogTitle>

      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogContent>
          <Grid container spacing={2}>
            {/* Text Input */}
            <Grid item xs={12}>
              <Controller
                name="title"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Title *"
                    fullWidth
                    error={!!errors.title}
                    helperText={errors.title?.message}
                  />
                )}
              />
            </Grid>

            {/* Text Area */}
            <Grid item xs={12}>
              <Controller
                name="description"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Description *"
                    multiline
                    rows={4}
                    fullWidth
                    error={!!errors.description}
                    helperText={
                      errors.description?.message ||
                      `${field.value?.length || 0}/2000 characters`
                    }
                  />
                )}
              />
            </Grid>

            {/* Date Picker */}
            <Grid item xs={12} sm={6}>
              <Controller
                name="eventDate"
                control={control}
                render={({ field }) => (
                  <DatePicker
                    {...field}
                    label="Event Date *"
                    slotProps={{
                      textField: {
                        fullWidth: true,
                        error: !!errors.eventDate,
                        helperText: errors.eventDate?.message,
                      },
                    }}
                  />
                )}
              />
            </Grid>

            {/* Select Dropdown */}
            <Grid item xs={12} sm={6}>
              <Controller
                name="eventType"
                control={control}
                render={({ field }) => (
                  <FormControl fullWidth error={!!errors.eventType}>
                    <InputLabel>Event Type *</InputLabel>
                    <Select {...field} label="Event Type *">
                      <MenuItem value="full_day">Full Day</MenuItem>
                      <MenuItem value="afternoon">Afternoon</MenuItem>
                      <MenuItem value="evening">Evening</MenuItem>
                    </Select>
                  </FormControl>
                )}
              />
            </Grid>

            {/* Number Input */}
            <Grid item xs={12} sm={6}>
              <Controller
                name="capacity"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Capacity *"
                    type="number"
                    fullWidth
                    error={!!errors.capacity}
                    helperText={errors.capacity?.message}
                  />
                )}
              />
            </Grid>
          </Grid>

          {/* Auto-save status (edit mode only) */}
          {mode === 'edit' && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
              {isSaving
                ? '💾 Saving...'
                : lastSaved
                ? `Last saved at ${lastSaved.toLocaleTimeString()}`
                : 'Auto-save enabled'}
            </Typography>
          )}
        </DialogContent>

        <DialogActions>
          <Button onClick={handleClose}>Cancel</Button>

          {mode === 'create' && (
            <Button
              type="submit"
              variant="outlined"
              onClick={() => {
                // Allow save as draft (no validation)
                onSubmit(formValues as any);
              }}
            >
              Save Draft
            </Button>
          )}

          <Button type="submit" variant="contained" color="primary">
            {mode === 'create' ? 'Save & Create' : 'Save Changes'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};
```

### Step 3: Create Debounce Hook (for Auto-Save)

```typescript
// src/hooks/useDebounce.ts
import { useState, useEffect } from 'react';

export function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}
```

## Advanced Patterns

### Partial Updates (PATCH)

Track changed fields and send only modifications:

```typescript
const [initialValues, setInitialValues] = useState(initialData);

const handlePartialUpdate = (formData: EntityFormData) => {
  const changedFields = Object.keys(formData).reduce((acc, key) => {
    if (formData[key] !== initialValues[key]) {
      acc[key] = formData[key];
    }
    return acc;
  }, {});

  // Only send changed fields
  onSubmit({ ...changedFields, version: initialData.version });
};
```

### Dynamic Field Validation

```typescript
const eventSchema = z.object({
  eventType: z.enum(['full_day', 'afternoon', 'evening']),
  duration: z.number()
    .refine((value, ctx) => {
      const eventType = ctx.parent.eventType;
      if (eventType === 'full_day') return value >= 8;
      if (eventType === 'afternoon') return value >= 4;
      if (eventType === 'evening') return value >= 3;
      return true;
    }, 'Duration must match event type requirements'),
});
```

### Conditional Field Rendering

```typescript
const eventType = watch('eventType');

{eventType === 'full_day' && (
  <Grid item xs={12}>
    <Controller
      name="lunchProvided"
      control={control}
      render={({ field }) => (
        <FormControlLabel
          control={<Checkbox {...field} checked={field.value} />}
          label="Lunch Provided"
        />
      )}
    />
  </Grid>
)}
```

## Testing

```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EntityForm } from './EntityForm';

describe('EntityForm', () => {
  it('should validate required fields', async () => {
    const onSubmit = vi.fn();

    render(
      <EntityForm
        open={true}
        mode="create"
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />
    );

    // Submit without filling fields
    const submitButton = screen.getByText('Save & Create');
    await userEvent.click(submitButton);

    // Should show validation errors
    expect(await screen.findByText('Title must be at least 10 characters')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('should auto-save after 5 seconds in edit mode', async () => {
    vi.useFakeTimers();
    const onSubmit = vi.fn();

    render(
      <EntityForm
        open={true}
        mode="edit"
        initialData={{ title: 'Test Event', description: 'Test description...' }}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />
    );

    // Change title
    const titleInput = screen.getByLabelText('Title *');
    await userEvent.clear(titleInput);
    await userEvent.type(titleInput, 'Updated Event Title');

    // Wait 5 seconds for debounce
    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
        title: 'Updated Event Title',
      }));
    });

    vi.useRealTimers();
  });
});
```

## Common Pitfalls

### Pitfall 1: Not Resetting Form on Close
**Problem**: Form retains old values when reopened
**Solution**: Call `reset()` in onClose handler

### Pitfall 2: Missing Unsaved Changes Warning
**Problem**: Users lose data when closing form
**Solution**: Check `isDirty` before allowing close

### Pitfall 3: Auto-Save in Create Mode
**Problem**: Auto-save creates incomplete entities
**Solution**: Only enable auto-save in edit mode

### Pitfall 4: Not Handling Concurrent Edits
**Problem**: Users overwrite each other's changes
**Solution**: Use version field for optimistic concurrency control

## Story-Specific Adaptations

### Story 2.5.3: Event Form with Auto-Save

- **Auto-Save**: Always enabled in edit mode (NOT configurable per AC20)
- **5-Second Debounce**: Uses `useDebounce` hook to prevent excessive API calls
- **Partial Updates**: Sends only changed fields via PATCH method
- **Concurrent Edit Detection**: Handles 409 status code from backend
- **Save Draft**: Create mode allows saving incomplete data
- **Cross-Field Validation**: Registration deadline must be 7+ days before event date
- **Material-UI Integration**: Full Material-UI Dialog with responsive Grid layout

## Related Templates

- `react-query-caching-pattern.md` - Use mutation hooks for form submission
- `api-service-pattern.md` - API client methods for CRUD operations
- `react-component-pattern.md` - Component structure and props

## References

- **React Hook Form**: https://react-hook-form.com/
- **Zod**: https://zod.dev/
- **Story 2.5.3**: Event Management Frontend (lines 1495-1677)
