/**
 * Scrolls to the element with id `fragment`, if it is on the page. React Router does not scroll
 * to a #fragment by itself; the "What's new" links and menu items point to ones on the dashboard.
 */
export function scrollToFragment(fragment: string): boolean {
  const element = document.getElementById(fragment);
  if (!element) return false;
  if (typeof element.scrollIntoView === 'function') {
    element.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  return true;
}
