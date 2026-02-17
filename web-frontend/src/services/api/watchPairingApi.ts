/**
 * Watch Pairing API Client
 * Story W2.1: Pairing Code Backend & Web Frontend
 */

import apiClient from '@/services/api/apiClient';
import type { PairingCodeResponse, PairingStatusResponse } from '@/types/watch';

const watchPairingApi = {
  generatePairingCode: (username: string): Promise<PairingCodeResponse> =>
    apiClient.post<PairingCodeResponse>(`/users/${username}/watch-pairing`).then((r) => r.data),

  getPairingStatus: (username: string): Promise<PairingStatusResponse> =>
    apiClient.get<PairingStatusResponse>(`/users/${username}/watch-pairing`).then((r) => r.data),

  unpairWatch: (username: string, watchId: string): Promise<void> =>
    apiClient.delete(`/users/${username}/watch-pairing/${watchId}`).then(() => undefined),
};

export default watchPairingApi;
