/**
 * EventWrapupContainer tests (Epic 14, Story 14.F.1)
 *
 * The container stacks Photos + Thank-you notes in one panel (no sub-tab switch);
 * each child has its own tests, so we stub them and assert both render together.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EventWrapupContainer } from '../EventWrapupContainer';

vi.mock('../EventPhotosTab', () => ({
  EventPhotosTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="stub-photos">{eventCode}</div>
  ),
}));
vi.mock('../EventAppreciationTab', () => ({
  EventAppreciationTab: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="stub-appreciation">{eventCode}</div>
  ),
}));

describe('EventWrapupContainer', () => {
  it('renders Photos and Thank-you notes stacked together (no sub-tab switch)', () => {
    render(<EventWrapupContainer eventCode="BAT54" />);
    expect(screen.getByTestId('stub-photos')).toBeInTheDocument();
    expect(screen.getByTestId('stub-appreciation')).toBeInTheDocument();
    // The Phase A sub-tab switch is gone.
    expect(screen.queryByTestId('wrapup-subtab-photos')).not.toBeInTheDocument();
    expect(screen.queryByTestId('wrapup-subtab-appreciation')).not.toBeInTheDocument();
  });

  it('passes the event code through to both children', () => {
    render(<EventWrapupContainer eventCode="BAT54" />);
    expect(screen.getByTestId('stub-photos')).toHaveTextContent('BAT54');
    expect(screen.getByTestId('stub-appreciation')).toHaveTextContent('BAT54');
  });
});
