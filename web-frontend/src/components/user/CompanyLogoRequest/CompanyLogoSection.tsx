/**
 * #1246 — the company logo on /profile, in the public styling.
 *
 * Shows the logo of the company on the participant's profile and lets them ask the organizers for
 * a new one: choose a file (PNG, JPG or SVG, at most 5 MB), add an optional note, send. Each send
 * uploads the file afresh (useFileUpload: presign, PUT, confirm) and then hands the upload over to
 * `POST /users/me/company-logo-requests`; a retry after a failure therefore never reuses an upload
 * the server has already moved. While a request is open the button is replaced by a notice. The
 * organizers decide in the Mail Center and the participant is told by mail.
 */

import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { Clock, ImageIcon, Loader2 } from 'lucide-react';
import { Button } from '@/components/public/ui/button';
import { useFileUpload } from '@/hooks/useFileUpload/useFileUpload';
import { companyApiClient } from '@/services/api/companyApi';
import { uploadTarget } from '@/services/uploadService';
import {
  createCompanyLogoRequest,
  getMyOpenCompanyLogoRequest,
} from '@/services/api/userAccountApi';

const ACCEPT = 'image/png,image/jpeg,image/svg+xml';

interface CompanyLogoSectionProps {
  /** Company identifier on the profile (ADR-003 slug). */
  companyName: string;
}

class UploadFailed extends Error {}

function errorKey(err: unknown): string {
  if (err instanceof UploadFailed) return 'profile.logo.errorUpload';
  const code = isAxiosError(err)
    ? (err.response?.data?.errorCode as string | undefined)
    : undefined;
  switch (code) {
    case 'LOGO_REQUEST_OPEN':
      return 'profile.logo.errorOpen';
    case 'NO_COMPANY':
      return 'profile.logo.errorNoCompany';
    default:
      return 'profile.logo.errorGeneric';
  }
}

/** The logo is uploaded without a target: the organizer's decision attaches it (#1246). */
const LOGO_UPLOAD = uploadTarget('company-logo');

export function CompanyLogoSection({ companyName }: CompanyLogoSectionProps) {
  const { t } = useTranslation('common');
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [sent, setSent] = useState(false);
  const { uploadFile } = useFileUpload({ target: LOGO_UPLOAD });

  const company = useQuery({
    queryKey: ['company', companyName],
    queryFn: () => companyApiClient.getCompany(companyName),
    enabled: !!companyName,
    retry: false,
  });
  const openRequest = useQuery({
    queryKey: ['company-logo-request', 'mine', companyName],
    queryFn: getMyOpenCompanyLogoRequest,
    enabled: !!companyName,
    retry: false,
  });

  useEffect(() => {
    if (!file || typeof URL.createObjectURL !== 'function') {
      setPreview(null);
      return undefined;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const send = useMutation({
    mutationFn: async () => {
      if (!file) throw new UploadFailed();
      const uploadId = await uploadFile(file);
      if (!uploadId) throw new UploadFailed();
      return createCompanyLogoRequest(uploadId, note);
    },
    onSuccess: () => {
      setSent(true);
      setFormOpen(false);
      setFile(null);
      setNote('');
      void queryClient.invalidateQueries({ queryKey: ['company-logo-request', 'mine'] });
    },
  });

  const logoUrl = company.data?.logo?.url;
  const displayName = company.data?.displayName || companyName;
  const pending = !!openRequest.data;

  return (
    <div className="mt-4" data-testid="company-logo-section">
      <span className="block text-sm text-zinc-400 mb-2">{t('profile.logo.label')}</span>
      <div className="flex items-center gap-4">
        <div className="flex h-16 w-32 items-center justify-center rounded border border-zinc-800 bg-white p-2">
          {logoUrl ? (
            <img
              src={logoUrl}
              alt={t('profile.logo.alt', { company: displayName })}
              className="max-h-full max-w-full object-contain"
              data-testid="company-logo-current"
            />
          ) : (
            <span
              className="flex items-center gap-1 text-xs text-zinc-500"
              data-testid="company-logo-none"
            >
              <ImageIcon className="h-4 w-4" />
              {t('profile.logo.none')}
            </span>
          )}
        </div>

        {pending ? (
          <span
            className="flex items-center gap-1 text-sm text-amber-400"
            data-testid="company-logo-pending"
          >
            <Clock className="h-4 w-4" />
            {t('profile.logo.pending')}
          </span>
        ) : (
          !formOpen && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setSent(false);
                send.reset();
                setFormOpen(true);
              }}
              data-testid="company-logo-request-button"
            >
              {t('profile.logo.request')}
            </Button>
          )
        )}
      </div>

      {sent && !formOpen && (
        <p className="text-sm text-green-400 mt-2" data-testid="company-logo-sent">
          {t('profile.logo.sent')}
        </p>
      )}

      {formOpen && !pending && (
        <div
          className="mt-4 space-y-3 rounded border border-zinc-800 p-4"
          data-testid="company-logo-form"
        >
          <p className="text-sm text-zinc-300">{t('profile.logo.explain')}</p>
          <label className="block text-sm text-zinc-400">
            {t('profile.logo.choose')}
            <input
              type="file"
              accept={ACCEPT}
              className="mt-1 block w-full text-sm text-zinc-300"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              data-testid="company-logo-file"
            />
          </label>
          <p className="text-xs text-zinc-500">{t('profile.logo.hint')}</p>
          {preview && (
            <div className="flex h-16 w-32 items-center justify-center rounded border border-zinc-800 bg-white p-2">
              <img
                src={preview}
                alt={t('profile.logo.previewAlt')}
                className="max-h-full max-w-full object-contain"
              />
            </div>
          )}
          <label className="block text-sm text-zinc-400">
            {t('profile.logo.note')}
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={500}
              rows={2}
              className="mt-1 block w-full rounded border border-zinc-700 bg-zinc-900 p-2 text-sm text-zinc-100"
              placeholder={t('profile.logo.notePlaceholder')}
              data-testid="company-logo-note"
            />
          </label>
          <div className="flex gap-2">
            <Button
              type="button"
              disabled={!file || send.isPending}
              onClick={() => send.mutate()}
              data-testid="company-logo-send"
            >
              {send.isPending && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              {t('profile.logo.send')}
            </Button>
            <Button
              type="button"
              variant="ghost"
              disabled={send.isPending}
              onClick={() => {
                setFormOpen(false);
                setFile(null);
                setNote('');
              }}
            >
              {t('profile.logo.cancel')}
            </Button>
          </div>
          {send.isError && (
            <p className="text-sm text-red-400" data-testid="company-logo-error">
              {t(errorKey(send.error))}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export default CompanyLogoSection;
