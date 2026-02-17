/**
 * SpeakerLoginPage Component (Story 9.3)
 *
 * Dual-authentication login page for speaker portal.
 * Accessed via: /speaker-portal/login
 *
 * Provides two auth paths (tabs):
 * 1. "Magischen Link" — email input → sends new magic link email → shows success message
 * 2. "Mit Passwort" — email + password → Cognito auth → redirect to /speaker-portal/dashboard
 *
 * Uses session bridge pattern (Story 9.1):
 * Cognito auth → backend issues opaque VIEW token → redirect to existing dashboard.
 */

import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { Loader2, AlertCircle, Mail, CheckCircle2 } from 'lucide-react';
import { speakerAuthService } from '@/services/speakerAuthService';

type AuthTab = 'magic-link' | 'password';
type PasswordState = 'idle' | 'loading' | 'success' | 'error';
type MagicLinkState = 'idle' | 'loading' | 'sent' | 'error';

const SpeakerLoginPage = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<AuthTab>('magic-link');

  // Magic link tab state
  const [magicEmail, setMagicEmail] = useState('');
  const [magicState, setMagicState] = useState<MagicLinkState>('idle');
  const [magicError, setMagicError] = useState('');

  // Password tab state
  const [pwEmail, setPwEmail] = useState('');
  const [pwPassword, setPwPassword] = useState('');
  const [pwState, setPwState] = useState<PasswordState>('idle');
  const [pwError, setPwError] = useState('');

  const handleMagicLinkSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMagicState('loading');
    setMagicError('');
    try {
      await speakerAuthService.requestMagicLink(magicEmail);
      setMagicState('sent');
    } catch {
      setMagicError('Der Link konnte nicht gesendet werden. Bitte versuche es erneut.');
      setMagicState('error');
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwState('loading');
    setPwError('');
    try {
      const response = await speakerAuthService.loginWithPassword(pwEmail, pwPassword);
      setPwState('success');
      navigate(`/speaker-portal/dashboard?token=${response.sessionToken}`);
    } catch (error: unknown) {
      const message =
        (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Ungültige E-Mail-Adresse oder Passwort.';
      setPwError(message);
      setPwState('error');
    }
  };

  return (
    <PublicLayout>
      <div className="min-h-screen flex items-center justify-center p-4">
        <Card className="max-w-md w-full p-8">
          <h1 className="text-2xl font-bold text-gray-900 mb-2 text-center">Speaker Portal</h1>
          <p className="text-gray-500 text-sm text-center mb-6">
            Anmeldung für eingeladene Speaker
          </p>

          {/* Tabs */}
          <div className="flex border-b border-gray-200 mb-6">
            <button
              onClick={() => setActiveTab('magic-link')}
              className={`flex-1 py-2 text-sm font-medium border-b-2 transition-colors ${
                activeTab === 'magic-link'
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              Magischen Link
            </button>
            <button
              onClick={() => setActiveTab('password')}
              className={`flex-1 py-2 text-sm font-medium border-b-2 transition-colors ${
                activeTab === 'password'
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              Mit Passwort
            </button>
          </div>

          {/* Magic Link Tab */}
          {activeTab === 'magic-link' && (
            <div>
              {magicState === 'sent' ? (
                <div className="text-center py-4">
                  <CheckCircle2 className="h-12 w-12 mx-auto text-green-500 mb-3" />
                  <h2 className="text-lg font-semibold text-gray-900 mb-1">Link gesendet!</h2>
                  <p className="text-gray-600 text-sm">
                    Überprüfe deine E-Mails und klicke auf den Anmeldelink.
                  </p>
                </div>
              ) : (
                <form onSubmit={handleMagicLinkSubmit} className="space-y-4">
                  <div>
                    <label
                      htmlFor="magic-email"
                      className="block text-sm font-medium text-gray-700 mb-1"
                    >
                      E-Mail-Adresse
                    </label>
                    <input
                      id="magic-email"
                      type="email"
                      required
                      value={magicEmail}
                      onChange={(e) => setMagicEmail(e.target.value)}
                      placeholder="deine@email.ch"
                      className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  {magicState === 'error' && (
                    <div className="flex items-center gap-2 text-red-600 text-sm">
                      <AlertCircle className="h-4 w-4 flex-shrink-0" />
                      <span>{magicError}</span>
                    </div>
                  )}
                  <button
                    type="submit"
                    disabled={magicState === 'loading'}
                    className="w-full bg-blue-600 text-white py-2 px-4 rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2"
                  >
                    {magicState === 'loading' && <Loader2 className="h-4 w-4 animate-spin" />}
                    Magischen Link senden
                  </button>
                </form>
              )}
            </div>
          )}

          {/* Password Tab */}
          {activeTab === 'password' && (
            <form onSubmit={handlePasswordSubmit} className="space-y-4">
              <div>
                <label htmlFor="pw-email" className="block text-sm font-medium text-gray-700 mb-1">
                  E-Mail-Adresse
                </label>
                <input
                  id="pw-email"
                  type="email"
                  required
                  value={pwEmail}
                  onChange={(e) => setPwEmail(e.target.value)}
                  placeholder="deine@email.ch"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label
                  htmlFor="pw-password"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Passwort
                </label>
                <input
                  id="pw-password"
                  type="password"
                  required
                  value={pwPassword}
                  onChange={(e) => setPwPassword(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div className="text-right">
                <Link
                  to="/speaker-portal/forgot-password"
                  className="text-sm text-blue-600 hover:underline"
                >
                  Passwort vergessen?
                </Link>
              </div>
              {pwState === 'error' && (
                <div className="flex items-center gap-2 text-red-600 text-sm">
                  <AlertCircle className="h-4 w-4 flex-shrink-0" />
                  <span>{pwError}</span>
                </div>
              )}
              <button
                type="submit"
                disabled={pwState === 'loading' || pwState === 'success'}
                className="w-full bg-blue-600 text-white py-2 px-4 rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {(pwState === 'loading' || pwState === 'success') && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                Mit Passwort anmelden
              </button>
            </form>
          )}

          {/* Contact info */}
          <div className="mt-6 pt-4 border-t border-gray-100 flex items-center justify-center gap-2 text-sm text-gray-500">
            <Mail className="h-4 w-4" />
            <a href="mailto:events@batbern.ch" className="text-blue-600 hover:underline">
              events@batbern.ch
            </a>
          </div>
        </Card>
      </div>
    </PublicLayout>
  );
};

export default SpeakerLoginPage;
