/**
 * Candidate duplicate companies and the company merge (#1251), for organizers.
 *
 * Errors are the raw axios errors, so a screen can read the `errorCode` of the problem body
 * (`apiErrorCode`): MERGE_NOT_ALLOWED, MERGE_BLOCKED_BOTH_PARTNERS, MERGE_BLOCKED,
 * MERGE_INCOMPLETE, MERGE_PREVIEW_INCOMPLETE, DUPLICATE_COUNTS_INCOMPLETE.
 */
import { isAxiosError } from 'axios';
import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/company-api.types';

export type CompanyDuplicateCandidate = components['schemas']['CompanyDuplicateCandidate'];
export type CompanyMergePreview = components['schemas']['CompanyMergePreview'];
export type CompanyMergeResult = components['schemas']['CompanyMergeResult'];

const COMPANY_API_PATH = '/companies';

/** Pairs of companies that are probably one, computed on the live data. */
export const listCompanyDuplicateCandidates = async (): Promise<CompanyDuplicateCandidate[]> => {
  const response = await apiClient.get<CompanyDuplicateCandidate[]>(
    `${COMPANY_API_PATH}/duplicate-candidates`
  );
  return response.data;
};

/** Marks the pair as "not a duplicate"; it no longer appears in the list. */
export const dismissCompanyDuplicateCandidate = async (
  nameA: string,
  nameB: string
): Promise<void> => {
  await apiClient.post(`${COMPANY_API_PATH}/duplicate-candidates/dismissals`, { nameA, nameB });
};

/** What merging `source` into `target` would move, fill and why it may be refused. */
export const getCompanyMergePreview = async (
  target: string,
  source: string
): Promise<CompanyMergePreview> => {
  const response = await apiClient.get<CompanyMergePreview>(
    `${COMPANY_API_PATH}/${encodeURIComponent(target)}/merge-preview`,
    { params: { source } }
  );
  return response.data;
};

/** Merges `source` into `target` and deletes `source`. */
export const mergeCompany = async (target: string, source: string): Promise<CompanyMergeResult> => {
  const response = await apiClient.post<CompanyMergeResult>(
    `${COMPANY_API_PATH}/${encodeURIComponent(target)}/merge`,
    { sourceName: source }
  );
  return response.data;
};

/** The `errorCode` of an API problem answer, if the error is one. */
export const apiErrorCode = (error: unknown): string | undefined => {
  if (isAxiosError(error)) {
    const code = (error.response?.data as { errorCode?: unknown } | undefined)?.errorCode;
    return typeof code === 'string' ? code : undefined;
  }
  return undefined;
};
