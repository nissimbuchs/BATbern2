import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { stagingConfig } from '../../../lib/config/staging-config';

/**
 * The Frontend stacks deploy web-frontend/dist, which only exists after a frontend build. Tests
 * of the backend stacks replace the website asset with an inline file. Use as the factory of
 * `jest.mock('aws-cdk-lib/aws-s3-deployment', ...)` in the test file, before the app is built.
 */
export function s3DeploymentWithInlineSite() {
  const actual = jest.requireActual('aws-cdk-lib/aws-s3-deployment');
  class Source extends actual.Source {
    static asset() {
      return actual.Source.data('index.html', '<!doctype html>');
    }
  }
  return { ...actual, Source };
}

/**
 * Synthesizes the real staging app (bin/batbern-infrastructure.ts), so a test sees the grants and
 * wiring exactly as deployed, including what bin adds across stacks. Returns template file name
 * to template JSON text, and a cleanup that restores the environment and removes the output.
 */
export function synthStagingApp(): { templates: Record<string, string>; cleanup: () => void } {
  const saved = { ...process.env };
  // Prefix cdk.out so test/global-teardown.js sweeps it even if cleanup never runs.
  const outdir = fs.mkdtempSync(path.join(os.tmpdir(), 'cdk.out-staging-app-'));
  process.env.ENVIRONMENT = 'staging';
  process.env.CDK_DEFAULT_ACCOUNT = stagingConfig.account;
  process.env.CDK_OUTDIR = outdir;
  // CI sets CI=true, and then the services need a pre-built image tag (container-image-helper).
  process.env.IMAGE_TAG = 'test-image-tag';
  // The app logs its target account and CDK reports template warnings; neither matters here.
  const quiet = (['log', 'warn', 'error'] as const).map((level) =>
    jest.spyOn(console, level).mockImplementation(() => undefined)
  );
  const cleanup = () => {
    process.env = saved;
    fs.rmSync(outdir, { recursive: true, force: true });
  };
  try {
    jest.isolateModules(() => {
      require('../../../bin/batbern-infrastructure');
    });
  } catch (error) {
    cleanup();
    throw error;
  } finally {
    quiet.forEach((spy) => spy.mockRestore());
  }

  const templates: Record<string, string> = {};
  for (const file of fs.readdirSync(outdir)) {
    if (file.endsWith('.template.json')) {
      templates[file] = fs.readFileSync(path.join(outdir, file), 'utf8');
    }
  }
  return { templates, cleanup };
}
