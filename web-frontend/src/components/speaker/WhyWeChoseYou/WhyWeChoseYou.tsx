/**
 * WhyWeChoseYou Component - Story 6.2
 *
 * Displays the personalized message from the organizer explaining
 * why they chose this speaker for the event.
 */

import { Card } from '@/components/public/ui/card';
import { Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface WhyWeChoseYouProps {
  message: string;
}

export const WhyWeChoseYou = ({ message }: WhyWeChoseYouProps) => {
  const { t } = useTranslation('speakerInvitation');

  return (
    <Card className="p-6 mb-6 bg-gradient-to-br from-blue-900/20 to-purple-900/20 border-blue-800/50">
      <div className="flex items-start gap-3">
        <Sparkles className="h-6 w-6 text-yellow-400 flex-shrink-0 mt-0.5" />
        <div>
          <h3 className="text-lg font-medium mb-2 text-blue-200">
            {t('whyWeChoseYou.title', 'Why We Chose You')}
          </h3>
          <p className="text-zinc-300 whitespace-pre-wrap">{message}</p>
        </div>
      </div>
    </Card>
  );
};

export default WhyWeChoseYou;
