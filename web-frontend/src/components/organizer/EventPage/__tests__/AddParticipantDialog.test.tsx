/**
 * AddParticipantDialog — organizer adds an existing user as a confirmed participant.
 * UserAutocomplete is stubbed (a button that fires onChange with a fake user); the
 * eventRegistrationService is mocked.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@/test/test-utils';
import userEvent from '@testing-library/user-event';
import { AddParticipantDialog } from '../AddParticipantDialog';

const addParticipant = vi.fn();
vi.mock('@/services/api/eventRegistrationService', () => ({
  addParticipant: (eventCode: string, params: unknown) => addParticipant(eventCode, params),
}));

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
  beforeEach(() => {
    addParticipant.mockReset();
  });

  it('disables Add until a user is selected, then submits with the chosen username', async () => {
    addParticipant.mockResolvedValue({ registrationCode: 'BATbern73-X', status: 'confirmed' });
    const { onAdded } = renderDialog();

    expect(screen.getByTestId('add-participant-confirm')).toBeDisabled();

    await userEvent.click(screen.getByTestId('mock-pick-user'));
    expect(screen.getByTestId('add-participant-confirm')).toBeEnabled();

    await userEvent.click(screen.getByTestId('add-participant-confirm'));

    await waitFor(() =>
      expect(addParticipant).toHaveBeenCalledWith('BATbern73', {
        username: 'jane.doe',
        force: false,
        notify: true,
      })
    );
    await waitFor(() => expect(onAdded).toHaveBeenCalledWith('Jane Doe'));
  });

  it('on capacity_exceeded shows "add anyway" and re-submits with force=true', async () => {
    addParticipant
      .mockRejectedValueOnce({
        response: { status: 409, data: { details: { code: 'capacity_exceeded' } } },
      })
      .mockResolvedValueOnce({ registrationCode: 'BATbern73-X', status: 'confirmed' });
    renderDialog();

    await userEvent.click(screen.getByTestId('mock-pick-user'));
    await userEvent.click(screen.getByTestId('add-participant-confirm'));

    // Warning shown; the button now offers the override.
    await waitFor(() => expect(screen.getByTestId('add-participant-error')).toBeInTheDocument());

    await userEvent.click(screen.getByTestId('add-participant-confirm'));
    await waitFor(() =>
      expect(addParticipant).toHaveBeenLastCalledWith('BATbern73', {
        username: 'jane.doe',
        force: true,
        notify: true,
      })
    );
  });

  it('on a generic 409 shows the duplicate error', async () => {
    addParticipant.mockRejectedValue({ response: { status: 409, data: { details: {} } } });
    renderDialog();

    await userEvent.click(screen.getByTestId('mock-pick-user'));
    await userEvent.click(screen.getByTestId('add-participant-confirm'));

    await waitFor(() => expect(screen.getByTestId('add-participant-error')).toBeInTheDocument());
    // Did not succeed.
    expect(addParticipant).toHaveBeenCalledTimes(1);
  });
});
