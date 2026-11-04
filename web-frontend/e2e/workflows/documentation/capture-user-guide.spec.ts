/**
 * User-guide screenshot capture: READ-ONLY.
 *
 * Captures the screenshots for docs/user-guide from LOCAL DEV running an ANONYMIZED copy of
 * production. It only navigates, switches tabs and opens menus/dialogs, which it closes again.
 * It never clicks a button that saves, sends, deletes or otherwise writes.
 *
 * Belt and braces: every context aborts any non-GET request to the local API (logged as
 * "[blocked]"), and the Live Control poll is sent without the organizer token so it does not
 * write the presence heartbeat (live_timing_presence).
 *
 * Run (local dev must be up; tokens in ~/.batbern/staging-{organizer,speaker,partner}.json):
 *   cd web-frontend
 *   npx playwright test -c playwright.user-guide.config.ts --workers=1
 *   ONLY=public,partner npx playwright test -c playwright.user-guide.config.ts --workers=1
 *   SHOT_ROOT=/tmp/shots ...   # write somewhere else (exploration)
 *
 * Output: docs/user-guide/assets/screenshots/<area>/<name>.png
 */
import { test, expect, type Browser, type BrowserContext, type Page } from '@playwright/test';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { forceUserProfileLanguage } from '../../helpers/mock-user-profile';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHOT_ROOT =
  process.env.SHOT_ROOT ??
  path.resolve(__dirname, '../../../../docs/user-guide/assets/screenshots');
const ONLY = (process.env.ONLY ?? '').split(',').filter(Boolean);
const SHOT = (process.env.SHOT ?? '').split(',').filter(Boolean); // filter by name substring

const MAIN_EVENT = 'BATbern60'; // AGENDA_PUBLISHED, event day 2026-11-06
const ARCHIVED_EVENT = 'BATbern59'; // archived, has photos and thank-you notes

const DESKTOP = { width: 1440, height: 900 };
const PHONE = { width: 390, height: 844 };

type Role = 'organizer' | 'speaker' | 'partner' | 'public';

// ── auth ──────────────────────────────────────────────────────────────────────────────────

function readTokens(role: Exclude<Role, 'public'>) {
  const file = path.join(os.homedir(), '.batbern', `staging-${role}.json`);
  const { idToken, accessToken, refreshToken } = JSON.parse(fs.readFileSync(file, 'utf8'));
  const claims = JSON.parse(Buffer.from(idToken.split('.')[1], 'base64url').toString());
  if (claims.exp * 1000 < Date.now() + 5 * 60_000) {
    throw new Error(`Token for ${role} expired: run scripts/auth/refresh-token.sh staging ${role}`);
  }
  return { idToken, accessToken, refreshToken };
}

const blocked: string[] = [];

async function newRoleContext(
  browser: Browser,
  role: Role,
  viewport = DESKTOP
): Promise<BrowserContext> {
  const isPhone = viewport.width < 600;
  const context = await browser.newContext({
    viewport,
    deviceScaleFactor: isPhone ? 2 : 1,
    isMobile: isPhone,
    hasTouch: isPhone,
    locale: 'en-US',
    timezoneId: 'Europe/Zurich',
  });

  const tokens = role === 'public' ? null : readTokens(role);
  await context.addInitScript((t) => {
    try {
      localStorage.setItem('batbern-language', 'en');
      localStorage.setItem('i18nextLng', 'en');
      if (!t) return;
      const payload = JSON.parse(
        decodeURIComponent(
          atob(t.idToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))
            .split('')
            .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
            .join('')
        )
      );
      const prefix = `CognitoIdentityServiceProvider.${payload.aud}`;
      const user = payload['cognito:username'];
      if (localStorage.getItem(`${prefix}.LastAuthUser`)) return; // keep refreshed tokens
      localStorage.setItem(`${prefix}.${user}.idToken`, t.idToken);
      localStorage.setItem(`${prefix}.${user}.accessToken`, t.accessToken);
      localStorage.setItem(`${prefix}.${user}.refreshToken`, t.refreshToken);
      localStorage.setItem(`${prefix}.${user}.clockDrift`, '0');
      localStorage.setItem(`${prefix}.LastAuthUser`, user);
      localStorage.setItem('authToken', t.idToken);
      localStorage.setItem('idToken', t.idToken);
      localStorage.setItem(
        'auth',
        JSON.stringify({
          idToken: t.idToken,
          accessToken: t.accessToken,
          refreshToken: t.refreshToken,
          expiresAt: payload.exp * 1000,
          isAuthenticated: true,
        })
      );
    } catch {
      /* ignore */
    }
  }, tokens);

  // READ-ONLY guard: abort every write to the local backend.
  await context.route(/localhost:(8000|8100)\/api\//, async (route) => {
    const req = route.request();
    const method = req.method();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      blocked.push(`${method} ${req.url()}`);
      console.log(`[blocked] ${method} ${req.url()}`);
      await route.abort('blockedbyclient');
      return;
    }
    // Live Control poll: drop the token so the backend does not write a presence heartbeat.
    if (/\/live-timing(\?|$)/.test(req.url())) {
      const headers = { ...req.headers() };
      delete headers['authorization'];
      await route.continue({ headers });
      return;
    }
    await route.fallback();
  });
  return context;
}

async function newRolePage(context: BrowserContext, role: Role): Promise<Page> {
  const page = await context.newPage();
  if (role !== 'public') await forceUserProfileLanguage(page, 'en');
  page.on('pageerror', (e) => console.log(`[pageerror] ${e.message.split('\n')[0]}`));
  return page;
}

// ── waiting + capture ─────────────────────────────────────────────────────────────────────

async function settle(page: Page, extraMs = 800) {
  // Never networkidle (Turnstile on the homepage, Vite lazy chunks). Wait for spinners instead.
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(600);
  const spinners = page.locator(
    '[role="progressbar"]:visible, .MuiCircularProgress-root:visible, .MuiSkeleton-root:visible'
  );
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    if ((await spinners.count()) === 0) break;
    await page.waitForTimeout(400);
  }
  const loadingText = page.getByText(/^Loading\.*$/);
  if (
    await loadingText
      .first()
      .isVisible()
      .catch(() => false)
  ) {
    await loadingText
      .first()
      .waitFor({ state: 'hidden', timeout: 20_000 })
      .catch(() => {});
  }
  await page.waitForTimeout(extraMs);
}

async function goto(page: Page, url: string, extraMs?: number) {
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  await settle(page, extraMs);
}

async function shot(
  page: Page,
  area: string,
  name: string,
  opts: { fullPage?: boolean; locator?: ReturnType<Page['locator']> } = {}
) {
  if (SHOT.length && !SHOT.some((s) => `${area}/${name}`.includes(s))) return;
  const dir = path.join(SHOT_ROOT, area);
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${name}.png`);
  // Hide caret / scrollbars / dev overlays for clean images.
  await page.addStyleTag({
    content: `*{caret-color:transparent!important} ::-webkit-scrollbar{display:none}
      vite-error-overlay,#tanstack-query-devtools,.tsqd-parent-container{display:none!important}`,
  });
  if (opts.locator) await opts.locator.screenshot({ path: file, animations: 'disabled' });
  else await page.screenshot({ path: file, fullPage: !!opts.fullPage, animations: 'disabled' });
  console.log(`[shot] ${area}/${name}.png`);
}

const want = (role: Role) => ONLY.length === 0 || ONLY.includes(role);

/** Run one capture step; a failing step is logged and skipped, the rest still runs. */
const failed: string[] = [];
async function step(label: string, fn: () => Promise<void>) {
  try {
    await fn();
  } catch (e) {
    failed.push(label);
    console.log(`[step-failed] ${label}: ${(e as Error).message.split('\n')[0]}`);
  }
}

async function closeOverlay(page: Page) {
  // Close dialogs/menus/drawers WITHOUT saving: Escape only.
  await page.keyboard.press('Escape');
  await page.waitForTimeout(400);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
}

const WF = 'workflow';

// ── PUBLIC (anonymous) ────────────────────────────────────────────────────────────────────

test.describe('public', () => {
  test.skip(!want('public'), 'filtered by ONLY');
  test.setTimeout(300_000);

  test('public pages', async ({ browser }) => {
    const ctx = await newRoleContext(browser, 'public');
    const page = await newRolePage(ctx, 'public');

    await step('homepage', async () => {
      await goto(page, '/', 2500);
      await shot(page, 'public', 'homepage');
    });
    await step('archive detail', async () => {
      await goto(page, `/archive/${ARCHIVED_EVENT}`, 2500);
      // Scroll so the first session card's Q&A link sits near the bottom of the viewport.
      await page.locator(':text("Q&A"):visible').first().scrollIntoViewIfNeeded();
      await page.mouse.wheel(0, 250);
      await page.waitForTimeout(800);
      await shot(page, 'public', 'archive-event-sessions');
    });
    await step('login', async () => {
      await goto(page, '/login', 1000);
      await shot(page, 'getting-started', 'login-page');
    });
    await step('forgot password', async () => {
      await goto(page, '/auth/forgot-password', 1000);
      await shot(page, 'getting-started', 'forgot-password');
    });
    await step('presentation', async () => {
      await goto(page, `/present/${MAIN_EVENT}`, 3000);
      await shot(page, 'features', 'presentation-page');
    });
    await ctx.close();
  });
});

// ── ORGANIZER ─────────────────────────────────────────────────────────────────────────────

test.describe('organizer', () => {
  test.skip(!want('organizer'), 'filtered by ONLY');
  test.setTimeout(600_000);

  test('organizer: navigation, lists, admin', async ({ browser }) => {
    const ctx = await newRoleContext(browser, 'organizer');
    const page = await newRolePage(ctx, 'organizer');

    await step('events list', async () => {
      await goto(page, '/organizer/events', 1500);
      await shot(page, 'getting-started', 'events-list');
      await shot(page, `${WF}/phase-a-setup`, 'a-01-event-dashboard');
      await shot(page, 'getting-started', 'top-bar', { locator: page.locator('header').first() });
    });
    await step('new event dialog', async () => {
      await page
        .getByRole('button', { name: /new event/i })
        .first()
        .click();
      const dlg = page.getByRole('dialog');
      await dlg.waitFor();
      await settle(page, 800);
      await shot(page, `${WF}/phase-a-setup`, 'a-02-event-creation-modal');
      const typeSelect = dlg
        .getByRole('combobox')
        .filter({ hasText: /event|day|evening|afternoon/i });
      const combo = (await typeSelect.count())
        ? typeSelect.first()
        : dlg.getByLabel(/event type/i).first();
      await combo.click();
      await page.getByRole('listbox').waitFor();
      await page.waitForTimeout(500);
      await shot(page, `${WF}/phase-a-setup`, 'a-03-event-type-dropdown-open');
      await closeOverlay(page);
    });
    await step('task board', async () => {
      await goto(page, '/organizer/tasks', 1500);
      const filter = page.getByRole('combobox').first();
      await filter.click();
      await page.getByRole('option', { name: /all tasks/i }).click();
      await settle(page, 1200);
      await shot(page, 'features', 'task-board');
    });
    for (const [url, area, name] of [
      ['/organizer/companies', 'entity-management', 'companies-list'],
      ['/organizer/users', 'entity-management', 'users-list'],
      ['/organizer/partners', 'entity-management', 'partners-list'],
      ['/organizer/partner-meetings', 'partner-portal', 'organizer-partner-meetings'],
      ['/organizer/partner-topics', 'partner-portal', 'organizer-partner-topics'],
      ['/organizer/analytics', 'features', 'analytics'],
      ['/organizer/mail', 'features', 'mail-center'],
    ] as const) {
      await step(name, async () => {
        await goto(page, url, 1500);
        await shot(page, area, name);
      });
    }
    await step('topic heat map', async () => {
      await goto(page, '/organizer/topics', 1500);
      await page
        .getByRole('button', { name: /heat map/i })
        .first()
        .click();
      await settle(page, 2000);
      await shot(page, `${WF}/phase-a-setup`, 'a-17-topic-heatmap');
    });
    const adminTabs = [
      'event-types',
      'import-data',
      'task-templates',
      'email-templates',
      'presentation',
      'ai-prompts',
      'settings',
      'global-images',
      // tab 8 'Venue & Catering' is NOT captured: the snapshot keeps the contacts' real first names
    ];
    for (let i = 0; i < adminTabs.length; i++) {
      await step(`admin ${i}`, async () => {
        await goto(page, `/organizer/admin?tab=${i}`, 1200);
        await shot(page, 'administration', `admin-${adminTabs[i]}`);
      });
    }
    await ctx.close();
  });

  test('organizer: event page', async ({ browser }) => {
    const ctx = await newRoleContext(browser, 'organizer');
    const page = await newRolePage(ctx, 'organizer');
    const base = `/organizer/events/${MAIN_EVENT}`;

    await step('cockpit', async () => {
      await goto(page, base, 2000);
      await shot(page, 'event-page', 'cockpit');
      const glance = page.getByText('At a glance').first();
      await glance.scrollIntoViewIfNeeded();
      await page.mouse.wheel(0, 250);
      await page.waitForTimeout(600);
      await shot(page, 'event-page', 'cockpit-at-a-glance');
    });
    await step('pool', async () => {
      await goto(page, `${base}?tab=speakers&view=pool`, 2000);
      await shot(page, 'event-page', 'speakers-pool');
      await page.getByText('Status Lanes').first().scrollIntoViewIfNeeded();
      await page.mouse.wheel(0, 200);
      await page.waitForTimeout(600);
      await shot(page, `${WF}/phase-b-outreach`, 'b-01-outreach-view-ready');
    });
    await step('add speakers dialog', async () => {
      await goto(page, `${base}?tab=speakers&view=pool`, 1500);
      await page
        .getByRole('button', { name: /add speakers/i })
        .first()
        .click();
      await page.getByRole('dialog').waitFor();
      await settle(page, 800);
      await shot(page, `${WF}/phase-a-setup`, 'a-20-speaker-brainstorming-form');
      await closeOverlay(page);
    });
    await step('speaker drawer', async () => {
      await goto(page, `${base}?tab=speakers&view=pool`, 1500);
      await page.getByText('Status Lanes').first().scrollIntoViewIfNeeded();
      await page.getByText('Quality Reviewed').first().click();
      await page.locator('.MuiDrawer-paper, [role="dialog"]').first().waitFor();
      await settle(page, 1200);
      await shot(page, `${WF}/phase-c-quality`, 'c-04-quality-review-1-opened');
      await closeOverlay(page);
    });
    await step('agenda', async () => {
      await goto(page, `${base}?tab=speakers&view=agenda`, 2000);
      await shot(page, 'event-page', 'speakers-agenda');
      await shot(page, `${WF}/phase-d-publishing`, 'd-02-sessions-view-loaded');
    });
    await step('materials drawer', async () => {
      await page
        .getByRole('button', { name: /^materials$/i })
        .first()
        .click();
      await page.locator('.MuiDrawer-paper, [role="dialog"]').first().waitFor();
      await settle(page, 1200);
      await shot(page, `${WF}/phase-b5-content`, 'b5-04-content-submission-drawer-1-opened');
      await closeOverlay(page);
    });
    await step('slots', async () => {
      await goto(page, `${base}?tab=speakers&view=slots`, 2000);
      await shot(page, `${WF}/phase-d-publishing`, 'd-03-slot-assignment-page-loaded');
    });
    await step('registrations', async () => {
      await goto(page, `${base}?tab=registrations`, 2000);
      await shot(page, 'event-page', 'registrations');
    });
    await step('communications', async () => {
      await goto(page, `${base}?tab=communications`, 2000);
      await shot(page, 'event-page', 'communications');
      await page
        .getByRole('button', { name: /new mail/i })
        .first()
        .click();
      await page.waitForTimeout(1200);
      await shot(page, 'features', 'event-mail-new-mail');
      await closeOverlay(page);
    });
    await step('publishing', async () => {
      await goto(page, `${base}?tab=publishing`, 2500);
      await shot(page, 'event-page', 'publishing');
      await shot(page, `${WF}/phase-d-publishing`, 'd-10-agenda-published');
    });
    await step('details', async () => {
      await goto(page, `${base}?tab=details`, 2000);
      await shot(page, 'event-page', 'details-info');
      await page.getByRole('tab', { name: /^tasks$/i }).click();
      await settle(page, 1500);
      await shot(page, 'event-page', 'details-tasks');
      await page.getByRole('tab', { name: /^settings$/i }).click();
      await settle(page, 1500);
      await shot(page, 'event-page', 'details-settings');
    });
    await step('change topic', async () => {
      await goto(page, `${base}?tab=details`, 1500);
      await page
        .getByRole('button', { name: /change topic/i })
        .first()
        .click();
      await page.waitForTimeout(800);
      await settle(page, 1500);
      await shot(page, `${WF}/phase-a-setup`, 'a-16-topic-selection-opened');
      await closeOverlay(page);
    });
    await step('wrap-up', async () => {
      await goto(page, `/organizer/events/${ARCHIVED_EVENT}?tab=wrapup`, 2500);
      // The photo grid itself is not captured (speakers' faces); only the thank-you notes.
      const notes = page.getByText(/thank-you notes/i).first();
      await notes.scrollIntoViewIfNeeded();
      await page.mouse.wheel(0, -120);
      await page.waitForTimeout(800);
      await shot(page, 'event-page', 'wrapup-thank-you-notes');
    });
    await ctx.close();
  });

  test('organizer: phone', async ({ browser }) => {
    const ctx = await newRoleContext(browser, 'organizer', PHONE);
    const page = await newRolePage(ctx, 'organizer');
    const base = `/organizer/events/${MAIN_EVENT}`;

    await step('cockpit phone', async () => {
      await goto(page, base, 2000);
      await shot(page, 'event-page', 'cockpit-phone');
      await page
        .getByRole('button', { name: /^more$/i })
        .last()
        .click();
      await page.waitForTimeout(1000);
      await shot(page, 'event-page', 'more-sheet-phone');
      await closeOverlay(page);
    });
    await step('slots phone', async () => {
      await goto(page, `${base}?tab=speakers&view=slots`, 2000);
      await page.getByText('Session Timeline').first().scrollIntoViewIfNeeded();
      await page.waitForTimeout(800);
      await shot(page, 'event-page', 'speakers-slots-phone');
    });
    await ctx.close();
  });

  test('organizer: live control (clock set to the event day)', async ({ browser }) => {
    const ctx = await newRoleContext(browser, 'organizer', PHONE);
    const page = await newRolePage(ctx, 'organizer');
    // Client-side only: Live Control derives the running session from the browser clock.
    await page.clock.setFixedTime(new Date('2026-11-06T16:22:00+01:00'));
    await step('live control', async () => {
      await goto(page, `/organizer/events/${MAIN_EVENT}/live-control`, 3000);
      await shot(page, 'features', 'live-control-phone');
      await page
        .getByRole('button', { name: /zeit anpassen/i })
        .first()
        .click();
      await page.waitForTimeout(1000);
      await shot(page, 'features', 'live-control-adjust-time');
      await closeOverlay(page);
    });
    await ctx.close();
  });
});

// ── SPEAKER ───────────────────────────────────────────────────────────────────────────────

test.describe('speaker', () => {
  test.skip(!want('speaker'), 'filtered by ONLY');

  test('speaker portal', async ({ browser }) => {
    const ctx = await newRoleContext(browser, 'speaker');
    const page = await newRolePage(ctx, 'speaker');
    // The test speaker has no engagements, so the dashboard is an empty state: only the
    // profile page is captured.
    await step('speaker profile', async () => {
      await goto(page, '/speaker-portal/profile', 1500);
      await shot(page, 'speaker-portal', 'profile');
    });
    await ctx.close();
  });
});

// ── PARTNER ───────────────────────────────────────────────────────────────────────────────

test.describe('partner', () => {
  test.skip(!want('partner'), 'filtered by ONLY');

  test('partner portal', async ({ browser }) => {
    const ctx = await newRoleContext(browser, 'partner');
    const page = await newRolePage(ctx, 'partner');
    for (const [url, name] of [
      ['/partners/company', 'company'],
      ['/partners/analytics', 'analytics'],
      ['/partners/topics', 'topics'],
    ] as const) {
      await step(`partner ${name}`, async () => {
        await goto(page, url, 2000);
        await shot(page, 'partner-portal', name);
      });
    }
    // The partner test account also has the ATTENDEE role: its attendee dashboard.
    await step('attendee dashboard', async () => {
      await goto(page, '/attendee/dashboard', 2000);
      await shot(page, 'attendee-experience', 'event-history-dashboard');
    });
    await ctx.close();
  });
});

test.afterAll(() => {
  console.log(`[read-only guard] blocked ${blocked.length} write(s)`);
  console.log(`[summary] failed steps: ${failed.length ? failed.join(', ') : 'none'}`);
  expect(failed).toEqual([]);
});
