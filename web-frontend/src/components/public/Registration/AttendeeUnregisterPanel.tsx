import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, ArrowLeft, Loader2 } from 'lucide-react';
import { Button } from '@/components/public/ui/button';
import { deleteMyRegistration } from '@/services/registrationService';
import type { MyRegistrationResponse } from '@/hooks/useMyRegistration';

interface AttendeeUnregisterPanelProps {
  eventCode: string;
  registration: MyRegistrationResponse;
  onCancel: () => void;
  inline?: boolean;
}

export const AttendeeUnregisterPanel = ({
  eventCode,
  registration,
  onCancel,
  inline = false,
}: AttendeeUnregisterPanelProps) => {
  const { t } = useTranslation('registration');
  const queryClient = useQueryClient();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleConfirm = async () => {
    setIsSubmitting(true);
    setError(null);
    try {
      await deleteMyRegistration(eventCode);
      queryClient.invalidateQueries({ queryKey: ['my-registration', eventCode] });
      onCancel();
    } catch {
      setError(t('unregister.error'));
      setIsSubmitting(false);
    }
  };

  const formattedDate = registration.registrationDate
    ? new Date(registration.registrationDate).toLocaleDateString()
    : null;

  return (
    <div className={`w-full ${inline ? 'max-w-4xl mx-auto' : ''}`}>
      <div className="text-center mb-8">
        <AlertTriangle className="h-12 w-12 text-amber-400 mx-auto mb-4" />
        <h2 className="text-3xl font-light mb-2">{t('unregister.title')}</h2>
        <p className="text-zinc-400">{t('unregister.subtitle')}</p>
      </div>

      <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-6">
        <p className="text-sm text-zinc-400 mb-1">{t('unregister.currentStatus')}</p>
        <p className="text-lg font-medium capitalize">
          {(registration.status ?? 'REGISTERED').toLowerCase()}
        </p>
        {formattedDate && (
          <p className="text-sm text-zinc-500 mt-1">
            {t('unregister.registeredOn')} {formattedDate}
          </p>
        )}
      </div>

      {error && (
        <div className="mb-4 p-4 bg-red-900/20 border border-red-800 rounded-lg">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      <div className="flex justify-between mt-8">
        <Button variant="outline" onClick={onCancel} disabled={isSubmitting}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          {t('unregister.cancelButton')}
        </Button>
        <Button
          variant="destructive"
          onClick={handleConfirm}
          disabled={isSubmitting}
          className="min-w-[200px]"
          data-testid="attendee-unregister-confirm-btn"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {t('wizard.buttons.submitting')}
            </>
          ) : (
            t('unregister.confirmButton')
          )}
        </Button>
      </div>
    </div>
  );
};
