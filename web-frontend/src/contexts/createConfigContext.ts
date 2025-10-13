import { createContext } from 'react';
import type { AppConfig } from '../config/runtime-config';

/**
 * Configuration Context
 *
 * Provides runtime configuration to all components in the app.
 * Configuration is loaded once at app startup and made available
 * throughout the component tree.
 */
export const ConfigContext = createContext<AppConfig | null>(null);
