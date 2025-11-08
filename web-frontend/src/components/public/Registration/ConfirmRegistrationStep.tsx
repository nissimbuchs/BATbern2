/**
 * ConfirmRegistrationStep Component (Story 4.1.5 - Task 6)
 *
 * Step 2 of registration wizard - review and confirm registration.
 * Displays summary, communication preferences, special requests, and terms.
 */

import { Card } from '@/components/public/ui/card';
import { Checkbox } from '@/components/public/ui/ui/checkbox';
import { Textarea } from '@/components/public/ui/ui/textarea';
import type { CreateRegistrationRequest } from '@/types/event.types';

export interface ConfirmRegistrationStepProps {
  /** Current form data */
  formData: CreateRegistrationRequest;
  /** Form data update handler */
  setFormData: (data: CreateRegistrationRequest | ((prev: CreateRegistrationRequest) => CreateRegistrationRequest)) => void;
  /** Handler to edit personal details (go back to Step 1) */
  onEdit: () => void;
}

/**
 * Step 2: Confirm Registration
 *
 * Displays summary of personal info with edit option.
 * Collects communication preferences, special requests, and terms acceptance.
 */
export const ConfirmRegistrationStep = ({
  formData,
  setFormData,
  onEdit,
}: ConfirmRegistrationStepProps) => {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-light text-zinc-100 mb-2">Review & Confirm</h2>
        <p className="text-sm text-zinc-400">
          Please review your information and complete your registration.
        </p>
      </div>

      {/* Personal Information Summary */}
      <Card className="bg-zinc-900/50 border-zinc-800 p-4">
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-medium text-zinc-100">Personal Information</h3>
          <button
            type="button"
            onClick={onEdit}
            className="text-sm text-blue-400 hover:underline focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-2 focus:ring-offset-zinc-950 rounded"
          >
            Edit
          </button>
        </div>
        <div className="space-y-1 text-sm text-zinc-400">
          <p>
            <span className="font-medium text-zinc-300">
              {formData.firstName} {formData.lastName}
            </span>
          </p>
          <p>{formData.email}</p>
          <p>
            {formData.company} • {formData.role}
          </p>
        </div>
      </Card>

      {/* Communication Preferences */}
      <div className="space-y-4">
        <h3 className="font-medium text-zinc-100">Communication Preferences</h3>

        <div className="flex items-start gap-3">
          <Checkbox
            id="eventReminders"
            checked={formData.communicationPreferences?.eventReminders ?? true}
            onCheckedChange={(checked) =>
              setFormData((prev) => ({
                ...prev,
                communicationPreferences: {
                  ...prev.communicationPreferences,
                  eventReminders: !!checked,
                },
              }))
            }
            className="mt-0.5"
          />
          <label
            htmlFor="eventReminders"
            className="text-sm text-zinc-300 cursor-pointer"
          >
            Send me event reminders (1 week and 1 day before)
          </label>
        </div>

        <div className="flex items-start gap-3">
          <Checkbox
            id="newsletter"
            checked={formData.communicationPreferences?.newsletterSubscribed ?? false}
            onCheckedChange={(checked) =>
              setFormData((prev) => ({
                ...prev,
                communicationPreferences: {
                  ...prev.communicationPreferences,
                  newsletterSubscribed: !!checked,
                },
              }))
            }
            className="mt-0.5"
          />
          <label
            htmlFor="newsletter"
            className="text-sm text-zinc-300 cursor-pointer"
          >
            Subscribe to BATbern newsletter (monthly updates)
          </label>
        </div>
      </div>

      {/* Special Requests */}
      <div className="space-y-2">
        <label htmlFor="specialRequests" className="font-medium text-zinc-100 block">
          Special Requests <span className="text-zinc-500 font-normal">(Optional)</span>
        </label>
        <Textarea
          id="specialRequests"
          value={formData.specialRequests || ''}
          onChange={(e) =>
            setFormData((prev) => ({ ...prev, specialRequests: e.target.value }))
          }
          placeholder="Dietary requirements, accessibility needs, etc."
          className="bg-zinc-900 border-zinc-800 text-zinc-100 min-h-[100px]"
          rows={4}
        />
        <p className="text-xs text-zinc-500">
          Let us know if you have any dietary requirements or accessibility needs.
        </p>
      </div>

      {/* Terms & Conditions */}
      <div className="border-t border-zinc-800 pt-6">
        <div className="flex items-start gap-3">
          <Checkbox
            id="terms"
            checked={formData.termsAccepted || false}
            onCheckedChange={(checked) =>
              setFormData((prev) => ({ ...prev, termsAccepted: !!checked }))
            }
            className="mt-0.5"
          />
          <label
            htmlFor="terms"
            className="text-sm text-zinc-300 cursor-pointer"
          >
            I agree to the{' '}
            <a
              href="/terms"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-400 hover:underline"
            >
              terms and conditions
            </a>{' '}
            and{' '}
            <a
              href="/privacy"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-400 hover:underline"
            >
              privacy policy
            </a>{' '}
            *
          </label>
        </div>

        {!formData.termsAccepted && (
          <p className="text-xs text-zinc-500 mt-2 ml-7">
            You must accept the terms and conditions to complete registration.
          </p>
        )}
      </div>

      {/* Account Creation CTA */}
      <Card className="bg-zinc-800/30 border-zinc-700 p-4">
        <div className="flex items-start gap-3">
          <span className="text-2xl">💡</span>
          <div>
            <p className="text-sm text-zinc-300 mb-1">
              Want to manage all your registrations in one place?
            </p>
            <p className="text-xs text-zinc-400">
              After registering, you can create a free account to view your registration history,
              update preferences, and get personalized event recommendations.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
};
