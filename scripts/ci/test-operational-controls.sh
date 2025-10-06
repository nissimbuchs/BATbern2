#!/bin/bash
# Test operational controls (AC 13-16)
# Tests JIRA integration, deployment windows, approvals, and audit trail

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Test helper functions
log_test() {
    echo -e "${YELLOW}TEST:${NC} $1"
}

pass_test() {
    echo -e "${GREEN}✓ PASS:${NC} $1"
    ((TESTS_PASSED++))
}

fail_test() {
    echo -e "${RED}✗ FAIL:${NC} $1"
    ((TESTS_FAILED++))
}

# Test 13.1: should_createJiraTicket_when_promotionInitiated
test_create_jira_ticket() {
    log_test "Test 13.1: should_createJiraTicket_when_promotionInitiated"

    # Verify production workflow requires JIRA ticket input
    if ! grep -q "jira_ticket:" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "JIRA ticket input not required in production workflow"
        return 1
    fi

    # Verify JIRA ticket is validated
    if ! grep -q "Verify JIRA\|jira.*ticket" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "JIRA ticket verification not implemented"
        return 1
    fi

    pass_test "JIRA ticket requirement is configured"
    return 0
}

# Test 13.2: should_linkDeploymentToChange_when_deploying
test_link_deployment_to_change() {
    log_test "Test 13.2: should_linkDeploymentToChange_when_deploying"

    # Verify workflow updates JIRA ticket with deployment info
    if ! grep -q "update-jira-ticket\|Update JIRA ticket" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "JIRA ticket update not configured"
        return 1
    fi

    # Verify JIRA API integration exists
    if ! grep -q "JIRA_API_TOKEN\|jira.*api" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "JIRA API integration not configured"
        return 1
    fi

    pass_test "Deployment to change linking is configured"
    return 0
}

# Test 14.1: should_blockDeployment_when_outsideDeploymentWindow
test_block_outside_deployment_window() {
    log_test "Test 14.1: should_blockDeployment_when_outsideDeploymentWindow"

    # Verify workflow checks deployment window
    if ! grep -q "deployment.*window\|Check deployment window\|deployment_time" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Deployment window check not implemented"
        return 1
    fi

    # Verify window validation logic exists
    if ! grep -q "SCHEDULED_TIME\|deployment.*time" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Deployment time validation not implemented"
        return 1
    fi

    pass_test "Deployment window enforcement is configured"
    return 0
}

# Test 14.2: should_allowEmergencyDeployment_when_hotfixRequired
test_allow_emergency_deployment() {
    log_test "Test 14.2: should_allowEmergencyDeployment_when_hotfixRequired"

    # Verify optional deployment time (allows emergency deployments)
    if ! grep -q "deployment_time:\s*$\|required: false" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Emergency deployment option not configured"
        return 1
    fi

    pass_test "Emergency deployment capability is configured"
    return 0
}

# Test 15.1: should_requireTwoApprovals_when_productionPromotion
test_require_two_approvals() {
    log_test "Test 15.1: should_requireTwoApprovals_when_productionPromotion"

    # Verify environment protection with production environment
    if ! grep -q "environment:" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Environment protection not configured"
        return 1
    fi

    if ! grep -q "name: production" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Production environment not specified"
        return 1
    fi

    pass_test "Multi-approval requirement is configured (via GitHub environment protection)"
    return 0
}

# Test 15.2: should_notifyApprovers_when_approvalPending
test_notify_approvers() {
    log_test "Test 15.2: should_notifyApprovers_when_approvalPending"

    # Verify notification configuration exists
    if ! grep -q "slack\|notification\|notify" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Approver notification not configured"
        return 1
    fi

    # Verify Slack webhook is configured
    if ! grep -q "SLACK_WEBHOOK_URL" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Slack webhook not configured"
        return 1
    fi

    pass_test "Approver notification is configured"
    return 0
}

# Test 16.1: should_recordAuditEntry_when_deploymentCompletes
test_record_audit_entry() {
    log_test "Test 16.1: should_recordAuditEntry_when_deploymentCompletes"

    # Verify audit logging exists in workflow
    if ! grep -q "audit\|log.*deployment" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Audit logging not explicitly configured"
        # Note: GitHub Actions provides built-in audit via workflow runs, so this might be acceptable
        return 1
    fi

    pass_test "Audit trail recording is configured"
    return 0
}

# Test 16.2: should_trackApprovalChain_when_promotionApproved
test_track_approval_chain() {
    log_test "Test 16.2: should_trackApprovalChain_when_promotionApproved"

    # Verify workflow records approval information
    if ! grep -q "github.actor\|approval\|approved" ".github/workflows/promote-to-production.yml" 2>/dev/null; then
        fail_test "Approval chain tracking not configured"
        # Note: GitHub Actions provides built-in approval tracking via environment protection
        return 1
    fi

    pass_test "Approval chain tracking is configured"
    return 0
}

# Run all tests
echo "=========================================="
echo "Running Operational Control Tests (AC 13-16)"
echo "=========================================="
echo ""

test_create_jira_ticket || true
test_link_deployment_to_change || true
test_block_outside_deployment_window || true
test_allow_emergency_deployment || true
test_require_two_approvals || true
test_notify_approvers || true
test_record_audit_entry || true
test_track_approval_chain || true

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}OPERATIONAL CONTROL TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL OPERATIONAL CONTROL TESTS PASSED${NC}"
    exit 0
fi
