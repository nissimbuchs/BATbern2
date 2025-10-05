import * as cdk from 'aws-cdk-lib';
import * as ses from 'aws-cdk-lib/aws-ses';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface SesStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * SES Stack - Provides email templates for authentication workflows
 *
 * Implements:
 * - Story 1.2.2: Bilingual password reset email templates (German/English)
 * - AC15-AC18: AWS SES email template integration
 */
export class SesStack extends cdk.Stack {
  public readonly passwordResetTemplateDE: ses.CfnTemplate;
  public readonly passwordResetTemplateEN: ses.CfnTemplate;

  constructor(scope: Construct, id: string, props: SesStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // German Password Reset Email Template
    this.passwordResetTemplateDE = new ses.CfnTemplate(this, 'PasswordResetDE', {
      template: {
        templateName: `BATbern-${envName}-PasswordReset-DE`,
        subjectPart: 'BATbern Passwort zurücksetzen',
        htmlPart: `
          <!DOCTYPE html>
          <html lang="de">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
              .container { max-width: 600px; margin: 0 auto; padding: 20px; }
              .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
              .content { padding: 30px 20px; background-color: #f9f9f9; }
              .button {
                display: inline-block;
                background-color: #1976d2;
                color: white;
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
                  <a href="{{resetLink}}" class="button">Passwort zurücksetzen</a>
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
          </html>
        `,
        textPart: `
BATbern - Passwort zurücksetzen

Hallo,

Sie haben eine Zurücksetzung Ihres Passworts für Ihr BATbern-Konto angefordert.

Klicken Sie auf den untenstehenden Link, um Ihr Passwort zurückzusetzen:

{{resetLink}}

⏰ WICHTIG: Dieser Link läuft in 1 Stunde ab.

Falls Sie diese Anfrage nicht gestellt haben, ignorieren Sie diese E-Mail bitte. Ihr Passwort bleibt unverändert.

Aus Sicherheitsgründen teilen Sie diesen Link nicht mit anderen Personen.

Mit freundlichen Grüßen,
BATbern Team

---
Dies ist eine automatisch generierte E-Mail. Bitte antworten Sie nicht auf diese Nachricht.
        `.trim(),
      },
    });

    // English Password Reset Email Template
    this.passwordResetTemplateEN = new ses.CfnTemplate(this, 'PasswordResetEN', {
      template: {
        templateName: `BATbern-${envName}-PasswordReset-EN`,
        subjectPart: 'Reset Your BATbern Password',
        htmlPart: `
          <!DOCTYPE html>
          <html lang="en">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
              .container { max-width: 600px; margin: 0 auto; padding: 20px; }
              .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
              .content { padding: 30px 20px; background-color: #f9f9f9; }
              .button {
                display: inline-block;
                background-color: #1976d2;
                color: white;
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
                  <a href="{{resetLink}}" class="button">Reset Password</a>
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
          </html>
        `,
        textPart: `
BATbern - Reset Your Password

Hello,

You requested to reset your password for your BATbern account.

Click the link below to reset your password:

{{resetLink}}

⏰ IMPORTANT: This link will expire in 1 hour.

If you didn't request this, please ignore this email. Your password will remain unchanged.

For security reasons, do not share this link with anyone else.

Best regards,
BATbern Team

---
This is an automated email. Please do not reply to this message.
        `.trim(),
      },
    });

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'Email');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'PasswordResetTemplateDE', {
      value: this.passwordResetTemplateDE.ref,
      description: 'German Password Reset Email Template Name',
      exportName: `${envName}-PasswordResetTemplateDE`,
    });

    new cdk.CfnOutput(this, 'PasswordResetTemplateEN', {
      value: this.passwordResetTemplateEN.ref,
      description: 'English Password Reset Email Template Name',
      exportName: `${envName}-PasswordResetTemplateEN`,
    });
  }
}
