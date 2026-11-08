import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { stagingConfig } from '../../lib/config/staging-config';

// The Frontend stacks deploy web-frontend/dist, which only exists after a frontend build. This
// test is about the backend stacks, so the website asset is replaced by an inline file.
jest.mock('aws-cdk-lib/aws-s3-deployment', () => {
  const actual = jest.requireActual('aws-cdk-lib/aws-s3-deployment');
  class Source extends actual.Source {
    static asset() {
      return actual.Source.data('index.html', '<!doctype html>');
    }
  }
  return { ...actual, Source };
});

/**
 * Story 17.2 part B: the EventBridge bus is gone and domain events stay in process.
 *
 * Synthesizes the real staging app (bin/batbern-infrastructure.ts) and checks every template:
 * no stack declares an `AWS::Events::EventBus`, no IAM policy grants `events:PutEvents`, and the
 * `-EventBus` stack is no longer part of the app. The EMS auto-publish scheduler's rule grants
 * (`events:PutRule` etc. on the default bus) are not affected and not checked here.
 */
describe('No event bus (Story 17.2 part B)', () => {
  let outdir: string;
  let templates: Record<string, string>;
  const saved = { ...process.env };

  beforeAll(() => {
    // Prefix cdk.out so test/global-teardown.js sweeps it even if afterAll never runs.
    outdir = fs.mkdtempSync(path.join(os.tmpdir(), 'cdk.out-no-event-bus-'));
    process.env.ENVIRONMENT = 'staging';
    process.env.CDK_DEFAULT_ACCOUNT = stagingConfig.account;
    process.env.CDK_OUTDIR = outdir;
    // CI sets CI=true, and then the services need a pre-built image tag (container-image-helper).
    process.env.IMAGE_TAG = 'test-image-tag';
    // The app logs its target account and CDK reports template warnings; neither matters here.
    const quiet = (['log', 'warn', 'error'] as const).map((level) =>
      jest.spyOn(console, level).mockImplementation(() => undefined)
    );
    try {
      jest.isolateModules(() => {
        require('../../bin/batbern-infrastructure');
      });
    } finally {
      quiet.forEach((spy) => spy.mockRestore());
    }

    templates = {};
    for (const file of fs.readdirSync(outdir)) {
      if (file.endsWith('.template.json')) {
        templates[file] = fs.readFileSync(path.join(outdir, file), 'utf8');
      }
    }
  }, 300000);

  afterAll(() => {
    process.env = saved;
    if (outdir) {
      fs.rmSync(outdir, { recursive: true, force: true });
    }
  });

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

  test('should_keepTheAutoPublishRuleGrant_when_stagingAppBuilt', () => {
    // The auto-publish scheduler's grant on the default bus stays (plan: "Never").
    expect(templates['BATbern-staging-EventManagement.template.json']).toContain(
      '"events:PutRule"'
    );
  });
});
