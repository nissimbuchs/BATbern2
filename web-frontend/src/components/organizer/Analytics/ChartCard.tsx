/**
 * ChartCard
 * Story 10.5: Analytics Dashboard (AC8, AC9)
 *
 * Wrapper for each chart widget:
 *   - Title + optional controls slot
 *   - Chart content slot
 *   - BATbernLoader while loading
 *   - EmptyChartState when no data
 *   - Collapsible data table (AC9)
 */

import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {
  Box,
  Button,
  Card,
  CardContent,
  Collapse,
  Divider,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@/components/shared/BATbernLoader';
import EmptyChartState from './EmptyChartState';

interface ChartCardProps {
  title: string;
  isLoading?: boolean;
  isEmpty?: boolean;
  controls?: React.ReactNode;
  children: React.ReactNode;
  dataTable?: React.ReactNode;
  chartHeight?: number;
}

const ChartCard = ({
  title,
  isLoading = false,
  isEmpty = false,
  controls,
  children,
  dataTable,
  chartHeight = 350,
}: ChartCardProps) => {
  const { t } = useTranslation('organizer');
  const [tableOpen, setTableOpen] = useState(false);

  return (
    <Card variant="outlined" sx={{ mb: 3 }}>
      <CardContent>
        <Box
          display="flex"
          alignItems="center"
          justifyContent="space-between"
          mb={2}
          flexWrap="wrap"
          gap={1}
        >
          <Typography variant="h6" component="h3">
            {title}
          </Typography>
          {controls && <Box>{controls}</Box>}
        </Box>

        {isLoading ? (
          <Box
            display="flex"
            justifyContent="center"
            alignItems="center"
            height={chartHeight}
          >
            <BATbernLoader size={48} speed="normal" />
          </Box>
        ) : isEmpty ? (
          <EmptyChartState height={chartHeight} />
        ) : (
          children
        )}

        {dataTable && !isLoading && !isEmpty && (
          <>
            <Divider sx={{ mt: 2 }} />
            <Button
              size="small"
              startIcon={tableOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
              onClick={() => setTableOpen((o) => !o)}
              sx={{ mt: 1 }}
            >
              {tableOpen
                ? t('analytics.labels.hideDataTable')
                : t('analytics.labels.showDataTable')}
            </Button>
            <Collapse in={tableOpen} unmountOnExit>
              <Box mt={1}>{dataTable}</Box>
            </Collapse>
          </>
        )}
      </CardContent>
    </Card>
  );
};

export default ChartCard;
