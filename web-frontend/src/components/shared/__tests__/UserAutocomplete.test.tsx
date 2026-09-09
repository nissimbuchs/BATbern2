/**
 * UserAutocomplete tests (added 2026-09-09 with the user-search bug fixes).
 *
 * This component shipped with no test file at all — every consumer stubs it — which is how
 * two defects survived:
 *
 *   1. the `role` filter ran CLIENT-side, on the already-truncated page of results, so a
 *      SPEAKER filter filtered whatever arbitrary rows the server happened to return rather
 *      than the best matches. It now goes to the backend.
 *   2. `getOptionLabel` returned `option.id` (the username), so selecting "Matthias Stürmer"
 *      put `matthias.stuermer` in the text field. It now shows the person's real name.
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UserAutocomplete } from '../UserAutocomplete';
import type { UserSearchResponse } from '@/types/user.types';

const searchUsersMock = vi.fn();

vi.mock('@/services/api/userManagementApi', () => ({
  searchUsers: (query: string, limit?: number, role?: string) =>
    searchUsersMock(query, limit, role),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const stuermer: UserSearchResponse = {
  id: 'matthias.stuermer',
  email: 'matthias.stuermer@bfh.ch',
  firstName: 'Matthias',
  lastName: 'Stürmer',
  roles: ['SPEAKER'],
  companyId: 'bfh',
};

const renderComponent = (props: Partial<React.ComponentProps<typeof UserAutocomplete>> = {}) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  const onChange = props.onChange ?? vi.fn();
  const result = render(
    <QueryClientProvider client={queryClient}>
      <UserAutocomplete
        value={null}
        onChange={onChange}
        data-testid="user-search-field"
        {...props}
      />
    </QueryClientProvider>
  );
  return { ...result, onChange };
};

describe('UserAutocomplete', () => {
  beforeEach(() => {
    searchUsersMock.mockReset();
    searchUsersMock.mockResolvedValue([stuermer]);
  });

  it('should_passRoleToTheBackend_when_roleFilterProvided', async () => {
    renderComponent({ role: 'SPEAKER' });

    await userEvent.type(screen.getByTestId('user-search-field'), 'Stürmer');

    await waitFor(
      () => {
        expect(searchUsersMock).toHaveBeenCalledWith('Stürmer', expect.any(Number), 'SPEAKER');
      },
      { timeout: 2000 }
    );
  });

  it('should_notFilterOptionsClientSide_when_roleFilterProvided', async () => {
    // The backend owns the role filter now. A row the server returned must be rendered even
    // if its `roles` array does not literally contain the filter (e.g. the server matched on
    // a role granted after the projection was cached) — client-side re-filtering is what used
    // to silently empty the dropdown.
    searchUsersMock.mockResolvedValue([{ ...stuermer, roles: [] }]);
    renderComponent({ role: 'SPEAKER' });

    await userEvent.type(screen.getByTestId('user-search-field'), 'Stürmer');

    await waitFor(
      () => {
        expect(screen.getByTestId('user-option-matthias.stuermer')).toBeInTheDocument();
      },
      { timeout: 2000 }
    );
  });

  it('should_showTheRealName_when_userIsSelected', async () => {
    renderComponent({ value: stuermer });

    await waitFor(() => {
      expect(screen.getByTestId('user-search-field')).toHaveValue('Matthias Stürmer');
    });
  });

  it('should_fallBackToUsername_when_userHasNoName', async () => {
    renderComponent({ value: { ...stuermer, firstName: undefined, lastName: undefined } });

    await waitFor(() => {
      expect(screen.getByTestId('user-search-field')).toHaveValue('matthias.stuermer');
    });
  });

  it('should_notResearch_when_theSelectedUsersNameIsAlreadyInTheField', async () => {
    // `isValueSelected` suppresses the query once the field holds the selection's label.
    // It used to compare against the username; with a name label it must compare against
    // the name, otherwise every selection fires a pointless extra search.
    renderComponent({ value: stuermer });

    await waitFor(() => {
      expect(screen.getByTestId('user-search-field')).toHaveValue('Matthias Stürmer');
    });
    await new Promise((resolve) => setTimeout(resolve, 500));

    expect(searchUsersMock).not.toHaveBeenCalled();
  });
});
