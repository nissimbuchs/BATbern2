/**
 * DeregistrationEntry (#1183, replaces the unconditional cancel link of Story 10.12 on the homepage)
 *
 * - Anonymous visitor: "Anmeldung stornieren" opens the by-e-mail modal, as before.
 * - Logged-in visitor without a registration: no cancel link. A notice says under which address
 *   they are not registered, and a second link opens the same modal for a registration made with
 *   another address (guest registration, second profile).
 * - Logged-in visitor with a registration: nothing here. The hero offers cancel directly.
 *
 * Tailwind only: rendered eagerly on the public HomePage, so no @mui/material.
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Info } from 'lucide-react';
import { DeregistrationByEmailModal } from './DeregistrationByEmailModal';
import type { RegistrationStatus } from './RegistrationStatusBanner';

interface DeregistrationEntryProps {
  eventCode: string;
  isAuthenticated: boolean;
  /** null = logged in and not registered; undefined = anonymous or not loaded yet */
  status: RegistrationStatus | null | undefined;
  isLoading: boolean;
  /** The logged-in user's address, shown in the "not registered" notice */
  userEmail?: string;
}

const linkClass = 'text-sm text-zinc-400 hover:text-zinc-200 underline transition-colors';

export function DeregistrationEntry({
  eventCode,
  isAuthenticated,
  status,
  isLoading,
  userEmail,
}: DeregistrationEntryProps) {
  const { t } = useTranslation('registration');
  const [modalOpen, setModalOpen] = useState(false);

  const modal = (
    <DeregistrationByEmailModal
      open={modalOpen}
      onClose={() => setModalOpen(false)}
      eventCode={eventCode}
    />
  );

  if (!isAuthenticated) {
    return (
      <>
        <div className="mt-2 text-center">
          <button type="button" onClick={() => setModalOpen(true)} className={linkClass}>
            {t('deregistration.homepage.cancelLink')}
          </button>
        </div>
        {modal}
      </>
    );
  }

  if (isLoading || status !== null) {
    return null;
  }

  return (
    <>
      <div
        data-testid="not-registered-notice"
        role="status"
        className="my-4 flex flex-col gap-1 rounded-md border border-zinc-600/30 bg-zinc-500/10 px-4 py-2.5 text-zinc-300 sm:flex-row sm:items-center sm:gap-2"
      >
        <Info className="hidden h-5 w-5 flex-shrink-0 text-zinc-400 sm:block" />
        <span className="flex-1">
          {t('deregistration.homepage.notRegistered', { email: userEmail ?? '' })}
        </span>
        <button
          type="button"
          onClick={() => setModalOpen(true)}
          className={`${linkClass} text-left`}
        >
          {t('deregistration.homepage.otherAddressLink')}
        </button>
      </div>
      {modal}
    </>
  );
}
