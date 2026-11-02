/**
 * MailBodyFrame (Stories 16.3, 16.2a): a mail body in a sandboxed iframe. `sandbox=""` allows no
 * scripts, no forms, no navigation and no same-origin access; inbound bodies are also sanitized on
 * the server (no images, styles or scripts).
 */

import React from 'react';
import { Box } from '@mui/material';

interface MailBodyFrameProps {
  html: string;
  title: string;
  /** Height on desktop; mobile uses most of the screen. */
  height?: number;
  'data-testid'?: string;
}

export const MailBodyFrame: React.FC<MailBodyFrameProps> = ({
  html,
  title,
  height = 560,
  'data-testid': testId,
}) => (
  <Box
    component="iframe"
    title={title}
    srcDoc={html}
    sandbox=""
    sx={{
      width: '100%',
      height: { xs: '70vh', md: height },
      border: 1,
      borderColor: 'divider',
      borderRadius: 1,
      bgcolor: 'background.paper',
    }}
    data-testid={testId}
  />
);

export default MailBodyFrame;
