/**
 * useSpeakerBatchImport Hook
 *
 * Handles batch import of speakers from legacy JSON format as SPEAKER role users.
 * Uses sequential API calls (one per speaker) with status tracking and duplicate detection.
 * Supports portrait upload via server-side URL fetch (same as company logos).
 */

import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { createUser, updateUser, UpdateUserData } from '@/services/api/userManagementApi';
import apiClient from '@/services/api/apiClient';
import type { SpeakerImportCandidate, SpeakerBatchImportResult } from '@/types/speakerImport.types';
import type { CreateUserFormData } from '@/types/user.types';

/**
 * Build update payload from changed fields
 * Only includes fields that actually changed
 */
function buildUpdatePayload(
  apiPayload: CreateUserFormData,
  changedFields: string[]
): UpdateUserData {
  const payload: UpdateUserData = {};

  if (changedFields.includes('firstName')) {
    payload.firstName = apiPayload.firstName;
  }
  if (changedFields.includes('lastName')) {
    payload.lastName = apiPayload.lastName;
  }
  if (changedFields.includes('bio')) {
    payload.bio = apiPayload.bio || '';
  }
  if (changedFields.includes('companyId')) {
    payload.companyId = apiPayload.companyId || '';
  }

  return payload;
}

/**
 * Upload portrait for a user after they've been created
 * Uses server-side upload endpoint to avoid binary data corruption
 * This endpoint fetches the image and uploads it directly to S3, then associates it with the user
 */
async function uploadPortraitForUser(
  username: string,
  portraitUrl: string,
  speakerName: string
): Promise<void> {
  try {
    const filename = `${speakerName}-portrait`;

    // Use server-side upload endpoint (bypasses frontend entirely)
    // This endpoint fetches the image, uploads to S3, and associates with the user in one call
    const response = await apiClient.post<{ profilePictureUrl: string }>(
      `/users/${encodeURIComponent(username)}/profile-picture/upload-from-url`,
      {
        url: portraitUrl,
        filename,
      }
    );

    console.log(
      `[BatchImport] Successfully uploaded portrait for ${speakerName}: ${response.data.profilePictureUrl}`
    );
  } catch (error) {
    // Log but don't fail the import - portrait is optional
    console.error(`[BatchImport] Failed to upload portrait for ${speakerName}:`, error);
  }
}

interface UseSpeakerBatchImportOptions {
  onProgress?: (current: number, total: number) => void;
  onComplete?: (result: SpeakerBatchImportResult) => void;
}

interface UseSpeakerBatchImportReturn {
  /** Start importing the candidates */
  importSpeakers: (candidates: SpeakerImportCandidate[]) => Promise<SpeakerBatchImportResult>;
  /** Whether an import is in progress */
  isImporting: boolean;
  /** Current progress (index of speaker being imported) */
  currentIndex: number;
  /** Total number of speakers to import */
  totalCount: number;
  /** Updated candidates with status */
  candidates: SpeakerImportCandidate[];
  /** Reset the hook state */
  reset: () => void;
}

export function useSpeakerBatchImport(
  options: UseSpeakerBatchImportOptions = {}
): UseSpeakerBatchImportReturn {
  const { onProgress, onComplete } = options;
  const queryClient = useQueryClient();

  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [candidates, setCandidates] = useState<SpeakerImportCandidate[]>([]);

  const reset = useCallback(() => {
    setIsImporting(false);
    setCurrentIndex(0);
    setTotalCount(0);
    setCandidates([]);
  }, []);

  const updateCandidate = useCallback((index: number, updates: Partial<SpeakerImportCandidate>) => {
    setCandidates((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], ...updates };
      return updated;
    });
  }, []);

  const importSpeakers = useCallback(
    async (importCandidates: SpeakerImportCandidate[]): Promise<SpeakerBatchImportResult> => {
      setIsImporting(true);
      setCandidates(importCandidates);
      setTotalCount(importCandidates.length);
      setCurrentIndex(0);

      const result: SpeakerBatchImportResult = {
        total: importCandidates.length,
        success: 0,
        updated: 0,
        failed: 0,
        skipped: 0,
      };

      // Process speakers sequentially
      for (let i = 0; i < importCandidates.length; i++) {
        const candidate = importCandidates[i];
        setCurrentIndex(i);
        onProgress?.(i + 1, importCandidates.length);

        // Update status to importing
        updateCandidate(i, { importStatus: 'importing' });

        try {
          const speakerName = `${candidate.apiPayload.firstName} ${candidate.apiPayload.lastName}`;

          // Check if speaker already exists (pre-marked during preview)
          if (candidate.existingUser) {
            // No changes needed - skip
            if (!candidate.hasChanges) {
              updateCandidate(i, {
                importStatus: 'skipped',
                errorMessage: 'No changes',
              });
              result.skipped++;
              continue;
            }

            // Has changes - process updates
            const updatedFields: string[] = [];
            const changedFields = candidate.changedFields || [];

            // Update profile fields (firstName, lastName, bio, companyId)
            const profileFields = changedFields.filter((f) => f !== 'portrait');
            if (profileFields.length > 0) {
              console.log(
                `[BatchImport] Updating profile for ${speakerName}: ${profileFields.join(', ')}`
              );
              try {
                const updatePayload = buildUpdatePayload(candidate.apiPayload, profileFields);
                await updateUser(candidate.existingUser.username, updatePayload);
                updatedFields.push(...profileFields);
              } catch (updateError) {
                console.error(
                  `[BatchImport] Profile update failed for ${speakerName}:`,
                  updateError
                );
              }
            }

            // Upload portrait if it's one of the changed fields
            if (changedFields.includes('portrait') && candidate.portraitUrl) {
              console.log(`[BatchImport] Uploading portrait for existing user ${speakerName}`);
              try {
                await uploadPortraitForUser(
                  candidate.existingUser.username,
                  candidate.portraitUrl,
                  speakerName
                );
                updatedFields.push('portrait');
              } catch (portraitError) {
                console.error(
                  `[BatchImport] Portrait upload failed for ${speakerName}:`,
                  portraitError
                );
              }
            }

            if (updatedFields.length > 0) {
              updateCandidate(i, {
                importStatus: 'updated',
                errorMessage: `Updated: ${updatedFields.join(', ')}`,
              });
              result.updated++;
            } else {
              updateCandidate(i, {
                importStatus: 'skipped',
                errorMessage: 'Update failed',
              });
              result.skipped++;
            }
            continue;
          }

          // Create user via API
          console.log(`[BatchImport] Creating speaker user: ${speakerName}`);
          const createdUser = await createUser(candidate.apiPayload);

          // Upload portrait if available (after user creation)
          if (candidate.portraitUrl) {
            console.log(
              `[BatchImport] Uploading portrait for ${speakerName} from ${candidate.portraitUrl}`
            );
            try {
              await uploadPortraitForUser(createdUser.id, candidate.portraitUrl, speakerName);
            } catch (portraitError) {
              // Log but continue - portrait is optional
              console.error(
                `[BatchImport] Portrait upload failed for ${speakerName}:`,
                portraitError
              );
            }
          }

          // Success - created
          updateCandidate(i, { importStatus: 'success' });
          result.success++;
        } catch (error) {
          // Check if it's a conflict (user already exists)
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';
          const isConflict =
            errorMessage.includes('409') ||
            errorMessage.includes('Conflict') ||
            errorMessage.toLowerCase().includes('already exists') ||
            errorMessage.toLowerCase().includes('duplicate');

          if (isConflict) {
            updateCandidate(i, {
              importStatus: 'skipped',
              errorMessage: 'User already exists',
            });
            result.skipped++;
          } else {
            updateCandidate(i, {
              importStatus: 'error',
              errorMessage: errorMessage,
            });
            result.failed++;
          }
        }
      }

      // Invalidate users cache to show new users
      await queryClient.invalidateQueries({ queryKey: ['users'] });

      setIsImporting(false);
      onComplete?.(result);

      return result;
    },
    [queryClient, onProgress, onComplete, updateCandidate]
  );

  return {
    importSpeakers,
    isImporting,
    currentIndex,
    totalCount,
    candidates,
    reset,
  };
}
