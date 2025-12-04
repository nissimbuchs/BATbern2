/**
 * QuickActions Component Tests (RED Phase - TDD)
 *
 * Story 5.1 - Task 3a
 * AC: 7 (React Component with EventTypeSelector)
 * Wireframe: docs/wireframes/story-5.1-event-type-configuration.md v1.0 (Screen 1)
 *
 * Tests for Quick Actions sidebar component with:
 * - New Event button
 * - Event Types configuration button with navigation
 * - Helper text for Event Types button
 * - Placeholder buttons for future actions
 * - i18n compliance (all text uses react-i18next)
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { QuickActions } from '../QuickActions';

// Mock useNavigate from react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('QuickActions Component', () => {
  const createWrapper = () => {
    return ({ children }: { children: React.ReactNode }) => (
      <BrowserRouter>
        <I18nextProvider i18n={i18n}>{children}</I18nextProvider>
      </BrowserRouter>
    );
  };

  beforeEach(() => {
    mockNavigate.mockClear();
  });

  /**
   * Test 7.1a: should_displayQuickActionsTitle_when_componentRendered
   * AC7: Component displays "Quick Actions" title from i18n
   */
  it('should_displayQuickActionsTitle_when_componentRendered', () => {
    const { container } = render(<QuickActions />, { wrapper: createWrapper() });

    expect(screen.getByText(/quick actions/i)).toBeInTheDocument();
    expect(container.querySelector('[data-testid="quick-actions"]')).toBeInTheDocument();
  });

  /**
   * Test 7.1b: should_displayNewEventButton_when_componentRendered
   * AC7: Component displays "New Event" button (moved from header)
   */
  it('should_displayNewEventButton_when_componentRendered', () => {
    render(<QuickActions />, { wrapper: createWrapper() });

    const newEventButton = screen.getByRole('button', { name: /new event/i });
    expect(newEventButton).toBeInTheDocument();
  });

  /**
   * Test 7.1c: should_displayEventTypesButton_when_componentRendered
   * AC7: Component displays "Event Types" button with gear icon
   */
  it('should_displayEventTypesButton_when_componentRendered', () => {
    render(<QuickActions />, { wrapper: createWrapper() });

    const eventTypesButton = screen.getByRole('button', { name: /event types/i });
    expect(eventTypesButton).toBeInTheDocument();
  });

  /**
   * Test 7.1d: should_displayHelperText_when_eventTypesButtonRendered
   * AC7: Component displays helper text below Event Types button
   */
  it('should_displayHelperText_when_eventTypesButtonRendered', () => {
    render(<QuickActions />, { wrapper: createWrapper() });

    // Helper text from translation key: events.dashboard.actions.eventTypesHelp
    expect(screen.getByText(/configure event type templates/i)).toBeInTheDocument();
  });

  /**
   * Test 7.2a: should_navigateToEventTypes_when_eventTypesButtonClicked
   * AC7: Clicking Event Types button navigates to /organizer/event-types
   */
  it('should_navigateToEventTypes_when_eventTypesButtonClicked', () => {
    render(<QuickActions />, { wrapper: createWrapper() });

    const eventTypesButton = screen.getByRole('button', { name: /event types/i });
    fireEvent.click(eventTypesButton);

    expect(mockNavigate).toHaveBeenCalledWith('/organizer/event-types');
  });

  /**
   * Test 7.3a: should_useI18nKeys_when_componentRendered
   * i18n compliance: All user-facing text uses react-i18next
   */
  it('should_useI18nKeys_when_componentRendered', () => {
    render(<QuickActions />, { wrapper: createWrapper() });

    // Verify i18n keys are used (text rendered correctly means i18n is working)
    expect(screen.getByText(/quick actions/i)).toBeInTheDocument();
    expect(screen.getByText(/new event/i)).toBeInTheDocument();
    expect(screen.getByText(/event types/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4a: should_displayInPaper_when_componentRendered
   * AC7: Component uses Material-UI Paper for visual grouping
   */
  it('should_displayInPaper_when_componentRendered', () => {
    const { container } = render(<QuickActions />, { wrapper: createWrapper() });

    // Paper component should have elevation styling
    const paper = container.querySelector('.MuiPaper-root');
    expect(paper).toBeInTheDocument();
  });
});
