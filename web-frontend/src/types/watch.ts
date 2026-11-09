/**
 * Watch pairing types
 * Story W2.1: Pairing Code Backend & Web Frontend
 */

import type { components } from '@/types/generated/user-api.types';

/** `expiresAt` ISO 8601 with zone. */
export type PairingCodeResponse = components['schemas']['PairingCodeResponse'];

export type PairingStatusResponse = components['schemas']['PairingStatusResponse'];

/** `id` is the stable identifier for unpair (`deviceName` is nullable). */
export type PairedWatch = components['schemas']['PairedWatch'];

export type PendingPairingCode = components['schemas']['PendingPairingCode'];
