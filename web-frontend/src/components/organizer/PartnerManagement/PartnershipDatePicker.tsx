import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { FormControl, FormHelperText } from '@mui/material';

interface PartnershipDatePickerProps {
  label: string;
  value: Date | null;
  onChange: (date: Date | null) => void;
  name: string;
  minDate?: Date;
  maxDate?: Date;
  error?: string;
  disabled?: boolean;
}

export const PartnershipDatePicker: React.FC<PartnershipDatePickerProps> = ({
  label,
  value,
  onChange,
  name,
  minDate,
  maxDate,
  error,
  disabled = false,
}) => {
  return (
    <FormControl fullWidth error={!!error}>
      <DatePicker
        label={label}
        value={value}
        onChange={onChange}
        minDate={minDate}
        maxDate={maxDate}
        disabled={disabled}
        slotProps={{
          textField: {
            name,
            error: !!error,
            fullWidth: true,
            'aria-describedby': error ? `${name}-error` : undefined,
          },
        }}
      />
      {error && (
        <FormHelperText id={`${name}-error`} error>
          {error}
        </FormHelperText>
      )}
    </FormControl>
  );
};
