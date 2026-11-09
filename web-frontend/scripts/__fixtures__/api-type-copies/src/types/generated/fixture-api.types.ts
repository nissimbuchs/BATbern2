/**
 * Fixture: the layout openapi-typescript writes. Only the members of components.schemas count.
 */

export interface paths {
  '/events/{eventCode}': {
    parameters: {
      query?: never;
    };
    get: operations['getEvent'];
  };
}
export type webhooks = Record<string, never>;
export interface components {
  schemas: {
    /** @description An event. */
    EventResponse: {
      eventCode: string;
      /** @description nested members are not schema names */
      title?: string;
    };
    PagedThing: {
      items?: string[];
    };
    AliasedOne: {
      id: string;
    };
    HiddenOne: {
      id: string;
    };
    CommentedOne: {
      id: string;
    };
    UnionOne: 'A' | 'B';
    IntersectionOne: {
      id: string;
    };
    'Quoted-Name': {
      id: string;
    };
    OptionalName?: {
      id: string;
    };
    TestOnly: {
      id: string;
    };
    TemplateOne: {
      id: string;
    };
  };
  responses: never;
  parameters: {
    EventCodeParam: string;
  };
  requestBodies: never;
  headers: never;
  pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
  getEvent: {
    parameters: {
      path: {
        eventCode: string;
      };
    };
  };
}
