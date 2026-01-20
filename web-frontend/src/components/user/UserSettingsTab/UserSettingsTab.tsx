/**
 * UserSettingsTab Component
 * Story 2.6: User Account Management Frontend
 */

import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Box,
  Tabs,
  Tab,
  Paper,
  Typography,
  TextField,
  Button,
  RadioGroup,
  FormControlLabel,
  Radio,
  FormGroup,
  Checkbox,
  Divider,
  Link,
  Alert,
} from '@mui/material';
import type { UserPreferences, UserSettings } from '@/types/userAccount.types';
import {
  useUpdateUserPreferences,
  useUpdateUserSettings,
} from '@/hooks/useUserAccount/useUserAccount';

interface UserSettingsTabProps {
  email?: string;
  preferences?: UserPreferences;
  settings?: UserSettings;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" hidden={value !== index} {...other}>
      {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
    </div>
  );
}

const UserSettingsTab: React.FC<UserSettingsTabProps> = ({ email, preferences, settings }) => {
  const { t } = useTranslation('common');
  const [activeSubTab, setActiveSubTab] = useState(0);
  const [preferencesForm, setPreferencesForm] = useState(
    preferences || {
      theme: 'LIGHT' as const,
      timezone: 'Europe/Zurich',
      notificationChannels: { email: true, inApp: true, push: false },
      notificationFrequency: 'IMMEDIATE' as const,
    }
  );
  const [settingsForm, setSettingsForm] = useState(
    settings || {
      profileVisibility: 'PUBLIC' as const,
      showEmail: true,
      showCompany: true,
      showActivity: true,
      allowMessaging: true,
    }
  );

  const updatePreferencesMutation = useUpdateUserPreferences();
  const updateSettingsMutation = useUpdateUserSettings();

  const handleSavePreferences = async () => {
    try {
      await updatePreferencesMutation.mutateAsync(preferencesForm);
    } catch (error) {
      console.error('Failed to save preferences:', error);
    }
  };

  const handleSaveSettings = async () => {
    try {
      await updateSettingsMutation.mutateAsync(settingsForm);
    } catch (error) {
      console.error('Failed to save settings:', error);
    }
  };

  return (
    <Box data-testid="user-settings-tab">
      <Tabs value={activeSubTab} onChange={(_, v) => setActiveSubTab(v)}>
        <Tab label={t('settings.tabs.account')} data-testid="account-subtab" />
        <Tab label={t('settings.tabs.notifications')} data-testid="notifications-subtab" />
        <Tab label={t('settings.tabs.privacy')} data-testid="privacy-subtab" />
      </Tabs>

      {/* Account Settings */}
      <TabPanel value={activeSubTab} index={0}>
        <Paper sx={{ p: 3 }} data-testid="account-settings-panel">
          <Typography variant="h6" gutterBottom>
            {t('settings.account.title')}
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <TextField
            fullWidth
            label={t('settings.account.email')}
            value={email || ''}
            InputProps={{ readOnly: true }}
            helperText={t('settings.account.emailHelperText')}
            margin="normal"
            data-testid="email-field"
          />

          <Button variant="outlined" sx={{ mt: 2 }} data-testid="change-password-button">
            {t('settings.account.changePassword')}
          </Button>

          <Typography variant="subtitle1" sx={{ mt: 3, mb: 2 }}>
            {t('settings.account.theme')}
          </Typography>
          <RadioGroup
            value={preferencesForm.theme}
            onChange={(e) =>
              setPreferencesForm({
                ...preferencesForm,
                theme: e.target.value as UserPreferences['theme'],
              })
            }
          >
            <FormControlLabel
              value="LIGHT"
              control={<Radio data-testid="theme-light" />}
              label={t('settings.account.themeLight')}
            />
            <FormControlLabel
              value="DARK"
              control={<Radio data-testid="theme-dark" />}
              label={t('settings.account.themeDark')}
            />
            <FormControlLabel
              value="AUTO"
              control={<Radio data-testid="theme-auto" />}
              label={t('settings.account.themeAuto')}
            />
          </RadioGroup>

          <TextField
            fullWidth
            label={t('settings.account.timezone')}
            value={preferencesForm.timezone}
            onChange={(e) => setPreferencesForm({ ...preferencesForm, timezone: e.target.value })}
            margin="normal"
            data-testid="timezone-autocomplete"
          />

          <Button
            variant="contained"
            onClick={handleSavePreferences}
            sx={{ mt: 2 }}
            data-testid="save-account-settings-button"
          >
            {t('settings.account.saveButton')}
          </Button>
        </Paper>
      </TabPanel>

      {/* Notification Settings */}
      <TabPanel value={activeSubTab} index={1}>
        <Paper sx={{ p: 3 }} data-testid="notification-settings-panel">
          <Typography variant="h6" gutterBottom>
            {t('settings.notifications.title')}
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Alert severity="info" sx={{ mb: 2 }} data-testid="advanced-features-info">
            {t('settings.notifications.advancedInfo')}
          </Alert>

          <Typography variant="subtitle1" gutterBottom>
            {t('settings.notifications.channels')}
          </Typography>
          <FormGroup>
            <FormControlLabel
              control={
                <Checkbox
                  checked={preferencesForm.notificationChannels.email}
                  onChange={(e) =>
                    setPreferencesForm({
                      ...preferencesForm,
                      notificationChannels: {
                        ...preferencesForm.notificationChannels,
                        email: e.target.checked,
                      },
                    })
                  }
                  data-testid="channel-email"
                />
              }
              label={t('settings.notifications.channelEmail')}
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={preferencesForm.notificationChannels.inApp}
                  onChange={(e) =>
                    setPreferencesForm({
                      ...preferencesForm,
                      notificationChannels: {
                        ...preferencesForm.notificationChannels,
                        inApp: e.target.checked,
                      },
                    })
                  }
                  data-testid="channel-in-app"
                />
              }
              label={t('settings.notifications.channelInApp')}
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={preferencesForm.notificationChannels.push}
                  onChange={(e) =>
                    setPreferencesForm({
                      ...preferencesForm,
                      notificationChannels: {
                        ...preferencesForm.notificationChannels,
                        push: e.target.checked,
                      },
                    })
                  }
                  data-testid="channel-push"
                />
              }
              label={t('settings.notifications.channelPush')}
            />
          </FormGroup>

          <Typography variant="subtitle1" sx={{ mt: 2 }} gutterBottom>
            {t('settings.notifications.frequency')}
          </Typography>
          <RadioGroup
            value={preferencesForm.notificationFrequency}
            onChange={(e) =>
              setPreferencesForm({
                ...preferencesForm,
                notificationFrequency: e.target.value as UserPreferences['notificationFrequency'],
              })
            }
          >
            <FormControlLabel
              value="IMMEDIATE"
              control={<Radio data-testid="frequency-immediate" />}
              label={t('settings.notifications.frequencyImmediate')}
            />
            <FormControlLabel
              value="DAILY_DIGEST"
              control={<Radio data-testid="frequency-daily" />}
              label={t('settings.notifications.frequencyDaily')}
            />
            <FormControlLabel
              value="WEEKLY_DIGEST"
              control={<Radio data-testid="frequency-weekly" />}
              label={t('settings.notifications.frequencyWeekly')}
            />
          </RadioGroup>

          <Button
            variant="contained"
            onClick={handleSavePreferences}
            sx={{ mt: 2 }}
            data-testid="save-notification-settings-button"
          >
            {t('settings.notifications.saveButton')}
          </Button>
        </Paper>
      </TabPanel>

      {/* Privacy Settings */}
      <TabPanel value={activeSubTab} index={2}>
        <Paper sx={{ p: 3 }} data-testid="privacy-settings-panel">
          <Typography variant="h6" gutterBottom>
            {t('settings.privacy.title')}
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Typography variant="subtitle1" gutterBottom>
            {t('settings.privacy.profileVisibility')}
          </Typography>
          <RadioGroup
            value={settingsForm.profileVisibility}
            onChange={(e) =>
              setSettingsForm({
                ...settingsForm,
                profileVisibility: e.target.value as UserSettings['profileVisibility'],
              })
            }
          >
            <FormControlLabel
              value="PUBLIC"
              control={<Radio data-testid="visibility-public" />}
              label={t('settings.privacy.visibilityPublic')}
            />
            <FormControlLabel
              value="MEMBERS_ONLY"
              control={<Radio data-testid="visibility-members-only" />}
              label={t('settings.privacy.visibilityMembersOnly')}
            />
            <FormControlLabel
              value="PRIVATE"
              control={<Radio data-testid="visibility-private" />}
              label={t('settings.privacy.visibilityPrivate')}
            />
          </RadioGroup>

          <Typography variant="subtitle1" sx={{ mt: 2 }} gutterBottom>
            {t('settings.privacy.informationDisplay')}
          </Typography>
          <FormGroup>
            <FormControlLabel
              control={
                <Checkbox
                  checked={settingsForm.showEmail}
                  onChange={(e) =>
                    setSettingsForm({ ...settingsForm, showEmail: e.target.checked })
                  }
                  data-testid="show-email-toggle"
                />
              }
              label={t('settings.privacy.showEmail')}
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={settingsForm.showCompany}
                  onChange={(e) =>
                    setSettingsForm({ ...settingsForm, showCompany: e.target.checked })
                  }
                  data-testid="show-company-toggle"
                />
              }
              label={t('settings.privacy.showCompany')}
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={settingsForm.showActivity}
                  onChange={(e) =>
                    setSettingsForm({ ...settingsForm, showActivity: e.target.checked })
                  }
                  data-testid="show-activity-toggle"
                />
              }
              label={t('settings.privacy.showActivity')}
            />
          </FormGroup>

          <Typography variant="subtitle1" sx={{ mt: 2 }} gutterBottom>
            {t('settings.privacy.communication')}
          </Typography>
          <FormGroup>
            <FormControlLabel
              control={
                <Checkbox
                  checked={settingsForm.allowMessaging}
                  onChange={(e) =>
                    setSettingsForm({ ...settingsForm, allowMessaging: e.target.checked })
                  }
                  data-testid="allow-messaging-toggle"
                />
              }
              label={t('settings.privacy.allowMessaging')}
            />
          </FormGroup>

          <Link
            href="/privacy-policy"
            target="_blank"
            sx={{ mt: 2, display: 'block' }}
            data-testid="privacy-policy-link"
          >
            {t('settings.privacy.viewPrivacyPolicy')}
          </Link>

          <Button
            variant="contained"
            onClick={handleSaveSettings}
            sx={{ mt: 2 }}
            data-testid="save-privacy-settings-button"
          >
            {t('settings.privacy.saveButton')}
          </Button>
        </Paper>
      </TabPanel>
    </Box>
  );
};

export default UserSettingsTab;
