/**
 * UserSettingsTab Component
 * Story 2.6: User Account Management Frontend
 */

import React, { useState } from 'react';
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
        <Tab label="Account" data-testid="account-subtab" />
        <Tab label="Notifications" data-testid="notifications-subtab" />
        <Tab label="Privacy" data-testid="privacy-subtab" />
      </Tabs>

      {/* Account Settings */}
      <TabPanel value={activeSubTab} index={0}>
        <Paper sx={{ p: 3 }} data-testid="account-settings-panel">
          <Typography variant="h6" gutterBottom>
            Account Settings
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <TextField
            fullWidth
            label="Email"
            value={email || ''}
            InputProps={{ readOnly: true }}
            helperText={<span data-testid="email-status">Verified (managed by Cognito)</span>}
            margin="normal"
            data-testid="email-field"
          />

          <Button variant="outlined" sx={{ mt: 2 }} data-testid="change-password-button">
            Change Password
          </Button>

          <Typography variant="subtitle1" sx={{ mt: 3, mb: 2 }}>
            Theme
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
              label="Light"
            />
            <FormControlLabel
              value="DARK"
              control={<Radio data-testid="theme-dark" />}
              label="Dark"
            />
            <FormControlLabel
              value="AUTO"
              control={<Radio data-testid="theme-auto" />}
              label="Auto"
            />
          </RadioGroup>

          <TextField
            fullWidth
            label="Timezone"
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
            Save Account Settings
          </Button>
        </Paper>
      </TabPanel>

      {/* Notification Settings */}
      <TabPanel value={activeSubTab} index={1}>
        <Paper sx={{ p: 3 }} data-testid="notification-settings-panel">
          <Typography variant="h6" gutterBottom>
            Notification Settings
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Alert severity="info" sx={{ mb: 2 }} data-testid="advanced-features-info">
            Advanced notification settings (quiet hours, granular controls) available in Epic 7
          </Alert>

          <Typography variant="subtitle1" gutterBottom>
            Notification Channels
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
              label="Email"
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
              label="In-app"
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
              label="Push notifications"
            />
          </FormGroup>

          <Typography variant="subtitle1" sx={{ mt: 2 }} gutterBottom>
            Frequency
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
              label="Immediate"
            />
            <FormControlLabel
              value="DAILY_DIGEST"
              control={<Radio data-testid="frequency-daily" />}
              label="Daily digest"
            />
            <FormControlLabel
              value="WEEKLY_DIGEST"
              control={<Radio data-testid="frequency-weekly" />}
              label="Weekly digest"
            />
          </RadioGroup>

          <Button
            variant="contained"
            onClick={handleSavePreferences}
            sx={{ mt: 2 }}
            data-testid="save-notification-settings-button"
          >
            Save Notification Settings
          </Button>
        </Paper>
      </TabPanel>

      {/* Privacy Settings */}
      <TabPanel value={activeSubTab} index={2}>
        <Paper sx={{ p: 3 }} data-testid="privacy-settings-panel">
          <Typography variant="h6" gutterBottom>
            Privacy Settings
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Typography variant="subtitle1" gutterBottom>
            Profile Visibility
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
              label="Public"
            />
            <FormControlLabel
              value="MEMBERS_ONLY"
              control={<Radio data-testid="visibility-members-only" />}
              label="Members only"
            />
            <FormControlLabel
              value="PRIVATE"
              control={<Radio data-testid="visibility-private" />}
              label="Private"
            />
          </RadioGroup>

          <Typography variant="subtitle1" sx={{ mt: 2 }} gutterBottom>
            Information Display
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
              label="Show email"
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
              label="Show company"
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
              label="Show activity history"
            />
          </FormGroup>

          <Typography variant="subtitle1" sx={{ mt: 2 }} gutterBottom>
            Communication
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
              label="Allow messaging from other users"
            />
          </FormGroup>

          <Link
            href="/privacy-policy"
            target="_blank"
            sx={{ mt: 2, display: 'block' }}
            data-testid="privacy-policy-link"
          >
            View Privacy Policy
          </Link>

          <Button
            variant="contained"
            onClick={handleSaveSettings}
            sx={{ mt: 2 }}
            data-testid="save-privacy-settings-button"
          >
            Save Privacy Settings
          </Button>
        </Paper>
      </TabPanel>
    </Box>
  );
};

export default UserSettingsTab;
