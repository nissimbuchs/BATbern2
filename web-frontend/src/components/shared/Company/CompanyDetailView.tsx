import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Tabs,
  Tab,
  Avatar,
  Chip,
  Skeleton,
  Alert,
  Stack,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  ArrowBack as ArrowBackIcon,
  Edit as EditIcon,
  CheckCircle as CheckCircleIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { CompanyDetail } from '@/types/company.types';
import { AssociatedUsersPanel } from '@/components/shared/Company/AssociatedUsersPanel';
import { CompanyStatistics } from '@/components/shared/Company/CompanyStatistics';
import { ActivityTimeline } from '@/components/shared/Company/ActivityTimeline';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`company-tabpanel-${index}`}
      aria-labelledby={`company-tab-${index}`}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

interface CompanyDetailViewProps {
  company: CompanyDetail | null;
  isLoading?: boolean;
  error?: string;
  canEdit?: boolean;
  onEdit: (companyId: string) => void;
  onBack: () => void;
  onRetry?: () => void;
}

export const CompanyDetailView: React.FC<CompanyDetailViewProps> = ({
  company,
  isLoading = false,
  error,
  canEdit = true,
  onEdit,
  onBack,
  onRetry,
}) => {
  const { t } = useTranslation('common');
  const [activeTab, setActiveTab] = useState(0);

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleEdit = () => {
    if (company) {
      onEdit(company.id);
    }
  };

  const handleLinkUser = (companyId: string, userId: string) => {
    console.log(`[CompanyDetailView] Linking user ${userId} to company ${companyId}`);
    // TODO: Implement user linking via User Service API when user service is available
    // This would call: POST /api/v1/users/{userId}/company with companyId in body
    alert(`User linking functionality will be implemented in User Management Service story. CompanyId: ${companyId}, UserId: ${userId}`);
  };

  const handleUnlinkUser = (companyId: string, userId: string) => {
    console.log(`[CompanyDetailView] Unlinking user ${userId} from company ${companyId}`);
    // TODO: Implement user unlinking via User Service API when user service is available
    // This would call: DELETE /api/v1/users/{userId}/company
    alert(`User unlinking functionality will be implemented in User Management Service story. CompanyId: ${companyId}, UserId: ${userId}`);
  };

  // Loading state
  if (isLoading) {
    return (
      <Box data-testid="detail-view-skeleton" sx={{ p: 3 }}>
        <Skeleton variant="rectangular" width="100%" height={200} sx={{ mb: 2 }} />
        <Skeleton variant="text" width="60%" height={40} sx={{ mb: 1 }} />
        <Skeleton variant="text" width="40%" height={30} />
      </Box>
    );
  }

  // Error state
  if (error) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
        {onRetry && (
          <Button variant="contained" onClick={onRetry}>
            {t('company.errors.retry')}
          </Button>
        )}
      </Box>
    );
  }

  // No company data
  if (!company) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="info">{t('company.detail.noData')}</Alert>
      </Box>
    );
  }

  const isMobile = window.innerWidth < 600;

  return (
    <Box
      data-testid="company-detail-view"
      className={isMobile ? 'mobile-layout' : ''}
      sx={{ p: 3 }}
    >
      {/* Header with Back button */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
        <Button startIcon={<ArrowBackIcon />} onClick={onBack}>
          {t('company.backToList')}
        </Button>
        {canEdit && (
          <Button
            variant="contained"
            startIcon={<EditIcon />}
            onClick={handleEdit}
            sx={{ ml: 'auto' }}
          >
            {t('actions.edit')}
          </Button>
        )}
      </Box>

      {/* Company Profile Card */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={3} component="div">
            <Grid size={{ xs: 12, sm: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'center' }}>
                <Avatar
                  src={company.logo?.url}
                  alt={`${company.name} logo`}
                  sx={{ width: 120, height: 120 }}
                >
                  {company.name.charAt(0)}
                </Avatar>
              </Box>
            </Grid>

            <Grid size={{ xs: 12, sm: 9 }}>
              <Stack spacing={2}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                  <Typography variant="h4" component="h1">
                    {company.name}
                  </Typography>
                  {company.isVerified && (
                    <Chip
                      data-testid="verified-badge"
                      icon={<CheckCircleIcon />}
                      label={t('company.badges.verified')}
                      color="success"
                      size="small"
                    />
                  )}
                </Box>

                {company.displayName && company.displayName !== company.name && (
                  <Typography variant="subtitle1" color="text.secondary">
                    Display Name: {company.displayName}
                  </Typography>
                )}

                {/* Company Info Grid */}
                <Grid container spacing={2} component="div">
                  {company.swissUID && (
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Swiss UID
                      </Typography>
                      <Typography variant="body1">{company.swissUID}</Typography>
                    </Grid>
                  )}
                  {company.industry && (
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Industry
                      </Typography>
                      <Typography variant="body1">{company.industry}</Typography>
                    </Grid>
                  )}
                  {company.website && (
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Website
                      </Typography>
                      <Typography variant="body1">
                        <a href={company.website} target="_blank" rel="noopener noreferrer">
                          {company.website}
                        </a>
                      </Typography>
                    </Grid>
                  )}
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Typography variant="body2" color="text.secondary">
                      Created By
                    </Typography>
                    <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                      {company.createdBy}
                    </Typography>
                  </Grid>
                </Grid>

                {company.description && (
                  <Box>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      Description
                    </Typography>
                    <Typography variant="body1">{company.description}</Typography>
                  </Box>
                )}
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>


      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tabs value={activeTab} onChange={handleTabChange} aria-label="company detail tabs">
          <Tab label={t('company.detail.tabs.overview')} id="company-tab-0" aria-controls="company-tabpanel-0" />
          <Tab label={t('company.detail.tabs.users')} id="company-tab-1" aria-controls="company-tabpanel-1" />
          <Tab label={t('company.detail.tabs.statistics')} id="company-tab-2" aria-controls="company-tabpanel-2" />
          <Tab label={t('company.detail.tabs.activity')} id="company-tab-3" aria-controls="company-tabpanel-3" />
        </Tabs>
      </Box>

      {/* Tab Panels */}
      <TabPanel value={activeTab} index={0}>
        <Typography variant="body1" paragraph>
          {t('company.detail.overviewText')}
        </Typography>
      </TabPanel>

      <TabPanel value={activeTab} index={1}>
        <AssociatedUsersPanel
          companyId={company.id}
          onLinkUser={handleLinkUser}
          onUnlinkUser={handleUnlinkUser}
        />
      </TabPanel>

      <TabPanel value={activeTab} index={2}>
        <CompanyStatistics statistics={company.statistics} />
      </TabPanel>

      <TabPanel value={activeTab} index={3}>
        <ActivityTimeline companyId={company.id} />
      </TabPanel>
    </Box>
  );
};
