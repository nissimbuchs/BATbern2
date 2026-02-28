/**
 * ContentSubmissionPage (Story 6.3)
 *
 * Speaker content self-submission portal page.
 * Allows speakers to submit their presentation title, abstract, and materials.
 *
 * Features:
 * - AC1: Session assignment check
 * - AC2: Title input with 200 char limit
 * - AC3: Abstract input with 1000 char limit
 * - AC4: Draft auto-save every 30 seconds
 * - AC5: Content submission with validation
 * - AC8: Revision feedback display
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { PublicLayout } from '@/components/public/PublicLayout';
import { speakerPortalService, ContentSubmitResponse } from '@/services/speakerPortalService';
import PresentationUpload from '@/components/speaker-portal/PresentationUpload';
import { User, LayoutDashboard } from 'lucide-react';
import { BATbernLoader } from '@components/shared/BATbernLoader';

// Constants
const MAX_TITLE_LENGTH = 200;
const MAX_ABSTRACT_LENGTH = 1000;
const AUTO_SAVE_INTERVAL_MS = 30000; // 30 seconds

interface FormState {
  title: string;
  contentAbstract: string;
}

interface FormErrors {
  title?: string;
  contentAbstract?: string;
}

export default function ContentSubmissionPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const queryClient = useQueryClient();

  // Form state
  const [formState, setFormState] = useState<FormState>({
    title: '',
    contentAbstract: '',
  });
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [isDirty, setIsDirty] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [submitResult, setSubmitResult] = useState<ContentSubmitResponse | null>(null);
  // AC7: Material upload state
  const [materialUrl, setMaterialUrl] = useState<string | null>(null);
  const [materialFileName, setMaterialFileName] = useState<string | null>(null);
  const [materialError, setMaterialError] = useState<string | null>(null);

  // Refs for auto-save
  const autoSaveTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastSavedStateRef = useRef<FormState | null>(null);

  // Fetch content info
  const {
    data: contentInfo,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['speaker-content', token],
    queryFn: () => speakerPortalService.getContentInfo(token!),
    enabled: !!token,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  // Save draft mutation
  const saveDraftMutation = useMutation({
    mutationFn: () =>
      speakerPortalService.saveDraft({
        token: token!,
        title: formState.title || null,
        contentAbstract: formState.contentAbstract || null,
      }),
    onMutate: () => {
      setIsSaving(true);
    },
    onSuccess: (response) => {
      setLastSavedAt(response.savedAt);
      lastSavedStateRef.current = { ...formState };
      setIsSaving(false);
    },
    onError: () => {
      setIsSaving(false);
    },
  });

  // Submit content mutation
  const submitMutation = useMutation({
    mutationFn: () =>
      speakerPortalService.submitContent({
        token: token!,
        title: formState.title,
        contentAbstract: formState.contentAbstract,
      }),
    onSuccess: (response) => {
      setSubmitResult(response);
      setIsSubmitted(true);
      queryClient.invalidateQueries({ queryKey: ['speaker-content', token] });
    },
  });

  // Initialize form from draft (AC4) and material (AC7)
  useEffect(() => {
    if (contentInfo) {
      if (contentInfo.hasDraft) {
        setFormState({
          title: contentInfo.draftTitle || '',
          contentAbstract: contentInfo.draftAbstract || '',
        });
        lastSavedStateRef.current = {
          title: contentInfo.draftTitle || '',
          contentAbstract: contentInfo.draftAbstract || '',
        };
        if (contentInfo.lastSavedAt) {
          setLastSavedAt(contentInfo.lastSavedAt);
        }
      } else {
        // Initialize saved state for new content (enables auto-save on first edit)
        lastSavedStateRef.current = {
          title: '',
          contentAbstract: '',
        };
      }
      // Initialize material state
      if (contentInfo.hasMaterial) {
        setMaterialUrl(contentInfo.materialUrl);
        setMaterialFileName(contentInfo.materialFileName);
      }
    }
  }, [contentInfo]);

  // Auto-save effect (AC4)
  useEffect(() => {
    if (!token || !contentInfo?.canSubmitContent || isSubmitted) {
      return;
    }

    // Set up auto-save interval
    autoSaveTimerRef.current = setInterval(() => {
      // Only save if dirty and content has changed
      if (
        isDirty &&
        lastSavedStateRef.current &&
        (formState.title !== lastSavedStateRef.current.title ||
          formState.contentAbstract !== lastSavedStateRef.current.contentAbstract)
      ) {
        saveDraftMutation.mutate();
      }
    }, AUTO_SAVE_INTERVAL_MS);

    return () => {
      if (autoSaveTimerRef.current) {
        clearInterval(autoSaveTimerRef.current);
      }
    };
  }, [token, contentInfo?.canSubmitContent, isDirty, formState, isSubmitted]);

  // Handle form field changes
  const handleFieldChange = useCallback(
    (field: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = e.target.value;
      setFormState((prev) => ({ ...prev, [field]: value }));
      setIsDirty(true);
      // Clear error when user starts typing
      if (formErrors[field]) {
        setFormErrors((prev) => ({ ...prev, [field]: undefined }));
      }
    },
    [formErrors]
  );

  // Validate form
  const validateForm = (): boolean => {
    const errors: FormErrors = {};

    if (!formState.title.trim()) {
      errors.title = t('speakerPortal.content.titleRequired');
    }

    if (!formState.contentAbstract.trim()) {
      errors.contentAbstract = t('speakerPortal.content.abstractRequired');
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // Handle submit
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    submitMutation.mutate();
  };

  // Render error states
  if (!token) {
    return (
      <PublicLayout>
        <div className="min-h-screen flex items-center justify-center px-4">
          <div className="max-w-md w-full bg-gray-800 rounded-lg p-8 text-center">
            <div className="text-red-400 text-6xl mb-4">!</div>
            <h1 className="text-2xl font-bold text-white mb-2">
              {t('speakerPortal.content.invalidLink')}
            </h1>
            <p className="text-gray-400">{t('speakerPortal.content.invalidLinkMessage')}</p>
          </div>
        </div>
      </PublicLayout>
    );
  }

  if (isLoading) {
    return (
      <PublicLayout>
        <div className="min-h-screen flex items-center justify-center px-4">
          <div className="text-center">
            <BATbernLoader size={96} />
            <p className="text-gray-400">{t('speakerPortal.content.loadingContent')}</p>
          </div>
        </div>
      </PublicLayout>
    );
  }

  if (error) {
    const errorCode = (error as Error & { errorCode?: string }).errorCode;
    const isExpired = errorCode === 'EXPIRED';
    const isNotFound = errorCode === 'NOT_FOUND';

    return (
      <PublicLayout>
        <div className="min-h-screen flex items-center justify-center px-4">
          <div className="max-w-md w-full bg-gray-800 rounded-lg p-8 text-center">
            <div className="text-red-400 text-6xl mb-4">!</div>
            <h1 className="text-2xl font-bold text-white mb-2">
              {isExpired
                ? t('speakerPortal.content.linkHasExpired')
                : isNotFound
                  ? t('speakerPortal.content.invalidLink')
                  : t('speakerPortal.invitationResponse.genericError')}
            </h1>
            <p className="text-gray-400">
              {isExpired
                ? t('speakerPortal.content.linkExpiredMessage')
                : isNotFound
                  ? t('speakerPortal.content.invalidLinkNotValidMessage')
                  : (error as Error).message}
            </p>
          </div>
        </div>
      </PublicLayout>
    );
  }

  // AC1: Cannot submit content (speaker not in correct state)
  if (!contentInfo?.canSubmitContent) {
    return (
      <PublicLayout>
        <div className="min-h-screen flex items-center justify-center px-4">
          <div className="max-w-md w-full bg-gray-800 rounded-lg p-8 text-center">
            <div className="text-yellow-400 text-6xl mb-4">!</div>
            <h1 className="text-2xl font-bold text-white mb-2">
              {t('speakerPortal.content.cannotSubmit')}
            </h1>
            <p className="text-gray-400">{t('speakerPortal.content.cannotSubmitMessage')}</p>
            <div className="mt-6 p-4 bg-gray-700 rounded-lg">
              <p className="text-sm text-gray-400">
                {t('common:labels.event')}:{' '}
                <span className="text-white">{contentInfo?.eventTitle}</span>
              </p>
            </div>
          </div>
        </div>
      </PublicLayout>
    );
  }

  // Success state after submission
  if (isSubmitted && submitResult) {
    return (
      <PublicLayout>
        <div className="min-h-screen flex items-center justify-center px-4">
          <div className="max-w-md w-full bg-gray-800 rounded-lg p-8 text-center">
            <div className="text-green-400 text-6xl mb-4">✓</div>
            <h1 className="text-2xl font-bold text-white mb-2">
              {t('speakerPortal.content.submittedSuccessfully')}
            </h1>
            <p className="text-gray-400 mb-4">
              {t('speakerPortal.content.submittedMessage', {
                sessionTitle: submitResult.sessionTitle,
              })}
            </p>
            <div className="bg-gray-700 rounded-lg p-4 text-left">
              <p className="text-sm text-gray-400">
                {t('speakerPortal.content.version')}:{' '}
                <span className="text-white">{submitResult.version}</span>
              </p>
              <p className="text-sm text-gray-400">
                {t('speakerPortal.content.status')}:{' '}
                <span className="text-green-400">{submitResult.status}</span>
              </p>
            </div>
            <p className="text-sm text-gray-500 mt-4">{t('speakerPortal.content.nextStepsInfo')}</p>
            <Link
              to={`/speaker-portal/dashboard?token=${token}`}
              className="inline-block mt-6 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors"
            >
              {t('speakerPortal.content.goToDashboard')}
            </Link>
          </div>
        </div>
      </PublicLayout>
    );
  }

  return (
    <PublicLayout>
      <div className="min-h-screen py-8 px-4">
        <div className="max-w-2xl mx-auto">
          {/* Header */}
          <div className="mb-8">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <div>
                <h1 className="text-3xl font-bold text-white mb-2">
                  {t('speakerPortal.content.submitYourContent')}
                </h1>
                <p className="text-gray-400">
                  {contentInfo.eventTitle} - {contentInfo.sessionTitle}
                </p>
              </div>
              <div className="flex gap-2">
                <Link
                  to={`/speaker-portal/dashboard?token=${token}`}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors text-sm"
                >
                  <LayoutDashboard className="h-4 w-4" />
                  {t('speakerPortal.content.dashboard')}
                </Link>
                {/* AC10: Edit Profile Navigation */}
                <Link
                  to={`/speaker-portal/profile?token=${token}`}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-gray-200 rounded-lg transition-colors text-sm"
                >
                  <User className="h-4 w-4" />
                  {t('speakerPortal.content.editProfile')}
                </Link>
              </div>
            </div>
          </div>

          {/* AC8: Revision Feedback */}
          {contentInfo.needsRevision && contentInfo.reviewerFeedback && (
            <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded-lg">
              <h2 className="text-lg font-semibold text-yellow-400 mb-2">
                {t('speakerPortal.content.revisionRequested')}
              </h2>
              <p className="text-gray-300 mb-2">{contentInfo.reviewerFeedback}</p>
              {contentInfo.reviewedBy && (
                <p className="text-sm text-gray-500">
                  {t('speakerPortal.content.reviewedBy')}: {contentInfo.reviewedBy}
                  {contentInfo.reviewedAt && (
                    <>
                      {' '}
                      {t('speakerPortal.content.on')}{' '}
                      {new Date(contentInfo.reviewedAt).toLocaleDateString()}
                    </>
                  )}
                </p>
              )}
            </div>
          )}

          {/* Content Form */}
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Title Input (AC2) */}
            <div>
              <label htmlFor="title" className="block text-sm font-medium text-gray-300 mb-2">
                {t('speakerPortal.content.presentationTitle')}{' '}
                <span className="text-red-400">*</span>
              </label>
              <input
                type="text"
                id="title"
                name="title"
                value={formState.title}
                onChange={handleFieldChange('title')}
                maxLength={MAX_TITLE_LENGTH}
                className={`w-full bg-gray-700 border ${
                  formErrors.title ? 'border-red-500' : 'border-gray-600'
                } rounded-lg px-4 py-3 text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500`}
                placeholder={t('speakerPortal.content.titlePlaceholder')}
                aria-describedby="title-count"
              />
              <div className="flex justify-between mt-1">
                {formErrors.title && (
                  <p className="text-red-400 text-sm" role="alert">
                    {formErrors.title}
                  </p>
                )}
                <p id="title-count" className="text-gray-500 text-sm ml-auto">
                  {formState.title.length} / {MAX_TITLE_LENGTH}
                </p>
              </div>
            </div>

            {/* Abstract Input (AC3) */}
            <div>
              <label htmlFor="abstract" className="block text-sm font-medium text-gray-300 mb-2">
                {t('speakerPortal.content.abstract')} <span className="text-red-400">*</span>
              </label>
              <textarea
                id="abstract"
                name="abstract"
                value={formState.contentAbstract}
                onChange={handleFieldChange('contentAbstract')}
                maxLength={MAX_ABSTRACT_LENGTH}
                rows={8}
                className={`w-full bg-gray-700 border ${
                  formErrors.contentAbstract ? 'border-red-500' : 'border-gray-600'
                } rounded-lg px-4 py-3 text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none`}
                placeholder={t('speakerPortal.content.abstractPlaceholder')}
                aria-describedby="abstract-count"
              />
              <div className="flex justify-between mt-1">
                {formErrors.contentAbstract && (
                  <p className="text-red-400 text-sm" role="alert">
                    {formErrors.contentAbstract}
                  </p>
                )}
                <p id="abstract-count" className="text-gray-500 text-sm ml-auto">
                  {formState.contentAbstract.length} / {MAX_ABSTRACT_LENGTH}
                </p>
              </div>
            </div>

            {/* Presentation Upload (AC7) - Optional */}
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                {t('speakerPortal.content.presentationFile')}
              </label>
              {contentInfo.hasSessionAssigned ? (
                <PresentationUpload
                  token={token}
                  currentMaterialUrl={materialUrl}
                  currentMaterialName={materialFileName}
                  onMaterialUploaded={(url, fileName) => {
                    setMaterialUrl(url);
                    setMaterialFileName(fileName);
                    setMaterialError(null);
                  }}
                  onError={(error) => {
                    setMaterialError(error.message);
                  }}
                />
              ) : (
                <div className="p-4 bg-yellow-900/30 border border-yellow-600 rounded-lg text-sm">
                  <p className="text-yellow-400">{t('speakerPortal.content.enableFileUploads')}</p>
                </div>
              )}
              {materialError && (
                <p className="text-red-400 text-sm mt-2" role="alert">
                  {materialError}
                </p>
              )}
            </div>

            {/* Save Status (AC4) */}
            <div className="flex items-center justify-between text-sm">
              <div className="text-gray-500">
                {isSaving ? (
                  <span className="text-blue-400">{t('speakerPortal.content.saving')}</span>
                ) : lastSavedAt ? (
                  <span>
                    {t('speakerPortal.content.lastSaved', {
                      time: new Date(lastSavedAt).toLocaleTimeString(),
                    })}
                  </span>
                ) : isDirty ? (
                  <span>{t('speakerPortal.content.unsavedChanges')}</span>
                ) : null}
              </div>
            </div>

            {/* Submit Button */}
            <div className="flex justify-end gap-4">
              <button
                type="submit"
                disabled={submitMutation.isPending}
                className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitMutation.isPending
                  ? t('speakerPortal.content.submitting')
                  : t('speakerPortal.content.submitContent')}
              </button>
            </div>

            {/* Submit Error */}
            {submitMutation.isError && (
              <div className="p-4 bg-red-900/30 border border-red-600 rounded-lg">
                <p className="text-red-400">{(submitMutation.error as Error).message}</p>
              </div>
            )}
          </form>
        </div>
      </div>
    </PublicLayout>
  );
}
