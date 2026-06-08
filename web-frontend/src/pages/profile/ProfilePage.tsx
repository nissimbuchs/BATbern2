/**
 * ProfilePage Component (Story 12.11 — generalized from the speaker-portal
 * ProfileUpdatePage; the original lives in git history at
 * web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx).
 *
 * Role-neutral profile page mounted at `/profile` behind a plain ProtectedRoute
 * (any authenticated role). Two tabs:
 *  - "Profile": firstName, lastName, bio, profile photo, read-only email, company
 *  - "Consent & Newsletter": write-once ToS/Privacy consent (Story 12.11 AC5b) and
 *    the newsletter toggle backed by the EXISTING EMS `newsletter_subscribers`
 *    self-service (`useMySubscription`/`usePatchMySubscription` — Scope Revision #1;
 *    NOT a user_profiles column).
 *
 * Onboarding gate (AC6): when mounted with `?onboarding=1` (set by ProtectedRoute for
 * consent-less users), the Consent tab is preselected, a non-dismissible notice is
 * shown, and a successful consent save refreshes the auth user (lifting the gate) and
 * navigates to `/dashboard`.
 *
 * History (pre-12.11): Code review 2026-05-18 (D1) switched profile editing to the
 * CUMS user endpoints (GET/PUT /api/v1/users/me via userAccountApi) — every speaker
 * is a User, and the user-level profile is not per-event. Speaker-specific fields
 * (expertiseAreas, speakingTopics, linkedInUrl, languages) remain deferred — no
 * backend storage (see deferred-work.md entry for 11.E.3).
 */

import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Tabs, Tab } from '@mui/material';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import {
  CheckCircle2,
  XCircle,
  Loader2,
  ArrowLeft,
  Save,
  User as UserIcon,
  AlertCircle,
  ShieldCheck,
} from 'lucide-react';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { getUserProfile, updateUserProfile } from '@/services/api/userAccountApi';
import { useMySubscription, usePatchMySubscription } from '@/hooks/useNewsletter/useNewsletter';
import { CompanyAutocomplete } from '@/components/public/Registration/CompanyAutocomplete';
import { getOrCreateCompany } from '@/services/api/companyApi';
import type { User } from '@/types/userAccount.types';
import { useAuth } from '@/hooks/useAuth';
import ProfilePhotoUpload from '@/components/speaker-portal/ProfilePhotoUpload';

type PageState = 'loading' | 'form' | 'error';

const ProfilePage = () => {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { isAuthenticated, isLoading: authLoading, refreshUser } = useAuth();

  // AC6: ProtectedRoute sends consent-less users here with ?onboarding=1.
  const isOnboarding = searchParams.get('onboarding') === '1';

  const [pageState, setPageState] = useState<PageState>('loading');
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  // 0 = Profile, 1 = Consent & Newsletter. Consent tab preselected during onboarding.
  const [activeTab, setActiveTab] = useState(isOnboarding ? 1 : 0);

  // Form state — only the CUMS-supported fields.
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [bio, setBio] = useState('');
  // companyId = stored ADR-003 slug; companyDisplayName = human label shown in the
  // picker chip. A new (not-yet-created) company has a display name but no slug until
  // it is materialised via get-or-create on save.
  const [companyId, setCompanyId] = useState('');
  const [companyDisplayName, setCompanyDisplayName] = useState('');
  const [companyResolving, setCompanyResolving] = useState(false);
  const [consentChecked, setConsentChecked] = useState(false);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [photoUploadError, setPhotoUploadError] = useState<string | null>(null);

  const {
    data: profileData,
    error: fetchError,
    isLoading,
  } = useQuery({
    queryKey: ['user-profile-me'],
    queryFn: () => getUserProfile(['company']),
    enabled: !!isAuthenticated,
    retry: false,
  });

  const user: User | undefined = profileData?.user;

  // Story 12.11: newsletter consent lives in EMS newsletter_subscribers (Story 10.7) —
  // same hooks as the UserSettingsTab NewsletterSection, NOT a user_profiles field.
  const { data: subscription, isLoading: subscriptionLoading } = useMySubscription();
  const patchSubscription = usePatchMySubscription();

  const updateMutation = useMutation({
    mutationFn: (updates: Partial<User> & { termsAccepted?: boolean }) =>
      updateUserProfile(updates),
    onSuccess: (updatedUser) => {
      queryClient.setQueryData(['user-profile-me'], { ...profileData, user: updatedUser });
      setHasUnsavedChanges(false);
    },
  });

  // Separate mutation instance for the consent save so its pending/error state
  // doesn't entangle with the profile form.
  const consentMutation = useMutation({
    mutationFn: () => updateUserProfile({ termsAccepted: true }),
    onSuccess: async (updatedUser) => {
      queryClient.setQueryData(['user-profile-me'], { ...profileData, user: updatedUser });
      // AC6: refresh the auth user so ProtectedRoute sees termsAcceptedAt and lifts
      // the gate, then leave the onboarding flow. The PUT response is server-
      // authoritative, so pass its termsAcceptedAt as an override — hydrateUserFromDb
      // fails open on a transient GET error and would otherwise re-apply the stale
      // null, bouncing the user straight back into the onboarding gate.
      await refreshUser?.(
        updatedUser.termsAcceptedAt ? { termsAcceptedAt: updatedUser.termsAcceptedAt } : undefined
      );
      if (isOnboarding) {
        navigate('/dashboard');
      }
    },
  });

  useEffect(() => {
    if (user) {
      setFirstName(user.firstName || '');
      setLastName(user.lastName || '');
      setBio(user.bio || '');
      setCompanyId(user.companyId || '');
      setCompanyDisplayName(user.company?.displayName || user.companyId || '');
      setPageState('form');
    }
  }, [user]);

  useEffect(() => {
    if (authLoading || isLoading) {
      setPageState('loading');
    } else if (fetchError) {
      setPageState('error');
    } else if (user) {
      setPageState('form');
    }
  }, [authLoading, isLoading, fetchError, user]);

  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (hasUnsavedChanges) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [hasUnsavedChanges]);

  const markDirty = useCallback(() => {
    setHasUnsavedChanges(true);
  }, []);

  const handlePhotoUploaded = useCallback(() => {
    setPhotoUploadError(null);
    queryClient.invalidateQueries({ queryKey: ['user-profile-me'] });
  }, [queryClient]);

  const handlePhotoError = useCallback((error: { type: string; message: string }) => {
    setPhotoUploadError(error.message);
  }, []);

  const handleNewsletterToggle = useCallback(
    (checked: boolean) => {
      const language = i18n.language?.startsWith('de') ? 'de' : 'en';
      patchSubscription.mutate({ subscribed: checked, language });
    },
    [i18n.language, patchSubscription]
  );

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (bio.length > 5000) {
      newErrors.bio = t('speakerPortal.profile.bioExceeds');
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;

    // A brand-new company picked in the autocomplete has a display name but no
    // slug yet — materialise it via get-or-create so we store a real company
    // reference (the canonical slug), never a free-typed string (ADR-003).
    let resolvedCompanyId = companyId;
    if (!companyId && companyDisplayName) {
      setCompanyResolving(true);
      try {
        const company = await getOrCreateCompany(companyDisplayName);
        resolvedCompanyId = company.name;
        setCompanyId(company.name);
      } catch {
        setErrors((prev) => ({ ...prev, companyId: t('profile.companySaveError') }));
        return;
      } finally {
        setCompanyResolving(false);
      }
    }

    updateMutation.mutate({
      firstName: firstName || undefined,
      lastName: lastName || undefined,
      bio: bio || undefined,
      companyId: resolvedCompanyId || undefined,
    });
  };

  const handleConsentSave = () => {
    if (!consentChecked) return;
    consentMutation.mutate();
  };

  const hasConsent = Boolean(user?.termsAcceptedAt);

  // Simple client-side completeness on the CUMS-supported fields.
  const completeness = user
    ? Math.round(
        ((firstName ? 1 : 0) +
          (lastName ? 1 : 0) +
          (user.email ? 1 : 0) +
          (bio ? 1 : 0) +
          (user.profilePictureUrl ? 1 : 0)) *
          (100 / 5)
      )
    : 0;

  const getErrorDetails = () => {
    const p = 'speakerPortal.profile';
    if (fetchError) {
      const err = fetchError as Error;
      return {
        title: t(`${p}.genericError`),
        message: err.message || t(`${p}.genericErrorMessage`),
      };
    }
    return {
      title: t(`${p}.genericError`),
      message: t(`${p}.unexpectedError`),
    };
  };

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl min-h-screen">
        {pageState === 'loading' && (
          <div
            className="text-center py-24"
            role="status"
            aria-label={t('speakerPortal.profile.loadingAria')}
          >
            <BATbernLoader size={128} />
            <h2 className="text-2xl font-light text-zinc-100 mb-2">
              {t('speakerPortal.profile.loading')}
            </h2>
            <p className="text-zinc-400">{t('speakerPortal.profile.loadingMessage')}</p>
          </div>
        )}

        {pageState === 'error' && (
          <div className="text-center">
            <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
            <h1 className="text-3xl font-light text-zinc-100 mb-2">{getErrorDetails().title}</h1>
            <p className="text-zinc-400 mb-8">{getErrorDetails().message}</p>
            <Button asChild variant="outline">
              <Link to="/dashboard">
                <ArrowLeft className="h-4 w-4 mr-2" />
                {t('speakerPortal.profile.backToHome')}
              </Link>
            </Button>
          </div>
        )}

        {pageState === 'form' && user && (
          <>
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
              <div>
                <h1 className="text-2xl font-light text-zinc-100">{t('profile.pageTitle')}</h1>
                <p className="text-zinc-400 mt-1">{t('profile.pageSubtitle')}</p>
              </div>
              <div className="flex items-center gap-3">
                <div className="text-right">
                  <div className="text-sm text-zinc-400">
                    {t('speakerPortal.profile.profileCompleteness')}
                  </div>
                  <div
                    className={`text-2xl font-semibold ${
                      completeness === 100
                        ? 'text-green-400'
                        : completeness >= 70
                          ? 'text-amber-400'
                          : 'text-red-400'
                    }`}
                  >
                    {completeness}%
                  </div>
                </div>
                {completeness === 100 ? (
                  <CheckCircle2 className="h-10 w-10 text-green-400" />
                ) : (
                  <div className="h-10 w-10 rounded-full border-4 border-zinc-700 flex items-center justify-center">
                    <div
                      className="h-6 w-6 rounded-full"
                      style={{
                        background: `conic-gradient(${completeness >= 70 ? '#fbbf24' : '#ef4444'} ${completeness * 3.6}deg, #3f3f46 0deg)`,
                      }}
                    />
                  </div>
                )}
              </div>
            </div>

            {/* AC6: non-dismissible onboarding notice for consent-less users */}
            {isOnboarding && !hasConsent && (
              <Card
                className="p-4 mb-6 border-sky-800 bg-sky-900/20"
                data-testid="onboarding-notice"
                role="alert"
              >
                <div className="flex items-start gap-3">
                  <AlertCircle className="h-5 w-5 text-sky-400 flex-shrink-0 mt-0.5" />
                  <p className="text-sm text-sky-300 font-medium">
                    {t('profile.onboardingNotice')}
                  </p>
                </div>
              </Card>
            )}

            <Tabs
              value={activeTab}
              onChange={(_, v: number) => setActiveTab(v)}
              sx={{
                mb: 3,
                '& .MuiTab-root': { color: '#a1a1aa' },
                '& .Mui-selected': { color: '#fafafa' },
              }}
            >
              <Tab label={t('profile.tabs.profile')} data-testid="profile-tab" />
              <Tab label={t('profile.tabs.consent')} data-testid="consent-tab" />
            </Tabs>

            {activeTab === 0 && (
              <>
                <Card className="p-6 mb-6">
                  <div className="flex items-center gap-2 mb-4">
                    <UserIcon className="h-5 w-5 text-zinc-400" />
                    <h2 className="text-lg font-light text-zinc-100">
                      {t('speakerPortal.profile.basicInfo')}
                    </h2>
                  </div>

                  <div className="space-y-4">
                    <div className="flex justify-center mb-6">
                      <ProfilePhotoUpload
                        currentPhotoUrl={user.profilePictureUrl}
                        onPhotoUploaded={handlePhotoUploaded}
                        onError={handlePhotoError}
                      />
                    </div>
                    {photoUploadError && (
                      <div className="text-center text-sm text-red-400 mb-4">
                        {photoUploadError}
                      </div>
                    )}

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div>
                        <label htmlFor="firstName" className="block text-sm text-zinc-400 mb-2">
                          {t('common:labels.firstName')}
                        </label>
                        <input
                          id="firstName"
                          type="text"
                          className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 min-h-[44px]"
                          value={firstName}
                          onChange={(e) => {
                            setFirstName(e.target.value);
                            markDirty();
                          }}
                        />
                      </div>
                      <div>
                        <label htmlFor="lastName" className="block text-sm text-zinc-400 mb-2">
                          {t('common:labels.lastName')}
                        </label>
                        <input
                          id="lastName"
                          type="text"
                          className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 min-h-[44px]"
                          value={lastName}
                          onChange={(e) => {
                            setLastName(e.target.value);
                            markDirty();
                          }}
                        />
                      </div>
                    </div>

                    <div>
                      <label htmlFor="email" className="block text-sm text-zinc-400 mb-2">
                        {t('speakerPortal.profile.emailReadOnly')}
                      </label>
                      <input
                        id="email"
                        type="email"
                        className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-4 py-2 text-zinc-500 min-h-[44px] cursor-not-allowed"
                        value={user.email}
                        disabled
                      />
                    </div>

                    <div>
                      <label htmlFor="company" className="block text-sm text-zinc-400 mb-2">
                        {t('profile.companyLabel')}
                      </label>
                      <CompanyAutocomplete
                        value={companyId}
                        valueLabel={companyDisplayName}
                        valueIsSlug
                        onCompanySelect={(selection) => {
                          setCompanyId(selection?.name ?? '');
                          setCompanyDisplayName(selection?.displayName ?? '');
                          markDirty();
                        }}
                        error={errors.companyId}
                      />
                    </div>

                    <div>
                      <label htmlFor="bio" className="block text-sm text-zinc-400 mb-2">
                        {t('speakerPortal.profile.bio')}
                        <span className="ml-2 text-zinc-500">({bio.length}/5000)</span>
                      </label>
                      <textarea
                        id="bio"
                        className={`w-full bg-zinc-800 border rounded-lg px-4 py-2 text-zinc-100 min-h-[100px] ${
                          errors.bio ? 'border-red-500' : 'border-zinc-700'
                        }`}
                        value={bio}
                        onChange={(e) => {
                          setBio(e.target.value);
                          markDirty();
                        }}
                        maxLength={5000}
                        placeholder={t('speakerPortal.profile.bioPlaceholder')}
                      />
                      {errors.bio && <p className="text-sm text-red-400 mt-1">{errors.bio}</p>}
                    </div>
                  </div>
                </Card>

                <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
                  <Button asChild variant="outline">
                    <Link to="/dashboard">
                      <ArrowLeft className="h-4 w-4 mr-2" />
                      {t('speakerPortal.profile.backToHome')}
                    </Link>
                  </Button>

                  <Button
                    onClick={handleSubmit}
                    disabled={updateMutation.isPending || companyResolving || !hasUnsavedChanges}
                    className="w-full sm:w-auto"
                  >
                    {updateMutation.isPending || companyResolving ? (
                      <>
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                        {t('speakerPortal.profile.saving')}
                      </>
                    ) : (
                      <>
                        <Save className="h-4 w-4 mr-2" />
                        {t('speakerPortal.profile.saveChanges')}
                      </>
                    )}
                  </Button>
                </div>
              </>
            )}

            {activeTab === 1 && (
              <>
                {/* Consent section — write-once (AC5b) */}
                <Card className="p-6 mb-6" data-testid="consent-section">
                  <div className="flex items-center gap-2 mb-4">
                    <ShieldCheck className="h-5 w-5 text-zinc-400" />
                    <h2 className="text-lg font-light text-zinc-100">
                      {t('profile.consentTitle')}
                    </h2>
                  </div>

                  {hasConsent ? (
                    <p className="text-sm text-zinc-300" data-testid="consent-accepted-on">
                      <CheckCircle2 className="inline h-4 w-4 text-green-400 mr-2" />
                      {t('profile.consentAcceptedOn', {
                        date: new Date(user.termsAcceptedAt as string).toLocaleDateString(
                          i18n.language
                        ),
                      })}
                    </p>
                  ) : (
                    <div className="space-y-4">
                      <label className="flex items-start gap-3 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={consentChecked}
                          onChange={(e) => setConsentChecked(e.target.checked)}
                          className="mt-1 h-4 w-4"
                          data-testid="consent-checkbox"
                        />
                        <span className="text-sm text-zinc-300">
                          {t('profile.consentLabelPrefix')}{' '}
                          <a
                            href="/terms"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="underline text-zinc-100"
                          >
                            {t('profile.termsOfService')}
                          </a>{' '}
                          {t('profile.consentLabelAnd')}{' '}
                          <a
                            href="/privacy"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="underline text-zinc-100"
                          >
                            {t('profile.privacyPolicy')}
                          </a>
                        </span>
                      </label>

                      {consentMutation.isError && (
                        <p className="text-sm text-red-400">{t('profile.consentSaveError')}</p>
                      )}

                      <Button
                        onClick={handleConsentSave}
                        disabled={!consentChecked || consentMutation.isPending}
                        data-testid="consent-save-button"
                      >
                        {consentMutation.isPending ? (
                          <>
                            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                            {t('speakerPortal.profile.saving')}
                          </>
                        ) : (
                          <>
                            <Save className="h-4 w-4 mr-2" />
                            {t('profile.consentSave')}
                          </>
                        )}
                      </Button>
                    </div>
                  )}
                </Card>

                {/* Newsletter section — EMS newsletter_subscribers self-service */}
                <Card className="p-6 mb-6" data-testid="newsletter-section">
                  <h2 className="text-lg font-light text-zinc-100 mb-2">
                    {t('profile.newsletterTitle')}
                  </h2>
                  <p className="text-sm text-zinc-400 mb-4">{t('profile.newsletterDescription')}</p>
                  <label className="flex items-center gap-3 cursor-pointer">
                    <input
                      type="checkbox"
                      role="switch"
                      checked={subscription?.subscribed ?? false}
                      disabled={subscriptionLoading || patchSubscription.isPending}
                      onChange={(e) => handleNewsletterToggle(e.target.checked)}
                      className="h-4 w-4"
                      data-testid="newsletter-toggle"
                    />
                    <span className="text-sm text-zinc-300">{t('profile.newsletterLabel')}</span>
                  </label>
                </Card>
              </>
            )}
          </>
        )}
      </div>
    </PublicLayout>
  );
};

export default ProfilePage;
