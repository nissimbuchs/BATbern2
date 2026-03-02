/**
 * Deregistration Service (Story 10.12)
 *
 * HTTP client for self-service deregistration APIs.
 */

import apiClient from '@/services/api/apiClient';

export interface DeregistrationVerifyResponse {
  registrationCode: string;
  eventCode: string;
  eventTitle: string;
  eventDate: string;
  attendeeFirstName: string;
}

export interface DeregistrationRequest {
  token: string;
}

export interface DeregistrationByEmailRequest {
  email: string;
  eventCode: string;
}

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
