/**
 * PreferencesForm Component - Story 6.2
 *
 * Form for speakers to provide their preferences when accepting an invitation.
 * Includes time slot preference, travel requirements, technical requirements,
 * initial presentation title, and comments for organizer.
 */

import { useState } from 'react';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { ArrowLeft, Send, Clock, Plane, Monitor, MessageSquare, FileText } from 'lucide-react';
import type {
  SpeakerResponsePreferences,
  PreferredTimeSlot,
  TravelRequirement,
  TechnicalRequirement,
} from '@/types/speakerInvitation.types';
import { useTranslation } from 'react-i18next';

interface PreferencesFormProps {
  onSubmit: (preferences: SpeakerResponsePreferences) => void;
  onBack: () => void;
}

export const PreferencesForm = ({ onSubmit, onBack }: PreferencesFormProps) => {
  const { t } = useTranslation('speakerInvitation');

  const [preferredTimeSlot, setPreferredTimeSlot] = useState<PreferredTimeSlot | undefined>();
  const [travelRequirements, setTravelRequirements] = useState<TravelRequirement | undefined>();
  const [technicalRequirements, setTechnicalRequirements] = useState<TechnicalRequirement[]>([]);
  const [initialPresentationTitle, setInitialPresentationTitle] = useState('');
  const [commentsForOrganizer, setCommentsForOrganizer] = useState('');

  const handleTechnicalRequirementToggle = (req: TechnicalRequirement) => {
    setTechnicalRequirements((prev) =>
      prev.includes(req) ? prev.filter((r) => r !== req) : [...prev, req]
    );
  };

  const handleSubmit = () => {
    const preferences: SpeakerResponsePreferences = {
      preferredTimeSlot,
      travelRequirements,
      technicalRequirements: technicalRequirements.length > 0 ? technicalRequirements : undefined,
      initialPresentationTitle: initialPresentationTitle || undefined,
      commentsForOrganizer: commentsForOrganizer || undefined,
    };
    onSubmit(preferences);
  };

  return (
    <Card className="p-6">
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" size="sm" onClick={onBack} className="p-2">
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <h3 className="text-xl font-light">{t('preferencesForm.title', 'Your Preferences')}</h3>
      </div>

      <p className="text-zinc-400 mb-6">
        {t(
          'preferencesForm.description',
          'Help us plan the perfect event by sharing your preferences. All fields are optional.'
        )}
      </p>

      <div className="space-y-6">
        {/* Preferred Time Slot */}
        <div>
          <label className="flex items-center gap-2 text-sm font-medium mb-3">
            <Clock className="h-4 w-4 text-blue-400" />
            {t('preferencesForm.timeSlot.label', 'Preferred Time Slot')}
          </label>
          <div className="grid grid-cols-3 gap-2">
            {[
              {
                value: 'MORNING' as const,
                label: t('preferencesForm.timeSlot.morning', 'Morning'),
              },
              {
                value: 'AFTERNOON' as const,
                label: t('preferencesForm.timeSlot.afternoon', 'Afternoon'),
              },
              {
                value: 'NO_PREFERENCE' as const,
                label: t('preferencesForm.timeSlot.noPreference', 'No Preference'),
              },
            ].map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setPreferredTimeSlot(option.value)}
                className={`p-3 rounded-lg border text-sm transition-colors ${
                  preferredTimeSlot === option.value
                    ? 'border-blue-500 bg-blue-500/20 text-blue-300'
                    : 'border-zinc-700 hover:border-zinc-600 text-zinc-300'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {/* Travel Requirements */}
        <div>
          <label className="flex items-center gap-2 text-sm font-medium mb-3">
            <Plane className="h-4 w-4 text-blue-400" />
            {t('preferencesForm.travel.label', 'Travel & Accommodation')}
          </label>
          <div className="grid grid-cols-3 gap-2">
            {[
              {
                value: 'LOCAL' as const,
                label: t('preferencesForm.travel.local', 'Local'),
                description: t('preferencesForm.travel.localDesc', 'No accommodation needed'),
              },
              {
                value: 'ACCOMMODATION' as const,
                label: t('preferencesForm.travel.accommodation', 'Need Hotel'),
                description: t(
                  'preferencesForm.travel.accommodationDesc',
                  'Overnight stay required'
                ),
              },
              {
                value: 'VIRTUAL' as const,
                label: t('preferencesForm.travel.virtual', 'Virtual'),
                description: t('preferencesForm.travel.virtualDesc', 'Remote participation'),
              },
            ].map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setTravelRequirements(option.value)}
                className={`p-3 rounded-lg border text-sm transition-colors text-left ${
                  travelRequirements === option.value
                    ? 'border-blue-500 bg-blue-500/20 text-blue-300'
                    : 'border-zinc-700 hover:border-zinc-600 text-zinc-300'
                }`}
              >
                <div className="font-medium">{option.label}</div>
                <div className="text-xs text-zinc-500 mt-1">{option.description}</div>
              </button>
            ))}
          </div>
        </div>

        {/* Technical Requirements */}
        <div>
          <label className="flex items-center gap-2 text-sm font-medium mb-3">
            <Monitor className="h-4 w-4 text-blue-400" />
            {t('preferencesForm.technical.label', 'Technical Requirements')}
          </label>
          <div className="space-y-2">
            {[
              {
                value: 'MAC_ADAPTER' as const,
                label: t('preferencesForm.technical.macAdapter', 'Mac Display Adapter'),
                description: t(
                  'preferencesForm.technical.macAdapterDesc',
                  'USB-C/HDMI adapter for Mac laptop'
                ),
              },
              {
                value: 'REMOTE_OPTION' as const,
                label: t('preferencesForm.technical.remoteOption', 'Remote Presentation Option'),
                description: t(
                  'preferencesForm.technical.remoteOptionDesc',
                  'Ability to present remotely if needed'
                ),
              },
              {
                value: 'SPECIAL_AV' as const,
                label: t('preferencesForm.technical.specialAv', 'Special A/V Equipment'),
                description: t(
                  'preferencesForm.technical.specialAvDesc',
                  'Additional audio/visual requirements'
                ),
              },
            ].map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => handleTechnicalRequirementToggle(option.value)}
                className={`w-full p-3 rounded-lg border text-sm transition-colors text-left flex items-start gap-3 ${
                  technicalRequirements.includes(option.value)
                    ? 'border-blue-500 bg-blue-500/20'
                    : 'border-zinc-700 hover:border-zinc-600'
                }`}
              >
                <div
                  className={`w-5 h-5 rounded border-2 flex items-center justify-center flex-shrink-0 mt-0.5 ${
                    technicalRequirements.includes(option.value)
                      ? 'border-blue-500 bg-blue-500'
                      : 'border-zinc-600'
                  }`}
                >
                  {technicalRequirements.includes(option.value) && (
                    <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path
                        fillRule="evenodd"
                        d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                        clipRule="evenodd"
                      />
                    </svg>
                  )}
                </div>
                <div>
                  <div className="font-medium text-zinc-300">{option.label}</div>
                  <div className="text-xs text-zinc-500">{option.description}</div>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Initial Presentation Title */}
        <div>
          <label className="flex items-center gap-2 text-sm font-medium mb-2">
            <FileText className="h-4 w-4 text-blue-400" />
            {t('preferencesForm.presentationTitle.label', 'Proposed Presentation Title')}
          </label>
          <input
            type="text"
            value={initialPresentationTitle}
            onChange={(e) => setInitialPresentationTitle(e.target.value)}
            placeholder={t(
              'preferencesForm.presentationTitle.placeholder',
              'Enter a working title for your presentation...'
            )}
            className="w-full p-3 rounded-lg bg-zinc-900 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
            maxLength={200}
          />
          <p className="text-xs text-zinc-500 mt-1">
            {t('preferencesForm.presentationTitle.hint', 'This can be changed later')}
          </p>
        </div>

        {/* Comments for Organizer */}
        <div>
          <label className="flex items-center gap-2 text-sm font-medium mb-2">
            <MessageSquare className="h-4 w-4 text-blue-400" />
            {t('preferencesForm.comments.label', 'Additional Comments')}
          </label>
          <textarea
            value={commentsForOrganizer}
            onChange={(e) => setCommentsForOrganizer(e.target.value)}
            placeholder={t(
              'preferencesForm.comments.placeholder',
              'Any other information or requests for the organizer...'
            )}
            className="w-full h-24 p-3 rounded-lg bg-zinc-900 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
            maxLength={2000}
          />
        </div>

        {/* Submit Button */}
        <Button
          onClick={handleSubmit}
          className="w-full h-14 text-lg bg-green-700 hover:bg-green-600"
          size="lg"
        >
          <Send className="h-5 w-5 mr-2" />
          {t('preferencesForm.submit', 'Confirm Acceptance')}
        </Button>
      </div>
    </Card>
  );
};

export default PreferencesForm;
