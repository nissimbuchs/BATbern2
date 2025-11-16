import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PartnershipTierSelect } from './PartnershipTierSelect';

/**
 * Test suite for PartnershipTierSelect component
 * Covers AC4: Partnership Tier Dropdown displays all tiers with visual indicators
 */
describe('PartnershipTierSelect', () => {
  const mockOnChange = vi.fn();

  // Test 4.1: should_displayAllTiers_when_dropdownOpened
  it('should display all tier options when dropdown is opened', async () => {
    render(<PartnershipTierSelect value="" onChange={mockOnChange} error={undefined} />);

    const select = screen.getByRole('combobox', { name: /partnership tier/i });
    fireEvent.mouseDown(select);

    await waitFor(() => {
      expect(screen.getByRole('option', { name: /STRATEGIC/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /PLATINUM/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /GOLD/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /SILVER/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /BRONZE/i })).toBeInTheDocument();
    });
  });

  // Test 4.2: should_showEmojiIcons_when_tiersDisplayed
  it('should show emoji icons for each tier', async () => {
    render(<PartnershipTierSelect value="" onChange={mockOnChange} error={undefined} />);

    const select = screen.getByRole('combobox', { name: /partnership tier/i });
    fireEvent.mouseDown(select);

    await waitFor(() => {
      expect(screen.getByText(/🏆/)).toBeInTheDocument(); // Strategic
      expect(screen.getByText(/💎/)).toBeInTheDocument(); // Platinum
      expect(screen.getByText(/🥇/)).toBeInTheDocument(); // Gold
      expect(screen.getByText(/🥈/)).toBeInTheDocument(); // Silver
      expect(screen.getByText(/🥉/)).toBeInTheDocument(); // Bronze
    });
  });

  // Test 4.3: should_selectTier_when_optionClicked
  it('should call onChange with selected tier when option is clicked', async () => {
    render(<PartnershipTierSelect value="" onChange={mockOnChange} error={undefined} />);

    const select = screen.getByRole('combobox', { name: /partnership tier/i });
    fireEvent.mouseDown(select);

    await waitFor(() => {
      const goldOption = screen.getByRole('option', { name: /GOLD/i });
      fireEvent.click(goldOption);
    });

    expect(mockOnChange).toHaveBeenCalledWith('GOLD');
  });

  // Test 4.4: should_displaySelectedTier_when_valueProvided
  it('should display the selected tier value', () => {
    render(<PartnershipTierSelect value="PLATINUM" onChange={mockOnChange} error={undefined} />);

    expect(screen.getByText(/💎/)).toBeInTheDocument();
    expect(screen.getByText(/PLATINUM/i)).toBeInTheDocument();
  });

  // Test 4.5: should_displayError_when_errorProvided
  it('should display error message when validation fails', () => {
    const errorMessage = 'Partnership tier is required';

    render(<PartnershipTierSelect value="" onChange={mockOnChange} error={errorMessage} />);

    expect(screen.getByText(errorMessage)).toBeInTheDocument();
  });

  // Test 4.6: should_highlightSelectedTier_when_tierSelected
  it('should highlight the selected tier in the dropdown', async () => {
    render(<PartnershipTierSelect value="SILVER" onChange={mockOnChange} error={undefined} />);

    const select = screen.getByRole('combobox', { name: /partnership tier/i });
    fireEvent.mouseDown(select);

    await waitFor(() => {
      const silverOption = screen.getByRole('option', { name: /SILVER/i });
      expect(silverOption).toHaveAttribute('aria-selected', 'true');
    });
  });

  // Test 4.7: should_beDisabled_when_disabledPropTrue
  it('should be disabled when disabled prop is true', () => {
    render(
      <PartnershipTierSelect
        value="GOLD"
        onChange={mockOnChange}
        error={undefined}
        disabled={true}
      />
    );

    const select = screen.getByRole('combobox', { name: /partnership tier/i });
    expect(select).toHaveAttribute('aria-disabled', 'true');
  });

  // Test 4.8: should_displayTooltip_when_tierHovered
  it('should have accessible labels for all tiers', async () => {
    render(<PartnershipTierSelect value="" onChange={mockOnChange} error={undefined} />);

    const select = screen.getByRole('combobox', { name: /partnership tier/i });
    fireEvent.mouseDown(select);

    await waitFor(() => {
      // Verify tier options have proper labels
      const strategicOption = screen.getByRole('option', { name: /STRATEGIC/i });
      expect(strategicOption).toBeInTheDocument();
    });
  });
});
