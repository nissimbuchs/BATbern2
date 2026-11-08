/**
 * The typed test API of the MSW harness (Story 17.0c1).
 *
 *   import { api } from '@/test/msw/api';
 *
 *   api.on('listCompanies', () => ({ data: [...], pagination: {...} }));  // typed body
 *   api.on('getCompany', () => HttpResponse.json(problem, { status: 404 })); // any Response
 *   api.on('createCompany', ({ body }) => ({ ...created, name: body.name }), { status: 201 });
 *   expect(api.calls('listCompanies')[0].query.get('search')).toBe('müller');
 *
 * Keys are `operationId`s from `docs/api/*.openapi.yml`; an operationId that two specs define is
 * qualified by its spec, e.g. `topics-api/listTopics`. The body a resolver returns is typed from
 * `src/types/generated/` (`operations[key]['responses'][status]['content']['application/json']`),
 * so a missing required field or a wrong shape fails `npm run type-check`. Overrides and calls
 * are reset after every test by `src/test/setup.ts`. Without an override, the operation answers
 * with its spec example (`server.ts`).
 */
import type { operations as CompanyOps } from '../../types/generated/company-api.types';
import type { operations as AiOps } from '../../types/generated/event-ai-api.types';
import type { operations as AnalyticsOps } from '../../types/generated/event-analytics-api.types';
import type { operations as AppSettingsOps } from '../../types/generated/event-app-settings-api.types';
import type { operations as MailOps } from '../../types/generated/event-mail-api.types';
import type { operations as MediaOps } from '../../types/generated/event-media-api.types';
import type { operations as NewsletterOps } from '../../types/generated/event-newsletter-api.types';
import type { operations as NotificationOps } from '../../types/generated/event-notifications-api.types';
import type { operations as RegistrationOps } from '../../types/generated/event-registrations-api.types';
import type { operations as EventOps } from '../../types/generated/events-core-api.types';
import type { operations as SessionOps } from '../../types/generated/event-sessions-api.types';
import type { operations as SpeakerOps } from '../../types/generated/event-speakers-api.types';
import type { operations as TaskOps } from '../../types/generated/event-tasks-api.types';
import type { operations as WatchOps } from '../../types/generated/event-watch-api.types';
import type { operations as GatewayOps } from '../../types/generated/gateway-config-api.types';
import type { operations as PartnerOps } from '../../types/generated/partner-api.types';
import type { operations as MeetingOps } from '../../types/generated/partner-meetings-api.types';
import type { operations as TopicOps } from '../../types/generated/topics-api.types';
import type { operations as UserOps } from '../../types/generated/user-api.types';

/** operationIds that partners-api and topics-api both define: qualified by spec in the key. */
type SharedIds = 'listTopics' | 'updateTopic' | 'deleteTopic';

/** Every frontend-callable operation, by harness key. */
export type Operations = CompanyOps &
  AiOps &
  AnalyticsOps &
  AppSettingsOps &
  MailOps &
  MediaOps &
  NewsletterOps &
  NotificationOps &
  RegistrationOps &
  EventOps &
  SessionOps &
  SpeakerOps &
  TaskOps &
  WatchOps &
  GatewayOps &
  Omit<PartnerOps, SharedIds> &
  MeetingOps &
  Omit<TopicOps, SharedIds> &
  UserOps & {
    'partners-api/listTopics': PartnerOps['listTopics'];
    'partners-api/updateTopic': PartnerOps['updateTopic'];
    'partners-api/deleteTopic': PartnerOps['deleteTopic'];
    'topics-api/listTopics': TopicOps['listTopics'];
    'topics-api/updateTopic': TopicOps['updateTopic'];
    'topics-api/deleteTopic': TopicOps['deleteTopic'];
  };

/** The keys `api.on` accepts. The guard test checks them against the specs at runtime. */
export const QUALIFIED_KEYS = [
  'partners-api/listTopics',
  'partners-api/updateTopic',
  'partners-api/deleteTopic',
  'topics-api/listTopics',
  'topics-api/updateTopic',
  'topics-api/deleteTopic',
] as const satisfies ReadonlyArray<keyof Operations>;

export type OperationKey = keyof Operations & string;

type Responses<K extends OperationKey> = Operations[K] extends { responses: infer R } ? R : never;

/** The 2xx statuses the operation declares. */
export type SuccessStatus<K extends OperationKey> = Extract<
  keyof Responses<K>,
  200 | 201 | 202 | 203 | 204 | 205 | 206
>;

/** The JSON body of response `S` of operation `K` (`undefined` when it has none). */
export type ResponseBody<K extends OperationKey, S extends keyof Responses<K>> = S extends unknown
  ? Responses<K>[S] extends { content: { 'application/json': infer B } }
    ? B
    : undefined
  : never;

/** The JSON request body of operation `K` (`undefined` when it has none). */
export type RequestBody<K extends OperationKey> = Operations[K] extends {
  requestBody?: { content: { 'application/json': infer B } };
}
  ? B
  : unknown;

export interface ApiCall<K extends OperationKey = OperationKey> {
  key: K;
  method: string;
  url: URL;
  query: URLSearchParams;
  /** Path parameters by spec name, decoded, e.g. `{ eventCode: 'BATbern57' }`. */
  params: Record<string, string>;
  /** The parsed JSON request body, the text for other bodies, `undefined` without one. */
  body: RequestBody<K> | undefined;
  request: Request;
}

type Awaitable<T> = T | Promise<T>;

export type Resolver<K extends OperationKey, S extends SuccessStatus<K>> = (
  call: ApiCall<K>
) => Awaitable<ResponseBody<K, S> | Response>;

interface Override {
  resolver: (call: ApiCall) => Awaitable<unknown>;
  status?: number;
}

const overrides = new Map<string, Override>();
const recorded: ApiCall[] = [];
const violations: string[] = [];

export const api = {
  /**
   * Answers operation `key` with `resolver` until the end of the test. The returned body is typed
   * from the generated API types; return a `Response` (`HttpResponse.json(..., { status: 500 })`)
   * for an error or any other answer. `status` picks the success status (default: the lowest 2xx
   * the spec declares).
   */
  on<K extends OperationKey, S extends SuccessStatus<K> = SuccessStatus<K>>(
    key: K,
    resolver: Resolver<K, S>,
    options: { status?: S } = {}
  ): void {
    overrides.set(key, {
      resolver: resolver as unknown as Override['resolver'],
      status: options.status as number | undefined,
    });
  },

  /** The requests operation `key` received in this test, oldest first. */
  calls<K extends OperationKey>(key: K): Array<ApiCall<K>> {
    return recorded.filter((c) => c.key === key) as Array<ApiCall<K>>;
  },

  /** Every request the harness answered in this test, oldest first. */
  allCalls(): ApiCall[] {
    return [...recorded];
  },

  /**
   * Returns and clears the requests outside the specs seen in this test. Only for a test that
   * provokes one on purpose; otherwise `src/test/setup.ts` fails the test with them.
   */
  takeViolations(): string[] {
    return violations.splice(0, violations.length);
  },
};

/** Thrown after a test whose code sent a request no spec defines. */
export class SpecViolationError extends Error {
  constructor(messages: string[]) {
    super(
      `MSW harness: ${messages.length} request(s) outside docs/api/*.openapi.yml:\n` +
        messages.map((m) => `  - ${m}`).join('\n') +
        '\nFix the call (path, method, query) or the spec. See docs/architecture/coding-standards.md, "Test fidelity rules".'
    );
    this.name = 'SpecViolationError';
  }
}

// Internal: used by server.ts and setup.ts only.
export const harnessState = {
  override: (key: string) => overrides.get(key),
  record: (call: ApiCall) => {
    recorded.push(call);
  },
  violation: (message: string) => {
    violations.push(message);
  },
  /** Throws `SpecViolationError` when the test sent a request outside the specs. */
  assertNoViolations: () => {
    const found = violations.splice(0, violations.length);
    if (found.length > 0) throw new SpecViolationError(found);
  },
  reset: () => {
    overrides.clear();
    recorded.length = 0;
    violations.length = 0;
  },
};
