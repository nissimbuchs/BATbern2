/**
 * SocialSharing Component (Story 4.1.4)
 * Social sharing buttons for LinkedIn, Twitter/X, and Email
 */

import { Button } from '@/components/public/ui/button';
import { Mail, Share2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const LinkedInIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="currentColor" className={className}>
    <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 01-2.063-2.065 2.064 2.064 0 112.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z" />
  </svg>
);

const XIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="currentColor" className={className}>
    <path d="M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z" />
  </svg>
);

interface SocialSharingProps {
  eventTitle: string;
  eventUrl: string;
}

export const SocialSharing = ({ eventTitle, eventUrl }: SocialSharingProps) => {
  const { t } = useTranslation('events');
  const shareText = t('public.social.shareText', { title: eventTitle });

  const shareLinks = {
    linkedin: `https://www.linkedin.com/sharing/share-offsite/?url=${encodeURIComponent(eventUrl)}`,
    twitter: `https://twitter.com/intent/tweet?text=${encodeURIComponent(shareText)}&url=${encodeURIComponent(eventUrl)}`,
    email: `mailto:?subject=${encodeURIComponent(eventTitle)}&body=${encodeURIComponent(`${shareText}\n\n${eventUrl}`)}`,
  };

  return (
    <div className="py-12">
      <div className="bg-zinc-900/50 border border-zinc-800 rounded-lg p-8">
        <div className="flex items-center gap-3 mb-6">
          <Share2 className="h-6 w-6 text-blue-400" />
          <h2 className="text-2xl font-light text-zinc-100">{t('public.social.title')}</h2>
        </div>

        <p className="text-zinc-400 mb-6">{t('public.social.description')}</p>

        <div className="flex flex-wrap gap-3">
          <Button
            asChild
            variant="outline"
            className="border-zinc-800 hover:border-[#0A66C2] hover:bg-[#0A66C2]/10 hover:text-[#0A66C2]"
          >
            <a
              href={shareLinks.linkedin}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2"
            >
              <LinkedInIcon className="h-4 w-4" />
              {t('public.social.shareLinkedIn')}
            </a>
          </Button>

          <Button
            asChild
            variant="outline"
            className="border-zinc-800 hover:border-[#1DA1F2] hover:bg-[#1DA1F2]/10 hover:text-[#1DA1F2]"
          >
            <a
              href={shareLinks.twitter}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2"
            >
              <XIcon className="h-4 w-4" />
              {t('public.social.shareTwitter')}
            </a>
          </Button>

          <Button
            asChild
            variant="outline"
            className="border-zinc-800 hover:border-blue-400 hover:bg-blue-400/10 hover:text-blue-400"
          >
            <a href={shareLinks.email} className="flex items-center gap-2">
              <Mail className="h-4 w-4" />
              {t('public.social.shareEmail')}
            </a>
          </Button>
        </div>
      </div>
    </div>
  );
};
