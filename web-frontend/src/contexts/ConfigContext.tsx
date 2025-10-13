import React from 'react';
import type { AppConfig } from '../config/runtime-config';
import { ConfigContext } from './createConfigContext';

export interface ConfigProviderProps {
  config: AppConfig;
  children: React.ReactNode;
}

/**
 * Configuration Provider Component
 *
 * Wraps the entire app to provide runtime configuration to all components.
 * Must be rendered after configuration is loaded.
 *
 * @param config - Runtime configuration loaded from backend
 * @param children - App components
 */
export function ConfigProvider({ config, children }: ConfigProviderProps) {
  return <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>;
}
