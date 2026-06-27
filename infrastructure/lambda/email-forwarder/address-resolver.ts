/**
 * Address Resolver (Story 10.26 — AC4, AC5)
 *
 * Resolves forwarding recipients by calling existing APIs via API Gateway.
 *
 * Story 10.32 — role-based resolution (`fetchUsersByRole`) now flattens each
 * user's primary `email` PLUS every entry in `additionalEmails[*].email`, so
 * `ok@`, `info@`, `events@`, `partner@` and the role-based portion of
 * `support@` deliver a copy to every additional address an organizer / partner
 * has declared on their profile. Backwards-compatible: missing
 * `additionalEmails` collapses to the primary-only behaviour.
 *
 * Event participant aliases (PR #788): `batbern{N}-participants@` is the canonical
 * "email every active registrant" alias (matches `-speaker@` / `-moderator@`). The older
 * bare `batbern{N}@` is DEPRECATED but kept as a back-compat forward — it now resolves via
 * the SAME participants distribution-list so the two can never diverge, and logs a
 * deprecation warning. Both go through `fetchEventDistributionList(eventCode, 'participants')`,
 * which (server-side) includes attended registrants and fans out to verified additionalEmails.
 */

const API_GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8000';
const FORWARDING_DOMAIN = process.env.FORWARDING_DOMAIN ?? 'batbern.ch';

interface AdditionalEmail {
  email?: string;
}

interface UserResponse {
  email: string;
  additionalEmails?: AdditionalEmail[];
}

interface PaginatedUsersResponse {
  data: UserResponse[];
  pagination?: {
    totalPages: number;
    page: number;
  };
}

interface AdminSettingResponse {
  key: string;
  value: string | null;
}

interface DistributionListResponse {
  eventCode?: string;
  kind?: string;
  emails: string[];
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

  // batbern{N}-speaker@ → event PRIMARY_SPEAKERs (must precede the bare batbern{N}@ branch
  // so the longer alias doesn't fall through to registrant resolution).
  const speakerMatch = localPart.match(/^batbern(\d+)-speaker$/);
  if (speakerMatch) {
    return fetchEventDistributionList(`BATbern${speakerMatch[1]}`, 'speakers');
  }

  // batbern{N}-moderator@ → event organizer
  const moderatorMatch = localPart.match(/^batbern(\d+)-moderator$/);
  if (moderatorMatch) {
    return fetchEventDistributionList(`BATbern${moderatorMatch[1]}`, 'moderator');
  }

  // batbern{N}-participants@ → all active registrants of the event ("Way 2": email every
  // participant directly). Must precede the bare batbern{N}@ branch so the longer alias is not
  // shadowed. Resolves the same audience as Communications → Event registrants ("Way 1").
  const participantsMatch = localPart.match(/^batbern(\d+)-participants$/);
  if (participantsMatch) {
    return fetchEventDistributionList(`BATbern${participantsMatch[1]}`, 'participants');
  }

  // batbern{N}@ → DEPRECATED alias for the event's participants. Superseded by the clearer,
  // self-documenting batbern{N}-participants@ (matches the -speaker@ / -moderator@ family).
  // Consolidated to resolve via the SAME participants distribution-list as -participants@ (active
  // registrants incl. attended, + additionalEmails fallback) so the two never diverge. Kept as a
  // back-compat forward for existing bookmarks/automation; logs a deprecation warning so we can
  // see when it's safe to retire.
  const eventMatch = localPart.match(/^batbern(\d+)$/);
  if (eventMatch) {
    console.warn(
      `Deprecated alias batbern${eventMatch[1]}@ used — forward to batbern${eventMatch[1]}-participants@ instead`,
    );
    return fetchEventDistributionList(`BATbern${eventMatch[1]}`, 'participants');
  }

  // Unknown address
  console.warn(`Unknown forwarding address: ${toAddress}`);
  return [];
}

/**
 * Fetch all users with a specific role. Story 10.32 flattens primary +
 * `additionalEmails[*].email` and deduplicates case-insensitively before
 * returning so the caller sees the full recipient set in a single list.
 */
async function fetchUsersByRole(role: string): Promise<string[]> {
  const seen = new Set<string>();
  const emails: string[] = [];
  let page = 0;
  let totalPages = 1;

  while (page < totalPages) {
    // ADR-013 §3: role is expressed via the JSON `filter` vocabulary, not an ad-hoc ?role= param.
    const filter = encodeURIComponent(JSON.stringify({ role }));
    const url = `${API_GATEWAY_URL}/api/v1/users?filter=${filter}&page=${page}&limit=100`;
    const response = await fetch(url);

    if (!response.ok) {
      console.error(`Failed to fetch users by role ${role}: ${response.status}`);
      return emails;
    }

    const data = (await response.json()) as PaginatedUsersResponse;
    for (const user of data.data) {
      const candidates: string[] = [];
      if (user.email) {
        candidates.push(user.email);
      }
      for (const extra of user.additionalEmails ?? []) {
        if (extra.email) {
          candidates.push(extra.email);
        }
      }
      for (const c of candidates) {
        const key = c.toLowerCase();
        if (!seen.has(key)) {
          seen.add(key);
          // P3-7 (review 2026-05-22): push the lowercased key, not the original
          // first-seen casing. SES is case-insensitive on recipients, downstream
          // MIME headers are nicer with consistent casing, and this defends
          // against any (rare) CUMS data drift where the same address differs in
          // case across users — keeping the lookup map and outgoing recipients
          // canonically lowercased keeps both halves in sync.
          emails.push(key);
        }
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

/**
 * Fetch the per-event distribution list via the event-management-service endpoint:
 * `speakers` = scheduled PRIMARY_SPEAKERs, `moderator` = event organizer,
 * `participants` = all active registrants. Used by the `batbern{N}-speaker@`,
 * `batbern{N}-moderator@` and `batbern{N}-participants@` aliases.
 * On 404 logs a WARN and returns []; on any other non-OK status logs ERROR and returns [].
 */
async function fetchEventDistributionList(
  eventCode: string,
  kind: 'speakers' | 'moderator' | 'participants',
): Promise<string[]> {
  const url = `${API_GATEWAY_URL}/api/v1/events/${eventCode}/distribution-list/${kind}`;
  const response = await fetch(url);

  if (!response.ok) {
    if (response.status === 404) {
      console.warn(`Event not found: ${eventCode}`);
    } else {
      console.error(
        `Failed to fetch ${kind} distribution list for ${eventCode}: ${response.status}`,
      );
    }
    return [];
  }

  const data = (await response.json()) as DistributionListResponse;
  return data.emails ?? [];
}
