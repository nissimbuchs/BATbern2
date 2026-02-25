/**
 * KpiCard
 * Story 10.5: Analytics Dashboard (AC2)
 *
 * Single KPI stat display: icon + label + formatted number.
 */

import { Box, Card, CardContent, Typography } from '@mui/material';
import type { SvgIconComponent } from '@mui/icons-material';

interface KpiCardProps {
  label: string;
  value: number;
  icon: SvgIconComponent;
  color?: string;
}

const KpiCard = ({ label, value, icon: Icon, color = '#2C5F7C' }: KpiCardProps) => (
  <Card variant="outlined" sx={{ flex: 1, minWidth: 160 }}>
    <CardContent>
      <Box display="flex" alignItems="center" gap={1.5}>
        <Icon sx={{ fontSize: 32, color }} />
        <Box>
          <Typography variant="h5" fontWeight={700} lineHeight={1}>
            {value.toLocaleString()}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {label}
          </Typography>
        </Box>
      </Box>
    </CardContent>
  </Card>
);

export default KpiCard;
