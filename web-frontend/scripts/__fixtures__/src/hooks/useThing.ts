// Fixture: an HTTP call outside the service layer.
import apiClient from '../services/apiClient';

export const useThing = () => apiClient.get('/events/current');
