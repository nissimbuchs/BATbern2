/**
 * HomePage Component
 * Story 4.1.2: Public Layout & Navigation
 *
 * Landing page for public BATbern website.
 * Displays hero section with CTA to view current event.
 */

import { Link } from 'react-router-dom';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Button } from '@/components/public/ui/button';
import { ArrowRight } from 'lucide-react';

const HomePage = () => {
  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-24">
        <div className="max-w-3xl mx-auto text-center">
          <h1 className="text-5xl font-light tracking-tight md:text-6xl">
            Welcome to BATbern
          </h1>
          <p className="mt-6 text-xl text-zinc-400 font-light">
            Berner Architekten Treffen brings together architects, engineers, and
            technology leaders for inspiring networking events in Bern, Switzerland.
          </p>
          <div className="mt-10">
            <Button asChild size="lg" className="group">
              <Link to="/current-event">
                View Current Event
                <ArrowRight className="ml-2 h-4 w-4 transition-transform group-hover:translate-x-1" />
              </Link>
            </Button>
          </div>
        </div>
      </div>
    </PublicLayout>
  );
};

export default HomePage;
