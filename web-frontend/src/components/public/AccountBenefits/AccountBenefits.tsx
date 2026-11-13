/**
 * AccountBenefits — the four things a free BATbern account gives a visitor (story "Visitors see
 * what a BATbern account gives them", owner-approved wording 2026-11-13).
 *
 * The one place the benefits are listed in the frontend: the registration wizard's last step, the
 * registration success page, the confirmation page and the public page `/why-an-account` all
 * render this component from the `registration:accountBenefits` block. The confirmation mail
 * (EMS templates, DE + EN) carries the same four items.
 */
import type { ElementType, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Building2, History, Lightbulb, MessageSquare, UserPlus } from 'lucide-react';
import { Button } from '@/components/public/ui/button';

/** The public page that explains the account. */
export const WHY_AN_ACCOUNT_PATH = '/why-an-account';

export interface AccountBenefitsProps {
  /** Heading element; `h1` on the account page, `h3` inside a card (default). */
  headingAs?: 'h1' | 'h2' | 'h3';
  /** Where "Create a free account" leads; no button when omitted. */
  signUpTo?: string;
  /** Replaces the sign-up button (the account page leads a logged-in visitor to the profile). */
  action?: ReactNode;
  /** Show "More about the account" (default true). */
  showMoreLink?: boolean;
  /** Open the account page in a new tab, so a half-filled form is kept (the wizard). */
  moreLinkInNewTab?: boolean;
  className?: string;
}

export const AccountBenefits = ({
  headingAs = 'h3',
  signUpTo,
  action,
  showMoreLink = true,
  moreLinkInNewTab = false,
  className,
}: AccountBenefitsProps) => {
  const { t } = useTranslation('registration');
  const Heading: ElementType = headingAs;
  // Literal keys, in the owner's order, so the i18n key gate sees each of them
  const benefits = [
    { key: 'qna', icon: MessageSquare, text: t('accountBenefits.items.qna') },
    { key: 'topics', icon: Lightbulb, text: t('accountBenefits.items.topics') },
    { key: 'history', icon: History, text: t('accountBenefits.items.history') },
    { key: 'logo', icon: Building2, text: t('accountBenefits.items.logo') },
  ];
  const headingClass =
    headingAs === 'h1'
      ? 'text-4xl md:text-5xl font-light mb-8 text-zinc-100'
      : 'text-lg font-light mb-3 text-zinc-100';

  return (
    <div className={className} data-testid="account-benefits">
      <Heading className={headingClass}>{t('accountBenefits.title')}</Heading>
      <ul className="space-y-3" data-testid="account-benefits-list">
        {benefits.map(({ key, icon: Icon, text }) => (
          <li key={key} className="flex items-start gap-3 text-sm text-zinc-300">
            <Icon className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" aria-hidden="true" />
            <span>{text}</span>
          </li>
        ))}
      </ul>

      {(action || signUpTo || showMoreLink) && (
        <div className="mt-6 flex flex-col sm:flex-row sm:items-center gap-4">
          {action ??
            (signUpTo && (
              <Button asChild variant="outline" size="sm">
                <Link to={signUpTo}>
                  <UserPlus className="h-4 w-4 mr-2" aria-hidden="true" />
                  {t('accountBenefits.signUp')}
                </Link>
              </Button>
            ))}
          {showMoreLink && (
            <Link
              to={WHY_AN_ACCOUNT_PATH}
              className="text-sm text-blue-400 hover:underline"
              {...(moreLinkInNewTab ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
            >
              {t('accountBenefits.moreLink')}
            </Link>
          )}
        </div>
      )}
    </div>
  );
};
