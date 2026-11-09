/**
 * UserSettingsTab: additional e-mails (Story 10.32, #1244), Q&A frequency (Story 15.7),
 * responsive sub-tabs, and the one settings resource (Story 4.9).
 *
 * Runs on the MSW harness with the real hooks, services and i18next (en): the harness checks
 * every request against users-api and records it; texts are looked up in the real bundles.
 */
import { describe, expect, test, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import UserSettingsTab from './UserSettingsTab';
import type { AdditionalEmail, UserPreferencesView } from '@/types/userAccount.types';

/** The en text of a userManagement key, as the screen renders it. */
const en = (key: string) => i18n.t(key, { ns: 'userManagement', lng: 'en' });

const address = (email: string, over: Partial<AdditionalEmail> = {}): AdditionalEmail => ({
  email,
  label: null,
  createdAt: '2026-05-22T10:00:00Z',
  verifiedAt: null,
  receiveEventMail: true,
  ...over,
});

function renderTab(additionalEmails: AdditionalEmail[] = [], preferences?: UserPreferencesView) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <UserSettingsTab
        email="anna.coordinator@example.com"
        additionalEmails={additionalEmails}
        preferences={preferences}
      />
    </QueryClientProvider>
  );
}

const typeAddress = (value: string) =>
  fireEvent.change(screen.getByTestId('additional-email-input').querySelector('input')!, {
    target: { value },
  });

describe('UserSettingsTab — Story 10.32 additional emails section', () => {
  test('should render the additional-emails section heading', () => {
    renderTab([]);
    expect(screen.getByTestId('additional-emails-section')).toBeInTheDocument();
    expect(screen.getByText(en('settings.account.additionalEmailsTitle'))).toBeInTheDocument();
  });

  test('should render existing additional emails with delete buttons and unverified pill', () => {
    renderTab([address('info@example.com', { label: 'Hostpoint shared' })]);
    expect(screen.getByTestId('additional-email-row-info@example.com')).toBeInTheDocument();
    expect(screen.getByTestId('additional-email-delete-info@example.com')).toBeInTheDocument();
    expect(screen.getByTestId('additional-email-unverified-info@example.com')).toBeInTheDocument();
  });

  test('should POST the new address on submit', async () => {
    renderTab([]);

    typeAddress('work@example.com');
    fireEvent.click(screen.getByTestId('additional-email-add-button'));

    await waitFor(() => expect(api.calls('addAdditionalEmail')).toHaveLength(1));
    expect(api.calls('addAdditionalEmail')[0].body).toEqual({ email: 'work@example.com' });
  });

  test('should short-circuit on empty input without calling the API', async () => {
    renderTab([]);

    typeAddress('   ');
    fireEvent.click(screen.getByTestId('additional-email-add-button'));

    // positive control: the form is there and the click went through
    expect(screen.getByTestId('additional-email-add-button')).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(api.calls('addAdditionalEmail')).toHaveLength(0);
  });

  test('should surface ADDITIONAL_EMAIL_DUPLICATE as inline error', async () => {
    api.on('addAdditionalEmail', () =>
      HttpResponse.json({ errorCode: 'ADDITIONAL_EMAIL_DUPLICATE' }, { status: 409 })
    );
    renderTab([]);

    typeAddress('work@example.com');
    fireEvent.click(screen.getByTestId('additional-email-add-button'));

    expect(
      await screen.findByText(en('settings.account.additionalEmailErrorDuplicate'))
    ).toBeInTheDocument();
  });

  test('should DELETE the address when Remove is confirmed in the dialog', async () => {
    renderTab([address('box@example.com')]);

    fireEvent.click(screen.getByTestId('additional-email-delete-box@example.com'));
    fireEvent.click(screen.getByTestId('additional-email-delete-confirm'));

    await waitFor(() => expect(api.calls('deleteAdditionalEmail')).toHaveLength(1));
    expect(api.calls('deleteAdditionalEmail')[0].params).toEqual({ email: 'box@example.com' });
  });

  test('should NOT delete when Cancel is clicked in the confirmation dialog', async () => {
    renderTab([address('box@example.com')]);

    fireEvent.click(screen.getByTestId('additional-email-delete-box@example.com'));
    expect(screen.getByTestId('additional-email-delete-cancel')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('additional-email-delete-cancel'));

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(api.calls('deleteAdditionalEmail')).toHaveLength(0);
  });

  test('should hide the add form and show limit alert when at cap', () => {
    renderTab(Array.from({ length: 5 }, (_, i) => address(`box${i}@example.com`)));

    expect(screen.getByTestId('additional-emails-at-limit')).toBeInTheDocument();
    expect(screen.queryByTestId('additional-email-add-button')).not.toBeInTheDocument();
  });

  test('should render verified pill (no resend) for a verified additional email', () => {
    renderTab([address('box@example.com', { verifiedAt: '2026-06-06T11:00:00Z' })]);
    expect(screen.getByTestId('additional-email-verified-box@example.com')).toBeInTheDocument();
    expect(
      screen.queryByTestId('additional-email-unverified-box@example.com')
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('additional-email-resend-box@example.com')).not.toBeInTheDocument();
  });

  test('should resend the verification when the Resend link is clicked', async () => {
    renderTab([address('box@example.com')]);

    fireEvent.click(screen.getByTestId('additional-email-resend-box@example.com'));

    await waitFor(() => expect(api.calls('resendAdditionalEmailVerification')).toHaveLength(1));
    expect(api.calls('resendAdditionalEmailVerification')[0].params).toEqual({
      email: 'box@example.com',
    });
    expect(
      await screen.findByText(en('settings.account.additionalEmailResendSuccess'))
    ).toBeInTheDocument();
  });

  // #1244: copy of event mails per confirmed address
  test('should switch the copy of event mails for a confirmed address and not offer it otherwise', async () => {
    renderTab([
      address('box@example.com', { verifiedAt: '2026-06-06T11:00:00Z', receiveEventMail: false }),
      address('pending@example.com'),
    ]);

    expect(screen.queryByTestId('additional-email-event-copy-pending@example.com')).toBeNull();
    fireEvent.click(screen.getByTestId('additional-email-event-copy-box@example.com'));

    await waitFor(() => expect(api.calls('updateAdditionalEmail')).toHaveLength(1));
    const [call] = api.calls('updateAdditionalEmail');
    expect(call.params).toEqual({ email: 'box@example.com' });
    expect(call.body).toEqual({ receiveEventMail: true });
  });
});

describe('UserSettingsTab — one settings resource (Story 4.9)', () => {
  test('should save theme and timezone together with PATCH /users/me/settings', async () => {
    renderTab([]);

    fireEvent.click(screen.getByTestId('theme-dark'));
    fireEvent.change(screen.getByTestId('timezone-autocomplete').querySelector('input')!, {
      target: { value: 'America/New_York' },
    });
    fireEvent.click(screen.getByTestId('save-account-settings-button'));

    await waitFor(() => expect(api.calls('updateUserSettings')).toHaveLength(1));
    expect(api.calls('updateUserSettings')[0].body).toMatchObject({
      theme: 'dark',
      timezone: 'America/New_York',
    });
    expect(screen.queryByTestId('save-account-settings-button-error')).toBeNull();
  });

  test('should show an error when CUMS rejects the value', async () => {
    api.on('updateUserSettings', () =>
      HttpResponse.json({ message: 'Not an IANA time zone id' }, { status: 400 })
    );
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    renderTab([]);

    fireEvent.change(screen.getByTestId('timezone-autocomplete').querySelector('input')!, {
      target: { value: 'Mars/Olympus' },
    });
    fireEvent.click(screen.getByTestId('save-account-settings-button'));

    expect(await screen.findByTestId('save-account-settings-button-error')).toHaveTextContent(
      en('settings.saveFailed')
    );
  });
});

describe('UserSettingsTab — Story 15.7 Q&A notification frequency', () => {
  const gotoNotifications = () =>
    fireEvent.click(screen.getByRole('tab', { name: en('settings.tabs.notifications') }));

  test('should render the three Q&A frequency options defaulting to live', () => {
    renderTab([]);
    gotoNotifications();

    expect(screen.getByText(en('settings.notifications.qnaFrequency'))).toBeInTheDocument();
    expect(screen.getByTestId('qna-frequency-live')).toBeInTheDocument();
    expect(screen.getByTestId('qna-frequency-daily')).toBeInTheDocument();
    expect(screen.getByTestId('qna-frequency-off')).toBeInTheDocument();
    expect(screen.getByTestId('qna-frequency-live').querySelector('input')).toBeChecked();
  });

  test('should save the chosen Q&A frequency with PATCH /users/me/settings', async () => {
    renderTab([]);
    gotoNotifications();

    fireEvent.click(screen.getByTestId('qna-frequency-off').querySelector('input')!);
    fireEvent.click(screen.getByTestId('save-notification-settings-button'));

    await waitFor(() => expect(api.calls('updateUserSettings')).toHaveLength(1));
    expect(api.calls('updateUserSettings')[0].body).toMatchObject({
      qnaNotificationFrequency: 'off',
    });
  });
});

/**
 * Deterministic matchMedia mock keyed on a simulated viewport width so
 * useBreakpoints().isMobile (down('md')) flips between icon-only and labelled
 * sub-tabs. Mirrors PartnerCreateEditModal.test.tsx.
 */
const installMatchMediaForWidth = (viewportWidth: number) => {
  window.matchMedia = vi.fn((query: string) => {
    const maxMatch = /max-width:\s*([\d.]+)px/.exec(query);
    const minMatch = /min-width:\s*([\d.]+)px/.exec(query);
    let matches = false;
    if (maxMatch) matches = viewportWidth <= parseFloat(maxMatch[1]);
    else if (minMatch) matches = viewportWidth >= parseFloat(minMatch[1]);
    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    };
  }) as unknown as typeof window.matchMedia;
};

describe('UserSettingsTab — responsive sub-tabs (organizer mobile)', () => {
  const originalMatchMedia = window.matchMedia;
  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    vi.restoreAllMocks();
  });

  test('should_renderIconOnlySubTabs_when_mobileViewport', () => {
    installMatchMediaForWidth(375);
    renderTab([]);

    const accountTab = screen.getByRole('tab', { name: en('settings.tabs.account') });
    expect(
      screen.getByRole('tab', { name: en('settings.tabs.notifications') })
    ).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: en('settings.tabs.privacy') })).toBeInTheDocument();
    // the visible textual label is suppressed (icon-only) on phones
    expect(accountTab).not.toHaveTextContent(en('settings.tabs.account'));
  });

  test('should_renderLabelledSubTabs_when_desktopViewport', () => {
    installMatchMediaForWidth(1280);
    renderTab([]);

    const accountTab = screen.getByRole('tab', { name: en('settings.tabs.account') });
    expect(accountTab).toHaveTextContent(en('settings.tabs.account'));
  });
});
