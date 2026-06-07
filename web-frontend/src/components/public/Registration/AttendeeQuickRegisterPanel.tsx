import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/public/ui/button';
import { useUserProfile } from '@/hooks/useUserProfile/useUserProfile';
import { createMyRegistration } from '@/services/registrationService';

interface AttendeeQuickRegisterPanelProps {
  eventCode: string;
  onCancel: () => void;
  inline?: boolean;
}

export const AttendeeQuickRegisterPanel = ({
  eventCode,
  onCancel,
  inline = false,
}: AttendeeQuickRegisterPanelProps) => {
  const { t } = useTranslation('registration');
  const queryClient = useQueryClient();
  const { userProfile } = useUserProfile({ enabled: true });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRegister = async () => {
    setIsSubmitting(true);
    setError(null);
    try {
      await createMyRegistration(eventCode);
      queryClient.invalidateQueries({ queryKey: ['my-registration', eventCode] });
      onCancel();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('attendeeQuickRegister.error'));
      setIsSubmitting(false);
    }
  };

  return (
    <div className={`w-full ${inline ? 'max-w-4xl mx-auto' : ''}`}>
      <div className="text-center mb-8">
        <h2 className="text-3xl font-light mb-2">{t('attendeeQuickRegister.title')}</h2>
      </div>

      {userProfile && (
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-6">
          <p className="text-sm text-zinc-400 mb-2">{t('attendeeQuickRegister.subtitle')}</p>
          <p className="text-lg font-medium">
            {userProfile.firstName} {userProfile.lastName}
          </p>
          <p className="text-zinc-400">{userProfile.email}</p>
          {userProfile.companyId && (
            <p className="text-sm text-zinc-500 mt-1">{userProfile.companyId}</p>
          )}
        </div>
      )}

      {error && (
        <div className="mb-4 p-4 bg-red-900/20 border border-red-800 rounded-lg">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      <div className="flex justify-between mt-8">
        <Button variant="outline" onClick={onCancel} disabled={isSubmitting}>
          {t('attendeeQuickRegister.cancelButton')}
        </Button>
        <Button
          onClick={handleRegister}
          disabled={isSubmitting}
          className="min-w-[200px]"
          data-testid="attendee-quick-register-btn"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {t('wizard.buttons.submitting')}
            </>
          ) : (
            t('attendeeQuickRegister.confirmButton')
          )}
        </Button>
      </div>
    </div>
  );
};
