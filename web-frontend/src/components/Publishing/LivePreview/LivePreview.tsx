import React, { useState, useRef } from 'react';
import { Box, ToggleButton, ToggleButtonGroup, IconButton, Paper, Typography } from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import {
  Computer as DesktopIcon,
  PhoneIphone as MobileIcon,
  Print as PrintIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { PublishingPhase } from '@/types/event.types';

type DeviceType = 'desktop' | 'mobile' | 'print';

interface DeviceDimensions {
  width: string;
  height: string;
}

export interface LivePreviewProps {
  eventCode: string;
  phase: PublishingPhase;
}

export const LivePreview: React.FC<LivePreviewProps> = ({ eventCode, phase }) => {
  const { t } = useTranslation('organizer');
  const [device, setDevice] = useState<DeviceType>('desktop');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [deviceAnnouncement, setDeviceAnnouncement] = useState('');
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const deviceDimensions: Record<DeviceType, DeviceDimensions> = {
    desktop: { width: '100%', height: '800px' },
    mobile: { width: '375px', height: '667px' },
    print: { width: '210mm', height: '297mm' },
  };

  const handleDeviceChange = (_: React.MouseEvent<HTMLElement>, newDevice: DeviceType | null) => {
    if (newDevice !== null) {
      setDevice(newDevice);
      setDeviceAnnouncement(t('livePreview.switchedTo', { device: newDevice }));
      // Clear announcement after brief delay
      setTimeout(() => setDeviceAnnouncement(''), 100);
    }
  };

  const handleRefresh = async () => {
    setIsRefreshing(true);
    if (iframeRef.current?.contentWindow && typeof window !== 'undefined') {
      // Reload iframe (only in browser environment)
      try {
        iframeRef.current.contentWindow.location.reload();
      } catch (error) {
        // Ignore errors in test environment
        console.warn('Failed to reload iframe:', error);
      }
    }
    // Simulate refresh delay
    await new Promise((resolve) => setTimeout(resolve, 500));
    setIsRefreshing(false);
  };

  const getPreviewUrl = () => {
    // The public event page in preview mode: shows how the event will look on the public site.
    const baseUrl = `/events/${eventCode}`;
    const params = new URLSearchParams({ preview: 'true' });
    if (phase) {
      params.append('phase', phase);
    }
    return `${baseUrl}?${params.toString()}`;
  };

  const dimensions = deviceDimensions[device];

  return (
    <Paper sx={{ p: 2 }}>
      {/* Screen reader announcement for device changes */}
      {deviceAnnouncement && (
        <div role="status" aria-live="polite" style={{ position: 'absolute', left: '-10000px' }}>
          {deviceAnnouncement}
        </div>
      )}

      {/* Controls */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        {/* Device Toggle */}
        <ToggleButtonGroup
          value={device}
          exclusive
          onChange={handleDeviceChange}
          aria-label={t('livePreview.devicePreviewMode')}
        >
          <ToggleButton
            value="desktop"
            aria-label={t('livePreview.desktopPreview')}
            aria-pressed={device === 'desktop'}
          >
            <DesktopIcon sx={{ mr: { xs: 0, md: 1 } }} />
            <Box component="span" sx={{ display: { xs: 'none', md: 'inline' } }}>
              {t('livePreview.desktop')}
            </Box>
          </ToggleButton>
          <ToggleButton
            value="mobile"
            aria-label={t('livePreview.mobilePreview')}
            aria-pressed={device === 'mobile'}
          >
            <MobileIcon sx={{ mr: { xs: 0, md: 1 } }} />
            <Box component="span" sx={{ display: { xs: 'none', md: 'inline' } }}>
              {t('livePreview.mobile')}
            </Box>
          </ToggleButton>
          <ToggleButton
            value="print"
            aria-label={t('livePreview.printPreview')}
            aria-pressed={device === 'print'}
          >
            <PrintIcon sx={{ mr: { xs: 0, md: 1 } }} />
            <Box component="span" sx={{ display: { xs: 'none', md: 'inline' } }}>
              {t('livePreview.print')}
            </Box>
          </ToggleButton>
        </ToggleButtonGroup>

        {/* Refresh Button */}
        <IconButton
          onClick={handleRefresh}
          disabled={isRefreshing}
          aria-label={t('livePreview.refreshPreview')}
          title={t('livePreview.refreshPreview')}
        >
          <RefreshIcon sx={{ animation: isRefreshing ? 'spin 1s linear infinite' : 'none' }} />
        </IconButton>
      </Box>

      {/* Preview Content */}
      <Box
        sx={{
          position: 'relative',
          display: 'flex',
          justifyContent: 'center',
          minHeight: '400px',
          bgcolor: 'grey.100',
          borderRadius: 1,
          overflow: 'hidden',
        }}
      >
        {/* Loading State */}
        {isRefreshing && (
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'rgba(255, 255, 255, 0.9)',
              zIndex: 2,
              gap: 2,
            }}
          >
            <BATbernLoader size={96} data-testid="preview-loading-spinner" />
            <Typography variant="body2" color="text.secondary">
              {t('livePreview.refreshingPreview')}
            </Typography>
          </Box>
        )}

        {/* Preview Iframe */}
        <iframe
          ref={iframeRef}
          data-testid="preview-iframe"
          src={getPreviewUrl()}
          title={t('livePreview.iframeTitle')}
          aria-label={t('livePreview.iframeAriaLabel')}
          className={device}
          style={{
            width: dimensions.width,
            height: dimensions.height,
            border: '1px solid #ccc',
            borderRadius: '4px',
            display: isRefreshing ? 'none' : 'block',
          }}
          onLoad={() => setIsRefreshing(false)}
        />
      </Box>

      {/* Preview Info */}
      <Box sx={{ mt: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="caption" color="text.secondary">
          {t('livePreview.previewPhaseCaption', {
            phase: phase || 'Topic',
          })}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {t('livePreview.deviceCaption', {
            device: device.charAt(0).toUpperCase() + device.slice(1),
            width: dimensions.width,
            height: dimensions.height,
          })}
        </Typography>
      </Box>

      {/* CSS for refresh animation */}
      <style>
        {`
          @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
        `}
      </style>
    </Paper>
  );
};
