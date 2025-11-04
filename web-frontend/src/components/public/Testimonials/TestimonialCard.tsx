/**
 * TestimonialCard Component
 * Displays a testimonial with avatar, name, quote, and company
 */

import { Card } from '@/components/public/ui/card';

interface TestimonialCardProps {
  avatar?: string;
  name: string;
  quote: string;
  company: string;
}

export const TestimonialCard = ({ avatar, name, quote, company }: TestimonialCardProps) => {
  // Get initials from first and last name
  const getInitials = (fullName: string) => {
    const names = fullName.trim().split(' ');
    if (names.length >= 2) {
      return `${names[0].charAt(0)}${names[names.length - 1].charAt(0)}`;
    }
    return names[0].charAt(0);
  };

  return (
    <Card className="flex-shrink-0 w-80 p-6 bg-zinc-900 border-zinc-800 hover:border-zinc-700 transition-colors">
      <div className="flex items-start gap-4">
        {/* Avatar */}
        <div className="flex-shrink-0">
          {avatar ? (
            <img
              src={avatar}
              alt={name}
              className="w-12 h-12 rounded-full object-cover"
            />
          ) : (
            <div className="w-12 h-12 rounded-full bg-primary flex items-center justify-center">
              <span className="text-base font-semibold text-primary-foreground">
                {getInitials(name)}
              </span>
            </div>
          )}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <p className="text-sm text-zinc-300 italic mb-3 line-clamp-3">
            "{quote}"
          </p>
          <div>
            <p className="text-sm font-medium text-zinc-100">{name}</p>
            <p className="text-xs text-zinc-400">{company}</p>
          </div>
        </div>
      </div>
    </Card>
  );
};
