// Fixture: calls that drift from the spec.
import axios from 'axios';
import apiClient from './apiClient';

const EVENTS_API_PATH = '/events';

export const getPublishPreview = (eventCode: string, phase: string) =>
  apiClient.get(`${EVENTS_API_PATH}/${eventCode}/publish/${phase}/preview`);

export const cancelSchedule = (eventCode: string) =>
  apiClient.delete(`${EVENTS_API_PATH}/${eventCode}/publish/schedule`);

export const unknownQuery = () => apiClient.get('/events/BATbern1/registrations?foo=1');

export const tooMany = () => apiClient.get('/events/BATbern1/registrations?limit=1000');

export const unresolvable = (endpoint: string) => apiClient.post(endpoint, {});

export const bareAxiosComputed = (target: string) => axios.get(target);

export const gluedSuffix = (suffix: string) => apiClient.get(`${EVENTS_API_PATH}${suffix}`);
