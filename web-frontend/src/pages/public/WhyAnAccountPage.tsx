/**
 * WhyAnAccountPage — `/why-an-account` (story "Visitors see what a BATbern account gives them").
 *
 * Public, prerendered, in the sitemap like `/about`. Linked from the registration confirmation mail,
 * the wizard's last step and the success pages. Logged out it offers sign-up; a logged-in visitor
 * already has the account, so the button leads to the profile instead.
 */
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { UserCircle, UserPlus } from 'lucide-react';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Button } from '@/components/public/ui/button';
import { AccountBenefits } from '@/components/public/AccountBenefits/AccountBenefits';
import { useAuth } from '@/hooks/useAuth';

const WhyAnAccountPage = () => {
  const { t } = useTranslation('registration');
  const { isAuthenticated } = useAuth();

  const action = isAuthenticated ? (
    <Button asChild>
      <Link to="/profile">
        <UserCircle className="h-4 w-4 mr-2" aria-hidden="true" />
        {t('accountBenefits.profileButton')}
      </Link>
    </Button>
  ) : (
    <Button asChild>
      <Link to="/auth/register">
        <UserPlus className="h-4 w-4 mr-2" aria-hidden="true" />
        {t('accountBenefits.signUp')}
      </Link>
    </Button>
  );

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl">
        <AccountBenefits headingAs="h1" action={action} showMoreLink={false} />
      </div>
    </PublicLayout>
  );
};

export default WhyAnAccountPage;
