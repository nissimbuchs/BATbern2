/**
 * CommunityTopicSuggestPanel — Story 7.1 "Topics From the Floor".
 *
 * Login-gated attendee surface: a logged-in attendee suggests a future event topic.
 * Submit-only (no browse). Flows into the existing topic pool tagged source=COMMUNITY.
 *
 * Tailwind-only by design — it lives on the PublicLayout-based AttendeeWelcomePage, which
 * must not import MUI (public-page bundle boundary). The route is login-gated upstream.
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import { Lightbulb } from 'lucide-react';
import { Card } from '@/components/public/ui/card';
import { Button } from '@/components/public/ui/button';
import { Input } from '@/components/public/ui/input';
import { Textarea } from '@/components/public/ui/textarea';
import { Label } from '@/components/public/ui/label';
import { suggestTopicAsAttendee } from '@/services/api/partnerTopicsApi';

const MIN_TITLE = 5;
const MAX_TITLE = 255;
const MAX_DESC = 500;

export const CommunityTopicSuggestPanel = () => {
  const { t } = useTranslation('common');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      suggestTopicAsAttendee({
        title: title.trim(),
        description: description.trim() || null,
      }),
    onSuccess: () => {
      setSubmitted(true);
      setTitle('');
      setDescription('');
    },
  });

  const titleValid = title.trim().length >= MIN_TITLE && title.trim().length <= MAX_TITLE;
  const descValid = description.length <= MAX_DESC;
  const canSubmit = titleValid && descValid && !mutation.isPending;

  return (
    <Card className="p-6 text-left" data-testid="community-topic-suggest-panel">
      <div className="flex items-center gap-2 mb-2">
        <Lightbulb className="h-5 w-5 text-blue-400" />
        <h2 className="text-sm font-medium text-zinc-300">{t('attendee.suggestTopic.heading')}</h2>
      </div>
      <p className="text-sm text-zinc-500 mb-4">{t('attendee.suggestTopic.intro')}</p>

      {submitted ? (
        <p className="text-sm text-green-400" data-testid="community-topic-success" role="status">
          {t('attendee.suggestTopic.success')}
        </p>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) mutation.mutate();
          }}
          className="space-y-4"
        >
          <div className="space-y-1.5">
            <Label htmlFor="community-topic-title">{t('attendee.suggestTopic.titleLabel')}</Label>
            <Input
              id="community-topic-title"
              data-testid="community-topic-title"
              value={title}
              maxLength={MAX_TITLE}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={t('attendee.suggestTopic.titlePlaceholder')}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="community-topic-description">
              {t('attendee.suggestTopic.descriptionLabel')}
            </Label>
            <Textarea
              id="community-topic-description"
              data-testid="community-topic-description"
              value={description}
              maxLength={MAX_DESC}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t('attendee.suggestTopic.descriptionPlaceholder')}
              rows={3}
            />
          </div>
          {mutation.isError && (
            <p className="text-sm text-red-400" data-testid="community-topic-error" role="alert">
              {t('attendee.suggestTopic.error')}
            </p>
          )}
          <Button type="submit" disabled={!canSubmit} data-testid="community-topic-submit">
            {mutation.isPending
              ? t('attendee.suggestTopic.submitting')
              : t('attendee.suggestTopic.submit')}
          </Button>
        </form>
      )}
    </Card>
  );
};

export default CommunityTopicSuggestPanel;
