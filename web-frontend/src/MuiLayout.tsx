/**
 * Lazy Material-UI theme boundary.
 *
 * Mounted as a PATHLESS layout route in App.tsx that wraps every route which renders
 * Material UI (auth, organizer, speaker, partner, account, registration flow, …).
 * Because this module is React.lazy-loaded, @mui/material + @emotion (the ~158 KB
 * `vendor-mui` chunk) is fetched only when one of those routes is visited — never for
 * the public, Tailwind-only homepage / archive / about pages, which are siblings of
 * this layout route. Child routes render into <Outlet/> under the ThemeProvider, so
 * they keep their absolute paths (no nested <Routes>).
 */

import { Outlet } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import theme from '@/theme';

export default function MuiLayout() {
  return (
    <ThemeProvider theme={theme}>
      <Outlet />
    </ThemeProvider>
  );
}
