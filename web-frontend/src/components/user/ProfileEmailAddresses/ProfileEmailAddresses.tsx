/**
 * #1244 — the participant's e-mail addresses on /profile, in the public styling.
 *
 * Primary address (login with password, all event mails), then up to five additional
 * addresses: confirmed or waiting for confirmation, remove, resend the confirmation link, and
 * per confirmed address "copy of event mails" (owner decision 2026-11-07: event mails go to the
 * primary address, copies are opt-in). The newsletter per address lives in the consent tab
 * ({@link ProfileNewsletterAddresses}). Same API as the organizer-side UserSettingsTab.
 */

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { CheckCircle2, Clock, Mail, Trash2 } from 'lucide-react';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { Input } from '@/components/public/ui/input';
import {
  addAdditionalEmail,
  deleteAdditionalEmail,
  resendAdditionalEmailVerification,
  updateAdditionalEmail,
  type AdditionalEmail,
} from '@/services/api/userAccountApi';

const MAX_ADDITIONAL = 5;

interface ProfileEmailAddressesProps {
  primaryEmail: string;
  additionalEmails: AdditionalEmail[];
}

function errorCode(err: unknown): string | undefined {
  return isAxiosError(err) ? (err.response?.data?.errorCode as string | undefined) : undefined;
}

export function ProfileEmailAddresses({
  primaryEmail,
  additionalEmails,
}: ProfileEmailAddressesProps) {
  const { t } = useTranslation('common');
  const queryClient = useQueryClient();
  const [newEmail, setNewEmail] = useState('');
  const [addError, setAddError] = useState<string | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
  const [resent, setResent] = useState<Record<string, boolean>>({});

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['user-profile-me'] });
    void queryClient.invalidateQueries({ queryKey: ['user-profile'] });
    void queryClient.invalidateQueries({ queryKey: ['newsletter', 'my-subscriptions'] });
  };

  const add = useMutation({
    mutationFn: (email: string) => addAdditionalEmail({ email }),
    onSuccess: () => {
      setNewEmail('');
      setAddError(null);
      refresh();
    },
    onError: (err) => {
      const code = errorCode(err);
      setAddError(
        code === 'ADDITIONAL_EMAIL_DUPLICATE'
          ? t('profile.emails.errorDuplicate')
          : code === 'ADDITIONAL_EMAIL_LIMIT_REACHED'
            ? t('profile.emails.errorLimit')
            : t('profile.emails.errorInvalid')
      );
    },
  });

  const remove = useMutation({
    mutationFn: (email: string) => deleteAdditionalEmail(email),
    onSuccess: () => {
      setConfirmRemove(null);
      refresh();
    },
  });

  const copy = useMutation({
    mutationFn: ({ email, receive }: { email: string; receive: boolean }) =>
      updateAdditionalEmail(email, { receiveEventMail: receive }),
    onSuccess: refresh,
  });

  const resend = useMutation({
    mutationFn: (email: string) => resendAdditionalEmailVerification(email),
    onSuccess: (_data, email) => setResent((r) => ({ ...r, [email]: true })),
  });

  const atLimit = additionalEmails.length >= MAX_ADDITIONAL;

  return (
    <Card className="p-6 mb-6" data-testid="email-addresses-section">
      <div className="flex items-center gap-2 mb-2">
        <Mail className="h-5 w-5 text-zinc-400" />
        <h2 className="text-lg font-light text-zinc-100">{t('profile.emails.title')}</h2>
      </div>
      <p className="text-sm text-zinc-400 mb-6">{t('profile.emails.description')}</p>

      <ul className="space-y-4">
        <li
          className="flex flex-col gap-1 border-b border-zinc-800 pb-4"
          data-testid="email-row-primary"
        >
          <span className="text-zinc-100 break-all">{primaryEmail}</span>
          <span className="text-xs text-zinc-400">{t('profile.emails.primary')}</span>
        </li>

        {additionalEmails.map((a) => {
          const confirmed = !!a.verifiedAt;
          return (
            <li
              key={a.email}
              className="flex flex-col gap-2 border-b border-zinc-800 pb-4"
              data-testid={`email-row-${a.email}`}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-zinc-100 break-all">{a.email}</span>
                {confirmRemove === a.email ? (
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      className="text-red-400 border-red-800"
                      disabled={remove.isPending}
                      onClick={() => remove.mutate(a.email)}
                      data-testid={`email-remove-confirm-${a.email}`}
                    >
                      {t('profile.emails.removeConfirm')}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setConfirmRemove(null)}>
                      {t('profile.emails.cancel')}
                    </Button>
                  </div>
                ) : (
                  <Button
                    size="sm"
                    variant="ghost"
                    aria-label={t('profile.emails.remove')}
                    onClick={() => setConfirmRemove(a.email)}
                    data-testid={`email-remove-${a.email}`}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                )}
              </div>

              {confirmed ? (
                <span className="flex items-center gap-1 text-xs text-green-400">
                  <CheckCircle2 className="h-3 w-3" />
                  {t('profile.emails.confirmed')}
                </span>
              ) : (
                <span className="flex flex-wrap items-center gap-2 text-xs text-amber-400">
                  <Clock className="h-3 w-3" />
                  {t('profile.emails.pending')}
                  {resent[a.email] ? (
                    <span className="text-zinc-400">{t('profile.emails.resent')}</span>
                  ) : (
                    <button
                      type="button"
                      className="underline text-zinc-300"
                      disabled={resend.isPending}
                      onClick={() => resend.mutate(a.email)}
                      data-testid={`email-resend-${a.email}`}
                    >
                      {t('profile.emails.resend')}
                    </button>
                  )}
                </span>
              )}

              <label
                className={`flex items-center gap-3 text-sm ${confirmed ? 'cursor-pointer text-zinc-300' : 'text-zinc-500'}`}
              >
                <input
                  type="checkbox"
                  className="h-4 w-4"
                  checked={!!a.receiveEventMail && confirmed}
                  disabled={!confirmed || copy.isPending}
                  onChange={(e) => copy.mutate({ email: a.email, receive: e.target.checked })}
                  data-testid={`event-copy-${a.email}`}
                />
                {t('profile.emails.eventCopy')}
              </label>
            </li>
          );
        })}
      </ul>

      <div className="mt-6">
        {atLimit ? (
          <p className="text-sm text-zinc-400">{t('profile.emails.errorLimit')}</p>
        ) : (
          <form
            className="flex flex-col sm:flex-row gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              const email = newEmail.trim();
              if (email) {
                add.mutate(email);
              }
            }}
          >
            <Input
              type="email"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              placeholder={t('profile.emails.addPlaceholder')}
              aria-label={t('profile.emails.addPlaceholder')}
              data-testid="email-add-input"
            />
            <Button
              type="submit"
              disabled={add.isPending || !newEmail.trim()}
              data-testid="email-add-button"
            >
              {t('profile.emails.add')}
            </Button>
          </form>
        )}
        {addError && <p className="text-sm text-red-400 mt-2">{addError}</p>}
        {!atLimit && <p className="text-xs text-zinc-500 mt-2">{t('profile.emails.addHelp')}</p>}
      </div>

      <p className="text-xs text-zinc-400 mt-6">{t('profile.emails.loginHint')}</p>
    </Card>
  );
}

export default ProfileEmailAddresses;
