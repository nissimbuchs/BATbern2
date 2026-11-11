/**
 * AddParticipantDialog — organizer adds an existing user as a confirmed participant.
 * UserAutocomplete is stubbed (a button that fires onChange with a fake user); the real
 * eventRegistrationService posts to `POST /events/{eventCode}/participants` (Story 4.10c,
 * `addParticipants`) on the MSW harness.
 */
import { describe, it, expect, vi } from 'vitest';
import { HttpResponse } from 'msw';
import { render, screen, waitFor } from '@/test/test-utils';
import userEvent from '@testing-library/user-event';
import { api } from '@/test/msw/api';
import { AddParticipantDialog } from '../AddParticipantDialog';

const added = {
  registrationCode: 'BATbern73-X',
  status: 'confirmed',
  attendeeUsername: 'jane.doe',
};

const conflict = (details: Record<string, unknown>) =>
  HttpResponse.json(
    {
      timestamp: '2026-11-11T10:00:00Z',
      path: '/api/v1/events/BATbern73/participants',
      status: 409,
      error: 'Conflict',
      message: 'x',
      details,
    },
    { status: 409 }
  );

// Stub the heavy autocomplete: clicking the button "selects" a user via onChange.
vi.mock('@/components/shared/UserAutocomplete', () => ({
  UserAutocomplete: ({ onChange }: { onChange: (u: unknown) => void }) => (
    <button
      type="button"
      data-testid="mock-pick-user"
      onClick={() =>
        onChange({ id: 'jane.doe', email: 'jane@x.ch', firstName: 'Jane', lastName: 'Doe' })
      }
    >
      pick
    </button>
  ),
}));

function renderDialog(overrides: Partial<React.ComponentProps<typeof AddParticipantDialog>> = {}) {
  const onClose = vi.fn();
  const onAdded = vi.fn();
  render(
    <AddParticipantDialog
      open
      onClose={onClose}
      eventCode="BATbern73"
      onAdded={onAdded}
      {...overrides}
    />
  );
  return { onClose, onAdded };
}

describe('AddParticipantDialog', () => {
  it('disables Add until a user is selected, then submits with the chosen username', async () => {
    api.on('addParticipants', () => added, { status: 201 });
    const { onAdded } = renderDialog();

    expect(screen.getByTestId('add-participant-confirm')).toBeDisabled();

    await userEvent.click(screen.getByTestId('mock-pick-user'));
    expect(screen.getByTestId('add-participant-confirm')).toBeEnabled();

    await userEvent.click(screen.getByTestId('add-participant-confirm'));

    await waitFor(() => expect(onAdded).toHaveBeenCalledWith('Jane Doe'));
    const [call] = api.calls('addParticipants');
    expect(call.params.eventCode).toBe('BATbern73');
    expect(call.body).toEqual({ username: 'jane.doe', force: false, notify: true });
  });

  it('on capacity_exceeded shows "add anyway" and re-submits with force=true', async () => {
    api.on(
      'addParticipants',
      ({ body }) => (body?.force ? added : conflict({ code: 'capacity_exceeded' })),
      { status: 201 }
    );
    renderDialog();

    await userEvent.click(screen.getByTestId('mock-pick-user'));
    await userEvent.click(screen.getByTestId('add-participant-confirm'));

    // Warning shown; the button now offers the override.
    await waitFor(() => expect(screen.getByTestId('add-participant-error')).toBeInTheDocument());

    await userEvent.click(screen.getByTestId('add-participant-confirm'));
    await waitFor(() => expect(api.calls('addParticipants')).toHaveLength(2));
    expect(api.calls('addParticipants')[1].body).toEqual({
      username: 'jane.doe',
      force: true,
      notify: true,
    });
  });

  it('on a generic 409 shows the duplicate error', async () => {
    api.on('addParticipants', () => conflict({}));
    renderDialog();

    await userEvent.click(screen.getByTestId('mock-pick-user'));
    await userEvent.click(screen.getByTestId('add-participant-confirm'));

    await waitFor(() => expect(screen.getByTestId('add-participant-error')).toBeInTheDocument());
    // Did not succeed.
    expect(api.calls('addParticipants')).toHaveLength(1);
  });
});
