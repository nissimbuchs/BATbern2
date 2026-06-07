/**
 * DeregistrationByEmailModal (Story 10.12 — AC9)
 *
 * Modal shown from HomePage and RegistrationWizard status guard.
 * User enters email → always shows "check your inbox" (anti-enumeration).
 *
 * Tailwind/shadcn + lucide (no MUI): this modal is imported eagerly by the public
 * HomePage, so it must stay off @mui/material to keep MUI out of the public bundle.
 */

import React, { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/public/ui/dialog';
import { Button } from '@/components/public/ui/button';
import { Input } from '@/components/public/ui/input';
import { Label } from '@/components/public/ui/label';
import { useDeregistrationByEmail } from '@/hooks/useDeregistration';

interface DeregistrationByEmailModalProps {
  open: boolean;
  onClose: () => void;
  eventCode: string;
}

export const DeregistrationByEmailModal: React.FC<DeregistrationByEmailModalProps> = ({
  open,
  onClose,
  eventCode,
}) => {
  const { t } = useTranslation(['registration', 'common']);
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const mutation = useDeregistrationByEmail();

  const validateEmail = (value: string): boolean => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  };

  const handleSubmit = () => {
    if (!validateEmail(email)) {
      setEmailError(t('deregistration.modal.emailError'));
      return;
    }
    setEmailError('');
    mutation.mutate(
      { email, eventCode },
      {
        onSettled: () => {
          // Always show success regardless of result (anti-enumeration)
          setSubmitted(true);
        },
      }
    );
  };

  const handleClose = () => {
    // Reset state on close
    setEmail('');
    setEmailError('');
    setSubmitted(false);
    onClose();
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        // shadcn fires onOpenChange(false) on ESC / overlay / close button
        if (!next) handleClose();
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('deregistration.modal.title')}</DialogTitle>
        </DialogHeader>

        {submitted ? (
          <p className="text-sm text-muted-foreground">
            {t('deregistration.modal.successMessage')}
          </p>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">{t('deregistration.modal.body')}</p>
            <div className="space-y-1.5">
              <Label htmlFor="deregistration-email">{t('deregistration.modal.emailLabel')}</Label>
              <Input
                id="deregistration-email"
                autoFocus
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                aria-invalid={Boolean(emailError)}
                disabled={mutation.isPending}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleSubmit();
                }}
              />
              {emailError && <p className="text-sm text-destructive">{emailError}</p>}
            </div>
          </div>
        )}

        <DialogFooter>
          {submitted ? (
            <Button variant="outline" onClick={handleClose}>
              {t('common:actions.close')}
            </Button>
          ) : (
            <>
              <Button variant="outline" onClick={handleClose} disabled={mutation.isPending}>
                {t('common:actions.close')}
              </Button>
              <Button
                variant="destructive"
                onClick={handleSubmit}
                disabled={mutation.isPending || !email}
              >
                {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                {t('deregistration.modal.submitButton')}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
