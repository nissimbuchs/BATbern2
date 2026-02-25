/**
 * EmptyChartState
 * Story 10.5: Analytics Dashboard (AC8)
 *
 * Consistent empty state for chart widgets when no data is available.
 */

import BarChartIcon from '@mui/icons-material/BarChart';
import { Box, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';

interface EmptyChartStateProps {
  message?: string;
  height?: number;
}

const EmptyChartState = ({
  message,
  height = 350,
}: EmptyChartStateProps) => {
  const { t } = useTranslation('organizer');
  return (
    <Box
      data-testid="empty-chart-state"
      display="flex"
      flexDirection="column"
      alignItems="center"
      justifyContent="center"
      height={height}
      gap={1}
      color="text.disabled"
    >
      <BarChartIcon sx={{ fontSize: 48, opacity: 0.4 }} />
      <Typography variant="body2" color="text.disabled">
        {message ?? t('analytics.labels.noData')}
      </Typography>
    </Box>
  );
};

export default EmptyChartState;
