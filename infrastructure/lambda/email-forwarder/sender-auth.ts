/**
 * Sender Authorization (Story 10.26 — AC6, AC7)
 *
 * Checks whether the sender is authorized to use a specific forwarding address.
 * Organizer email list is cached for 5 minutes (per Lambda instance).
 */

const API_GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8000';
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

interface CacheEntry {
  emails: string[];
  timestamp: number;
}

let organizerCache: CacheEntry | null = null;

// Addresses that anyone can send to (public contact addresses)
const PUBLIC_ADDRESSES = new Set(['info', 'events', 'support']);

/**
 * Check if the sender is authorized to use the given forwarding address.
 *
 * Authorization rules:
 *   ok@, partner@, batbern{N}@ → organizers only
 *   info@, events@, support@   → anyone
 */
export async function isAuthorizedSender(
  toAddress: string,
  senderEmail: string,
): Promise<boolean> {
  const localPart = toAddress.split('@')[0]?.toLowerCase();

  if (!localPart) {
    return false;
  }

  // Public addresses: anyone can send
  if (PUBLIC_ADDRESSES.has(localPart)) {
    return true;
  }

  // Restricted addresses: organizers only
  const organizerEmails = await getOrganizerEmails();
  return organizerEmails.includes(senderEmail.toLowerCase());
}

/** Get cached organizer email list (5-minute TTL). Paginates all pages. */
async function getOrganizerEmails(): Promise<string[]> {
  const now = Date.now();

  if (organizerCache && now - organizerCache.timestamp < CACHE_TTL_MS) {
    return organizerCache.emails;
  }

  try {
    interface UserEmailResponse {
      data: Array<{ email: string }>;
      pagination?: { totalPages: number; page: number };
    }

    const emails: string[] = [];
    let page = 0;
    let totalPages = 1;

    while (page < totalPages) {
      const url = `${API_GATEWAY_URL}/api/v1/users?role=ORGANIZER&limit=100&page=${page}`;
      const response = await fetch(url);

      if (!response.ok) {
        console.error(`Failed to fetch organizer list (page ${page}): ${response.status}`);
        return organizerCache?.emails ?? [];
      }

      const data = (await response.json()) as UserEmailResponse;
      for (const u of data.data) {
        if (u.email) {
          emails.push(u.email.toLowerCase());
        }
      }

      totalPages = data.pagination?.totalPages ?? 1;
      page++;
    }

    organizerCache = { emails, timestamp: now };
    return emails;
  } catch (err) {
    console.error('Failed to fetch organizer emails', err);
    return organizerCache?.emails ?? [];
  }
}

/** Reset cache (for testing). */
export function resetCache(): void {
  organizerCache = null;
}
