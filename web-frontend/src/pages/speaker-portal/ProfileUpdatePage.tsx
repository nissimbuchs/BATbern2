/**
 * ProfileUpdatePage Component (Story 6.2b)
 *
 * Speaker profile management page.
 * Accessed via magic link: /speaker-portal/profile?token={token}
 *
 * Features:
 * - View combined User + Speaker profile
 * - Update profile fields (name, bio, expertise, topics, languages, LinkedIn)
 * - Profile completeness indicator
 * - Unsaved changes warning
 */

import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import {
  CheckCircle2,
  XCircle,
  Loader2,
  ArrowLeft,
  Save,
  User,
  Globe,
  Briefcase,
  Languages,
  AlertCircle,
} from 'lucide-react';
import { speakerPortalService, ProfileUpdateRequest } from '@/services/speakerPortalService';

type PageState = 'loading' | 'form' | 'error';

// Available languages for selection
const AVAILABLE_LANGUAGES = [
  { code: 'de', name: 'German' },
  { code: 'en', name: 'English' },
  { code: 'fr', name: 'French' },
  { code: 'it', name: 'Italian' },
  { code: 'es', name: 'Spanish' },
  { code: 'pt', name: 'Portuguese' },
];

const ProfileUpdatePage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const queryClient = useQueryClient();

  const [pageState, setPageState] = useState<PageState>('loading');
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  // Form state
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [bio, setBio] = useState('');
  const [expertiseAreas, setExpertiseAreas] = useState<string[]>([]);
  const [speakingTopics, setSpeakingTopics] = useState<string[]>([]);
  const [linkedInUrl, setLinkedInUrl] = useState('');
  const [languages, setLanguages] = useState<string[]>([]);

  // Input state for adding new items
  const [newExpertise, setNewExpertise] = useState('');
  const [newTopic, setNewTopic] = useState('');

  // Validation state
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Fetch profile
  const {
    data: profile,
    error: fetchError,
    isLoading,
  } = useQuery({
    queryKey: ['speaker-profile', token],
    queryFn: () => speakerPortalService.getProfile(token!),
    enabled: !!token,
    retry: false,
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (request: ProfileUpdateRequest) => speakerPortalService.updateProfile(request),
    onSuccess: (updatedProfile) => {
      queryClient.setQueryData(['speaker-profile', token], updatedProfile);
      setHasUnsavedChanges(false);
    },
  });

  // Initialize form when profile loads
  useEffect(() => {
    if (profile) {
      setFirstName(profile.firstName || '');
      setLastName(profile.lastName || '');
      setBio(profile.bio || '');
      setExpertiseAreas(profile.expertiseAreas || []);
      setSpeakingTopics(profile.speakingTopics || []);
      setLinkedInUrl(profile.linkedInUrl || '');
      setLanguages(profile.languages || []);
      setPageState('form');
    }
  }, [profile]);

  // Update page state based on fetch result
  useEffect(() => {
    if (isLoading) {
      setPageState('loading');
    } else if (fetchError) {
      setPageState('error');
    } else if (profile) {
      setPageState('form');
    }
  }, [isLoading, fetchError, profile]);

  // Warn on unsaved changes when navigating away
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

  // Mark form as dirty when values change
  const markDirty = useCallback(() => {
    setHasUnsavedChanges(true);
  }, []);

  // Validate form
  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (bio.length > 500) {
      newErrors.bio = 'Bio must not exceed 500 characters';
    }

    if (expertiseAreas.length > 10) {
      newErrors.expertiseAreas = 'Maximum 10 expertise areas allowed';
    }

    if (speakingTopics.length > 10) {
      newErrors.speakingTopics = 'Maximum 10 speaking topics allowed';
    }

    if (linkedInUrl && !linkedInUrl.match(/^https?:\/\/(www\.)?linkedin\.com\/.+$/i)) {
      newErrors.linkedInUrl = 'Must be a valid LinkedIn URL';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // Handle form submission
  const handleSubmit = () => {
    if (!validate()) return;

    const request: ProfileUpdateRequest = {
      token: token!,
      firstName: firstName || undefined,
      lastName: lastName || undefined,
      bio: bio || undefined,
      expertiseAreas,
      speakingTopics,
      linkedInUrl: linkedInUrl || undefined,
      languages,
    };

    updateMutation.mutate(request);
  };

  // Add expertise area
  const addExpertise = () => {
    if (newExpertise.trim() && expertiseAreas.length < 10) {
      setExpertiseAreas([...expertiseAreas, newExpertise.trim()]);
      setNewExpertise('');
      markDirty();
    }
  };

  // Remove expertise area
  const removeExpertise = (index: number) => {
    setExpertiseAreas(expertiseAreas.filter((_, i) => i !== index));
    markDirty();
  };

  // Add speaking topic
  const addTopic = () => {
    if (newTopic.trim() && speakingTopics.length < 10) {
      setSpeakingTopics([...speakingTopics, newTopic.trim()]);
      setNewTopic('');
      markDirty();
    }
  };

  // Remove speaking topic
  const removeTopic = (index: number) => {
    setSpeakingTopics(speakingTopics.filter((_, i) => i !== index));
    markDirty();
  };

  // Toggle language
  const toggleLanguage = (code: string) => {
    if (languages.includes(code)) {
      setLanguages(languages.filter((l) => l !== code));
    } else {
      setLanguages([...languages, code]);
    }
    markDirty();
  };

  // Get error details
  const getErrorDetails = () => {
    if (fetchError) {
      const err = fetchError as Error & { errorCode?: string };
      if (err.errorCode === 'EXPIRED') {
        return {
          title: 'Link Expired',
          message: 'This profile link has expired. Please contact the event organizers.',
        };
      }
      if (err.errorCode === 'NOT_FOUND') {
        return {
          title: 'Invalid Link',
          message: 'This profile link is not valid.',
        };
      }
      return {
        title: 'Error',
        message: err.message || 'An error occurred while loading your profile.',
      };
    }
    return {
      title: 'Error',
      message: 'An unexpected error occurred.',
    };
  };

  // No token in URL
  if (!token) {
    return (
      <PublicLayout>
        <div className="container mx-auto px-4 py-12 max-w-3xl">
          <div className="text-center">
            <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
            <h1 className="text-3xl font-light mb-2 text-zinc-100">Invalid Link</h1>
            <p className="text-zinc-400 mb-8">
              This page requires a valid profile link from your email.
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

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl min-h-screen">
        {/* Loading State */}
        {pageState === 'loading' && (
          <div className="text-center py-24" role="status" aria-label="Loading profile">
            <Loader2 className="h-16 w-16 text-blue-400 mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-light text-zinc-100 mb-2">Loading Profile...</h2>
            <p className="text-zinc-400">Please wait while we load your profile.</p>
          </div>
        )}

        {/* Error State */}
        {pageState === 'error' && (
          <div className="text-center">
            <XCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
            <h1 className="text-3xl font-light text-zinc-100 mb-2">{getErrorDetails().title}</h1>
            <p className="text-zinc-400 mb-8">{getErrorDetails().message}</p>
            <Button asChild variant="outline">
              <Link to="/">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Home
              </Link>
            </Button>
          </div>
        )}

        {/* Form State */}
        {pageState === 'form' && profile && (
          <>
            {/* Header with Completeness */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-8">
              <div>
                <h1 className="text-2xl font-light text-zinc-100">Your Speaker Profile</h1>
                <p className="text-zinc-400 mt-1">Update your information for event materials</p>
              </div>
              <div className="flex items-center gap-3">
                <div className="text-right">
                  <div className="text-sm text-zinc-400">Profile Completeness</div>
                  <div
                    className={`text-2xl font-semibold ${
                      profile.profileCompleteness === 100
                        ? 'text-green-400'
                        : profile.profileCompleteness >= 70
                          ? 'text-amber-400'
                          : 'text-red-400'
                    }`}
                  >
                    {profile.profileCompleteness}%
                  </div>
                </div>
                {profile.profileCompleteness === 100 ? (
                  <CheckCircle2 className="h-10 w-10 text-green-400" />
                ) : (
                  <div className="h-10 w-10 rounded-full border-4 border-zinc-700 flex items-center justify-center">
                    <div
                      className="h-6 w-6 rounded-full"
                      style={{
                        background: `conic-gradient(${profile.profileCompleteness >= 70 ? '#fbbf24' : '#ef4444'} ${profile.profileCompleteness * 3.6}deg, #3f3f46 0deg)`,
                      }}
                    />
                  </div>
                )}
              </div>
            </div>

            {/* Missing Fields Alert */}
            {profile.missingFields.length > 0 && (
              <Card className="p-4 mb-6 border-amber-800 bg-amber-900/20">
                <div className="flex items-start gap-3">
                  <AlertCircle className="h-5 w-5 text-amber-400 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm text-amber-300 font-medium">
                      Complete your profile for better visibility
                    </p>
                    <p className="text-sm text-amber-400/80 mt-1">
                      Missing: {profile.missingFields.join(', ')}
                    </p>
                  </div>
                </div>
              </Card>
            )}

            {/* Basic Info Card */}
            <Card className="p-6 mb-6">
              <div className="flex items-center gap-2 mb-4">
                <User className="h-5 w-5 text-zinc-400" />
                <h2 className="text-lg font-light text-zinc-100">Basic Information</h2>
              </div>

              <div className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="firstName" className="block text-sm text-zinc-400 mb-2">
                      First Name
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
                      Last Name
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
                    Email (read-only)
                  </label>
                  <input
                    id="email"
                    type="email"
                    className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-4 py-2 text-zinc-500 min-h-[44px] cursor-not-allowed"
                    value={profile.email}
                    disabled
                  />
                </div>

                <div>
                  <label htmlFor="bio" className="block text-sm text-zinc-400 mb-2">
                    Bio
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
                    placeholder="Tell us about yourself..."
                  />
                  {errors.bio && <p className="text-sm text-red-400 mt-1">{errors.bio}</p>}
                </div>
              </div>
            </Card>

            {/* Expertise Card */}
            <Card className="p-6 mb-6">
              <div className="flex items-center gap-2 mb-4">
                <Briefcase className="h-5 w-5 text-zinc-400" />
                <h2 className="text-lg font-light text-zinc-100">Expertise & Topics</h2>
              </div>

              <div className="space-y-4">
                {/* Expertise Areas */}
                <div>
                  <label className="block text-sm text-zinc-400 mb-2">
                    Expertise Areas
                    <span className="ml-2 text-zinc-500">({expertiseAreas.length}/10)</span>
                  </label>
                  <div className="flex flex-wrap gap-2 mb-2">
                    {expertiseAreas.map((area, index) => (
                      <span
                        key={index}
                        className="inline-flex items-center gap-1 px-3 py-1 bg-blue-900/30 text-blue-300 rounded-full text-sm"
                      >
                        {area}
                        <button
                          type="button"
                          onClick={() => removeExpertise(index)}
                          className="hover:text-red-400 ml-1"
                          aria-label={`Remove ${area}`}
                        >
                          &times;
                        </button>
                      </span>
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      className="flex-1 bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 min-h-[44px]"
                      value={newExpertise}
                      onChange={(e) => setNewExpertise(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addExpertise())}
                      placeholder="Add expertise area..."
                      disabled={expertiseAreas.length >= 10}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      onClick={addExpertise}
                      disabled={!newExpertise.trim() || expertiseAreas.length >= 10}
                    >
                      Add
                    </Button>
                  </div>
                  {errors.expertiseAreas && (
                    <p className="text-sm text-red-400 mt-1">{errors.expertiseAreas}</p>
                  )}
                </div>

                {/* Speaking Topics */}
                <div>
                  <label className="block text-sm text-zinc-400 mb-2">
                    Speaking Topics
                    <span className="ml-2 text-zinc-500">({speakingTopics.length}/10)</span>
                  </label>
                  <div className="flex flex-wrap gap-2 mb-2">
                    {speakingTopics.map((topic, index) => (
                      <span
                        key={index}
                        className="inline-flex items-center gap-1 px-3 py-1 bg-green-900/30 text-green-300 rounded-full text-sm"
                      >
                        {topic}
                        <button
                          type="button"
                          onClick={() => removeTopic(index)}
                          className="hover:text-red-400 ml-1"
                          aria-label={`Remove ${topic}`}
                        >
                          &times;
                        </button>
                      </span>
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      className="flex-1 bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 min-h-[44px]"
                      value={newTopic}
                      onChange={(e) => setNewTopic(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addTopic())}
                      placeholder="Add speaking topic..."
                      disabled={speakingTopics.length >= 10}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      onClick={addTopic}
                      disabled={!newTopic.trim() || speakingTopics.length >= 10}
                    >
                      Add
                    </Button>
                  </div>
                  {errors.speakingTopics && (
                    <p className="text-sm text-red-400 mt-1">{errors.speakingTopics}</p>
                  )}
                </div>
              </div>
            </Card>

            {/* Languages Card */}
            <Card className="p-6 mb-6">
              <div className="flex items-center gap-2 mb-4">
                <Languages className="h-5 w-5 text-zinc-400" />
                <h2 className="text-lg font-light text-zinc-100">Languages</h2>
              </div>

              <div className="flex flex-wrap gap-2">
                {AVAILABLE_LANGUAGES.map((lang) => (
                  <button
                    key={lang.code}
                    type="button"
                    onClick={() => toggleLanguage(lang.code)}
                    className={`px-4 py-2 rounded-lg border transition-colors ${
                      languages.includes(lang.code)
                        ? 'bg-blue-600 border-blue-600 text-white'
                        : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:border-zinc-600'
                    }`}
                  >
                    {lang.name}
                  </button>
                ))}
              </div>
            </Card>

            {/* LinkedIn Card */}
            <Card className="p-6 mb-6">
              <div className="flex items-center gap-2 mb-4">
                <Globe className="h-5 w-5 text-zinc-400" />
                <h2 className="text-lg font-light text-zinc-100">Social Links</h2>
              </div>

              <div>
                <label htmlFor="linkedIn" className="block text-sm text-zinc-400 mb-2">
                  LinkedIn URL
                </label>
                <input
                  id="linkedIn"
                  type="url"
                  className={`w-full bg-zinc-800 border rounded-lg px-4 py-2 text-zinc-100 min-h-[44px] ${
                    errors.linkedInUrl ? 'border-red-500' : 'border-zinc-700'
                  }`}
                  value={linkedInUrl}
                  onChange={(e) => {
                    setLinkedInUrl(e.target.value);
                    markDirty();
                  }}
                  placeholder="https://linkedin.com/in/yourprofile"
                />
                {errors.linkedInUrl && (
                  <p className="text-sm text-red-400 mt-1">{errors.linkedInUrl}</p>
                )}
              </div>
            </Card>

            {/* Save Button */}
            <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
              <Button asChild variant="outline">
                <Link to="/">
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  Back to Home
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
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4 mr-2" />
                    Save Changes
                  </>
                )}
              </Button>
            </div>

            {/* Success/Error Messages */}
            {updateMutation.isSuccess && (
              <div className="mt-4 p-4 bg-green-900/20 border border-green-800 rounded-lg text-center">
                <p className="text-green-400">Profile updated successfully!</p>
              </div>
            )}

            {updateMutation.error && (
              <div className="mt-4 p-4 bg-red-900/20 border border-red-800 rounded-lg text-center">
                <p className="text-red-400">
                  {(updateMutation.error as Error).message || 'Failed to save profile'}
                </p>
              </div>
            )}
          </>
        )}
      </div>
    </PublicLayout>
  );
};

export default ProfileUpdatePage;
