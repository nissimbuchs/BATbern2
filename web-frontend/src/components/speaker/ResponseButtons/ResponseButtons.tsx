/**
 * ResponseButtons Component - Story 6.2
 *
 * Displays Accept, Decline, and Need More Info buttons for speaker response.
 * Shows decline reason textarea when Decline is selected.
 */

import { useState } from 'react';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { CheckCircle2, XCircle, HelpCircle, ChevronDown, ChevronUp } from 'lucide-react';
import type { ResponseType } from '@/types/speakerInvitation.types';
import { useTranslation } from 'react-i18next';

interface ResponseButtonsProps {
  onSelect: (responseType: ResponseType) => void;
  declineReason: string;
  onDeclineReasonChange: (reason: string) => void;
  notes: string;
  onNotesChange: (notes: string) => void;
  onSubmitDecline: () => void;
  onSubmitTentative: () => void;
}

export const ResponseButtons = ({
  onSelect,
  declineReason,
  onDeclineReasonChange,
  notes,
  onNotesChange,
  onSubmitDecline,
  onSubmitTentative,
}: ResponseButtonsProps) => {
  const { t } = useTranslation('speakerInvitation');
  const [expandedOption, setExpandedOption] = useState<'decline' | 'tentative' | null>(null);

  const handleAcceptClick = () => {
    onSelect('ACCEPTED');
  };

  const handleDeclineClick = () => {
    if (expandedOption === 'decline') {
      setExpandedOption(null);
    } else {
      setExpandedOption('decline');
      onSelect('DECLINED');
    }
  };

  const handleTentativeClick = () => {
    if (expandedOption === 'tentative') {
      setExpandedOption(null);
    } else {
      setExpandedOption('tentative');
      onSelect('TENTATIVE');
    }
  };

  return (
    <Card className="p-6">
      <h3 className="text-xl font-light mb-4 text-center">
        {t('responseButtons.title', 'How would you like to respond?')}
      </h3>

      <div className="space-y-4">
        {/* Accept Button */}
        <Button
          onClick={handleAcceptClick}
          className="w-full h-16 text-lg bg-green-700 hover:bg-green-600 border-green-600"
          size="lg"
        >
          <CheckCircle2 className="h-6 w-6 mr-3" />
          {t('responseButtons.accept', 'Accept Invitation')}
        </Button>

        {/* Decline Button with expandable reason */}
        <div>
          <Button
            onClick={handleDeclineClick}
            variant="outline"
            className="w-full h-16 text-lg border-red-700 text-red-400 hover:bg-red-900/20 hover:text-red-300"
            size="lg"
          >
            <XCircle className="h-6 w-6 mr-3" />
            {t('responseButtons.decline', 'Decline Invitation')}
            {expandedOption === 'decline' ? (
              <ChevronUp className="h-5 w-5 ml-auto" />
            ) : (
              <ChevronDown className="h-5 w-5 ml-auto" />
            )}
          </Button>

          {expandedOption === 'decline' && (
            <div className="mt-4 p-4 bg-zinc-800/50 rounded-lg space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2">
                  {t('responseButtons.declineReasonLabel', 'Reason for declining (optional)')}
                </label>
                <textarea
                  value={declineReason}
                  onChange={(e) => onDeclineReasonChange(e.target.value)}
                  placeholder={t(
                    'responseButtons.declineReasonPlaceholder',
                    'Let us know why you cannot participate...'
                  )}
                  className="w-full h-24 p-3 rounded-lg bg-zinc-900 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  maxLength={2000}
                />
              </div>
              <Button onClick={onSubmitDecline} className="w-full bg-red-700 hover:bg-red-600">
                {t('responseButtons.confirmDecline', 'Confirm Decline')}
              </Button>
            </div>
          )}
        </div>

        {/* Need More Info Button with expandable notes */}
        <div>
          <Button
            onClick={handleTentativeClick}
            variant="outline"
            className="w-full h-16 text-lg border-amber-700 text-amber-400 hover:bg-amber-900/20 hover:text-amber-300"
            size="lg"
          >
            <HelpCircle className="h-6 w-6 mr-3" />
            {t('responseButtons.needMoreInfo', 'Need More Info')}
            {expandedOption === 'tentative' ? (
              <ChevronUp className="h-5 w-5 ml-auto" />
            ) : (
              <ChevronDown className="h-5 w-5 ml-auto" />
            )}
          </Button>

          {expandedOption === 'tentative' && (
            <div className="mt-4 p-4 bg-zinc-800/50 rounded-lg space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2">
                  {t('responseButtons.questionsLabel', 'What questions do you have?')}
                </label>
                <textarea
                  value={notes}
                  onChange={(e) => onNotesChange(e.target.value)}
                  placeholder={t(
                    'responseButtons.questionsPlaceholder',
                    'Let us know what information you need...'
                  )}
                  className="w-full h-24 p-3 rounded-lg bg-zinc-900 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  maxLength={2000}
                />
              </div>
              <Button
                onClick={onSubmitTentative}
                className="w-full bg-amber-700 hover:bg-amber-600"
              >
                {t('responseButtons.submitQuestions', 'Send Questions')}
              </Button>
            </div>
          )}
        </div>
      </div>

      <p className="text-sm text-zinc-500 text-center mt-4">
        {t('responseButtons.hint', 'Accepting will show additional options for your preferences.')}
      </p>
    </Card>
  );
};

export default ResponseButtons;
