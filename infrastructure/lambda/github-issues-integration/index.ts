import { SNSEvent } from 'aws-lambda';
import { SSMClient, GetParameterCommand } from '@aws-sdk/client-ssm';
import {
  CloudWatchClient,
  DescribeAlarmsCommand,
  GetMetricDataCommand,
} from '@aws-sdk/client-cloudwatch';
import { Octokit } from '@octokit/rest';

const ssm = new SSMClient({});
const cloudwatch = new CloudWatchClient({});

interface CloudWatchAlarm {
  AlarmName: string;
  AlarmDescription?: string;
  NewStateValue: 'ALARM' | 'OK' | 'INSUFFICIENT_DATA';
  NewStateReason: string;
  StateChangeTime: string;
  Region: string;
  AlarmArn: string;
  OldStateValue?: string;
  Trigger?: {
    MetricName: string;
    Namespace: string;
    Statistic: string;
    Threshold: number;
    ComparisonOperator: string;
  };
}

/**
 * Lambda function to create GitHub Issues from CloudWatch alarms.
 *
 * When alarms trigger:
 * - ALARM state → Creates a new GitHub Issue
 * - OK state → Closes the corresponding GitHub Issue
 *
 * Environment variables:
 * - GITHUB_OWNER: GitHub repository owner (e.g., "nissimbuchs")
 * - GITHUB_REPO: GitHub repository name (e.g., "BATbern2")
 * - GITHUB_TOKEN_PARAM: SSM parameter name for GitHub PAT
 */
export const handler = async (event: SNSEvent): Promise<void> => {
  console.log('Received SNS event:', JSON.stringify(event, null, 2));

  // Defaults match the real repository. They previously read 'batbern' / 'BATbern-develop',
  // neither of which exists ('BATbern-develop' is a local checkout directory, not a repo), so
  // a caller relying on them would have filed into a 404. GitHubIssuesConstruct always sets
  // these env vars, which is why it never bit; the construct carries the same note about its
  // own defaults, and this is the second half of that same trap.
  const githubOwner = process.env.GITHUB_OWNER || 'nissimbuchs';
  const githubRepo = process.env.GITHUB_REPO || 'BATbern2';
  const githubTokenParam = process.env.GITHUB_TOKEN_PARAM || '/batbern/staging/github/token';

  // Get GitHub token from SSM Parameter Store
  const githubToken = await getParameter(githubTokenParam);
  const octokit = new Octokit({ auth: githubToken });

  for (const record of event.Records) {
    try {
      const message: CloudWatchAlarm = JSON.parse(record.Sns.Message);
      console.log('Processing alarm:', message.AlarmName, 'State:', message.NewStateValue);

      if (message.NewStateValue === 'ALARM') {
        await createOrUpdateIssue(octokit, githubOwner, githubRepo, message);
      } else if (message.NewStateValue === 'OK') {
        await closeIssue(octokit, githubOwner, githubRepo, message);
      }
    } catch (error) {
      console.error('Error processing SNS record:', error);
      // Continue processing other records
    }
  }
};

/**
 * Create or update a GitHub Issue for an alarm.
 */
async function createOrUpdateIssue(
  octokit: Octokit,
  owner: string,
  repo: string,
  alarm: CloudWatchAlarm
): Promise<void> {
  const issueTitle = `🚨 [${alarm.AlarmName}] CloudWatch Alarm Triggered`;
  // Only for a NEW issue — a re-trigger comment does not need the table repeated.
  const metricContext = await fetchMetricContext(alarm);
  const issueBody = formatIssueBody(alarm, metricContext);
  const labels = getLabels(alarm);

  // Check if issue already exists
  const existingIssue = await findIssueByAlarm(octokit, owner, repo, alarm.AlarmName);

  if (existingIssue) {
    // Update existing issue
    console.log(`Updating existing issue #${existingIssue.number}`);
    await octokit.issues.createComment({
      owner,
      repo,
      issue_number: existingIssue.number,
      body: `⚠️ **Alarm re-triggered at ${alarm.StateChangeTime}**\n\n${alarm.NewStateReason}`,
    });

    // Reopen if closed
    if (existingIssue.state === 'closed') {
      await octokit.issues.update({
        owner,
        repo,
        issue_number: existingIssue.number,
        state: 'open',
      });
    }
  } else {
    // Create new issue
    console.log('Creating new issue for alarm:', alarm.AlarmName);
    const { data: issue } = await octokit.issues.create({
      owner,
      repo,
      title: issueTitle,
      body: issueBody,
      labels,
    });
    console.log(`Created issue #${issue.number}`);
  }
}

/**
 * Close a GitHub Issue when alarm returns to OK state.
 */
async function closeIssue(
  octokit: Octokit,
  owner: string,
  repo: string,
  alarm: CloudWatchAlarm
): Promise<void> {
  const existingIssue = await findIssueByAlarm(octokit, owner, repo, alarm.AlarmName);

  if (existingIssue && existingIssue.state === 'open') {
    console.log(`Closing issue #${existingIssue.number} - alarm resolved`);

    await octokit.issues.createComment({
      owner,
      repo,
      issue_number: existingIssue.number,
      body: `✅ **Alarm resolved at ${alarm.StateChangeTime}**\n\nThe alarm has returned to OK state. Closing this issue.`,
    });

    await octokit.issues.update({
      owner,
      repo,
      issue_number: existingIssue.number,
      state: 'closed',
    });

    console.log(`Issue #${existingIssue.number} closed`);
  }
}

/**
 * Find an existing GitHub Issue for an alarm.
 */
async function findIssueByAlarm(
  octokit: Octokit,
  owner: string,
  repo: string,
  alarmName: string
): Promise<{ number: number; state: string } | null> {
  const query = `repo:${owner}/${repo} is:issue "${alarmName}" in:title`;

  try {
    const { data } = await octokit.search.issuesAndPullRequests({ q: query, per_page: 1 });

    if (data.items.length > 0) {
      return {
        number: data.items[0].number,
        state: data.items[0].state,
      };
    }
  } catch (error) {
    console.error('Error searching for existing issue:', error);
  }

  return null;
}

/**
 * The AWS region CODE for console URLs, e.g. `eu-central-1`.
 *
 * #987: `alarm.Region` cannot be used for this. CloudWatch populates that field with the
 * human DISPLAY NAME — `"EU (Frankfurt)"`, with a space and parentheses — so every console
 * link this Lambda produced read `region=EU (Frankfurt)` and none of them resolved. AWS's own
 * notification email links `region=eu-central-1` correctly, which left the mail usable and
 * the GitHub issue not, backwards given the issue is meant to be the primary workflow.
 *
 * The alarm ARN in the same payload always carries the code as its 4th colon-separated field
 * (`arn:aws:cloudwatch:eu-central-1:188701360969:alarm:name`), so that is the authority here.
 * `alarm.Region` is still the right thing to SHOW a human; it is only wrong inside a URL.
 */
function regionCode(alarm: CloudWatchAlarm): string {
  const fromArn = alarm.AlarmArn?.split(':')[3];
  if (fromArn) {
    return fromArn;
  }
  // Last resort: a Region value that already looks like a code (no spaces) is usable.
  if (alarm.Region && !/\s/.test(alarm.Region)) {
    return alarm.Region;
  }
  return 'eu-central-1';
}

/**
 * Format the issue body with alarm details.
 */
function formatIssueBody(alarm: CloudWatchAlarm, metricContext = ''): string {
  const region = regionCode(alarm);
  const dashboardUrl = `https://console.aws.amazon.com/cloudwatch/home?region=${region}#alarmsV2:alarm/${encodeURIComponent(alarm.AlarmName)}`;

  return `## CloudWatch Alarm Details

**Alarm Name:** \`${alarm.AlarmName}\`
**State:** ${alarm.NewStateValue}
**Time:** ${alarm.StateChangeTime}
**Region:** ${alarm.Region}

### Reason
${alarm.NewStateReason}

${alarm.AlarmDescription ? `### Description\n${alarm.AlarmDescription}\n` : ''}

${
  alarm.Trigger
    ? `### Threshold
- **Metric:** ${alarm.Trigger.MetricName}
- **Namespace:** ${alarm.Trigger.Namespace}
- **Statistic:** ${alarm.Trigger.Statistic}
- **Threshold:** ${alarm.Trigger.ComparisonOperator} ${alarm.Trigger.Threshold}
`
    : ''
}

${metricContext}
### Actions
- [ ] Investigate the root cause
- [ ] Check CloudWatch dashboard
- [ ] Review application logs
- [ ] Deploy fix if needed
- [ ] Update runbook if this is a new scenario
${triageHandoff()}
### Links
- [CloudWatch Alarm](${dashboardUrl})
- [CloudWatch Dashboard](https://console.aws.amazon.com/cloudwatch/home?region=${region}#dashboards:name=BATbern-${getEnvironment(alarm.AlarmName)})
- [Application Logs](https://console.aws.amazon.com/cloudwatch/home?region=${region}#logsV2:log-groups/log-group/$252Faws$252Flogs$252FBATbern-${getEnvironment(alarm.AlarmName)}$252Fapplication)

---
*This issue was automatically created by CloudWatch alarm integration.*
`;
}

/**
 * Recent datapoints for the alarm's own metric, rendered as a markdown table (#996).
 *
 * Why this exists: the '@claude' triage handoff works, but the workflow grants the agent no tool
 * permissions and no AWS credentials, so on its first real firing (#993, #994) it could not query
 * CloudWatch at all and said so — every conclusion was inference from repo code. The answer to
 * #994 was only visible in the metrics. Embedding the numbers in the issue body means the
 * triage has something real to work from regardless of what tools it is granted, and it helps a
 * human reading the issue on a phone just as much.
 *
 * Works for metric-math alarms as well as plain ones: DescribeAlarms returns a `Metrics` array
 * for math alarms, which GetMetricData accepts directly. That matters because
 * batbern-{env}-api-4xx-ratio — the alarm most in need of context — is metric math.
 *
 * Strictly fail-open. Any error returns an empty string and the issue is filed without this
 * section. An enrichment that can prevent an incident being recorded is worse than no enrichment.
 */
async function fetchMetricContext(alarm: CloudWatchAlarm): Promise<string> {
  try {
    const { MetricAlarms } = await cloudwatch.send(
      new DescribeAlarmsCommand({ AlarmNames: [alarm.AlarmName] })
    );
    const definition = MetricAlarms?.[0];
    if (!definition) {
      return '';
    }

    const period = definition.Period ?? 300;
    const queries =
      definition.Metrics && definition.Metrics.length > 0
        ? definition.Metrics
        : [
            {
              Id: 'm1',
              MetricStat: {
                Metric: {
                  Namespace: definition.Namespace,
                  MetricName: definition.MetricName,
                  Dimensions: definition.Dimensions,
                },
                Period: period,
                Stat: definition.Statistic ?? definition.ExtendedStatistic ?? 'Sum',
              },
              ReturnData: true,
            },
          ];

    // A window that brackets the transition: enough history to see the trend that caused it,
    // and a little after so a self-healing blip is visible as one.
    const stateChange = new Date(alarm.StateChangeTime).getTime();
    const { MetricDataResults } = await cloudwatch.send(
      new GetMetricDataCommand({
        MetricDataQueries: queries,
        StartTime: new Date(stateChange - 45 * 60 * 1000),
        EndTime: new Date(stateChange + 5 * 60 * 1000),
      })
    );

    const series = (MetricDataResults ?? []).filter(
      (r) => (r.Timestamps ?? []).length > 0
    );
    if (series.length === 0) {
      return '\n### Metric context\n\n_No datapoints in the 45 minutes before the transition._\n';
    }

    let out = '\n### Metric context (45 min before → 5 min after the transition)\n\n';
    for (const r of series) {
      const rows = (r.Timestamps ?? [])
        .map((t, i) => ({ t: new Date(t as unknown as string).toISOString(), v: r.Values?.[i] }))
        .sort((a, b) => a.t.localeCompare(b.t))
        .slice(-14);
      out += `**${r.Label ?? r.Id}**\n\n| time (UTC) | value |\n|---|---|\n`;
      for (const row of rows) {
        const v = typeof row.v === 'number' ? Math.round(row.v * 100) / 100 : '—';
        out += `| ${row.t.slice(11, 16)} | ${v} |\n`;
      }
      out += '\n';
    }
    return out;
  } catch (error) {
    // Never let enrichment stop an incident from being filed.
    console.warn('Could not fetch metric context; filing issue without it:', error);
    return '';
  }
}

/**
 * The `@claude` handoff appended to a NEWLY CREATED alarm issue.
 *
 * `.github/workflows/claude.yml` fires on `issues: opened` only when the body or title
 * contains `@claude`, and for that trigger the issue body IS the prompt. So the boundaries
 * have to be written into the body — there is nowhere else to put them.
 *
 * Three deliberate constraints, and they are not decoration:
 *
 * 1. **Read-only.** The agent diagnoses; it does not remediate. This mirrors the standing
 *    rule that where money moves or regulators ask questions the core stays deterministic
 *    and the AI sits at the edge.
 * 2. **No pull request.** This is the important one. In this repository, opening a PR
 *    against `develop` triggers `deploy-staging.yml` and ships that branch to
 *    www.batbern.ch — unmerged, unreviewed, drafts included. An agent that "fixed" an alarm
 *    by opening a PR would be performing an unreviewed production deploy in response to a
 *    CloudWatch metric. The workflow's `permissions:` block withholds `contents: write` so
 *    it cannot push a branch either; this instruction and that permission are belt and
 *    braces, and neither is sufficient alone.
 * 3. **Say when the answer is "nothing".** The alarm that prompted this whole change fired
 *    six times on a PHP scanner. "This is not a fault, here is why" is the most useful
 *    output such a run can produce, and an agent that feels obliged to find a defect will
 *    invent one.
 *
 * ONLY on creation. The re-trigger comment and the recovery comment must never carry the
 * mention: an oscillating alarm would otherwise start one agent run per cycle, and the alarm
 * retired in this same change managed six cycles in three days, two of them 60 seconds long.
 * Tests pin all three cases.
 *
 * Kill switch: `CLAUDE_TRIAGE_ENABLED=false` on the Lambda drops the handoff and leaves the
 * issue otherwise untouched. A Lambda env var change is effective immediately, so an agent
 * storm can be stopped without deploying code.
 */
function triageHandoff(): string {
  if ((process.env.CLAUDE_TRIAGE_ENABLED ?? 'true').toLowerCase() === 'false') {
    return '';
  }

  return `
### Triage

@claude please triage this alarm.

Work the question "is this a fault, and if so where", and stop there:

- Read the CloudWatch metric and the relevant \`/aws/ecs/BATbern-staging/*\` log groups around
  the state-change time. Establish what actually happened before proposing anything.
- Separate what you **measured** from what you **infer**. Say which commands you ran. If you
  did not run something, say so rather than implying you did.
- **If this is not a fault, say that and say why.** A clean "this is external traffic / a
  deploy warmup / expected test load, here is the evidence" is a complete and valuable answer.
  Do not go looking for a defect to justify the run.
- Stay **read-only** against AWS. Describe, get, filter, query. Do not mutate infrastructure,
  restart services, or change alarm configuration.
- **Do not open a pull request** and do not push a branch. In this repository a PR against
  \`develop\` deploys straight to www.batbern.ch, so that would be an unreviewed production
  deploy triggered by a metric. Propose the change in a comment and let a human take it.
- If the alarm is itself the problem — wrong metric, wrong threshold, watching something we do
  not control — say so. That has already been the answer once (#986).

Post your findings as a comment on this issue.
`;
}

/**
 * Get labels for the issue based on alarm severity and type.
 */
function getLabels(alarm: CloudWatchAlarm): string[] {
  const labels = ['incident', 'monitoring'];

  // Add environment label
  const env = getEnvironment(alarm.AlarmName);
  if (env) {
    labels.push(`env:${env}`);
  }

  // Add severity label based on alarm name
  // Platform Stability Improvements (Phase 3): OOM kills are critical
  if (alarm.AlarmName.includes('OOM-Kills') || alarm.AlarmName.includes('availability') || alarm.AlarmName.includes('high-errors')) {
    labels.push('severity:critical');
  } else if (alarm.AlarmName.includes('high-latency') || alarm.AlarmName.includes('high-cpu') || alarm.AlarmName.includes('High-Memory') || alarm.AlarmName.includes('Task-Failures')) {
    labels.push('severity:high');
  } else if (alarm.AlarmName.includes('budget') || alarm.AlarmName.includes('EventBridge-Failures')) {
    labels.push('severity:medium');
  } else {
    labels.push('severity:low');
  }

  // Add component label
  // Platform Stability Improvements (Phase 3): ECS service alarms
  if (alarm.AlarmName.includes('database')) {
    labels.push('component:database');
  } else if (alarm.AlarmName.includes('api')) {
    labels.push('component:api');
  } else if (alarm.AlarmName.includes('High-Memory') || alarm.AlarmName.includes('OOM-Kills') || alarm.AlarmName.includes('Task-Failures') || alarm.AlarmName.includes('EventBridge-Failures')) {
    labels.push('component:ecs');
  } else {
    labels.push('component:infrastructure');
  }

  // Platform Stability Improvements (Phase 3): Add service attribution label
  // Extract service name from alarm (e.g., "batbern-staging-EventManagement-High-Memory" → "service:event-management")
  const serviceMatch = alarm.AlarmName.match(/-(EventManagement|SpeakerCoordination|PartnerCoordination|AttendeeExperience|CompanyManagement|ApiGatewayService)-/);
  if (serviceMatch) {
    const serviceName = serviceMatch[1]
      .replace(/([A-Z])/g, '-$1')
      .toLowerCase()
      .substring(1);
    labels.push(`service:${serviceName}`);
  }

  return labels;
}

/**
 * Extract environment from alarm name.
 */
function getEnvironment(alarmName: string): string {
  if (alarmName.includes('production')) return 'production';
  if (alarmName.includes('staging')) return 'staging';
  if (alarmName.includes('dev')) return 'development';
  return 'staging'; // default
}

/**
 * Get parameter from SSM Parameter Store.
 */
async function getParameter(parameterName: string): Promise<string> {
  const command = new GetParameterCommand({
    Name: parameterName,
    WithDecryption: true,
  });

  const response = await ssm.send(command);
  if (!response.Parameter?.Value) {
    throw new Error(`Parameter ${parameterName} not found`);
  }

  return response.Parameter.Value;
}
