// Fixture for scripts/check-api-type-copies.test.ts. Read as text, never compiled.
import type { components } from '@/types/generated/fixture-api.types';

export interface EventResponse {
  eventCode: string;
}

export type PagedThing<T extends { id: string } = { id: string }> = {
  items: T[];
};

export type AliasedOne = components['schemas']['AliasedOne'];

interface HiddenOne {
  id: string;
}

// export interface CommentedOne { id: string }
/*
export type CommentedOne = { id: string };
*/

export type UnionOne = 'A' | { other: true };

export type IntersectionOne = Omit<HiddenOne, 'id'> & { id: number };

export interface EventCardProps {
  event: EventResponse;
}

export declare interface OptionalName {
  id: string;
}

const text = 'export interface Quoted-Name {}';
export const unused = text;
