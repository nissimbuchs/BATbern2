import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PartnerSettingsTab } from './PartnerSettingsTab';

// Mock data
const mockPartnerDetail = {
  id: '123e4567-e89b-12d3-a456-426614174000',
  companyName: 'GoogleZH',
  partnershipLevel: 'PLATINUM' as const,
  partnershipStartDate: '2022-01-01T00:00:00Z',
  isActive: true,
};

// Mock organizer user
const mockOrganizerUser = {
  username: 'organizer1',
  role: 'ORGANIZER' as const,
};

// Mock non-organizer user
const mockNonOrganizerUser = {
  username: 'partner1',
  role: 'PARTNER' as const,
};

describe('PartnerSettingsTab - AC8 Tests', () => {
  /**
   * Test 8.1: should_renderSettingsTab_when_tabActivatedByOrganizer
   * Verify settings tab renders for organizer role
   */
  it('should_renderSettingsTab_when_tabActivatedByOrganizer', () => {
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockOrganizerUser}
        onUpdateStatus={vi.fn()}
      />
    );

    expect(screen.getByRole('heading', { name: /Settings/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Active Partnership/i })).toBeInTheDocument();
  });

  /**
   * Test 8.2: should_hideSettingsTab_when_userNotOrganizer
   * Verify settings tab shows access denied for non-organizer
   */
  it('should_hideSettingsTab_when_userNotOrganizer', () => {
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockNonOrganizerUser}
        onUpdateStatus={vi.fn()}
      />
    );

    expect(screen.getByText(/Access Denied/i)).toBeInTheDocument();
    expect(screen.getByText(/only accessible to organizers/i)).toBeInTheDocument();
  });

  /**
   * Test 8.3: should_togglePartnershipStatus_when_toggleChanged
   * Verify status toggle triggers callback with new value
   */
  it('should_togglePartnershipStatus_when_toggleChanged', async () => {
    const onUpdateStatus = vi.fn();
    const user = userEvent.setup();
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockOrganizerUser}
        onUpdateStatus={onUpdateStatus}
      />
    );

    const statusSwitch = screen.getByRole('switch', { name: /Active/i });
    expect(statusSwitch).toBeChecked();

    await user.click(statusSwitch);
    expect(onUpdateStatus).toHaveBeenCalledWith(false);
  });

  /**
   * Auto-renewal was REMOVED from this tab (issue #821). `autoRenewal` and `renewalDate`
   * exist in neither `PartnerResponse` nor `UpdatePartnerRequest` — the switch and the
   * "renews on" line rendered fields the backend has never had, so they were always
   * undefined and the switch could never persist anything. This asserts they stay gone.
   */
  it('should_notRenderAutoRenewalControls_when_settingsTabRendered', () => {
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockOrganizerUser}
        onUpdateStatus={vi.fn()}
      />
    );

    expect(screen.queryByRole('switch', { name: /Auto-Renewal/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/Renewal Date/i)).not.toBeInTheDocument();
  });

  /**
   * Issue #821: the switch must be disabled while the activate/deactivate call is in
   * flight, so a double-click cannot fire two opposing mutations.
   */
  it('should_disableStatusToggle_when_updateInFlight', () => {
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockOrganizerUser}
        onUpdateStatus={vi.fn()}
        isUpdatingStatus
      />
    );

    // Query by role, not testid: MUI puts data-testid on the wrapper span while the
    // disabled attribute lives on the inner input.
    expect(screen.getByRole('switch', { name: /Active/i })).toBeDisabled();
  });

  /**
   * Test 8.5: should_disableExportButton_when_epic8Deferred
   * Verify export button is disabled with tooltip
   */
  it('should_disableExportButton_when_epic8Deferred', () => {
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockOrganizerUser}
        onUpdateStatus={vi.fn()}
      />
    );

    const exportButton = screen.getByRole('button', { name: /Export/i });
    expect(exportButton).toBeDisabled();
    expect(exportButton).toHaveAttribute('title', expect.stringContaining('Epic 8'));
  });

  /**
   * Test 8.6: should_disableDeleteButton_when_epic8Deferred
   * Verify delete button is disabled with tooltip
   */
  it('should_disableDeleteButton_when_epic8Deferred', () => {
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockOrganizerUser}
        onUpdateStatus={vi.fn()}
      />
    );

    const deleteButton = screen.getByRole('button', { name: /Delete/i });
    expect(deleteButton).toBeDisabled();
    expect(deleteButton).toHaveAttribute('title', expect.stringContaining('Epic 8'));
  });

  /**
   * Test 8.8: should_showActiveStatus_when_partnerActive
   * Verify active status is displayed correctly
   */
  it('should_showActiveStatus_when_partnerActive', () => {
    render(
      <PartnerSettingsTab
        partner={mockPartnerDetail}
        currentUser={mockOrganizerUser}
        onUpdateStatus={vi.fn()}
      />
    );

    const statusSwitch = screen.getByRole('switch', { name: /Active/i });
    expect(statusSwitch).toBeChecked();
  });

  /**
   * Test 8.9: should_showInactiveStatus_when_partnerInactive
   * Verify inactive status is displayed correctly
   */
  it('should_showInactiveStatus_when_partnerInactive', () => {
    const inactivePartner = { ...mockPartnerDetail, isActive: false };
    render(
      <PartnerSettingsTab
        partner={inactivePartner}
        currentUser={mockOrganizerUser}
        onUpdateStatus={vi.fn()}
      />
    );

    const statusSwitch = screen.getByRole('switch', { name: /Active/i });
    expect(statusSwitch).not.toBeChecked();
  });
});
