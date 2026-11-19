import { synthStagingApp } from './helpers/synth-staging-app';

// Backend stacks only: the website asset is replaced by an inline file.
jest.mock('aws-cdk-lib/aws-s3-deployment', () =>
  require('./helpers/synth-staging-app').s3DeploymentWithInlineSite()
);

/**
 * Story 17.2 part B: the EventBridge bus is gone and domain events stay in process.
 *
 * Synthesizes the real staging app (bin/batbern-infrastructure.ts) and checks every template:
 * no stack declares an `AWS::Events::EventBus`, no IAM policy grants `events:PutEvents`, and the
 * `-EventBus` stack is no longer part of the app. EMS holds no `events:` grant at all
 * (`ems-task-role-grants.test.ts`): auto-publish is a `@Scheduled` job.
 */
describe('No event bus (Story 17.2 part B)', () => {
  let templates: Record<string, string>;
  let cleanup: () => void;

  beforeAll(() => {
    ({ templates, cleanup } = synthStagingApp());
  }, 300000);

  afterAll(() => cleanup?.());

  test('should_synthesizeTheServiceStacks_when_stagingAppBuilt', () => {
    // Positive control: the check below is only meaningful if the app really synthesized.
    const names = Object.keys(templates);
    expect(names).toEqual(
      expect.arrayContaining([
        'BATbern-staging-EventManagement.template.json',
        'BATbern-staging-CompanyManagement.template.json',
        'BATbern-staging-PartnerCoordination.template.json',
      ])
    );
  });

  test('should_haveNoEventBusStack_when_stagingAppBuilt', () => {
    expect(Object.keys(templates).filter((name) => name.includes('EventBus'))).toEqual([]);
  });

  test('should_declareNoEventBus_when_stagingAppBuilt', () => {
    const offenders = Object.entries(templates)
      .filter(([, body]) => body.includes('"AWS::Events::EventBus"'))
      .map(([name]) => name);
    expect(offenders).toEqual([]);
  });

  test('should_grantNoPutEvents_when_stagingAppBuilt', () => {
    const offenders = Object.entries(templates)
      .filter(([, body]) => body.includes('"events:PutEvents"'))
      .map(([name]) => name);
    expect(offenders).toEqual([]);
  });

  // Story 17.2 part A: the two stub services are gone too (ADR-014). Their stacks were deleted
  // in production on 2026-11-10 after no stack imported their exports; this keeps them out.
  test('should_haveNoStubServiceStack_when_stagingAppBuilt', () => {
    expect(
      Object.keys(templates).filter((name) => /SpeakerCoordination|AttendeeExperience/.test(name))
    ).toEqual([]);
  });

  test('should_declareNoStubServiceRepository_when_stagingAppBuilt', () => {
    const offenders = Object.entries(templates)
      .filter(([, body]) => /speaker-coordination|attendee-experience/.test(body))
      .map(([name]) => name);
    expect(offenders).toEqual([]);
  });
});
