/**
 * Watch pairing types
 * Story W2.1: Pairing Code Backend & Web Frontend
 */

export interface PairingCodeResponse {
  pairingCode: string;
  expiresAt: string; // ISO 8601
  hoursUntilExpiry: number;
}

export interface PairingStatusResponse {
  pairedWatches: PairedWatch[];
  pendingCode: PendingPairingCode | null;
}

export interface PairedWatch {
  id: string; // UUID — stable identifier for unpair (deviceName is nullable)
  deviceName: string | null;
  pairedAt: string; // ISO 8601
}

export interface PendingPairingCode {
  code: string;
  expiresAt: string; // ISO 8601
}
