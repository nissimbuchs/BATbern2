/**
 * Dev-only email inbox page.
 * Accessible at http://localhost:8100/dev/emails (local development only).
 *
 * Two-panel layout: left = email list, right = header + HTML preview.
 * Talks directly to EMS /dev/emails (no auth needed, @Profile("local") on server).
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  AppBar,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import EmailIcon from '@mui/icons-material/Email';
import { CapturedEmail, devEmailService } from '@/services/devEmailService';

function formatTime(isoString: string): string {
  return new Date(isoString).toLocaleTimeString('de-CH', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatDateTime(isoString: string): string {
  return new Date(isoString).toLocaleString('de-CH', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export default function DevEmailInboxPage() {
  const [emails, setEmails] = useState<CapturedEmail[]>([]);
  const [selected, setSelected] = useState<CapturedEmail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await devEmailService.fetchAll();
      setEmails(data);
      // Keep selected in sync if it still exists
      if (selected) {
        const updated = data.find((e) => e.id === selected.id);
        setSelected(updated ?? null);
      }
    } catch (e) {
      setError(`Could not reach EMS at localhost:8002 — is the service running? (${e})`);
    } finally {
      setLoading(false);
    }
  }, [selected]);

  useEffect(() => {
    void load();
  }, []);

  const handleClear = async () => {
    try {
      await devEmailService.clearAll();
      setEmails([]);
      setSelected(null);
    } catch (e) {
      setError(`Clear failed: ${e}`);
    }
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        bgcolor: 'background.default',
      }}
    >
      {/* Toolbar */}
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar variant="dense">
          <EmailIcon sx={{ mr: 1, color: 'text.secondary' }} />
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 600 }}>
            Dev Email Inbox
          </Typography>
          <Chip
            label={emails.length}
            size="small"
            color={emails.length > 0 ? 'primary' : 'default'}
            sx={{ mr: 2 }}
          />
          <Tooltip title="Refresh">
            <IconButton onClick={() => void load()} disabled={loading} size="small" sx={{ mr: 1 }}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Clear all emails">
            <Button
              onClick={() => void handleClear()}
              color="error"
              size="small"
              startIcon={<DeleteSweepIcon />}
              disabled={emails.length === 0}
            >
              Clear All
            </Button>
          </Tooltip>
        </Toolbar>
      </AppBar>

      {/* Error banner */}
      {error && (
        <Box sx={{ px: 2, py: 1, bgcolor: 'error.light' }}>
          <Typography variant="caption" color="error.contrastText">
            {error}
          </Typography>
        </Box>
      )}

      {/* Two-panel body */}
      <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left panel — email list */}
        <Box
          sx={{
            width: 350,
            flexShrink: 0,
            borderRight: 1,
            borderColor: 'divider',
            overflowY: 'auto',
            bgcolor: 'background.paper',
          }}
        >
          <List disablePadding>
            {emails.length === 0 && !loading && (
              <Box sx={{ p: 3, textAlign: 'center' }}>
                <EmailIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                <Typography variant="body2" color="text.secondary">
                  No emails captured yet.
                  <br />
                  Trigger a newsletter send or registration to see emails here.
                </Typography>
              </Box>
            )}
            {emails.map((email, index) => (
              <React.Fragment key={email.id}>
                {index > 0 && <Divider component="li" />}
                <ListItemButton
                  selected={selected?.id === email.id}
                  onClick={() => setSelected(email)}
                  sx={{ py: 1.5, px: 2 }}
                >
                  <ListItemText
                    primary={email.subject}
                    secondary={`${email.to} · ${formatTime(email.capturedAt)}`}
                    primaryTypographyProps={{
                      noWrap: true,
                      fontWeight: selected?.id === email.id ? 600 : 400,
                      fontSize: '0.875rem',
                    }}
                    secondaryTypographyProps={{
                      noWrap: true,
                      fontSize: '0.75rem',
                    }}
                  />
                </ListItemButton>
              </React.Fragment>
            ))}
          </List>
        </Box>

        {/* Right panel — email preview */}
        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {selected ? (
            <>
              {/* Email header */}
              <Box
                sx={{
                  p: 2,
                  borderBottom: 1,
                  borderColor: 'divider',
                  bgcolor: 'grey.50',
                  flexShrink: 0,
                }}
              >
                <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, lineHeight: 1.3 }}>
                  {selected.subject}
                </Typography>
                <Box sx={{ display: 'grid', gridTemplateColumns: '60px 1fr', gap: 0.5 }}>
                  <Typography variant="body2" color="text.secondary">
                    To:
                  </Typography>
                  <Typography variant="body2">{selected.to}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    From:
                  </Typography>
                  <Typography variant="body2">
                    {selected.fromName} &lt;{selected.fromEmail}&gt;
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Sent:
                  </Typography>
                  <Typography variant="body2">{formatDateTime(selected.capturedAt)}</Typography>
                </Box>
              </Box>

              {/* HTML preview */}
              <Box sx={{ flex: 1, overflow: 'hidden' }}>
                <iframe
                  srcDoc={selected.htmlBody}
                  style={{ width: '100%', height: '100%', border: 'none' }}
                  title="Email preview"
                  sandbox="allow-same-origin"
                />
              </Box>
            </>
          ) : (
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                flex: 1,
                gap: 1,
              }}
            >
              <EmailIcon sx={{ fontSize: 64, color: 'text.disabled' }} />
              <Typography color="text.secondary">Select an email to preview</Typography>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
}
