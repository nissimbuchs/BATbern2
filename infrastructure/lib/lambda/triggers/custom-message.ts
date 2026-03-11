/**
 * CustomMessage Lambda Trigger
 * Story 1.2.2: Implement Forgot Password Flow - Task 1a
 *
 * This Lambda function is triggered by AWS Cognito for custom email messages.
 * It intercepts ForgotPassword events and sends branded bilingual HTML emails
 * with clickable reset links directly via SES.
 *
 * AC15-AC18: Bilingual email templates (German/English) with branded design
 * - Detects user language from custom:language attribute (fallback to 'en')
 * - Builds reset link with code, email, and language parameters
 * - Sends email directly via SES (bypasses Cognito's default email)
 */

import { CustomMessageTriggerEvent, CustomMessageTriggerHandler } from 'aws-lambda';
import { SESClient, SendEmailCommand } from '@aws-sdk/client-ses';

/**
 * Supported languages for email templates
 */
const SUPPORTED_LANGUAGES = ['de', 'en'] as const;
type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

/**
 * Environment variables
 */
interface EnvironmentConfig {
  FRONTEND_DOMAIN: string;
}

/**
 * Validate required environment variables
 */
function getEnvironmentConfig(): EnvironmentConfig {
  const requiredVars = ['FRONTEND_DOMAIN'];
  const missing = requiredVars.filter((varName) => !process.env[varName]);

  if (missing.length > 0) {
    throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
  }

  return {
    FRONTEND_DOMAIN: process.env.FRONTEND_DOMAIN!,
  };
}

/**
 * Detect user's preferred language from Cognito attributes
 * Fallback chain: custom:language → 'en' (default)
 */
function detectUserLanguage(userAttributes: Record<string, string>): SupportedLanguage {
  const languageAttr = userAttributes['custom:language'] || 'en';
  const languageCode = languageAttr.toLowerCase().substring(0, 2);

  // Validate language is supported
  if (SUPPORTED_LANGUAGES.includes(languageCode as SupportedLanguage)) {
    return languageCode as SupportedLanguage;
  }

  // Fallback to English
  return 'en';
}

/**
 * Build password reset link with code, email, and language parameters
 */
function buildResetLink(
  frontendDomain: string,
  code: string,
  email: string,
  language: SupportedLanguage
): string {
  const baseUrl = frontendDomain.endsWith('/') ? frontendDomain.slice(0, -1) : frontendDomain;
  const encodedEmail = encodeURIComponent(email);
  return `${baseUrl}/auth/reset-password?code=${code}&email=${encodedEmail}&lang=${language}`;
}

/**
 * Generate HTML email content
 */
function getEmailHtml(language: SupportedLanguage, resetLink: string): string {
  if (language === 'de') {
    return `<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
    .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
    .content { padding: 30px 20px; background-color: #f9f9f9; }
    .button {
      display: inline-block;
      background-color: #1976d2;
      color: white !important;
      padding: 12px 30px;
      text-decoration: none;
      border-radius: 4px;
      margin: 20px 0;
    }
    .footer { padding: 20px; text-align: center; color: #666; font-size: 12px; }
    .warning {
      background-color: #fff3cd;
      border-left: 4px solid #ffc107;
      padding: 12px;
      margin: 20px 0;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>BATbern</h1>
    </div>
    <div class="content">
      <h2>Passwort zurücksetzen</h2>
      <p>Hallo,</p>
      <p>Sie haben eine Zurücksetzung Ihres Passworts für Ihr BATbern-Konto angefordert.</p>
      <p>Klicken Sie auf den untenstehenden Button, um Ihr Passwort zurückzusetzen:</p>
      <div style="text-align: center;">
        <a href="${resetLink}" class="button">Passwort zurücksetzen</a>
      </div>
      <div class="warning">
        <strong>⏰ Dieser Link läuft in 1 Stunde ab.</strong>
      </div>
      <p>Falls Sie diese Anfrage nicht gestellt haben, ignorieren Sie diese E-Mail bitte. Ihr Passwort bleibt unverändert.</p>
      <p>Aus Sicherheitsgründen teilen Sie diesen Link nicht mit anderen Personen.</p>
    </div>
    <div class="footer">
      <p>Mit freundlichen Grüßen,<br><strong>BATbern Team</strong></p>
      <p style="margin-top: 20px;">
        Dies ist eine automatisch generierte E-Mail. Bitte antworten Sie nicht auf diese Nachricht.
      </p>
    </div>
  </div>
</body>
</html>`;
  } else {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
    .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
    .content { padding: 30px 20px; background-color: #f9f9f9; }
    .button {
      display: inline-block;
      background-color: #1976d2;
      color: white !important;
      padding: 12px 30px;
      text-decoration: none;
      border-radius: 4px;
      margin: 20px 0;
    }
    .footer { padding: 20px; text-align: center; color: #666; font-size: 12px; }
    .warning {
      background-color: #fff3cd;
      border-left: 4px solid #ffc107;
      padding: 12px;
      margin: 20px 0;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>BATbern</h1>
    </div>
    <div class="content">
      <h2>Reset Your Password</h2>
      <p>Hello,</p>
      <p>You requested to reset your password for your BATbern account.</p>
      <p>Click the button below to reset your password:</p>
      <div style="text-align: center;">
        <a href="${resetLink}" class="button">Reset Password</a>
      </div>
      <div class="warning">
        <strong>⏰ This link will expire in 1 hour.</strong>
      </div>
      <p>If you didn't request this, please ignore this email. Your password will remain unchanged.</p>
      <p>For security reasons, do not share this link with anyone else.</p>
    </div>
    <div class="footer">
      <p>Best regards,<br><strong>BATbern Team</strong></p>
      <p style="margin-top: 20px;">
        This is an automated email. Please do not reply to this message.
      </p>
    </div>
  </div>
</body>
</html>`;
  }
}

/**
 * Main Lambda handler for CustomMessage trigger
 *
 * IMPORTANT: This function sends emails directly via SES and suppresses Cognito's default email.
 * If SES sending fails, Cognito will fall back to its default email.
 */
export const handler: CustomMessageTriggerHandler = async (event) => {
  console.log('CustomMessage trigger invoked', {
    userPoolId: event.userPoolId,
    userName: event.userName,
    triggerSource: event.triggerSource,
  });

  // Only intercept ForgotPassword trigger
  if (event.triggerSource !== 'CustomMessage_ForgotPassword') {
    console.log('Ignoring non-ForgotPassword trigger', { triggerSource: event.triggerSource });
    return event;
  }

  try {
    const startTime = Date.now();

    // Get environment configuration
    const config = getEnvironmentConfig();

    // Extract user attributes and code
    const email = event.request.userAttributes.email;
    const codeParameter = event.request.codeParameter;

    if (!email) {
      throw new Error('Missing email in user attributes');
    }

    if (!codeParameter) {
      throw new Error('Missing code parameter');
    }

    // Detect user language (i18n support)
    const language = detectUserLanguage(event.request.userAttributes);

    // DEBUG: Log full event to understand code parameter
    console.log('Full event.request:', JSON.stringify(event.request, null, 2));
    console.log('Processing ForgotPassword custom message', {
      email,
      language,
      codeParameter,
      codeLength: codeParameter.length,
    });

    // Build reset link with actual verification code
    const resetLink = buildResetLink(config.FRONTEND_DOMAIN, codeParameter, email, language);

    console.log('Reset link built', {
      email,
      language,
      resetLinkLength: resetLink.length,
    });

    // Generate HTML email content
    const htmlContent = getEmailHtml(language, resetLink);
    const subject = language === 'de' ? 'BATbern Passwort zurücksetzen' : 'Reset Your BATbern Password';

    // Send email directly via SES
    const sesClient = new SESClient({ region: process.env.AWS_REGION || 'eu-central-1' });
    const sendEmailCommand = new SendEmailCommand({
      Source: process.env.FROM_EMAIL || 'BATbern <noreply@batbern.ch>',
      Destination: {
        ToAddresses: [email],
      },
      Message: {
        Subject: {
          Data: subject,
          Charset: 'UTF-8',
        },
        Body: {
          Html: {
            Data: htmlContent,
            Charset: 'UTF-8',
          },
          Text: {
            Data: `Reset your password: ${resetLink}`,
            Charset: 'UTF-8',
          },
        },
      },
    });

    const sesResponse = await sesClient.send(sendEmailCommand);

    console.log('SES email sent successfully', {
      email,
      language,
      messageId: sesResponse.MessageId,
    });

    // CRITICAL: Suppress Cognito's default email by returning empty response
    // This prevents duplicate emails
    event.response.emailSubject = '';
    event.response.emailMessage = '';

    const duration = Date.now() - startTime;
    console.log('CustomMessage completed successfully', {
      email,
      language,
      duration,
    });
  } catch (error) {
    // Log error but DO NOT throw - allow Cognito to send default email as fallback
    console.error('CustomMessage failed (non-blocking, Cognito will send default email)', {
      error: error instanceof Error ? error.message : 'Unknown error',
      stack: error instanceof Error ? error.stack : undefined,
    });

    // Return event unchanged - Cognito will send its default email
    // This ensures users can still reset passwords even if custom email fails
  }

  // Always return the event (modified or unchanged)
  return event;
};
