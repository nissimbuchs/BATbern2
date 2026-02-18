import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  ToggleButton,
  ToggleButtonGroup,
  IconButton,
  Alert,
  Button,
  Paper,
  Typography,
} from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import {
  Computer as DesktopIcon,
  PhoneIphone as MobileIcon,
  Print as PrintIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { usePublishing } from '@/hooks/usePublishing/usePublishing';
import { PublishingPhase, PublishingMode } from '@/types/event.types';

type DeviceType = 'desktop' | 'mobile' | 'print';

interface DeviceDimensions {
  width: string;
  height: string;
}

export interface LivePreviewProps {
  eventCode: string;
  phase: PublishingPhase;
  mode: PublishingMode;
}

export const LivePreview: React.FC<LivePreviewProps> = ({ eventCode, phase, mode }) => {
  const { preview, fetchPreview, isLoadingPreview, previewError } = usePublishing(eventCode);
  const [device, setDevice] = useState<DeviceType>('desktop');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [deviceAnnouncement, setDeviceAnnouncement] = useState('');
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const prevPreviewRef = useRef(preview);

  const deviceDimensions: Record<DeviceType, DeviceDimensions> = {
    desktop: { width: '100%', height: '800px' },
    mobile: { width: '375px', height: '667px' },
    print: { width: '210mm', height: '297mm' },
  };

  // Auto-refresh when preview content changes
  useEffect(() => {
    if (prevPreviewRef.current && preview && prevPreviewRef.current !== preview) {
      // Content changed, reload iframe
      if (iframeRef.current) {
        iframeRef.current.src = preview.previewUrl;
      }
    }
    prevPreviewRef.current = preview;
  }, [preview]);

  // Fetch preview on mount or when phase/mode changes
  // DISABLED: Causes infinite loop because fetchPreview is not memoized
  // Users can manually refresh using the refresh button
  // useEffect(() => {
  //   fetchPreview?.(phase, mode);
  // }, [phase, mode, fetchPreview]);

  const handleDeviceChange = (_: React.MouseEvent<HTMLElement>, newDevice: DeviceType | null) => {
    if (newDevice !== null) {
      setDevice(newDevice);
      setDeviceAnnouncement(`Switched to ${newDevice} preview`);
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

  const handleRetry = () => {
    fetchPreview?.(phase, mode);
  };

  const getPreviewUrl = () => {
    if (preview?.previewUrl) {
      return preview.previewUrl;
    }
    // Generate preview URL - use actual public event page with preview mode
    // This allows previewing how the event will look on the public site
    const baseUrl = `/events/${eventCode}`;
    const params = new URLSearchParams({ preview: 'true', mode: mode || 'progressive' });
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
          aria-label="device preview mode"
        >
          <ToggleButton
            value="desktop"
            aria-label="Desktop preview"
            aria-pressed={device === 'desktop'}
          >
            <DesktopIcon sx={{ mr: 1 }} />
            Desktop
          </ToggleButton>
          <ToggleButton
            value="mobile"
            aria-label="Mobile preview"
            aria-pressed={device === 'mobile'}
          >
            <MobileIcon sx={{ mr: 1 }} />
            Mobile
          </ToggleButton>
          <ToggleButton value="print" aria-label="Print preview" aria-pressed={device === 'print'}>
            <PrintIcon sx={{ mr: 1 }} />
            Print
          </ToggleButton>
        </ToggleButtonGroup>

        {/* Refresh Button */}
        <IconButton
          onClick={handleRefresh}
          disabled={isRefreshing || isLoadingPreview}
          aria-label="Refresh preview"
          title="Refresh preview"
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
        {(isLoadingPreview || isRefreshing) && (
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
            <BATbernLoader size={48} data-testid="preview-loading-spinner" />
            <Typography variant="body2" color="text.secondary">
              Refreshing preview...
            </Typography>
          </Box>
        )}

        {/* Error State */}
        {previewError && !isLoadingPreview && (
          <Box
            sx={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              width: '80%',
              maxWidth: '500px',
            }}
          >
            <Alert
              severity="error"
              action={
                <Button color="inherit" size="small" onClick={handleRetry}>
                  Retry
                </Button>
              }
            >
              Failed to load preview. Please try again.
            </Alert>
          </Box>
        )}

        {/* Preview Iframe */}
        {!previewError && !isLoadingPreview && (
          <iframe
            ref={iframeRef}
            data-testid="preview-iframe"
            src={getPreviewUrl()}
            title="Event preview"
            aria-label="Event content preview"
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
        )}
      </Box>

      {/* Preview Info */}
      <Box sx={{ mt: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="caption" color="text.secondary">
          Preview Mode: {mode || 'Progressive'} | Phase: {phase || 'Topic'}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Device: {device.charAt(0).toUpperCase() + device.slice(1)} ({dimensions.width} x{' '}
          {dimensions.height})
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
