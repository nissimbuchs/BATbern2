/**
 * LifecycleSpine tests (Story 14.B.1 / FR8).
 */
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { LifecycleSpine } from '../LifecycleSpine';

const renderSpine = (props: { workflowState: string; compact?: boolean }) =>
  render(
    <I18nextProvider i18n={i18n}>
      <LifecycleSpine {...props} />
    </I18nextProvider>
  );

describe('LifecycleSpine', () => {
  it('renders the full 8-state stepper with the current "Step N of 8" caption', () => {
    renderSpine({ workflowState: 'SPEAKER_IDENTIFICATION' }); // step 3
    expect(screen.getByTestId('cockpit-lifecycle-spine')).toBeInTheDocument();
    expect(screen.getByText(/Step 3 of 8/i)).toBeInTheDocument();
    // A couple of the 8 state labels render.
    expect(screen.getByText('Created')).toBeInTheDocument();
    expect(screen.getByText('Speaker Identification')).toBeInTheDocument();
    expect(screen.getByText('Archived')).toBeInTheDocument();
  });

  it('falls back to step 1 for an unknown state without crashing', () => {
    renderSpine({ workflowState: 'BOGUS' });
    expect(screen.getByText(/Step 1 of 8/i)).toBeInTheDocument();
  });

  it('compact mode renders a progress bar + step caption (mobile seam)', () => {
    renderSpine({ workflowState: 'AGENDA_PUBLISHED', compact: true }); // step 5
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.getByText(/Step 5 of 8/i)).toBeInTheDocument();
  });
});
