/**
 * Paths that once had a page of their own and now lead to where that work is done.
 *
 * `/organizer/speakers` and `/speakers` showed a placeholder ("Speaker coordination interface")
 * from the Story 1.17 skeleton that never got content (#1223 item 8). Organizers find speakers
 * among the users (role filter) and coordinate them on each event's Speakers tab; speakers have
 * their portal. A bookmark or an old link still arrives somewhere useful.
 */
import { Navigate, Route } from 'react-router-dom';

export const RETIRED_ROUTES: ReadonlyArray<{ from: string; to: string }> = [
  { from: '/organizer/speakers', to: '/organizer/users' },
  { from: '/speakers', to: '/speaker-portal/dashboard' },
];

/** The redirects as `<Route>` elements, for the app's `<Routes>`. */
export function retiredRouteRedirects() {
  return RETIRED_ROUTES.map(({ from, to }) => (
    <Route key={from} path={from} element={<Navigate to={to} replace />} />
  ));
}
