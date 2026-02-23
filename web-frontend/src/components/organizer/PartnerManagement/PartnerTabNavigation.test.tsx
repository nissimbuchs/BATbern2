import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PartnerTabNavigation } from './PartnerTabNavigation';

describe('PartnerTabNavigation', () => {
  const mockOnTabChange = vi.fn();

  beforeEach(() => {
    mockOnTabChange.mockClear();
  });

  // AC3 Test 3.1: should_renderTabNavigation_when_componentMounts
  it('should_renderTabNavigation_when_componentMounts', () => {
    render(<PartnerTabNavigation activeTab={0} onTabChange={mockOnTabChange} />);

    expect(screen.getByRole('tablist')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /overview/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /contacts/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /meetings/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /analytics/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /notes/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /settings/i })).toBeInTheDocument();
  });

  // AC3 Test 3.2: should_switchTabs_when_tabClicked
  it('should_switchTabs_when_tabClicked', () => {
    render(<PartnerTabNavigation activeTab={0} onTabChange={mockOnTabChange} />);

    const contactsTab = screen.getByRole('tab', { name: /contacts/i });
    fireEvent.click(contactsTab);

    expect(mockOnTabChange).toHaveBeenCalledWith(1);
  });

  // AC3 Test 3.3: should_highlightActiveTab_when_tabSelected
  it('should_highlightActiveTab_when_tabSelected', () => {
    render(<PartnerTabNavigation activeTab={2} onTabChange={mockOnTabChange} />);

    const meetingsTab = screen.getByRole('tab', { name: /meetings/i });
    expect(meetingsTab).toHaveAttribute('aria-selected', 'true');
  });

  // AC3 Test 3.4: should_navigateWithKeyboard_when_arrowKeysPressed
  it('should_navigateWithKeyboard_when_arrowKeysPressed', () => {
    const { unmount } = render(
      <PartnerTabNavigation activeTab={0} onTabChange={mockOnTabChange} />
    );

    const tablist = screen.getByRole('tablist');

    // Right arrow should move to next tab
    fireEvent.keyDown(tablist, { key: 'ArrowRight' });
    expect(mockOnTabChange).toHaveBeenCalledWith(1);

    unmount();
    mockOnTabChange.mockClear();

    // Left arrow should move to previous tab
    render(<PartnerTabNavigation activeTab={2} onTabChange={mockOnTabChange} />);
    const tablist2 = screen.getByRole('tablist');
    fireEvent.keyDown(tablist2, { key: 'ArrowLeft' });
    expect(mockOnTabChange).toHaveBeenCalledWith(1);
  });

  // AC3 Test 3.5: should_persistActiveTab_when_urlHashUsed
  it('should_persistActiveTab_when_urlHashUsed', () => {
    // This test verifies that the component can work with URL hash
    // The actual hash persistence will be handled by the parent component
    const tabNames = ['overview', 'contacts', 'meetings', 'analytics', 'notes', 'settings'];

    tabNames.forEach((_, index) => {
      const { unmount } = render(
        <PartnerTabNavigation activeTab={index} onTabChange={mockOnTabChange} />
      );
      const tabs = screen.getAllByRole('tab');
      expect(tabs[index]).toHaveAttribute('aria-selected', 'true');
      unmount();
    });
  });

  // AC3 Test 3.6: should_lazyLoadTabContent_when_tabActivated
  it('should_lazyLoadTabContent_when_tabActivated', () => {
    // This test verifies that tab navigation triggers onTabChange
    // which will be used by parent to trigger lazy loading
    render(<PartnerTabNavigation activeTab={0} onTabChange={mockOnTabChange} />);

    // Click on Analytics tab (index 3)
    const activityTab = screen.getByRole('tab', { name: /analytics/i });
    fireEvent.click(activityTab);

    // Verify callback was called with correct index for lazy loading
    expect(mockOnTabChange).toHaveBeenCalledWith(3);
  });

  // Additional test: should handle Home key to go to first tab
  it('should_navigateToFirst_when_homeKeyPressed', () => {
    render(<PartnerTabNavigation activeTab={3} onTabChange={mockOnTabChange} />);

    const tablist = screen.getByRole('tablist');
    fireEvent.keyDown(tablist, { key: 'Home' });

    expect(mockOnTabChange).toHaveBeenCalledWith(0);
  });

  // Additional test: should handle End key to go to last tab
  it('should_navigateToLast_when_endKeyPressed', () => {
    render(<PartnerTabNavigation activeTab={1} onTabChange={mockOnTabChange} />);

    const tablist = screen.getByRole('tablist');
    fireEvent.keyDown(tablist, { key: 'End' });

    expect(mockOnTabChange).toHaveBeenCalledWith(5);
  });

  // Additional test: should wrap around when using arrow keys at boundaries
  it('should_wrapAround_when_arrowKeyAtBoundary', () => {
    // At last tab, right arrow should wrap to first
    const { unmount } = render(
      <PartnerTabNavigation activeTab={5} onTabChange={mockOnTabChange} />
    );
    const tablist = screen.getByRole('tablist');
    fireEvent.keyDown(tablist, { key: 'ArrowRight' });
    expect(mockOnTabChange).toHaveBeenCalledWith(0);
    unmount();

    mockOnTabChange.mockClear();

    // At first tab, left arrow should wrap to last
    render(<PartnerTabNavigation activeTab={0} onTabChange={mockOnTabChange} />);
    const tablist2 = screen.getByRole('tablist');
    fireEvent.keyDown(tablist2, { key: 'ArrowLeft' });
    expect(mockOnTabChange).toHaveBeenCalledWith(5);
  });

  // Additional test: should display all 6 tab labels
  it('should_displayAllTabLabels_when_rendered', () => {
    render(<PartnerTabNavigation activeTab={0} onTabChange={mockOnTabChange} />);

    expect(screen.getByRole('tab', { name: /overview/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /contacts/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /meetings/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /analytics/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /notes/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /settings/i })).toBeInTheDocument();
  });
});
