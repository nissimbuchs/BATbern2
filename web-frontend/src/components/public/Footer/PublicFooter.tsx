/**
 * PublicFooter Component
 * Story 4.1.2: Public Layout & Navigation
 *
 * Footer for public pages with:
 * - Contact information
 * - Quick links
 * - Legal links
 * - Copyright notice
 */

import { Link } from 'react-router-dom';

export const PublicFooter = () => {
  return (
    <footer className="border-t border-zinc-800 bg-zinc-900">
      <div className="container mx-auto px-4 py-8">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
          {/* Branding */}
          <div>
            <h3 className="text-lg font-light">BATbern</h3>
            <p className="mt-2 text-sm text-zinc-400">
              Berner Architekten Treffen - Where innovation meets tradition
            </p>
          </div>

          {/* Links */}
          <div>
            <h4 className="text-sm font-medium text-zinc-300">Quick Links</h4>
            <ul className="mt-2 space-y-2">
              <li>
                <Link to="/current-event" className="text-sm text-zinc-400 hover:text-blue-400">
                  Current Event
                </Link>
              </li>
              <li>
                <Link to="/archive" className="text-sm text-zinc-400 hover:text-blue-400">
                  Past Events
                </Link>
              </li>
              <li>
                <Link to="/about" className="text-sm text-zinc-400 hover:text-blue-400">
                  About BATbern
                </Link>
              </li>
            </ul>
          </div>

          {/* Legal */}
          <div>
            <h4 className="text-sm font-medium text-zinc-300">Legal</h4>
            <ul className="mt-2 space-y-2">
              <li>
                <Link to="/privacy" className="text-sm text-zinc-400 hover:text-blue-400">
                  Privacy Policy
                </Link>
              </li>
              <li>
                <Link to="/terms" className="text-sm text-zinc-400 hover:text-blue-400">
                  Terms of Service
                </Link>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-8 border-t border-zinc-800 pt-8 text-center">
          <p className="text-sm text-zinc-400">
            © {new Date().getFullYear()} Berner Architekten Treffen. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
};
