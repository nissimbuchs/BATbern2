/**
 * EventDetailsContainer tests (Epic 14, Story 14.F.2)
 *
 * The merged config tab: Info / Tasks / Settings sub-tabs. Children are stubbed
 * (each has its own tests); here we verify the sub-tab switch + default.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { EventDetailsContainer } from '../EventDetailsContainer';
import type { Event } from '@/types/event.types';

vi.mock('../EventInfoTab', () => ({
  EventInfoTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="stub-info">{eventCode}</div>
  ),
}));
vi.mock('../EventTasksLiveTab', () => ({
  EventTasksLiveTab: () => <div data-testid="stub-tasks" />,
}));
vi.mock('../EventSettingsTab', () => ({
  EventSettingsTab: () => <div data-testid="stub-settings" />,
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (_k: string, def?: string) => def ?? _k }),
}));

const mockEvent = { eventCode: 'BAT54', title: 'Spring Conf' } as Event;

function renderContainer() {
  return render(<EventDetailsContainer event={mockEvent} eventCode="BAT54" />);
}

describe('EventDetailsContainer', () => {
  it('renders the three sub-tabs and defaults to Info', () => {
    renderContainer();
    expect(screen.getByTestId('details-subtab-info')).toBeInTheDocument();
    expect(screen.getByTestId('details-subtab-tasks')).toBeInTheDocument();
    expect(screen.getByTestId('details-subtab-settings')).toBeInTheDocument();
    expect(screen.getByTestId('stub-info')).toHaveTextContent('BAT54');
    expect(screen.queryByTestId('stub-tasks')).not.toBeInTheDocument();
  });

  it('switches to the Tasks sub-tab', () => {
    renderContainer();
    fireEvent.click(screen.getByTestId('details-subtab-tasks'));
    expect(screen.getByTestId('stub-tasks')).toBeInTheDocument();
    expect(screen.queryByTestId('stub-info')).not.toBeInTheDocument();
  });

  it('switches to the Settings sub-tab', () => {
    renderContainer();
    fireEvent.click(screen.getByTestId('details-subtab-settings'));
    expect(screen.getByTestId('stub-settings')).toBeInTheDocument();
  });
});
