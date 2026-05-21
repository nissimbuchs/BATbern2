/**
 * DetailsTabPanel — Phase D.5 (BATbern75 follow-up, 2026-05-21)
 *
 * Verifies the drawer Details subtab lockdown: once a session is provisioned
 * (status ≥ READY per Story 11.E.8), the only editable field is
 * `assignedOrganizerId`. speakerName / company / expertise / notes become
 * read-only inputs sourced from the linked User / brainstorm columns.
 *
 * Pre-session brainstorm UX is preserved.
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { DetailsTabPanel } from '../DetailsTabPanel';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

// Lean mocks: the only outbound surfaces we care about are usePatchSpeakerPool
// (the save mutation) and usePublicUser (the linked-User name resolver).
vi.mock('@/hooks/useSpeakerPool', () => ({
  usePatchSpeakerPool: () => ({
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
  }),
}));

vi.mock('@/hooks/useUserPortrait', () => ({
  usePublicUser: () => ({
    data: { firstName: 'Jane', lastName: 'Doe', username: 'jane.doe' },
  }),
}));

// OrganizerSelect is a complex Autocomplete tree; stub it out — we only need to
// observe that the field is present and editable.
vi.mock('@/components/shared/OrganizerSelect', () => ({
  OrganizerSelect: ({
    value,
    onChange,
    disabled,
  }: {
    value: string;
    onChange: (v: string) => void;
    disabled?: boolean;
  }) => (
    <input
      data-testid="organizer-select-stub"
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
    />
  ),
}));

const makeSpeaker = (overrides: Partial<SpeakerPoolEntry> = {}): SpeakerPoolEntry => ({
  id: 'speaker-pool-1',
  eventId: 'event-1',
  speakerName: 'Jane Doe',
  status: 'IDENTIFIED',
  assignedOrganizerId: 'nissim.buchs',
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-15T00:00:00Z',
  ...overrides,
});

const renderPanel = (speaker: SpeakerPoolEntry, isEditing = true) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <DetailsTabPanel
          speaker={speaker}
          eventCode="BATbern75"
          isEditing={isEditing}
          onExitEditMode={vi.fn()}
        />
      </QueryClientProvider>
    </I18nextProvider>
  );
};

describe('DetailsTabPanel — Phase D.5 lockdown (drawer is workflow-only post-session)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('locks company / expertise / notes when sessionId is set; assignedOrganizer stays editable', () => {
    const sessionAssigned = makeSpeaker({
      sessionId: 'session-abc',
      status: 'CONTENT_SUBMITTED',
      username: 'jane.doe',
      company: 'TestCo',
      expertise: 'cloud',
      notes: 'internal notes',
    });

    renderPanel(sessionAssigned, true);

    // company, expertise, notes are rendered but disabled.
    const company = screen.getByTestId('details-edit-company') as HTMLInputElement;
    const expertise = screen.getByTestId('details-edit-expertise') as HTMLInputElement;
    const notes = screen.getByTestId('details-edit-notes') as HTMLTextAreaElement;
    expect(company.disabled).toBe(true);
    expect(expertise.disabled).toBe(true);
    expect(notes.disabled).toBe(true);

    // assignedOrganizer (the only editable field post-session) stays interactive.
    const organizerSelect = screen.getByTestId('organizer-select-stub') as HTMLInputElement;
    expect(organizerSelect.disabled).toBe(false);
  });

  it('keeps all fields editable pre-session (IDENTIFIED / CONTACTED)', () => {
    const brainstorm = makeSpeaker({
      sessionId: undefined,
      status: 'IDENTIFIED',
      company: 'TestCo',
      expertise: 'cloud',
      notes: 'brainstorm notes',
    });

    renderPanel(brainstorm, true);

    const company = screen.getByTestId('details-edit-company') as HTMLInputElement;
    const expertise = screen.getByTestId('details-edit-expertise') as HTMLInputElement;
    const notes = screen.getByTestId('details-edit-notes') as HTMLTextAreaElement;
    expect(company.disabled).toBe(false);
    expect(expertise.disabled).toBe(false);
    expect(notes.disabled).toBe(false);

    const organizerSelect = screen.getByTestId('organizer-select-stub') as HTMLInputElement;
    expect(organizerSelect.disabled).toBe(false);
  });

  it('save payload strips locked fields when sessionId is set — only assignedOrganizerId on the wire', async () => {
    // This test reaches into the mocked patch mutation to inspect the request body.
    const mutateAsync = vi.fn().mockResolvedValue({});
    vi.doMock('@/hooks/useSpeakerPool', () => ({
      usePatchSpeakerPool: () => ({ mutateAsync, isPending: false }),
    }));
    // Re-import after re-mocking so the override takes effect.
    vi.resetModules();
    const { DetailsTabPanel: PatchedPanel } = await import('../DetailsTabPanel');

    const sessionAssigned = makeSpeaker({
      sessionId: 'session-abc',
      status: 'CONTENT_SUBMITTED',
      username: 'jane.doe',
      company: 'TestCo',
      expertise: 'cloud',
      notes: 'internal notes',
    });

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    render(
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <PatchedPanel
            speaker={sessionAssigned}
            eventCode="BATbern75"
            isEditing={true}
            onExitEditMode={vi.fn()}
          />
        </QueryClientProvider>
      </I18nextProvider>
    );

    const user = userEvent.setup();
    await user.click(screen.getByTestId('details-edit-save'));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    const call = mutateAsync.mock.calls[0]?.[0] as {
      request: Record<string, unknown>;
    };
    expect(call.request).toBeDefined();
    expect(Object.keys(call.request)).toEqual(['assignedOrganizerId']);
    // Speaker-identity columns are stripped from the wire payload.
    expect(call.request).not.toHaveProperty('speakerName');
    expect(call.request).not.toHaveProperty('company');
    expect(call.request).not.toHaveProperty('expertise');
    expect(call.request).not.toHaveProperty('notes');
  });
});
