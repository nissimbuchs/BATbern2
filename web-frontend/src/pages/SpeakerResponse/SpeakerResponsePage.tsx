/**
 * SpeakerResponsePage Component - Story 6.2
 *
 * Public page for speakers to respond to event invitations.
 * Accessible via /respond/:token URL from email link.
 * No authentication required - uses cryptographic token for access.
 */

import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { Loader2, XCircle, ArrowLeft, CheckCircle2, UserPlus } from 'lucide-react';
import { speakerInvitationService } from '@/services/speakerInvitationService';
import { InvitationDetails } from '@/components/speaker/InvitationDetails';
import { WhyWeChoseYou } from '@/components/speaker/WhyWeChoseYou';
import { ResponseButtons } from '@/components/speaker/ResponseButtons';
import { PreferencesForm } from '@/components/speaker/PreferencesForm';
import type {
  InvitationResponse,
  ResponseType,
  SpeakerResponsePreferences,
} from '@/types/speakerInvitation.types';
import { useTranslation } from 'react-i18next';

type PageState =
  | 'loading'
  | 'ready' // Invitation loaded, awaiting response selection
  | 'preferences' // User selected Accept, showing preferences form
  | 'submitting'
  | 'success'
  | 'error'
  | 'expired'
  | 'already_responded';

const SpeakerResponsePage = () => {
  const { token } = useParams<{ token: string }>();
  const { t } = useTranslation('speakerInvitation');

  const [pageState, setPageState] = useState<PageState>('loading');
  const [invitation, setInvitation] = useState<InvitationResponse | null>(null);
  const [selectedResponse, setSelectedResponse] = useState<ResponseType | null>(null);
  const [declineReason, setDeclineReason] = useState('');
  const [notes, setNotes] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Load invitation on mount
  useEffect(() => {
    if (!token) {
      setPageState('error');
      setErrorMessage(t('errors.noToken', 'No invitation token provided'));
      return;
    }

    const loadInvitation = async () => {
      try {
        const data = await speakerInvitationService.getInvitationByToken(token);

        // Check if already responded
        if (data.invitationStatus === 'RESPONDED') {
          setInvitation(data);
          setPageState('already_responded');
          return;
        }

        // Check if expired
        if (data.invitationStatus === 'EXPIRED' || new Date(data.expiresAt) < new Date()) {
          setInvitation(data);
          setPageState('expired');
          return;
        }

        setInvitation(data);
        setPageState('ready');
      } catch (error: unknown) {
        setPageState('error');
        const errorMsg = error instanceof Error ? error.message : '';
        if (errorMsg.includes('expired')) {
          setErrorMessage(t('errors.expired', 'This invitation has expired.'));
        } else if (errorMsg.includes('not found') || errorMsg.includes('Invalid')) {
          setErrorMessage(t('errors.invalid', 'Invalid or expired invitation link.'));
        } else {
          setErrorMessage(
            t('errors.loadFailed', 'Failed to load invitation. Please try again later.')
          );
        }
      }
    };

    loadInvitation();
  }, [token, t]);

  // Handle response selection
  const handleResponseSelect = (responseType: ResponseType) => {
    setSelectedResponse(responseType);
    if (responseType === 'ACCEPTED') {
      setPageState('preferences');
    }
  };

  // Handle back from preferences form
  const handleBackFromPreferences = () => {
    setSelectedResponse(null);
    setPageState('ready');
  };

  // Submit decline or need more info
  const handleSubmitDeclineOrTentative = async () => {
    if (!token || !selectedResponse) return;

    setPageState('submitting');
    try {
      const result = await speakerInvitationService.respondToInvitation(token, {
        responseType: selectedResponse,
        declineReason: selectedResponse === 'DECLINED' ? declineReason : undefined,
        notes: notes || undefined,
      });
      setInvitation(result);
      setPageState('success');
    } catch (error: unknown) {
      setPageState('error');
      const errorMsg = error instanceof Error ? error.message : '';
      if (errorMsg.includes('already responded')) {
        setErrorMessage(
          t('errors.alreadyResponded', 'You have already responded to this invitation.')
        );
      } else {
        setErrorMessage(
          t('errors.submitFailed', 'Failed to submit your response. Please try again.')
        );
      }
    }
  };

  // Submit acceptance with preferences
  const handleSubmitAcceptance = async (preferences: SpeakerResponsePreferences) => {
    if (!token) return;

    setPageState('submitting');
    try {
      const result = await speakerInvitationService.respondToInvitation(token, {
        responseType: 'ACCEPTED',
        preferences,
        notes: notes || undefined,
      });
      setInvitation(result);
      setPageState('success');
    } catch (error: unknown) {
      setPageState('error');
      const errorMsg = error instanceof Error ? error.message : '';
      if (errorMsg.includes('already responded')) {
        setErrorMessage(
          t('errors.alreadyResponded', 'You have already responded to this invitation.')
        );
      } else {
        setErrorMessage(
          t('errors.submitFailed', 'Failed to submit your response. Please try again.')
        );
      }
    }
  };

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl">
        {/* Loading State */}
        {pageState === 'loading' && (
          <div className="text-center py-24">
            <Loader2 className="h-16 w-16 text-blue-400 mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-light mb-2">
              {t('loading.title', 'Loading Invitation...')}
            </h2>
            <p className="text-zinc-400">
              {t('loading.description', 'Please wait while we retrieve your invitation details.')}
            </p>
          </div>
        )}

        {/* Submitting State */}
        {pageState === 'submitting' && (
          <div className="text-center py-24">
            <Loader2 className="h-16 w-16 text-blue-400 mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-light mb-2">
              {t('submitting.title', 'Submitting Your Response...')}
            </h2>
            <p className="text-zinc-400">
              {t('submitting.description', 'Please wait while we process your response.')}
            </p>
          </div>
        )}

        {/* Ready State - Show invitation and response buttons */}
        {pageState === 'ready' && invitation && (
          <>
            <InvitationDetails invitation={invitation} />

            {invitation.personalMessage?.trim() && (
              <WhyWeChoseYou message={invitation.personalMessage} />
            )}

            <ResponseButtons
              onSelect={handleResponseSelect}
              declineReason={declineReason}
              onDeclineReasonChange={setDeclineReason}
              notes={notes}
              onNotesChange={setNotes}
              onSubmitDecline={handleSubmitDeclineOrTentative}
              onSubmitTentative={handleSubmitDeclineOrTentative}
            />
          </>
        )}

        {/* Preferences State - Show preferences form for acceptance */}
        {pageState === 'preferences' && invitation && (
          <>
            <InvitationDetails invitation={invitation} />

            <PreferencesForm onSubmit={handleSubmitAcceptance} onBack={handleBackFromPreferences} />
          </>
        )}

        {/* Success State */}
        {pageState === 'success' && invitation && (
          <>
            <div className="text-center mb-8">
              <CheckCircle2 className="h-16 w-16 text-green-400 mx-auto mb-4" />
              <h1 className="text-4xl font-light mb-2">
                {invitation.responseType === 'ACCEPTED'
                  ? t('success.accepted.title', 'Thank You for Accepting!')
                  : invitation.responseType === 'DECLINED'
                    ? t('success.declined.title', 'Response Received')
                    : t('success.tentative.title', 'Thank You for Your Response')}
              </h1>
              <p className="text-xl text-zinc-400">
                {invitation.responseType === 'ACCEPTED'
                  ? t(
                      'success.accepted.description',
                      'We look forward to having you speak at the event.'
                    )
                  : invitation.responseType === 'DECLINED'
                    ? t('success.declined.description', 'We appreciate you letting us know.')
                    : t(
                        'success.tentative.description',
                        'The organizer will reach out with more information.'
                      )}
              </p>
            </div>

            <Card className="p-8 mb-8">
              <h2 className="text-xl font-light mb-4">
                {t('success.nextSteps.title', "What's Next?")}
              </h2>
              {invitation.responseType === 'ACCEPTED' ? (
                <ul className="space-y-3 text-zinc-300">
                  <li>
                    {t(
                      'success.nextSteps.accepted1',
                      'The organizer will contact you with scheduling details.'
                    )}
                  </li>
                  <li>
                    {t(
                      'success.nextSteps.accepted2',
                      "You'll receive confirmation of your time slot."
                    )}
                  </li>
                  <li>
                    {t(
                      'success.nextSteps.accepted3',
                      "Closer to the event, we'll send presentation guidelines."
                    )}
                  </li>
                </ul>
              ) : invitation.responseType === 'TENTATIVE' ? (
                <ul className="space-y-3 text-zinc-300">
                  <li>
                    {t(
                      'success.nextSteps.tentative1',
                      'The organizer will reach out with additional information.'
                    )}
                  </li>
                  <li>
                    {t(
                      'success.nextSteps.tentative2',
                      'Feel free to contact us if you have questions.'
                    )}
                  </li>
                </ul>
              ) : (
                <p className="text-zinc-300">
                  {t(
                    'success.nextSteps.declined',
                    'We hope to invite you to future events. Thank you for your time.'
                  )}
                </p>
              )}
            </Card>

            {/* Account Creation CTA - Story 6.3 */}
            {invitation.responseType === 'ACCEPTED' && invitation.speakerEmail && (
              <Card className="p-6 mb-8 bg-blue-950/30 border-blue-500/30">
                <div className="flex items-start gap-4">
                  <UserPlus className="h-6 w-6 text-blue-400 flex-shrink-0 mt-1" />
                  <div className="flex-1">
                    <h3 className="text-lg font-medium mb-2">
                      {t(
                        'success.createAccount.title',
                        'Create an account to manage your speaker participation'
                      )}
                    </h3>
                    <p className="text-sm text-zinc-400 mb-4">
                      {t(
                        'success.createAccount.description',
                        'With an account, you can access the speaker dashboard to:'
                      )}
                    </p>
                    <ul className="text-sm text-zinc-400 mb-4 space-y-1">
                      <li>
                        {t(
                          'success.createAccount.benefit1',
                          '• View your session details and schedule'
                        )}
                      </li>
                      <li>
                        {t('success.createAccount.benefit2', '• Upload presentation materials')}
                      </li>
                      <li>
                        {t('success.createAccount.benefit3', '• Communicate with organizers')}
                      </li>
                    </ul>
                    <Button asChild>
                      <Link
                        to={`/auth/register?email=${encodeURIComponent(invitation.speakerEmail)}&flow=speaker`}
                      >
                        <UserPlus className="h-4 w-4 mr-2" />
                        {t('success.createAccount.button', 'Create Account')}
                      </Link>
                    </Button>
                  </div>
                </div>
              </Card>
            )}

            <div className="flex justify-center">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {t('actions.backToHome', 'Back to Home')}
                </Link>
              </Button>
            </div>
          </>
        )}

        {/* Already Responded State */}
        {pageState === 'already_responded' && invitation && (
          <>
            <div className="text-center mb-8">
              <CheckCircle2 className="h-16 w-16 text-blue-400 mx-auto mb-4" />
              <h1 className="text-3xl font-light mb-2">
                {t('alreadyResponded.title', 'Already Responded')}
              </h1>
              <p className="text-xl text-zinc-400">
                {t(
                  'alreadyResponded.description',
                  'You have already responded to this invitation.'
                )}
              </p>
            </div>

            <Card className="p-6 mb-8 text-center">
              <p className="text-zinc-300">
                {t('alreadyResponded.status', 'Your response: ')}
                <strong>
                  {invitation.responseType === 'ACCEPTED'
                    ? t('responseTypes.accepted', 'Accepted')
                    : invitation.responseType === 'DECLINED'
                      ? t('responseTypes.declined', 'Declined')
                      : t('responseTypes.tentative', 'Need More Info')}
                </strong>
              </p>
              {invitation.respondedAt && (
                <p className="text-zinc-400 text-sm mt-2">
                  {t('alreadyResponded.respondedAt', 'Responded on: ')}
                  {new Date(invitation.respondedAt).toLocaleDateString()}
                </p>
              )}
            </Card>

            <div className="flex justify-center">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {t('actions.backToHome', 'Back to Home')}
                </Link>
              </Button>
            </div>
          </>
        )}

        {/* Expired State */}
        {pageState === 'expired' && (
          <>
            <div className="text-center mb-8">
              <XCircle className="h-16 w-16 text-amber-400 mx-auto mb-4" />
              <h1 className="text-3xl font-light mb-2">
                {t('expired.title', 'Invitation Expired')}
              </h1>
              <p className="text-xl text-zinc-400">
                {t('expired.description', 'This invitation is no longer available.')}
              </p>
            </div>

            <Card className="p-6 mb-8">
              <p className="text-zinc-300 text-center">
                {t(
                  'expired.message',
                  'The deadline for responding to this invitation has passed. Please contact the organizer if you would still like to participate.'
                )}
              </p>
            </Card>

            <div className="flex justify-center">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {t('actions.backToHome', 'Back to Home')}
                </Link>
              </Button>
            </div>
          </>
        )}

        {/* Error State */}
        {pageState === 'error' && (
          <>
            <div className="text-center mb-8">
              <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
              <h1 className="text-3xl font-light mb-2">
                {t('error.title', 'Something Went Wrong')}
              </h1>
              <p className="text-xl text-zinc-400">
                {errorMessage || t('error.description', 'Unable to process your request.')}
              </p>
            </div>

            <Card className="p-6 mb-8">
              <h3 className="text-lg font-light mb-3">{t('error.whatToDo', 'What can you do?')}</h3>
              <ul className="space-y-2 text-sm text-zinc-400">
                <li>{t('error.tip1', 'Check if you clicked the correct link from your email')}</li>
                <li>
                  {t(
                    'error.tip2',
                    'Invitation links expire - you may need to contact the organizer'
                  )}
                </li>
                <li>{t('error.tip3', 'Try refreshing the page')}</li>
                <li>{t('error.tip4', 'Contact support if the problem persists')}</li>
              </ul>
            </Card>

            <div className="flex justify-center gap-4">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {t('actions.backToHome', 'Back to Home')}
                </Link>
              </Button>
              <Button onClick={() => window.location.reload()}>
                {t('actions.tryAgain', 'Try Again')}
              </Button>
            </div>
          </>
        )}
      </div>
    </PublicLayout>
  );
};

export default SpeakerResponsePage;
