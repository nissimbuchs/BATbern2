# Forgot Password Flow

Password reset runs **entirely between the browser and Cognito** through AWS Amplify. There is no
BATbern backend endpoint for it: no `/api/v1/auth/forgot-password`, no `RateLimitService`, no
`auth-endpoints` OpenAPI spec. Those were described here until 2026-11 but never existed in this
form (#1213).

## Flow

1. `/auth/forgot-password`: `ForgotPasswordForm` calls `useForgotPassword`, which calls Amplify
   `resetPassword({ username: email })`. Cognito sends a verification code by mail.
2. `ForgotPasswordConfirmation` shows the masked address and lets the user resend (it calls
   `useForgotPassword` again).
3. `/auth/reset-password`: `ResetPasswordForm` calls `useResetPassword`, which calls Amplify
   `confirmResetPassword` with the code and the new password.

## Where the code is

| Part | Location |
|---|---|
| Forms | `web-frontend/src/components/auth/ForgotPasswordForm/`, `ForgotPasswordConfirmation/`, `ResetPasswordForm/` |
| Hooks | `web-frontend/src/hooks/useForgotPassword/`, `useResetPassword/` |
| Texts | `auth` namespace in `web-frontend/public/locales/{locale}/auth.json` (`forgot.*`) |
| Reset mail | Cognito `CustomEmailSender` trigger, `infrastructure/lib/lambda/triggers/custom-email-sender.ts` (handles `CustomEmailSender_ForgotPassword`) |
| E2E | `web-frontend/e2e/auth/forgot-password.spec.ts`, `reset-password.spec.ts` |

Throttling of reset requests is Cognito's own; BATbern adds none. The gateway's
`RateLimitingFilter` does not see these calls, because they go straight to Cognito.

## Related

- [06d-notification-system.md](../architecture/06d-notification-system.md)
- [06b-user-lifecycle-sync.md](../architecture/06b-user-lifecycle-sync.md)
