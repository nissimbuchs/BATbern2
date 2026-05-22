/**
 * ProfileUpdatePage Component
 *
 * Speaker profile management page.
 *
 * Code review 2026-05-18 (D1): the original implementation called
 * {@code speakerPortalService.getProfile/updateProfile}, which targeted
 * {@code /api/v1/speaker-portal/.../profile} endpoints that Story 11.C.1 had already deleted.
 * Per PM resolution 2026-05-18, profile editing now uses the CUMS user endpoints
 * ({@code GET/PUT /api/v1/users/me} via {@link userAccountApi}) directly — every speaker is
 * a User, and the user-level profile is not per-event. The route no longer takes an
 * {@code eventCode} path parameter; a backward-compat redirect is mounted in {@code App.tsx}.
 *
 * Speaker-specific fields (expertiseAreas, speakingTopics, linkedInUrl, languages) were
 * deferred to a follow-up — they have no current backend storage in either CUMS User or
 * Speaker entity. See {@code deferred-work.md} entry for 11.E.3.
 */

import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
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
} from 'lucide-react';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { getUserProfile, updateUserProfile } from '@/services/api/userAccountApi';
import type { User } from '@/types/userAccount.types';
import { useAuth } from '@/hooks/useAuth';
import ProfilePhotoUpload from '@/components/speaker-portal/ProfilePhotoUpload';

type PageState = 'loading' | 'form' | 'error';

const ProfileUpdatePage = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { isAuthenticated, isLoading: authLoading } = useAuth();

  const [pageState, setPageState] = useState<PageState>('loading');
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  // Form state — only the CUMS-supported fields.
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [bio, setBio] = useState('');

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

  const updateMutation = useMutation({
    mutationFn: (updates: Partial<User>) => updateUserProfile(updates),
    onSuccess: (updatedUser) => {
      queryClient.setQueryData(['user-profile-me'], { ...profileData, user: updatedUser });
      setHasUnsavedChanges(false);
    },
  });

  useEffect(() => {
    if (user) {
      setFirstName(user.firstName || '');
      setLastName(user.lastName || '');
      setBio(user.bio || '');
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

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (bio.length > 500) {
      newErrors.bio = t('speakerPortal.profile.bioExceeds');
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) return;
    updateMutation.mutate({
      firstName: firstName || undefined,
      lastName: lastName || undefined,
      bio: bio || undefined,
    });
  };

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
              <Link to="/speaker-portal/dashboard">
                <ArrowLeft className="h-4 w-4 mr-2" />
                {t('speakerPortal.profile.backToHome')}
              </Link>
            </Button>
          </div>
        )}

        {pageState === 'form' && user && (
          <>
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-8">
              <div>
                <h1 className="text-2xl font-light text-zinc-100">
                  {t('speakerPortal.profile.pageTitle')}
                </h1>
                <p className="text-zinc-400 mt-1">{t('speakerPortal.profile.pageSubtitle')}</p>
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

            {completeness < 100 && (
              <Card className="p-4 mb-6 border-amber-800 bg-amber-900/20">
                <div className="flex items-start gap-3">
                  <AlertCircle className="h-5 w-5 text-amber-400 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm text-amber-300 font-medium">
                      {t('speakerPortal.profile.completeProfileHint')}
                    </p>
                  </div>
                </div>
              </Card>
            )}

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
                  <div className="text-center text-sm text-red-400 mb-4">{photoUploadError}</div>
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
                  <label htmlFor="bio" className="block text-sm text-zinc-400 mb-2">
                    {t('speakerPortal.profile.bio')}
                    <span className="ml-2 text-zinc-500">({bio.length}/500)</span>
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
                    maxLength={500}
                    placeholder={t('speakerPortal.profile.bioPlaceholder')}
                  />
                  {errors.bio && <p className="text-sm text-red-400 mt-1">{errors.bio}</p>}
                </div>
              </div>
            </Card>

            <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
              <Button asChild variant="outline">
                <Link to="/speaker-portal/dashboard">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {t('speakerPortal.profile.backToHome')}
                </Link>
              </Button>

              <Button
                onClick={handleSubmit}
                disabled={updateMutation.isPending || !hasUnsavedChanges}
                className="w-full sm:w-auto"
              >
                {updateMutation.isPending ? (
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
      </div>
    </PublicLayout>
  );
};

export default ProfileUpdatePage;
