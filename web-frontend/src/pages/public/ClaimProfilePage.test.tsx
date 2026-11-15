/**
 * #1183 D — the claim link page: POST only on the button; 403 means another login.
 *
 * Rendered as the app routes it, inside the real `ProtectedRoute` with the provider's own path
 * rules, so a role that may not open `/claim-profile` lands on `/dashboard` here too (#1217).
 * The network is the MSW harness.
 */
import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import ClaimProfilePage from './ClaimProfilePage';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute/ProtectedRoute';
import { renderWithAuth, signedInAs } from '@/test/auth';
import { api } from '@/test/msw/api';
import type { UserRole } from '@/types/auth';

const renderClaimLink = (role: UserRole = 'attendee', search = '?token=abc') =>
  renderWithAuth(
    <Routes>
      <Route path="/dashboard" element={<div>Dashboard Page</div>} />
      <Route
        path="/claim-profile"
        element={
          <ProtectedRoute>
            <ClaimProfilePage />
          </ProtectedRoute>
        }
      />
    </Routes>,
    { auth: signedInAs(role), initialEntries: [`/claim-profile${search}`] }
  );

describe('ClaimProfilePage (#1183 D)', () => {
  it.each<UserRole>(['attendee', 'speaker', 'partner', 'organizer'])(
    'should_openTheClaimPage_when_%sFollowsTheLink',
    async (role) => {
      renderClaimLink(role);

      expect(await screen.findByTestId('claim-confirm-button')).toBeInTheDocument();
      expect(screen.queryByText('Dashboard Page')).not.toBeInTheDocument();
    }
  );

  it('should_notCallApi_until_buttonClicked_then_showMerged', async () => {
    api.on('confirmProfileClaim', () => ({ merged: true, alreadyMerged: false }));
    renderClaimLink();

    const button = await screen.findByTestId('claim-confirm-button');
    expect(api.calls('confirmProfileClaim')).toHaveLength(0);
    await userEvent.click(button);

    expect(await screen.findByTestId('claim-merged')).toBeInTheDocument();
    expect(api.calls('confirmProfileClaim')[0].body).toEqual({ token: 'abc' });
  });

  it('should_showOtherAccount_when_403', async () => {
    api.on('confirmProfileClaim', () =>
      HttpResponse.json({ message: 'forbidden' }, { status: 403 })
    );
    renderClaimLink();

    await userEvent.click(await screen.findByTestId('claim-confirm-button'));

    expect(await screen.findByTestId('claim-otherAccount')).toBeInTheDocument();
  });

  it('should_showInvalid_when_noToken', async () => {
    renderClaimLink('attendee', '');
    expect(await screen.findByTestId('claim-invalid')).toBeInTheDocument();
  });
});
