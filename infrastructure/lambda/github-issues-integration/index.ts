import { SNSEvent } from 'aws-lambda';
import { SSMClient, GetParameterCommand } from '@aws-sdk/client-ssm';
import { Octokit } from '@octokit/rest';

const ssm = new SSMClient({});

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
 * - GITHUB_OWNER: GitHub repository owner (e.g., "batbern")
 * - GITHUB_REPO: GitHub repository name (e.g., "BATbern-develop")
 * - GITHUB_TOKEN_PARAM: SSM parameter name for GitHub PAT
 */
export const handler = async (event: SNSEvent): Promise<void> => {
  console.log('Received SNS event:', JSON.stringify(event, null, 2));

  const githubOwner = process.env.GITHUB_OWNER || 'batbern';
  const githubRepo = process.env.GITHUB_REPO || 'BATbern-develop';
  const githubTokenParam = process.env.GITHUB_TOKEN_PARAM || '/batbern/production/github/token';

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
  const issueBody = formatIssueBody(alarm);
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
 * Format the issue body with alarm details.
 */
function formatIssueBody(alarm: CloudWatchAlarm): string {
  const dashboardUrl = `https://console.aws.amazon.com/cloudwatch/home?region=${alarm.Region}#alarmsV2:alarm/${encodeURIComponent(alarm.AlarmName)}`;

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

### Actions
- [ ] Investigate the root cause
- [ ] Check CloudWatch dashboard
- [ ] Review application logs
- [ ] Deploy fix if needed
- [ ] Update runbook if this is a new scenario

### Links
- [CloudWatch Alarm](${dashboardUrl})
- [CloudWatch Dashboard](https://console.aws.amazon.com/cloudwatch/home?region=${alarm.Region}#dashboards:name=BATbern-${getEnvironment(alarm.AlarmName)})
- [Application Logs](https://console.aws.amazon.com/cloudwatch/home?region=${alarm.Region}#logsV2:log-groups/log-group/$252Faws$252Flogs$252FBATbern-${getEnvironment(alarm.AlarmName)}$252Fapplication)

---
*This issue was automatically created by CloudWatch alarm integration.*
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
  if (alarm.AlarmName.includes('availability') || alarm.AlarmName.includes('high-errors')) {
    labels.push('severity:critical');
  } else if (alarm.AlarmName.includes('high-latency') || alarm.AlarmName.includes('high-cpu')) {
    labels.push('severity:high');
  } else if (alarm.AlarmName.includes('budget') || alarm.AlarmName.includes('high-memory')) {
    labels.push('severity:medium');
  } else {
    labels.push('severity:low');
  }

  // Add component label
  if (alarm.AlarmName.includes('database')) {
    labels.push('component:database');
  } else if (alarm.AlarmName.includes('api')) {
    labels.push('component:api');
  } else {
    labels.push('component:infrastructure');
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
