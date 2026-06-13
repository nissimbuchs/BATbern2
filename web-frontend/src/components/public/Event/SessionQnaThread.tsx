/**
 * SessionQnaThread (Story 7.5 — "The Apéro Continues")
 *
 * Tailwind-only (NO MUI) per-session Q&A thread, mounted on the public session card both
 * PRE-event (SPEAKERS_PUBLISHED trigger / manual organizer open) and post-event/archive.
 * Anyone can read; logged-in users can post questions + one-level answers while the window is OPEN;
 * organizers can take down posts. A FROZEN window is read-only (the permanent archive thread).
 * Removed posts render as "removed by organizer" tombstones, preserving thread structure.
 *
 * The thread is COLLAPSED by default behind a compact "Ask a question" toggle — discoverable but
 * unobtrusive when shown under many session cards (e.g. pre-event with empty threads).
 */

import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useSessionQna, useAddQnaPost, useRemoveQnaPost } from '@/hooks/useQna/useQna';
import type { QnaPostResponse } from '@/services/qnaService';
import { SpeakerDisplay } from '@/components/public/Event/SpeakerDisplay';
import type { SessionSpeaker } from '@/types/event.types';

/**
 * Map a Q&A post's enriched poster fields onto the {@link SessionSpeaker} shape so the same
 * portrait + name + company-logo component used on the public speaker cards renders the author.
 * The portrait image is omitted on purpose — SpeakerDisplay lazy-loads it from the public-user
 * endpoint (usePublicUser) by username. Name + company come from the server-side read enrichment,
 * exactly as the speaker cards source them. Falls back to the username when the name is unknown.
 */
function postToSpeaker(post: QnaPostResponse): SessionSpeaker {
  const firstName = post.postedByFirstName?.trim() || post.postedByUsername || '';
  return {
    username: post.postedByUsername ?? '',
    firstName,
    lastName: post.postedByLastName ?? '',
    company: post.postedByCompanyName ?? undefined,
    companyDisplayName: post.postedByCompanyName ?? undefined,
    companyLogoUrl: post.postedByCompanyLogoUrl ?? undefined,
    // Required by SessionSpeaker but irrelevant for a Q&A poster (SpeakerDisplay ignores them).
    speakerRole: 'PRIMARY_SPEAKER',
    isConfirmed: true,
  };
}

interface SessionQnaThreadProps {
  eventCode: string;
  sessionSlug: string;
}

export function SessionQnaThread({ eventCode, sessionSlug }: SessionQnaThreadProps) {
  const { t } = useTranslation('events');
  const { isAuthenticated, hasRole } = useAuth();
  const isOrganizer = hasRole('organizer');

  const { data: thread, isError } = useSessionQna(eventCode, sessionSlug, true);
  const addPost = useAddQnaPost(eventCode, sessionSlug);
  const removePost = useRemoveQnaPost(eventCode, sessionSlug);

  const [expanded, setExpanded] = useState(false);
  const [question, setQuestion] = useState('');
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyBody, setReplyBody] = useState('');

  // No window for this session (event not completed, or unknown) → render nothing.
  if (isError || !thread) {
    return null;
  }

  const isOpen = thread.status === 'OPEN';
  // Taken-down posts are removed from the thread entirely (the backend already drops them; this
  // is a defensive client-side guard so a removed post never renders or inflates the count).
  const posts = (thread.posts ?? []).filter((p) => !p.removed);
  const questions = posts.filter((p) => !p.parentPostId);
  const answersByParent = posts.reduce<Record<string, QnaPostResponse[]>>((acc, p) => {
    if (p.parentPostId) {
      (acc[p.parentPostId] ??= []).push(p);
    }
    return acc;
  }, {});

  function submitQuestion(e: React.FormEvent) {
    e.preventDefault();
    if (!question.trim()) return;
    addPost.mutate({ body: question.trim() }, { onSuccess: () => setQuestion('') });
  }

  function submitReply(e: React.FormEvent, parentPostId: string) {
    e.preventDefault();
    if (!replyBody.trim()) return;
    addPost.mutate(
      { body: replyBody.trim(), parentPostId },
      {
        onSuccess: () => {
          setReplyBody('');
          setReplyTo(null);
        },
      }
    );
  }

  function renderPost(post: QnaPostResponse, isAnswer: boolean) {
    return (
      <div
        key={post.id}
        className={`rounded bg-zinc-800/40 p-3 ${isAnswer ? 'ml-6 mt-2' : ''}`}
        data-testid="qna-post"
      >
        <SpeakerDisplay speaker={postToSpeaker(post)} size="small" />
        <p className="mt-2 whitespace-pre-wrap text-sm text-zinc-200">{post.body}</p>
        {isOrganizer && (
          <button
            type="button"
            onClick={() => removePost.mutate(post.id)}
            className="mt-1 text-xs text-red-400 hover:text-red-300"
            data-testid="qna-takedown"
          >
            {t('qna.takedown')}
          </button>
        )}
      </div>
    );
  }

  const questionCount = questions.length;
  const panelId = `qna-panel-${sessionSlug}`;

  return (
    <div className="pt-2 border-t border-zinc-800" data-testid="qna-thread">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        aria-controls={panelId}
        data-testid="qna-toggle"
        className="flex w-full items-center gap-2 rounded py-1 text-left transition-colors hover:text-zinc-100"
      >
        <span className="text-sm text-zinc-300">💬 {t('qna.title')}</span>
        <span
          className={`rounded px-1.5 py-0.5 text-[10px] uppercase ${
            isOpen ? 'bg-green-900/50 text-green-300' : 'bg-zinc-700 text-zinc-400'
          }`}
          data-testid="qna-status"
        >
          {isOpen ? t('qna.statusOpen') : t('qna.statusFrozen')}
        </span>
        {questionCount > 0 && (
          <span
            className="rounded-full bg-zinc-700 px-1.5 py-0.5 text-[10px] text-zinc-300"
            data-testid="qna-count"
          >
            {questionCount}
          </span>
        )}
        <span className="ml-auto flex items-center gap-1 text-xs text-blue-400">
          {!expanded && (isOpen ? t('qna.askQuestion') : t('qna.viewThread'))}
          <ChevronDown
            className={`h-4 w-4 transition-transform ${expanded ? 'rotate-180' : ''}`}
            aria-hidden="true"
          />
        </span>
      </button>

      {expanded && (
        <div id={panelId} className="mt-3" data-testid="qna-panel">
          {questions.length === 0 && (
            <p className="mb-3 text-sm text-zinc-500" data-testid="qna-empty">
              {t('qna.empty')}
            </p>
          )}

          <div className="space-y-3">
            {questions.map((q) => (
              <div key={q.id}>
                {renderPost(q, false)}
                {(answersByParent[q.id] ?? []).map((a) => renderPost(a, true))}

                {isOpen && isAuthenticated && (
                  <div className="ml-6 mt-2">
                    {replyTo === q.id ? (
                      <form onSubmit={(e) => submitReply(e, q.id)} className="flex flex-col gap-2">
                        <textarea
                          value={replyBody}
                          onChange={(e) => setReplyBody(e.target.value)}
                          maxLength={5000}
                          rows={2}
                          placeholder={t('qna.replyPlaceholder')}
                          aria-label={t('qna.replyPlaceholder')}
                          className="w-full resize-none rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-zinc-200"
                          data-testid="qna-reply-input"
                        />
                        <div className="flex gap-2">
                          <button
                            type="submit"
                            disabled={addPost.isPending}
                            className="rounded bg-blue-600 px-3 py-1 text-xs text-white hover:bg-blue-500 disabled:opacity-50"
                            data-testid="qna-reply-submit"
                          >
                            {t('qna.reply')}
                          </button>
                          <button
                            type="button"
                            onClick={() => setReplyTo(null)}
                            className="text-xs text-zinc-400 hover:text-zinc-300"
                          >
                            {t('qna.cancel')}
                          </button>
                        </div>
                      </form>
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          setReplyTo(q.id);
                          setReplyBody('');
                        }}
                        className="text-xs text-blue-400 hover:text-blue-300"
                        data-testid="qna-reply-button"
                      >
                        {t('qna.reply')}
                      </button>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>

          {isOpen && isAuthenticated && (
            <form
              onSubmit={submitQuestion}
              className="mt-4 flex flex-col gap-2"
              data-testid="qna-question-form"
            >
              <textarea
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                maxLength={5000}
                rows={2}
                placeholder={t('qna.questionPlaceholder')}
                aria-label={t('qna.questionPlaceholder')}
                className="w-full resize-none rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-zinc-200"
                data-testid="qna-question-input"
              />
              <button
                type="submit"
                disabled={addPost.isPending}
                className="self-start rounded bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-500 disabled:opacity-50"
                data-testid="qna-question-submit"
              >
                {t('qna.askQuestion')}
              </button>
            </form>
          )}

          {isOpen && !isAuthenticated && (
            <p className="mt-3 text-xs text-zinc-500" data-testid="qna-login-hint">
              {t('qna.loginToPost')}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
