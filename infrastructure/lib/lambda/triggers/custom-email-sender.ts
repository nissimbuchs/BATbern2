/**
 * CustomEmailSender Lambda Trigger
 * Story 1.2.2: Implement Forgot Password Flow - Task 1a (UPDATED)
 *
 * This Lambda function is triggered by AWS Cognito for custom email delivery.
 * It intercepts ForgotPassword events, decrypts the actual verification code,
 * and sends branded bilingual HTML emails with clickable reset links via SES.
 *
 * AC15-AC18: Bilingual email templates (German/English) with branded design
 * - Detects user language from custom:language attribute (fallback to 'en')
 * - Decrypts actual verification code from Cognito using KMS
 * - Builds reset link with actual code, email, and language parameters
 * - Sends email directly via SES
 */

import { Handler } from 'aws-lambda';
import { SESClient, SendEmailCommand } from '@aws-sdk/client-ses';
import { KmsKeyringNode, buildClient, CommitmentPolicy } from '@aws-crypto/client-node';

const { decrypt } = buildClient(CommitmentPolicy.REQUIRE_ENCRYPT_ALLOW_DECRYPT);

/**
 * Supported languages for email templates
 */
const SUPPORTED_LANGUAGES = ['de', 'en'] as const;
type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

/**
 * CustomEmailSender event structure
 */
interface CustomEmailSenderEvent {
  version: string;
  triggerSource: string;
  region: string;
  userPoolId: string;
  userName: string;
  callerContext: {
    awsSdkVersion: string;
    clientId: string;
  };
  request: {
    type: 'customEmailSenderRequestV1';
    code: string; // Base64-encoded encrypted verification code
    clientMetadata?: Record<string, string>;
    userAttributes: Record<string, string>;
  };
  response: Record<string, never>;
}

/**
 * Environment variables
 */
interface EnvironmentConfig {
  FRONTEND_DOMAIN: string;
  FROM_EMAIL: string;
  KEY_ARN: string;
  KEY_ID: string;
}

/**
 * Validate required environment variables
 */
function getEnvironmentConfig(): EnvironmentConfig {
  const requiredVars = ['FRONTEND_DOMAIN', 'FROM_EMAIL', 'KEY_ARN', 'KEY_ID'];
  const missing = requiredVars.filter((varName) => !process.env[varName]);

  if (missing.length > 0) {
    throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
  }

  return {
    FRONTEND_DOMAIN: process.env.FRONTEND_DOMAIN!,
    FROM_EMAIL: process.env.FROM_EMAIL!,
    KEY_ARN: process.env.KEY_ARN!,
    KEY_ID: process.env.KEY_ID!,
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
 * Decrypt the verification code using KMS
 */
async function decryptCode(encryptedCode: string, keyArn: string, keyId: string): Promise<string> {
  const keyring = new KmsKeyringNode({
    generatorKeyId: keyArn,
    keyIds: [keyId],
  });

  const { plaintext } = await decrypt(keyring, Buffer.from(encryptedCode, 'base64'));
  return Buffer.from(plaintext).toString('utf-8');
}

/**
 * Build password reset link with actual code, email, and language parameters
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
 * Generate signup verification HTML email content
 */
function getSignupEmailHtml(
  language: SupportedLanguage,
  verificationLink: string,
  logoUrl: string
): string {
  if (language === 'de') {
    return `<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
    .header { background-color: #1976d2; color: white; padding: 30px 20px; text-align: center; }
    .logo { max-width: 200px; height: auto; margin-bottom: 10px; }
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
      background-color: #e3f2fd;
      border-left: 4px solid #1976d2;
      padding: 12px;
      margin: 20px 0;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <img src="${logoUrl}" alt="BATbern Logo" class="logo" />
      <h1>Willkommen bei BATbern!</h1>
    </div>
    <div class="content">
      <h2>Bestätigen Sie Ihre E-Mail-Adresse</h2>
      <p>Hallo,</p>
      <p>Vielen Dank für Ihre Registrierung bei BATbern!</p>
      <p>Um Ihr Konto zu aktivieren, bestätigen Sie bitte Ihre E-Mail-Adresse, indem Sie auf den Button unten klicken:</p>
      <div style="text-align: center;">
        <a href="${verificationLink}" class="button">E-Mail bestätigen</a>
      </div>
      <div class="warning">
        <strong>ℹ️ Dieser Link ist 24 Stunden gültig.</strong>
      </div>
      <p>Sobald Sie Ihre E-Mail bestätigt haben, können Sie sich bei Ihrem Konto anmelden.</p>
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
    .header { background-color: #1976d2; color: white; padding: 30px 20px; text-align: center; }
    .logo { max-width: 200px; height: auto; margin-bottom: 10px; }
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
      background-color: #e3f2fd;
      border-left: 4px solid #1976d2;
      padding: 12px;
      margin: 20px 0;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <img src="${logoUrl}" alt="BATbern Logo" class="logo" />
      <h1>Welcome to BATbern!</h1>
    </div>
    <div class="content">
      <h2>Verify Your Email Address</h2>
      <p>Hello,</p>
      <p>Thank you for registering with BATbern!</p>
      <p>To activate your account, please verify your email address by clicking the button below:</p>
      <div style="text-align: center;">
        <a href="${verificationLink}" class="button">Verify Email</a>
      </div>
      <div class="warning">
        <strong>ℹ️ This link is valid for 24 hours.</strong>
      </div>
      <p>Once you've verified your email, you'll be able to sign in to your account.</p>
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
 * Generate password reset HTML email content
 */
function getPasswordResetEmailHtml(
  language: SupportedLanguage,
  resetLink: string,
  logoUrl: string
): string {
  if (language === 'de') {
    return `<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
    .header { background-color: #1976d2; color: white; padding: 30px 20px; text-align: center; }
    .logo { max-width: 200px; height: auto; margin-bottom: 10px; }
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
      <img src="${logoUrl}" alt="BATbern Logo" class="logo" />
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
    .header { background-color: #1976d2; color: white; padding: 30px 20px; text-align: center; }
    .logo { max-width: 200px; height: auto; margin-bottom: 10px; }
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
      <img src="${logoUrl}" alt="BATbern Logo" class="logo" />
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
 * Main Lambda handler for CustomEmailSender trigger
 */
export const handler: Handler<CustomEmailSenderEvent, CustomEmailSenderEvent> = async (event) => {
  console.log('CustomEmailSender trigger invoked', {
    userPoolId: event.userPoolId,
    userName: event.userName,
    triggerSource: event.triggerSource,
  });

  // Handle different trigger sources
  if (!event.triggerSource.startsWith('CustomEmailSender_')) {
    console.log('Ignoring non-CustomEmailSender trigger', { triggerSource: event.triggerSource });
    return event;
  }

  // Only handle ForgotPassword and SignUp triggers
  const supportedTriggers = ['CustomEmailSender_ForgotPassword', 'CustomEmailSender_SignUp'];
  if (!supportedTriggers.includes(event.triggerSource)) {
    console.log('Skipping unsupported trigger (not implemented yet)', { triggerSource: event.triggerSource });
    // Return event without sending - Cognito won't send email for unsupported triggers
    return event;
  }

  try {
    const startTime = Date.now();

    // Get environment configuration
    const config = getEnvironmentConfig();

    // Extract user attributes
    const email = event.request.userAttributes.email;
    if (!email) {
      throw new Error('Missing email in user attributes');
    }

    // Detect user language (i18n support)
    const language = detectUserLanguage(event.request.userAttributes);

    console.log('Processing ForgotPassword email', {
      email,
      language,
      encryptedCodeLength: event.request.code.length,
    });

    // Decrypt the verification code
    const verificationCode = await decryptCode(event.request.code, config.KEY_ARN, config.KEY_ID);

    console.log('Verification code decrypted', {
      email,
      codeLength: verificationCode.length,
    });

    // Build logo URL from frontend domain
    const logoUrl = `${config.FRONTEND_DOMAIN}/BATbern_color_logo.svg`;

    // Build appropriate link and email content based on trigger source
    let link: string;
    let htmlContent: string;
    let subject: string;
    let plainText: string;

    if (event.triggerSource === 'CustomEmailSender_SignUp') {
      // Signup verification link
      link = `${config.FRONTEND_DOMAIN}/auth/verify-email?code=${verificationCode}&email=${encodeURIComponent(email)}&lang=${language}`;
      htmlContent = getSignupEmailHtml(language, link, logoUrl);
      subject = language === 'de' ? 'Willkommen bei BATbern - E-Mail bestätigen' : 'Welcome to BATbern - Verify Email';
      plainText = `Verify your email: ${link}`;

      console.log('Signup verification link built', {
        email,
        language,
        linkLength: link.length,
      });
    } else {
      // Password reset link
      link = buildResetLink(config.FRONTEND_DOMAIN, verificationCode, email, language);
      htmlContent = getPasswordResetEmailHtml(language, link, logoUrl);
      subject = language === 'de' ? 'BATbern Passwort zurücksetzen' : 'Reset Your BATbern Password';
      plainText = `Reset your password: ${link}`;

      console.log('Reset link built', {
        email,
        language,
        resetLinkLength: link.length,
      });
    }

    // Send email directly via SES
    const sesClient = new SESClient({ region: process.env.AWS_REGION || 'eu-central-1' });
    const sendEmailCommand = new SendEmailCommand({
      Source: config.FROM_EMAIL,
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
            Data: plainText,
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

    const duration = Date.now() - startTime;
    console.log('CustomEmailSender completed successfully', {
      email,
      language,
      duration,
    });
  } catch (error) {
    // Log error and throw - Cognito will handle fallback
    console.error('CustomEmailSender failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      stack: error instanceof Error ? error.stack : undefined,
    });
    throw error; // Must throw for Cognito to know sending failed
  }

  return event;
};
