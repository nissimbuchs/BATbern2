/**
 * AssignedOrganizerField tests (Story 10.30, AC3)
 *
 * Coverage:
 * - Renders OrganizerSelect with current assignedOrganizerId
 * - Calls usePatchSpeakerPool on organizer change
 * - Shows success snackbar on successful PATCH
 * - Shows error snackbar on failed PATCH
 * - Disables select while mutation is pending
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AssignedOrganizerField } from '../AssignedOrganizerField';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
    i18n: { language: 'en' },
  }),
}));

// Mock OrganizerSelect
vi.mock('@/components/shared/OrganizerSelect/OrganizerSelect', () => ({
  OrganizerSelect: ({
    value,
    onChange,
    disabled,
  }: {
    value: string;
    onChange: (v: string) => void;
    disabled?: boolean;
  }) => (
    <select
      data-testid="organizer-select"
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="">Unassigned</option>
      <option value="alice">Alice</option>
      <option value="bob">Bob</option>
    </select>
  ),
}));

// Mock usePatchSpeakerPool
const mockMutate = vi.fn();
let mockIsPending = false;
let mutateCallbacks: { onSuccess?: () => void; onError?: () => void } = {};

vi.mock('@/hooks/useSpeakerPool', () => ({
  usePatchSpeakerPool: () => ({
    mutate: (
      vars: unknown,
      callbacks: { onSuccess?: () => void; onError?: () => void }
    ) => {
      mutateCallbacks = callbacks;
      mockMutate(vars);
    },
    isPending: mockIsPending,
  }),
}));

function makeSpeaker(overrides: Partial<SpeakerPoolEntry> = {}): SpeakerPoolEntry {
  return {
    id: 'sp-1',
    eventId: 'evt-1',
    speakerName: 'Jane Doe',
    status: 'IDENTIFIED',
    createdAt: '2026-01-01T00:00:00Z',
    assignedOrganizerId: 'alice',
    ...overrides,
  };
}

function renderComponent(speaker = makeSpeaker()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AssignedOrganizerField speaker={speaker} eventCode="EVT-2026" />
    </QueryClientProvider>
  );
}

beforeEach(() => {
  mockIsPending = false;
  mutateCallbacks = {};
  vi.clearAllMocks();
});

describe('AssignedOrganizerField', () => {
  it('renders OrganizerSelect with current assignedOrganizerId', () => {
    renderComponent();
    const select = screen.getByTestId('organizer-select') as HTMLSelectElement;
    expect(select.value).toBe('alice');
  });

  it('renders with empty value when no organizer assigned', () => {
    renderComponent(makeSpeaker({ assignedOrganizerId: undefined }));
    const select = screen.getByTestId('organizer-select') as HTMLSelectElement;
    expect(select.value).toBe('');
  });

  it('calls usePatchSpeakerPool with correct args on organizer change', async () => {
    renderComponent();
    await userEvent.selectOptions(screen.getByTestId('organizer-select'), 'bob');
    expect(mockMutate).toHaveBeenCalledWith({
      eventCode: 'EVT-2026',
      speakerId: 'sp-1',
      request: { assignedOrganizerId: 'bob' },
    });
  });

  it('shows success snackbar after successful PATCH', async () => {
    renderComponent();
    await userEvent.selectOptions(screen.getByTestId('organizer-select'), 'bob');
    mutateCallbacks.onSuccess?.();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.getByRole('alert').textContent).toMatch(/organizer updated/i);
  });

  it('shows error snackbar after failed PATCH', async () => {
    renderComponent();
    await userEvent.selectOptions(screen.getByTestId('organizer-select'), 'bob');
    mutateCallbacks.onError?.();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.getByRole('alert').textContent).toMatch(/failed to update organizer/i);
  });

  it('disables select while mutation is pending', () => {
    mockIsPending = true;
    renderComponent();
    expect(screen.getByTestId('organizer-select')).toBeDisabled();
  });
});
