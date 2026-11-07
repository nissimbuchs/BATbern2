// Fixture: calls the check must resolve and match (Story 17.3 scan tests).
import axios from 'axios';
import apiClient from './apiClient';

const EVENTS_API_PATH = '/events';
const registrations = (eventCode: string) => `${EVENTS_API_PATH}/${eventCode}/registrations`;

function schedulePath(eventCode: string): string {
  const code = eventCode || '_global';
  return `/events/${code}/publish/schedule`;
}

class EventService {
  private readonly basePath = '/users';

  async list(eventCode: string) {
    // apiClient.get('/commented/out') must not count
    /* apiClient.post('/also/commented') */
    return apiClient.get<Array<{ id: string }>>(registrations(eventCode), { params: { x: 1 } });
  }

  async me() {
    return apiClient.get(`${this.basePath}/me`);
  }

  async schedule(eventCode: string) {
    return apiClient.get(schedulePath(eventCode));
  }

  async current(full: boolean) {
    const url = full ? `${EVENTS_API_PATH}/current` : `${EVENTS_API_PATH}/BATbern1/`;
    return apiClient.get(url);
  }

  async upload(uploadUrl: string, file: Blob) {
    await axios.put(uploadUrl, file);
    await axios.get('https://example.com/absolute');
  }
}

export const eventService = new EventService();
