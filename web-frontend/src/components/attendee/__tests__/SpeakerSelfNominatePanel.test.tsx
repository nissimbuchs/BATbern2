/**
 * SpeakerSelfNominatePanel (Story 7.2 "I Could Speak on That", #1263).
 *
 * Network at the wire (MSW, `selfNominateSpeaker`, checked against event-speakers-api), real
 * auth context (`renderWithAuth`), real router, real i18next (English). The login page is a stand-in
 * that shows where the real `LoginPage` would send the visitor back to (`loginTarget`).
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedInAs, signedOut } from '@/test/auth';
import type { UseAuthReturn } from '@/contexts/AuthContext';
import { loginTarget } from '@/utils/auth/loginTarget';
import { SpeakerSelfNominatePanel } from '../SpeakerSelfNominatePanel';

function LoginStandIn() {
  const location = useLocation();
  return <p data-testid="login-return-target">{loginTarget(location.state)}</p>;
}

function renderPanel(
  auth: UseAuthReturn,
  props: Partial<React.ComponentProps<typeof SpeakerSelfNominatePanel>> = {}
) {
  return renderWithAuth(
    <Routes>
      <Route
        path="/"
        element={
          <SpeakerSelfNominatePanel eventCode="BATbern57" topicName="Cloud Native" {...props} />
        }
      />
      <Route path="/login" element={<LoginStandIn />} />
    </Routes>,
    { auth }
  );
}

const conflict = (code: string) =>
  HttpResponse.json(
    { message: 'Conflict', status: 409, error: 'Conflict', details: { code } },
    { status: 409 }
  );

async function fillAndSubmit(title: string, abstract: string) {
  const user = userEvent.setup();
  await user.click(screen.getByTestId('self-nominate-button'));
  await user.type(await screen.findByTestId('self-nominate-title'), title);
  await user.type(screen.getByTestId('self-nominate-abstract'), abstract);
  await user.click(screen.getByTestId('self-nominate-submit'));
}

describe('SpeakerSelfNominatePanel', () => {
  describe('signed out', () => {
    it('shows the button with the existing wording', () => {
      renderPanel(signedOut());
      expect(screen.getByTestId('self-nominate-button')).toHaveTextContent(
        i18n.t('common:attendee.selfNominate.button')
      );
    });

    it('sends the click to the login, which comes back to /?nominate=<code>', async () => {
      const user = userEvent.setup();
      renderPanel(signedOut());

      await user.click(screen.getByTestId('self-nominate-button'));

      expect(await screen.findByTestId('login-return-target')).toHaveTextContent(
        '/?nominate=BATbern57'
      );
      expect(screen.queryByTestId('self-nominate-dialog')).not.toBeInTheDocument();
      expect(api.calls('selfNominateSpeaker')).toHaveLength(0);
    });

    it('does not open the form from autoOpen while signed out', () => {
      renderPanel(signedOut(), { autoOpen: true });
      expect(screen.getByTestId('self-nominate-button')).toBeInTheDocument();
      expect(screen.queryByTestId('self-nominate-dialog')).not.toBeInTheDocument();
    });

    it('renders nothing with hideWhenSignedOut (the hero on an event page)', () => {
      renderPanel(signedOut(), { hideWhenSignedOut: true });
      expect(screen.queryByTestId('self-nominate-button')).not.toBeInTheDocument();
    });
  });

  describe('signed in', () => {
    it('shows the button with hideWhenSignedOut too, and opens the form on click', async () => {
      const user = userEvent.setup();
      renderPanel(signedInAs('attendee'), { hideWhenSignedOut: true });

      await user.click(screen.getByTestId('self-nominate-button'));

      expect(await screen.findByTestId('self-nominate-dialog')).toBeInTheDocument();
      expect(screen.getByTestId('self-nominate-title')).toBeInTheDocument();
      expect(screen.getByTestId('self-nominate-abstract')).toBeInTheDocument();
    });

    it('opens the form on mount with autoOpen', async () => {
      renderPanel(signedInAs('attendee'), { autoOpen: true });
      expect(await screen.findByTestId('self-nominate-dialog')).toBeInTheDocument();
    });

    it('disables submit until both title and abstract are valid', async () => {
      const user = userEvent.setup();
      renderPanel(signedInAs('attendee'));

      await user.click(screen.getByTestId('self-nominate-button'));
      const submit = await screen.findByTestId('self-nominate-submit');
      expect(submit).toBeDisabled();

      await user.type(screen.getByTestId('self-nominate-title'), 'A solid talk title');
      expect(submit).toBeDisabled(); // abstract still empty

      await user.type(screen.getByTestId('self-nominate-abstract'), 'A few sentences of pitch.');
      expect(submit).toBeEnabled();
    });

    it('posts the nomination for this event and shows success', async () => {
      renderPanel(signedInAs('attendee'));

      await fillAndSubmit('Event-driven in practice', 'A field report from the trenches.');

      expect(await screen.findByTestId('self-nominate-success')).toHaveTextContent(
        i18n.t('common:attendee.selfNominate.success')
      );
      const [call] = api.calls('selfNominateSpeaker');
      expect(call.params).toEqual({ eventCode: 'BATbern57' });
      expect(call.body).toEqual({
        sessionTitle: 'Event-driven in practice',
        abstract: 'A field report from the trenches.',
      });
    });

    it('treats a 409 DUPLICATE_SELF_NOMINATION as already-done', async () => {
      api.on('selfNominateSpeaker', () => conflict('DUPLICATE_SELF_NOMINATION'));
      renderPanel(signedInAs('attendee'));

      await fillAndSubmit('Another talk title', 'Pitch text goes here.');

      expect(await screen.findByTestId('self-nominate-success')).toBeInTheDocument();
    });

    it('shows the generic error (not already-done) for a 409 SELF_NOMINATION_NOT_ALLOWED', async () => {
      api.on('selfNominateSpeaker', () => conflict('SELF_NOMINATION_NOT_ALLOWED'));
      renderPanel(signedInAs('attendee'));

      await fillAndSubmit('A timely talk title', 'Pitch text goes here.');

      expect(await screen.findByTestId('self-nominate-error')).toHaveTextContent(
        i18n.t('common:attendee.selfNominate.error')
      );
      expect(screen.queryByTestId('self-nominate-success')).not.toBeInTheDocument();
    });

    it('shows the generic error when the submission fails for another reason', async () => {
      api.on('selfNominateSpeaker', () =>
        HttpResponse.json({ message: 'boom', status: 500, error: 'Error' }, { status: 500 })
      );
      renderPanel(signedInAs('attendee'));

      await fillAndSubmit('Yet another title', 'Some abstract content.');

      await waitFor(() => expect(screen.getByTestId('self-nominate-error')).toBeInTheDocument());
    });
  });
});
