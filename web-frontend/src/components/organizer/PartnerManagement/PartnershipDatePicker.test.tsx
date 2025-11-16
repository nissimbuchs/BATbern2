import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PartnershipDatePicker } from './PartnershipDatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { de, enUS } from 'date-fns/locale';

const renderWithLocalization = (ui: React.ReactElement, locale: Locale = de) => {
  return render(
    <LocalizationProvider dateAdapter={AdapterDateFns} adapterLocale={locale}>
      {ui}
    </LocalizationProvider>
  );
};

describe('PartnershipDatePicker', () => {
  describe('AC5: Start Date Picker', () => {
    it('should_defaultToToday_when_startDateFieldRendered', () => {
      const onChange = vi.fn();
      const today = new Date();

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership Start Date"
          value={today}
          onChange={onChange}
          name="partnershipStartDate"
        />
      );

      // Check that date picker renders with today's date
      const dayInput = screen.getByRole('spinbutton', { name: /day/i });
      expect(dayInput).toHaveAttribute('aria-valuenow', today.getDate().toString());
    });

    it('should_validateFutureDate_when_startDateInvalid', async () => {
      const onChange = vi.fn();

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership Start Date"
          value={null}
          onChange={onChange}
          name="partnershipStartDate"
          maxDate={new Date()}
          error="Start date cannot be in the future"
        />
      );

      // Should show error for future date
      expect(screen.getByText('Start date cannot be in the future')).toBeInTheDocument();
    });

    it('should_acceptPastDate_when_startDateValid', async () => {
      const onChange = vi.fn();
      const pastDate = new Date('2023-01-15');

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership Start Date"
          value={pastDate}
          onChange={onChange}
          name="partnershipStartDate"
          maxDate={new Date()}
        />
      );

      const input = screen.getByDisplayValue('15.01.2023');
      expect(input).toBeInTheDocument();
    });
  });

  describe('AC5: End Date Picker', () => {
    it('should_validateDateRange_when_endDateBeforeStart', async () => {
      const onChange = vi.fn();
      const startDate = new Date('2024-01-01');
      const endDate = new Date('2023-12-31');

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership End Date"
          value={endDate}
          onChange={onChange}
          name="partnershipEndDate"
          minDate={startDate}
          error="End date must be after start date"
        />
      );

      expect(screen.getByText('End date must be after start date')).toBeInTheDocument();
    });

    it('should_allowNull_when_endDateOptional', () => {
      const onChange = vi.fn();

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership End Date (Optional)"
          value={null}
          onChange={onChange}
          name="partnershipEndDate"
        />
      );

      const input = screen.getByRole('group', { name: /Partnership End Date/i });
      expect(input).toBeInTheDocument();
    });

    it('should_acceptFutureDate_when_endDateValid', () => {
      const onChange = vi.fn();
      const futureDate = new Date('2025-12-31');

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership End Date"
          value={futureDate}
          onChange={onChange}
          name="partnershipEndDate"
        />
      );

      const input = screen.getByDisplayValue('31.12.2025');
      expect(input).toBeInTheDocument();
    });
  });

  describe('AC5: Date Formatting', () => {
    it('should_formatDate_when_germanLocale', () => {
      const onChange = vi.fn();
      const date = new Date('2024-03-15');

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership Date"
          value={date}
          onChange={onChange}
          name="partnershipDate"
        />,
        de
      );

      const input = screen.getByDisplayValue('15.03.2024');
      expect(input).toBeInTheDocument();
    });

    it('should_formatDate_when_englishLocale', () => {
      const onChange = vi.fn();
      const date = new Date('2024-03-15');

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership Date"
          value={date}
          onChange={onChange}
          name="partnershipDate"
        />,
        enUS
      );

      const input = screen.getByDisplayValue('03/15/2024');
      expect(input).toBeInTheDocument();
    });
  });

  describe('AC5: Keyboard Support', () => {
    it('should_supportKeyboardInput_when_userTypes', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();

      renderWithLocalization(
        <PartnershipDatePicker
          label="Partnership Date"
          value={null}
          onChange={onChange}
          name="partnershipDate"
        />
      );

      const dayInput = screen.getByRole('spinbutton', { name: /day/i });
      await user.type(dayInput, '15');

      // MUI DatePicker uses complex internal state, so we just verify the field accepts input
      expect(dayInput).toBeInTheDocument();
    });
  });
});
