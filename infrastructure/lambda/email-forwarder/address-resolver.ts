/**
 * Address Resolver (Story 10.26 — AC4, AC5)
 *
 * Resolves forwarding recipients by calling existing APIs via API Gateway.
 */

const API_GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8000';
const FORWARDING_DOMAIN = process.env.FORWARDING_DOMAIN ?? 'batbern.ch';

interface UserResponse {
  email: string;
}

interface PaginatedUsersResponse {
  data: UserResponse[];
  pagination?: {
    totalPages: number;
    page: number;
  };
}

interface RegistrationResponse {
  attendeeEmail: string;
}

interface PaginatedRegistrationsResponse {
  data: RegistrationResponse[];
  pagination?: {
    totalPages: number;
    page: number;
  };
}

interface AdminSettingResponse {
  key: string;
  value: string | null;
}

/**
 * Resolve recipients for a given forwarding address.
 * Returns a list of email addresses to forward to.
 */
export async function resolveRecipients(toAddress: string): Promise<string[]> {
  const localPart = toAddress.split('@')[0]?.toLowerCase();

  if (!localPart) {
    return [];
  }

  // ok@, info@, events@ → all organizers
  if (localPart === 'ok' || localPart === 'info' || localPart === 'events') {
    return fetchUsersByRole('ORGANIZER');
  }

  // partner@ → all partners
  if (localPart === 'partner') {
    return fetchUsersByRole('PARTNER');
  }

  // support@ → configured support contacts, fallback to organizers
  if (localPart === 'support') {
    return fetchSupportContacts();
  }

  // batbern{N}@ → event registrants
  const eventMatch = localPart.match(/^batbern(\d+)$/);
  if (eventMatch) {
    const eventNumber = eventMatch[1];
    return fetchEventRegistrants(`BATbern${eventNumber}`);
  }

  // Unknown address
  console.warn(`Unknown forwarding address: ${toAddress}`);
  return [];
}

/** Fetch all users with a specific role. */
async function fetchUsersByRole(role: string): Promise<string[]> {
  const emails: string[] = [];
  let page = 0;
  let totalPages = 1;

  while (page < totalPages) {
    const url = `${API_GATEWAY_URL}/api/v1/users?role=${role}&page=${page}&limit=100`;
    const response = await fetch(url);

    if (!response.ok) {
      console.error(`Failed to fetch users by role ${role}: ${response.status}`);
      return emails;
    }

    const data = (await response.json()) as PaginatedUsersResponse;
    for (const user of data.data) {
      if (user.email) {
        emails.push(user.email);
      }
    }

    totalPages = data.pagination?.totalPages ?? 1;
    page++;
  }

  return emails;
}

/** Fetch support contacts from admin settings, fallback to organizer list. */
async function fetchSupportContacts(): Promise<string[]> {
  try {
    const url = `${API_GATEWAY_URL}/api/v1/admin/settings/email-forwarding.support-contacts`;
    const response = await fetch(url);

    if (response.ok) {
      const data = (await response.json()) as AdminSettingResponse;
      if (data.value && data.value.trim().length > 0) {
        return data.value
          .split(',')
          .map((e) => e.trim())
          .filter((e) => e.length > 0);
      }
    }
  } catch (err) {
    console.error('Failed to fetch support contacts', err);
  }

  // Fallback: forward to organizers
  return fetchUsersByRole('ORGANIZER');
}

/** Fetch all registered attendees for an event. */
async function fetchEventRegistrants(eventCode: string): Promise<string[]> {
  const emails: string[] = [];
  let page = 0;
  let totalPages = 1;

  while (page < totalPages) {
    const url =
      `${API_GATEWAY_URL}/api/v1/events/${eventCode}/registrations`
      + `?status=registered&status=confirmed&limit=500&page=${page}`;
    const response = await fetch(url);

    if (!response.ok) {
      if (response.status === 404) {
        console.warn(`Event not found: ${eventCode}`);
      } else {
        console.error(`Failed to fetch registrations for ${eventCode}: ${response.status}`);
      }
      return emails;
    }

    const data = (await response.json()) as PaginatedRegistrationsResponse;
    for (const reg of data.data) {
      if (reg.attendeeEmail) {
        emails.push(reg.attendeeEmail);
      }
    }

    totalPages = data.pagination?.totalPages ?? 1;
    page++;
  }

  return emails;
}
