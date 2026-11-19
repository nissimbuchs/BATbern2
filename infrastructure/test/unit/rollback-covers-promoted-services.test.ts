import * as fs from 'fs';
import * as path from 'path';

/**
 * The auto-rollback (scripts/ci/rollback-deployment.sh) must roll back every service whose image
 * is promoted to `staging-stable` (scripts/ci/promote-ecr-tag.sh). Until 2026-11-19 the API
 * gateway was promoted but never rolled back, so a failed post-deploy gate left the new gateway
 * running next to rolled-back services.
 */
const scriptsDir = path.join(__dirname, '../../../scripts/ci');

function bashArray(file: string, name: string): string[] {
  const text = fs.readFileSync(path.join(scriptsDir, file), 'utf8');
  const match = text.match(new RegExp(`^${name}=\\(\\n([\\s\\S]*?)\\n\\)`, 'm'));
  if (!match) throw new Error(`${name}=( ... ) not found in ${file}`);
  return match[1]
    .split('\n')
    .map((line) => line.trim().replace(/^"|"$/g, ''))
    .filter((line) => line && !line.startsWith('#'));
}

describe('auto-rollback covers every promoted service', () => {
  it('rolls back each ECR repo that promote-ecr-tag.sh tags staging-stable', () => {
    const promoted = bashArray('promote-ecr-tag.sh', 'REPOS');
    const rolledBack = bashArray('rollback-deployment.sh', 'SERVICES').map((e) => e.split(':')[0]);
    expect(promoted.length).toBeGreaterThan(0);
    expect([...rolledBack].sort()).toEqual([...promoted].sort());
  });

  it('names each ECS service by the CDK fragment the deployed service carries', () => {
    const fragments = bashArray('rollback-deployment.sh', 'SERVICES').map((e) => e.split(':')[1]);
    // live names, checked 2026-11-19: BATbern-staging-<Fragment>-Service<hash>-<suffix>
    expect(fragments.sort()).toEqual(
      ['ApiGatewayService', 'CompanyManagement', 'EventManagement', 'PartnerCoordination'].sort()
    );
  });
});
