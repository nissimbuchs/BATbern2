import { eventBatchRegistrationHandlers } from './eventBatchRegistrationHandlers';

// Combine all MSW handlers
export const handlers = [...eventBatchRegistrationHandlers];
