/**
 * Deregistration Service (Story 10.12)
 *
 * HTTP client for self-service deregistration APIs.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-registrations-api.types';

export type DeregistrationVerifyResponse = components['schemas']['DeregistrationVerifyResponse'];
export type DeregistrationRequest = components['schemas']['DeregistrationRequest'];
export type DeregistrationByEmailRequest = components['schemas']['DeregistrationByEmailRequest'];

/** Verify a deregistration token — returns registration details if valid. Throws 404 if invalid or already cancelled. */
export async function verifyDeregistrationToken(
  token: string
): Promise<DeregistrationVerifyResponse> {
  const response = await apiClient.get<DeregistrationVerifyResponse>(
    `/registrations/deregister/verify?token=${encodeURIComponent(token)}`
  );
  return response.data;
}

/** Cancel registration using deregistration token. Throws 404 if invalid, 409 if already cancelled. */
export async function deregisterByToken(token: string): Promise<void> {
  await apiClient.post('/registrations/deregister', { token } as DeregistrationRequest);
}

/** Request deregistration link via email (always returns 200 — anti-enumeration). */
export async function deregisterByEmail(email: string, eventCode: string): Promise<void> {
  await apiClient.post('/registrations/deregister/by-email', {
    email,
    eventCode,
  } as DeregistrationByEmailRequest);
}
