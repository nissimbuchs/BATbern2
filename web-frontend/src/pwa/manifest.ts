/**
 * Web App Manifest Configuration
 *
 * Provides PWA manifest configuration for BATbern platform
 * Includes Swiss design colors and icon configurations
 *
 * Used by: Vite PWA plugin (configured in vite.config.ts)
 */

export interface ManifestIcon {
  src: string;
  sizes: string;
  type: string;
  purpose?: string;
}

export interface WebAppManifest {
  name: string;
  short_name: string;
  description: string;
  start_url: string;
  display: string;
  theme_color: string;
  background_color: string;
  icons: ManifestIcon[];
}

/**
 * Get the web app manifest configuration
 * @returns Web app manifest object
 */
export function getManifest(): WebAppManifest {
  return {
    name: 'BATbern Platform',
    short_name: 'BATbern',
    description: 'BATbern Event Management Platform - Business Accelerator for Tomorrow',
    start_url: '/',
    display: 'standalone',
    theme_color: '#D52B1E', // Swiss red
    background_color: '#FFFFFF', // White
    icons: [
      {
        src: '/icons/icon-192x192.png',
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-512x512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-512x512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
  };
}
