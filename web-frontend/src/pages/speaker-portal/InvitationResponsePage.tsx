/**
 * InvitationResponsePage Component (Story 6.2a - Task 6)
 *
 * Main page for speaker invitation responses.
 * Accessed via magic link: /speaker-portal/respond?token={token}
 *
 * Flow:
 * 1. Extract token from URL
 * 2. Validate token and display invitation details
 * 3. Show response form (Accept/Decline)
 * 4. Show confirmation after response
 */

import { useState, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import {
  CheckCircle2,
  XCircle,
  Loader2,
  Calendar,
  MapPin,
  Clock,
  AlertCircle,
  ArrowLeft,
  ThumbsUp,
  ThumbsDown,
} from 'lucide-react';
import {
  speakerPortalService,
  SpeakerResponseType,
  SpeakerResponsePreferences,
  SpeakerResponseResult,
} from '@/services/speakerPortalService';

type PageState = 'loading' | 'form' | 'success' | 'error' | 'already_responded';

interface ResponseFormData {
  response: SpeakerResponseType;
  reason?: string;
  preferences?: SpeakerResponsePreferences;
}

const InvitationResponsePage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const actionParam = searchParams.get('action'); // 'accept' or 'decline' from email link

  const [pageState, setPageState] = useState<PageState>('loading');
  const [selectedResponse, setSelectedResponse] = useState<SpeakerResponseType | null>(null);
  const [reason, setReason] = useState('');
  const [preferences, setPreferences] = useState<SpeakerResponsePreferences>({});
  const [responseResult, setResponseResult] = useState<SpeakerResponseResult | null>(null);
  const [hasAutoSelected, setHasAutoSelected] = useState(false);

  // Validate token on load
  const {
    data: invitation,
    error: validationError,
    isLoading,
  } = useQuery({
    queryKey: ['speaker-invitation', token],
    queryFn: () => speakerPortalService.validateToken(token!),
    enabled: !!token,
    retry: false,
  });

  // Submit response mutation
  const respondMutation = useMutation({
    mutationFn: (data: ResponseFormData) =>
      speakerPortalService.respond({
        token: token!,
        response: data.response,
        reason: data.reason,
        preferences: data.preferences,
      }),
    onSuccess: (result) => {
      setResponseResult(result);
      setPageState('success');
      // Clear token from URL for security
      window.history.replaceState({}, '', '/speaker-portal/respond');
    },
  });

  // Update page state based on validation result
  useEffect(() => {
    if (isLoading) {
      setPageState('loading');
    } else if (validationError) {
      setPageState('error');
    } else if (invitation?.alreadyResponded) {
      setPageState('already_responded');
    } else if (invitation?.valid) {
      setPageState('form');
    } else if (invitation && !invitation.valid) {
      setPageState('error');
    }
  }, [isLoading, validationError, invitation]);

  // Auto-select response based on action parameter from email link
  useEffect(() => {
    if (pageState === 'form' && actionParam && !hasAutoSelected) {
      setHasAutoSelected(true);
      if (actionParam === 'accept') {
        setSelectedResponse('ACCEPT');
      } else if (actionParam === 'decline') {
        setSelectedResponse('DECLINE');
      }
    }
  }, [pageState, actionParam, hasAutoSelected]);

  // No token in URL
  if (!token) {
    return (
      <PublicLayout>
        <div className="container mx-auto px-4 py-12 max-w-3xl">
          <div className="text-center">
            <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
            <h1 className="text-3xl font-light mb-2 text-zinc-100">Invalid Link</h1>
            <p className="text-zinc-400 mb-8">
              This page requires a valid invitation link from your email.
            </p>
            <Button asChild variant="outline">
              <Link to="/">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Home
              </Link>
            </Button>
          </div>
        </div>
      </PublicLayout>
    );
  }

  // Handle response button clicks
  const handleResponseSelect = (response: SpeakerResponseType) => {
    setSelectedResponse(response);
    setReason('');
    setPreferences({});
  };

  // Handle form submission
  const handleSubmit = () => {
    if (!selectedResponse) return;

    const data: ResponseFormData = {
      response: selectedResponse,
    };

    if (selectedResponse === 'DECLINE') {
      data.reason = reason;
    }

    if (selectedResponse === 'ACCEPT' && preferences.comments) {
      data.preferences = { comments: preferences.comments };
    }

    respondMutation.mutate(data);
  };

  // Check if submit is valid
  const isSubmitValid = () => {
    if (!selectedResponse) return false;
    if (selectedResponse === 'DECLINE' && !reason.trim()) return false;
    return true;
  };

  // Extract error details
  const getErrorDetails = () => {
    if (validationError) {
      const err = validationError as Error & { errorCode?: string };
      if (err.errorCode === 'EXPIRED') {
        return {
          title: 'Link Expired',
          message: 'This invitation link has expired. Please contact the event organizers.',
        };
      }
      if (err.errorCode === 'ALREADY_USED') {
        return {
          title: 'Link Already Used',
          message: 'This invitation link has already been used.',
        };
      }
      if (err.errorCode === 'NOT_FOUND') {
        return {
          title: 'Invalid Link',
          message: 'This invitation link is not valid.',
        };
      }
      return {
        title: 'Error',
        message: err.message || 'An error occurred while validating your invitation.',
      };
    }
    if (invitation && !invitation.valid) {
      return {
        title: 'Invalid Link',
        message: invitation.error || 'This invitation link is not valid.',
      };
    }
    return {
      title: 'Error',
      message: 'An unexpected error occurred.',
    };
  };

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl min-h-screen">
        {/* Loading State */}
        {pageState === 'loading' && (
          <div className="text-center py-24" role="status" aria-label="Loading invitation">
            <Loader2 className="h-16 w-16 text-blue-400 mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-light text-zinc-100 mb-2">Loading Invitation...</h2>
            <p className="text-zinc-400">Please wait while we verify your link.</p>
          </div>
        )}

        {/* Error State */}
        {pageState === 'error' && (
          <div className="text-center">
            <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
            <h1 className="text-3xl font-light text-zinc-100 mb-2">{getErrorDetails().title}</h1>
            <p className="text-zinc-400 mb-8">{getErrorDetails().message}</p>
            <Card className="p-6 mb-8 text-left">
              <h3 className="text-lg font-light text-zinc-100 mb-3">Need Help?</h3>
              <ul className="space-y-2 text-sm text-zinc-400">
                <li>
                  Contact the event organizers at{' '}
                  <a href="mailto:info@batbern.ch" className="text-blue-400 hover:underline">
                    info@batbern.ch
                  </a>
                </li>
                <li>Check your email for a more recent invitation link</li>
                <li>Request a new invitation from the organizers</li>
              </ul>
            </Card>
            <Button asChild variant="outline">
              <Link to="/">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Home
              </Link>
            </Button>
          </div>
        )}

        {/* Already Responded State */}
        {pageState === 'already_responded' && invitation && (
          <div className="text-center">
            <AlertCircle className="h-16 w-16 text-amber-400 mx-auto mb-4" />
            <h1 className="text-3xl font-light text-zinc-100 mb-2">Already Responded</h1>
            <p className="text-zinc-400 mb-8">
              You have already responded to this invitation on{' '}
              {invitation.previousResponseDate
                ? new Date(invitation.previousResponseDate).toLocaleDateString('de-CH')
                : 'a previous date'}
              .
            </p>
            <Card className="p-6 mb-8">
              <p className="text-zinc-300">
                Your response:{' '}
                <span
                  className={`font-semibold ${
                    invitation.previousResponse === 'ACCEPTED'
                      ? 'text-green-400'
                      : invitation.previousResponse === 'DECLINED'
                        ? 'text-red-400'
                        : 'text-amber-400'
                  }`}
                >
                  {invitation.previousResponse === 'ACCEPTED' && 'Accepted'}
                  {invitation.previousResponse === 'DECLINED' && 'Declined'}
                  {invitation.previousResponse === 'TENTATIVE' && 'Tentative'}
                </span>
              </p>
              {invitation.previousResponse === 'ACCEPTED' && (
                <p className="mt-4 text-sm text-zinc-400">
                  You can manage your speaker profile and content submissions from your speaker
                  dashboard.
                </p>
              )}
              {invitation.previousResponse === 'DECLINED' && (
                <p className="mt-4 text-sm text-zinc-400">
                  If you&apos;d like to reconsider, please contact the event organizers directly.
                </p>
              )}
            </Card>
            <Button asChild variant="outline">
              <Link to="/">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Home
              </Link>
            </Button>
          </div>
        )}

        {/* Form State */}
        {pageState === 'form' && invitation && (
          <>
            {/* Event Details Card */}
            <Card className="p-6 mb-8">
              <h1 className="text-2xl font-light text-zinc-100 mb-4">
                You&apos;re Invited to Speak at {invitation.eventTitle}
              </h1>

              <div className="flex flex-wrap gap-4 text-zinc-400 mb-6">
                {invitation.eventDate && (
                  <div className="flex items-center gap-2">
                    <Calendar className="h-4 w-4" />
                    <span>{invitation.eventDate}</span>
                  </div>
                )}
                {invitation.sessionTitle && (
                  <div className="flex items-center gap-2">
                    <MapPin className="h-4 w-4" />
                    <span>{invitation.sessionTitle}</span>
                  </div>
                )}
                {invitation.responseDeadline && (
                  <div className="flex items-center gap-2">
                    <Clock className="h-4 w-4" />
                    <span>Respond by {invitation.responseDeadline}</span>
                  </div>
                )}
              </div>

              {invitation.invitationMessage && (
                <div className="bg-zinc-800/50 rounded-lg p-4 mb-4">
                  <p className="text-sm text-zinc-300 italic">
                    &ldquo;{invitation.invitationMessage}&rdquo;
                  </p>
                </div>
              )}

              <p className="text-zinc-300">
                Dear <span className="font-medium text-zinc-100">{invitation.speakerName}</span>,
                <br />
                <br />
                We would be honored to have you as a speaker at this event. Please let us know if
                you can join us by selecting one of the options below.
              </p>
            </Card>

            {/* Response Buttons */}
            <Card className="p-6 mb-8">
              <h2 className="text-lg font-light text-zinc-100 mb-4">Your Response</h2>

              <div className="flex flex-col sm:flex-row gap-4 mb-6">
                <Button
                  variant={selectedResponse === 'ACCEPT' ? 'default' : 'outline'}
                  className={`flex-1 h-14 ${selectedResponse === 'ACCEPT' ? 'bg-green-600 hover:bg-green-700 border-green-600' : ''}`}
                  onClick={() => handleResponseSelect('ACCEPT')}
                  disabled={respondMutation.isPending}
                >
                  <ThumbsUp className="h-5 w-5 mr-2" />
                  Accept
                </Button>
                <Button
                  variant={selectedResponse === 'DECLINE' ? 'default' : 'outline'}
                  className={`flex-1 h-14 ${selectedResponse === 'DECLINE' ? 'bg-red-600 hover:bg-red-700 border-red-600' : ''}`}
                  onClick={() => handleResponseSelect('DECLINE')}
                  disabled={respondMutation.isPending}
                >
                  <ThumbsDown className="h-5 w-5 mr-2" />
                  Decline
                </Button>
              </div>

              {/* Accept - Optional Message */}
              {selectedResponse === 'ACCEPT' && (
                <div className="space-y-4 border-t border-zinc-700 pt-6">
                  <div>
                    <label htmlFor="comments" className="block text-sm text-zinc-400 mb-2">
                      Message to Organizers (optional)
                    </label>
                    <textarea
                      id="comments"
                      className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 min-h-[80px]"
                      placeholder="Any questions or comments for the organizers..."
                      value={preferences.comments || ''}
                      onChange={(e) => setPreferences({ ...preferences, comments: e.target.value })}
                    />
                  </div>
                </div>
              )}

              {/* Decline Reason Form */}
              {selectedResponse === 'DECLINE' && (
                <div className="space-y-4 border-t border-zinc-700 pt-6">
                  <div>
                    <label htmlFor="declineReason" className="block text-sm text-zinc-400 mb-2">
                      Reason for declining{' '}
                      <span className="text-red-400" aria-hidden="true">
                        *
                      </span>
                      <span className="sr-only">(required)</span>
                    </label>
                    <textarea
                      id="declineReason"
                      className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 min-h-[100px]"
                      placeholder="Please let us know why you can't participate..."
                      value={reason}
                      onChange={(e) => setReason(e.target.value)}
                      required
                      aria-required="true"
                      aria-describedby={!reason.trim() ? 'declineReasonError' : undefined}
                    />
                    {!reason.trim() && (
                      <p id="declineReasonError" className="text-sm text-red-400 mt-1" role="alert">
                        A reason is required to decline
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* Submit Button */}
              {selectedResponse && (
                <div className="mt-6">
                  <Button
                    onClick={handleSubmit}
                    disabled={!isSubmitValid() || respondMutation.isPending}
                    className="w-full h-12"
                  >
                    {respondMutation.isPending ? (
                      <>
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                        Submitting...
                      </>
                    ) : (
                      <>Submit Response</>
                    )}
                  </Button>

                  {respondMutation.error && (
                    <p className="text-sm text-red-400 mt-2 text-center">
                      {(respondMutation.error as Error).message || 'Failed to submit response'}
                    </p>
                  )}
                </div>
              )}
            </Card>
          </>
        )}

        {/* Success State */}
        {pageState === 'success' && responseResult && (
          <>
            <div className="text-center mb-8">
              <CheckCircle2 className="h-16 w-16 text-green-400 mx-auto mb-4" />
              <h1 className="text-3xl font-light text-zinc-100 mb-2">Response Submitted!</h1>
              <p className="text-xl text-zinc-400">
                Thank you, {responseResult.speakerName}, for your response.
              </p>
            </div>

            <Card className="p-6 mb-8">
              {responseResult.nextSteps && responseResult.nextSteps.length > 0 && (
                <>
                  <h2 className="text-lg font-light text-zinc-100 mb-4">What&apos;s Next?</h2>
                  <ul className="space-y-3 text-zinc-300">
                    {responseResult.nextSteps.map((step, index) => (
                      <li key={index} className="flex items-start gap-3">
                        <CheckCircle2 className="h-5 w-5 text-green-400 flex-shrink-0 mt-0.5" />
                        <span>{step}</span>
                      </li>
                    ))}
                  </ul>
                </>
              )}

              {responseResult.contentDeadline && (
                <div className="mt-6 p-4 bg-blue-900/20 rounded-lg border border-blue-800">
                  <p className="text-sm text-blue-300">
                    <Calendar className="h-4 w-4 inline mr-2" />
                    Content submission deadline: {responseResult.contentDeadline}
                  </p>
                </div>
              )}

              {responseResult.message && (
                <p className="mt-4 text-zinc-400">{responseResult.message}</p>
              )}
            </Card>

            <div className="flex justify-center gap-4">
              {responseResult.profileUrl && (
                <Button asChild>
                  <a href={responseResult.profileUrl}>Complete Your Profile</a>
                </Button>
              )}
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  Back to Home
                </Link>
              </Button>
            </div>
          </>
        )}
      </div>
    </PublicLayout>
  );
};

export default InvitationResponsePage;
