/**
 * RegistrationSuccessPage Component
 *
 * Displays success message after registration submission.
 * User must confirm registration via email link.
 * No sensitive data (registration code) displayed.
 */

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Button } from '@/components/public/ui/button';
import { Card } from '@/components/public/ui/card';
import { CheckCircle2, Mail, UserPlus, ArrowLeft } from 'lucide-react';

const RegistrationSuccessPage = () => {
  const [email, setEmail] = useState<string | null>(null);
  const [isExpired, setIsExpired] = useState(false);

  useEffect(() => {
    // Load registration data from sessionStorage
    const stored = sessionStorage.getItem('pendingRegistration');

    if (!stored) {
      setIsExpired(true);
      return;
    }

    try {
      const { email, expiresAt } = JSON.parse(stored);

      // Check if expired (5 minutes timeout)
      if (Date.now() > expiresAt) {
        sessionStorage.removeItem('pendingRegistration');
        setIsExpired(true);
        return;
      }

      setEmail(email);

      // Clear from sessionStorage after successful display (one-time use)
      sessionStorage.removeItem('pendingRegistration');
    } catch (err) {
      console.error('Failed to load registration data:', err);
      setIsExpired(true);
    }
  }, []);

  if (isExpired) {
    return (
      <PublicLayout>
        <div className="container mx-auto px-4 py-24 max-w-2xl text-center">
          <Mail className="h-16 w-16 text-blue-400 mx-auto mb-4" />
          <h1 className="text-3xl font-light mb-4">Registration Pending</h1>
          <p className="text-zinc-400 mb-4">
            If you submitted a registration, please check your email for the confirmation link.
          </p>
          <p className="text-sm text-zinc-500 mb-8">The confirmation link is valid for 48 hours.</p>
          <Button asChild variant="outline">
            <Link to="/">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Home
            </Link>
          </Button>
        </div>
      </PublicLayout>
    );
  }

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl">
        {/* Success Header */}
        <div className="text-center mb-8">
          <CheckCircle2 className="h-16 w-16 text-green-400 mx-auto mb-4" />
          <h1 className="text-4xl font-light mb-2">Registration Submitted!</h1>
          <p className="text-xl text-zinc-400">Check your email to confirm your registration</p>
        </div>

        {/* Email Confirmation Card */}
        <Card className="p-8 mb-8 text-center">
          <Mail className="h-12 w-12 text-blue-400 mx-auto mb-4" />
          <h2 className="text-2xl font-light mb-4">Confirmation Email Sent</h2>
          {email && (
            <p className="text-zinc-300 mb-4">
              We've sent a confirmation email to{' '}
              <span className="font-mono text-blue-400">{email}</span>
            </p>
          )}
          <p className="text-sm text-zinc-400 mb-6">
            Click the confirmation link in the email to complete your registration. The link is
            valid for <span className="text-zinc-300 font-medium">48 hours</span>.
          </p>

          <div className="border-t border-zinc-800 pt-6 mt-6">
            <h3 className="text-sm font-medium text-zinc-300 mb-2">Didn't receive the email?</h3>
            <ul className="text-sm text-zinc-400 space-y-1">
              <li>• Check your spam/junk folder</li>
              <li>• Make sure you entered the correct email address</li>
              <li>• Wait a few minutes and check again</li>
            </ul>
          </div>
        </Card>

        {/* Account Creation CTA */}
        <Card className="p-6 mb-8 bg-blue-950/30 border-blue-500/30">
          <div className="flex items-start gap-3">
            <UserPlus className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <h3 className="text-lg font-light mb-2">
                Create an Account to Manage Your Registrations
              </h3>
              <p className="text-sm text-zinc-400 mb-4">
                After confirming your registration via email, you can create an account to view and
                manage all your event registrations in one place.
              </p>
              {email && (
                <Button asChild variant="outline" size="sm">
                  <Link to={`/auth/register?email=${encodeURIComponent(email)}`}>
                    <UserPlus className="h-4 w-4 mr-2" />
                    Create Account
                  </Link>
                </Button>
              )}
            </div>
          </div>
        </Card>

        {/* Navigation */}
        <div className="flex justify-center">
          <Button asChild variant="outline">
            <Link to="/">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Home
            </Link>
          </Button>
        </div>
      </div>
    </PublicLayout>
  );
};

export default RegistrationSuccessPage;
