/**
 * SocialSharing Component (Story 4.1.4)
 * Social sharing buttons for LinkedIn, Twitter/X, and Email
 */

import { Button } from '@/components/public/ui/button';
import { Linkedin, Twitter, Mail, Share2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

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

        <p className="text-zinc-400 mb-6">
          {t('public.social.description')}
        </p>

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
              <Linkedin className="h-4 w-4" />
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
              <Twitter className="h-4 w-4" />
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
