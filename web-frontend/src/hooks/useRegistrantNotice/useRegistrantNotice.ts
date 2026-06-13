/**
 * useRegistrantNotice hooks (Story 7.3 hardening)
 *
 * React Query mutations for previewing + sending a registrant-targeted notice (e.g. the
 * "slides are online" mail) to an event's active registrants.
 */

import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import * as registrantNoticeService from '@/services/registrantNoticeService';
import type {
  RegistrantNoticePreviewRequest,
  RegistrantNoticePreviewResponse,
  RegistrantNoticeSendResponse,
} from '@/services/registrantNoticeService';

/** Preview a registrant-notice template in a chosen language. */
export function usePreviewRegistrantNotice(
  eventCode: string
): UseMutationResult<RegistrantNoticePreviewResponse, Error, RegistrantNoticePreviewRequest> {
  return useMutation({
    mutationFn: (request: RegistrantNoticePreviewRequest) =>
      registrantNoticeService.preview(eventCode, request),
  });
}

/** Send a registrant-notice mail to the event's active registrants. */
export function useSendRegistrantNotice(
  eventCode: string
): UseMutationResult<RegistrantNoticeSendResponse, Error, string> {
  return useMutation({
    mutationFn: (templateKey: string) => registrantNoticeService.send(eventCode, templateKey),
  });
}
