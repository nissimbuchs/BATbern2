/**
 * #819: a logged-in visitor saw their own address ("user-profile name") on the public website,
 * outside any menu. The source was the mobile navigation drawer: closed, it was only moved off
 * the right edge, so its identity strip (initials and address) stayed rendered and could show on
 * the page (#1223 item 10, fixed in #1323 by making the closed drawer invisible and inert).
 *
 * This guards the whole public layout, not only the drawer: signed in, with every menu closed,
 * the visitor's address is not visible anywhere on the page. Real AuthContext
 * (`renderWithAuth`) and the real i18next `en` bundle.
 */
import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PublicLayout } from '../PublicLayout';
import { anAuthState, anAuthUser, renderWithAuth } from '@/test/auth';
import type { UserRole } from '@/types/auth';

const EMAIL = 'ines.probe@example.com';

const renderSignedIn = (role: UserRole) =>
  renderWithAuth(
    <PublicLayout>
      <p>Page content</p>
    </PublicLayout>,
    { auth: anAuthState(anAuthUser({ role, roles: [role], email: EMAIL, username: 'ines.probe' })) }
  );

/**
 * Whether the element is visually hidden (a closed menu). aria-hidden alone does not count: the
 * closed drawer carried it before #1323 and its strip still showed on the page.
 */
const isHidden = (element: Element): boolean => element.closest('.invisible, [hidden]') !== null;

describe('PublicLayout signed in (#819)', () => {
  it.each<[UserRole]>([['attendee'], ['speaker'], ['organizer'], ['partner']])(
    'shows no address of the %s on the page while every menu is closed',
    (role) => {
      renderSignedIn(role);

      expect(screen.getByText('Page content')).toBeInTheDocument();
      const shown = screen.queryAllByText(EMAIL).filter((element) => !isHidden(element));
      expect(shown).toEqual([]);
    }
  );

  it('shows the address once the visitor opens the mobile menu (positive control)', async () => {
    const user = userEvent.setup();
    renderSignedIn('attendee');

    await user.click(screen.getByRole('button', { name: 'Open menu' }));

    const shown = screen.queryAllByText(EMAIL).filter((element) => !isHidden(element));
    expect(shown).toHaveLength(1);
  });
});
