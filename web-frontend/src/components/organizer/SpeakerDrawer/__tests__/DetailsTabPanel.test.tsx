/**
 * DetailsTabPanel — Phase D.5 (BATbern75 follow-up, 2026-05-21) and the proposed talk.
 *
 * Verifies the drawer Details subtab lockdown: once a session is provisioned
 * (status ≥ READY per Story 11.E.8), the only editable field is
 * `assignedOrganizerId`. speakerName / company / expertise / notes become
 * read-only inputs sourced from the linked User / brainstorm columns.
 * Pre-session brainstorm UX is preserved.
 *
 * A self-nomination shows what the person proposed (title, abstract, when) and a link to the
 * Mail Center conversation where organizers answer, read-only in both modes: the proposal stays
 * as submitted; the talk is edited on the session after promote.
 *
 * Real hooks, services and en i18n on the MSW harness; the user comes from `renderWithAuth`.
 */
import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/test/msw/api';
import { renderWithAuth } from '@/test/auth';
import { DetailsTabPanel } from '../DetailsTabPanel';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import { aSpeakerPoolEntry } from '@/test/fixtures';

const makeSpeaker = (overrides: Partial<SpeakerPoolEntry> = {}): SpeakerPoolEntry => ({
  ...aSpeakerPoolEntry(),
  id: 'speaker-pool-1',
  eventId: 'event-1',
  speakerName: 'Jane Doe',
  status: 'IDENTIFIED',
  assignedOrganizerId: 'nissim.buchs',
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-15T00:00:00Z',
  ...overrides,
});

const selfNomination = (overrides: Partial<SpeakerPoolEntry> = {}) =>
  makeSpeaker({
    source: 'self_nomination',
    proposedByUsername: 'jane.doe',
    proposedSessionTitle: 'FinOps zwischen Cloud und KI',
    proposedAbstract: 'Wie Kosten sichtbar werden,\nbevor die Rechnung kommt.',
    proposedAt: '2026-11-17T05:14:48Z',
    proposalConversationKey: 'BATbern75-c42',
    ...overrides,
  });

const renderPanel = (speaker: SpeakerPoolEntry, isEditing = true) =>
  renderWithAuth(
    <DetailsTabPanel
      speaker={speaker}
      eventCode="BATbern75"
      isEditing={isEditing}
      onExitEditMode={vi.fn()}
    />
  );

const organizerSelect = () => screen.getByRole('combobox', { name: /Assign to Organizer/ });

describe('DetailsTabPanel — Phase D.5 lockdown (drawer is workflow-only post-session)', () => {
  it('locks company / expertise / notes when sessionId is set; assignedOrganizer stays editable', async () => {
    renderPanel(
      makeSpeaker({
        sessionId: 'session-abc',
        status: 'CONTENT_SUBMITTED',
        username: 'jane.doe',
        company: 'TestCo',
        expertise: 'cloud',
        notes: 'internal notes',
      })
    );

    expect(screen.getByTestId('details-edit-company')).toBeDisabled();
    expect(screen.getByTestId('details-edit-expertise')).toBeDisabled();
    expect(screen.getByTestId('details-edit-notes')).toBeDisabled();
    // assignedOrganizer (the only editable field post-session) stays interactive once loaded.
    await waitFor(() => expect(organizerSelect()).not.toHaveAttribute('aria-disabled', 'true'));
  });

  it('keeps all fields editable pre-session (IDENTIFIED / CONTACTED)', async () => {
    renderPanel(
      makeSpeaker({ status: 'IDENTIFIED', company: 'TestCo', expertise: 'cloud', notes: 'n' })
    );

    expect(screen.getByTestId('details-edit-company')).toBeEnabled();
    expect(screen.getByTestId('details-edit-expertise')).toBeEnabled();
    expect(screen.getByTestId('details-edit-notes')).toBeEnabled();
    await waitFor(() => expect(organizerSelect()).not.toHaveAttribute('aria-disabled', 'true'));
  });

  it('sends only assignedOrganizerId when sessionId is set', async () => {
    renderPanel(
      makeSpeaker({
        sessionId: 'session-abc',
        status: 'CONTENT_SUBMITTED',
        username: 'jane.doe',
        company: 'TestCo',
        expertise: 'cloud',
        notes: 'internal notes',
      })
    );

    await userEvent.click(screen.getByTestId('details-edit-save'));

    await waitFor(() => expect(api.calls('patchSpeakerPoolEntry')).toHaveLength(1));
    const call = api.calls('patchSpeakerPoolEntry')[0];
    expect(call.params.speakerId).toBe('speaker-pool-1');
    expect(Object.keys(call.body as object)).toEqual(['assignedOrganizerId']);
  });
});

describe('DetailsTabPanel — the proposed talk of a self-nomination', () => {
  it('should show title, abstract, date and the conversation link', () => {
    renderPanel(selfNomination(), false);

    const proposal = screen.getByTestId('details-proposal');
    expect(within(proposal).getByText('Proposed talk')).toBeInTheDocument();
    expect(within(proposal).getByText('FinOps zwischen Cloud und KI')).toBeInTheDocument();
    expect(within(proposal).getByText(/Wie Kosten sichtbar werden/)).toBeInTheDocument();
    expect(within(proposal).getByText(/2026/)).toBeInTheDocument();
    expect(within(proposal).getByRole('link', { name: 'Open conversation' })).toHaveAttribute(
      'href',
      '/organizer/events/BATbern75?tab=communications&conversation=BATbern75-c42'
    );
  });

  it('should show the proposal read-only in edit mode, outside the form fields', () => {
    renderPanel(selfNomination(), true);

    expect(
      within(screen.getByTestId('details-proposal')).getByText('FinOps zwischen Cloud und KI')
    ).toBeInTheDocument();
    for (const box of screen.getAllByRole('textbox')) {
      expect(box).not.toHaveValue('FinOps zwischen Cloud und KI');
    }
  });

  it('should show no link when the proposal has no conversation', () => {
    renderPanel(selfNomination({ proposalConversationKey: null }), false);

    const proposal = screen.getByTestId('details-proposal');
    expect(within(proposal).getByText('FinOps zwischen Cloud und KI')).toBeInTheDocument();
    expect(within(proposal).queryByRole('link')).not.toBeInTheDocument();
  });

  it('should show no proposal for an organizer-added speaker', () => {
    // positive control: the same panel shows it for a self-nomination
    const { unmount } = renderPanel(selfNomination(), false);
    expect(screen.getByTestId('details-proposal')).toBeInTheDocument();
    unmount();

    renderPanel(makeSpeaker({ source: 'organizer_added' }), false);
    expect(screen.queryByTestId('details-proposal')).not.toBeInTheDocument();
  });
});
