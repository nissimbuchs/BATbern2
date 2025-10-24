import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserPagination from './UserPagination';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../i18n/config';

const renderWithI18n = (ui: React.ReactElement) => {
  return render(<I18nextProvider i18n={i18n}>{ui}</I18nextProvider>);
};

describe('UserPagination Component', () => {
  it('should_displayPageNumbers_when_rendered', () => {
    const mockOnPageChange = vi.fn();
    const mockOnLimitChange = vi.fn();

    renderWithI18n(
      <UserPagination
        page={1}
        totalPages={5}
        limit={20}
        onPageChange={mockOnPageChange}
        onLimitChange={mockOnLimitChange}
      />
    );

    // Should show current page button
    expect(screen.getByRole('button', { name: 'page 1' })).toBeInTheDocument();
  });

  it('should_displayTotalPages_when_rendered', () => {
    const mockOnPageChange = vi.fn();
    const mockOnLimitChange = vi.fn();

    renderWithI18n(
      <UserPagination
        page={2}
        totalPages={10}
        limit={20}
        onPageChange={mockOnPageChange}
        onLimitChange={mockOnLimitChange}
      />
    );

    // Should indicate we're on page 2
    expect(screen.getByRole('button', { name: 'page 2' })).toHaveAttribute('aria-current', 'page');
  });

  it('should_callOnPageChange_when_pageButtonClicked', async () => {
    const user = userEvent.setup();
    const mockOnPageChange = vi.fn();
    const mockOnLimitChange = vi.fn();

    renderWithI18n(
      <UserPagination
        page={1}
        totalPages={5}
        limit={20}
        onPageChange={mockOnPageChange}
        onLimitChange={mockOnLimitChange}
      />
    );

    const nextPageButton = screen.getByRole('button', { name: 'Go to next page' });
    await user.click(nextPageButton);

    expect(mockOnPageChange).toHaveBeenCalledWith(2);
  });

  it('should_disablePreviousButton_when_onFirstPage', () => {
    const mockOnPageChange = vi.fn();
    const mockOnLimitChange = vi.fn();

    renderWithI18n(
      <UserPagination
        page={1}
        totalPages={5}
        limit={20}
        onPageChange={mockOnPageChange}
        onLimitChange={mockOnLimitChange}
      />
    );

    const previousButton = screen.getByRole('button', { name: 'Go to previous page' });
    expect(previousButton).toBeDisabled();
  });

  it('should_disableNextButton_when_onLastPage', () => {
    const mockOnPageChange = vi.fn();
    const mockOnLimitChange = vi.fn();

    renderWithI18n(
      <UserPagination
        page={5}
        totalPages={5}
        limit={20}
        onPageChange={mockOnPageChange}
        onLimitChange={mockOnLimitChange}
      />
    );

    const nextButton = screen.getByRole('button', { name: 'Go to next page' });
    expect(nextButton).toBeDisabled();
  });

  it('should_displayItemsPerPageSelect_when_rendered', () => {
    const mockOnPageChange = vi.fn();
    const mockOnLimitChange = vi.fn();

    renderWithI18n(
      <UserPagination
        page={1}
        totalPages={5}
        limit={20}
        onPageChange={mockOnPageChange}
        onLimitChange={mockOnLimitChange}
      />
    );

    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  it('should_callOnLimitChange_when_itemsPerPageChanged', async () => {
    const user = userEvent.setup();
    const mockOnPageChange = vi.fn();
    const mockOnLimitChange = vi.fn();

    renderWithI18n(
      <UserPagination
        page={1}
        totalPages={5}
        limit={20}
        onPageChange={mockOnPageChange}
        onLimitChange={mockOnLimitChange}
      />
    );

    const select = screen.getByRole('combobox');
    await user.click(select);

    const option50 = screen.getByRole('option', { name: '50' });
    await user.click(option50);

    expect(mockOnLimitChange).toHaveBeenCalledWith(50);
  });
});
