/**
 * UserSettingsTab Component
 * Story 2.6: User Account Management Frontend
 */

import React, { useEffect, useState } from 'react';
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
  Switch,
  Divider,
  Link,
  Alert,
  Chip,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { isAxiosError } from 'axios';
import type { AdditionalEmail, UserPreferences, UserSettings } from '@/types/userAccount.types';
import {
  useAddAdditionalEmail,
  useDeleteAdditionalEmail,
  useUpdateUserPreferences,
  useUpdateUserSettings,
} from '@/hooks/useUserAccount/useUserAccount';
import { useTranslation } from 'react-i18next';
import { useMySubscription, usePatchMySubscription } from '@/hooks/useNewsletter/useNewsletter';

const ADDITIONAL_EMAILS_LIMIT = 5;
const TOAST_AUTO_DISMISS_MS = 5000;

interface UserSettingsTabProps {
  email?: string;
  preferences?: UserPreferences;
  settings?: UserSettings;
  /**
   * Story 10.32 — additional email addresses registered on the user's profile.
   * Optional; falls back to an empty list when the parent hasn't loaded it yet
   * or when the user has none.
   */
  additionalEmails?: AdditionalEmail[];
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

/**
 * Story 10.32 — additional emails section under the Account sub-tab.
 *
 * Receives the current list as a prop (from the parent's user profile fetch)
 * and uses the {@link useAddAdditionalEmail} / {@link useDeleteAdditionalEmail}
 * mutation hooks for writes. Errors surface inline on the form field; success
 * relies on the parent's `['user-profile']` query invalidation to refresh.
 */
function AdditionalEmailsSection({ additionalEmails }: { additionalEmails: AdditionalEmail[] }) {
  const { t } = useTranslation('userManagement');
  const addMutation = useAddAdditionalEmail();
  const deleteMutation = useDeleteAdditionalEmail();
  const [emailInput, setEmailInput] = useState('');
  const [labelInput, setLabelInput] = useState('');
  const [inlineError, setInlineError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(
    null
  );
  // Story 10.32 (P3-1, P3-8 from 2026-05-22 review): the confirm prompt is a
  // MUI Dialog rather than `window.confirm()` so it inherits the design system,
  // is screen-reader friendly, and identifies which email is being removed.
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  const atLimit = additionalEmails.length >= ADDITIONAL_EMAILS_LIMIT;

  // P3-2: auto-dismiss the toast after 5s so stale success messages don't
  // linger on top of subsequent attempts. The user can also still close it
  // manually via the Alert's onClose.
  useEffect(() => {
    if (!toast) {
      return;
    }
    const timeout = window.setTimeout(() => setToast(null), TOAST_AUTO_DISMISS_MS);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  function readErrorCode(err: unknown): string | undefined {
    if (isAxiosError(err)) {
      const code = err.response?.data?.errorCode as string | undefined;
      return code;
    }
    return undefined;
  }

  async function handleAdd() {
    setInlineError(null);
    const email = emailInput.trim();
    // P3-3: client-side regex dropped. The `type="email"` input provides
    // basic format hints; authoritative validation is the backend's
    // jakarta `@Email` + OpenAPI `format: email`, which returns 400 with
    // `errorCode = ADDITIONAL_EMAIL_INVALID` for malformed input. The
    // round-trip is fine here — this is a single-field, low-frequency form.
    if (!email) {
      setInlineError(t('settings.account.additionalEmailErrorInvalid'));
      return;
    }
    try {
      await addMutation.mutateAsync({
        email,
        label: labelInput.trim() || undefined,
      });
      setEmailInput('');
      setLabelInput('');
      setToast({
        message: t('settings.account.additionalEmailAdded'),
        severity: 'success',
      });
    } catch (err) {
      const code = readErrorCode(err);
      if (code === 'ADDITIONAL_EMAIL_DUPLICATE') {
        setInlineError(t('settings.account.additionalEmailErrorDuplicate'));
      } else if (code === 'ADDITIONAL_EMAIL_LIMIT_REACHED') {
        setInlineError(t('settings.account.additionalEmailErrorLimit'));
      } else {
        setInlineError(t('settings.account.additionalEmailErrorInvalid'));
      }
    }
  }

  function requestDelete(email: string) {
    setPendingDelete(email);
  }

  function cancelDelete() {
    setPendingDelete(null);
  }

  async function confirmDelete() {
    const email = pendingDelete;
    setPendingDelete(null);
    if (!email) {
      return;
    }
    try {
      await deleteMutation.mutateAsync(email);
      setToast({
        message: t('settings.account.additionalEmailRemoved'),
        severity: 'success',
      });
    } catch (err) {
      // P2-4: map backend error codes so the toast tells the user the actual
      // failure mode instead of the misleading "invalid email" message used
      // for every error pre-review.
      const code = readErrorCode(err);
      if (code === 'ADDITIONAL_EMAIL_NOT_FOUND') {
        setToast({
          message: t('settings.account.additionalEmailDeleteNotFound'),
          severity: 'error',
        });
      } else {
        setToast({
          message: t('settings.account.additionalEmailDeleteFailed'),
          severity: 'error',
        });
      }
    }
  }

  return (
    <Box sx={{ mt: 4 }} data-testid="additional-emails-section">
      <Typography variant="subtitle1" gutterBottom>
        {t('settings.account.additionalEmailsTitle')}
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
        {t('settings.account.additionalEmailsHelp')}
      </Typography>

      {additionalEmails.length > 0 && (
        <List dense data-testid="additional-emails-list" sx={{ mb: 1 }}>
          {additionalEmails.map((entry) => (
            <ListItem
              key={entry.email}
              data-testid={`additional-email-row-${entry.email}`}
              secondaryAction={
                <IconButton
                  edge="end"
                  size="small"
                  data-testid={`additional-email-delete-${entry.email}`}
                  aria-label={t('settings.account.additionalEmailDeleteAriaLabel', {
                    email: entry.email,
                  })}
                  onClick={() => requestDelete(entry.email)}
                  disabled={deleteMutation.isPending || pendingDelete !== null}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              }
            >
              <ListItemText
                primary={
                  <Stack direction="row" spacing={1} alignItems="center">
                    <span>{entry.email}</span>
                    {entry.verifiedAt === null && (
                      <Chip
                        size="small"
                        label={t('settings.account.additionalEmailUnverifiedPill')}
                        data-testid={`additional-email-unverified-${entry.email}`}
                      />
                    )}
                  </Stack>
                }
                secondary={entry.label || undefined}
              />
            </ListItem>
          ))}
        </List>
      )}

      {atLimit ? (
        <Alert severity="info" data-testid="additional-emails-at-limit">
          {t('settings.account.additionalEmailAtLimit')}
        </Alert>
      ) : (
        <Box>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1 }}>
            <TextField
              size="small"
              fullWidth
              type="email"
              label={t('common:labels.email')}
              value={emailInput}
              onChange={(e) => setEmailInput(e.target.value)}
              error={Boolean(inlineError)}
              helperText={inlineError || undefined}
              data-testid="additional-email-input"
              disabled={addMutation.isPending}
            />
            <TextField
              size="small"
              fullWidth
              label={t('settings.account.additionalEmailLabel')}
              value={labelInput}
              onChange={(e) => setLabelInput(e.target.value)}
              inputProps={{ maxLength: 100 }}
              data-testid="additional-email-label-input"
              disabled={addMutation.isPending}
            />
            <Button
              variant="contained"
              onClick={handleAdd}
              disabled={addMutation.isPending || !emailInput.trim()}
              data-testid="additional-email-add-button"
              sx={{ minWidth: 160 }}
            >
              {t('settings.account.additionalEmailAddButton')}
            </Button>
          </Stack>
        </Box>
      )}

      {toast && (
        <Alert
          severity={toast.severity}
          onClose={() => setToast(null)}
          sx={{ mt: 1 }}
          data-testid="additional-email-toast"
        >
          {toast.message}
        </Alert>
      )}

      <Dialog
        open={pendingDelete !== null}
        onClose={cancelDelete}
        aria-labelledby="additional-email-delete-confirm-title"
        aria-describedby="additional-email-delete-confirm-description"
        data-testid="additional-email-delete-confirm-dialog"
      >
        <DialogTitle id="additional-email-delete-confirm-title">
          {t('settings.account.additionalEmailRemoveConfirmTitle')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="additional-email-delete-confirm-description">
            {t('settings.account.additionalEmailRemoveConfirm', { email: pendingDelete ?? '' })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={cancelDelete} data-testid="additional-email-delete-cancel">
            {t('settings.account.additionalEmailRemoveConfirmCancel')}
          </Button>
          <Button
            onClick={confirmDelete}
            color="error"
            variant="contained"
            data-testid="additional-email-delete-confirm"
          >
            {t('settings.account.additionalEmailRemoveConfirmYes')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

/** Newsletter subscription toggle section for authenticated users (Story 10.7 — AC7). */
function NewsletterSection() {
  const { data: status, isLoading } = useMySubscription();
  const patchMutation = usePatchMySubscription();
  const { t, i18n } = useTranslation('userManagement');

  function handleToggle(checked: boolean) {
    const language = i18n.language?.startsWith('de') ? 'de' : 'en';
    patchMutation.mutate({ subscribed: checked, language });
  }

  if (isLoading) {
    return null;
  }

  return (
    <Box>
      <Typography variant="subtitle1" gutterBottom>
        {t('settings.newsletter.title')}
      </Typography>
      <FormControlLabel
        control={
          <Switch
            checked={status?.subscribed ?? false}
            onChange={(e) => handleToggle(e.target.checked)}
            disabled={patchMutation.isPending}
            data-testid="newsletter-subscribe-toggle"
          />
        }
        label={
          <Box>
            <Typography variant="body2">{t('settings.newsletter.subscribe')}</Typography>
            <Typography variant="caption" color="text.secondary">
              {t('settings.newsletter.description')}
            </Typography>
          </Box>
        }
      />
    </Box>
  );
}

const UserSettingsTab: React.FC<UserSettingsTabProps> = ({
  email,
  preferences,
  settings,
  additionalEmails,
}) => {
  const { t } = useTranslation('userManagement');
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
            label={t('common:labels.email')}
            value={email || ''}
            InputProps={{ readOnly: true }}
            helperText={<span data-testid="email-status">{t('settings.account.emailStatus')}</span>}
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
            {t('settings.account.save')}
          </Button>

          <Divider sx={{ my: 3 }} />

          {/* Story 10.32: Additional emails section */}
          <AdditionalEmailsSection additionalEmails={additionalEmails ?? []} />
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
            {t('settings.notifications.epic7Notice')}
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
            {t('settings.notifications.save')}
          </Button>

          <Divider sx={{ my: 3 }} />

          {/* Newsletter section (Story 10.7 — AC7) */}
          <NewsletterSection />
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
            {t('settings.privacy.visibility')}
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
            {t('settings.privacy.infoDisplay')}
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
            {t('settings.privacy.viewPolicy')}
          </Link>

          <Button
            variant="contained"
            onClick={handleSaveSettings}
            sx={{ mt: 2 }}
            data-testid="save-privacy-settings-button"
          >
            {t('settings.privacy.save')}
          </Button>
        </Paper>
      </TabPanel>
    </Box>
  );
};

export default UserSettingsTab;
