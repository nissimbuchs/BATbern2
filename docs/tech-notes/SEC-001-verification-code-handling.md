# SEC-001: Verification Code Handling - Technical Analysis

**Story**: 1.2.2 - Implement Forgot Password Flow
**Issue ID**: SEC-001
**Severity**: Medium
**Status**: Documented - Requires Team Decision
**Date**: 2025-10-05

## Problem Statement

The current password reset implementation has an ambiguity in verification code delivery that could result in users receiving two separate emails:

1. **Cognito's built-in email**: AWS Cognito automatically sends a verification code email when `forgotPassword()` is called
2. **Custom SES email**: Our application sends a branded email via SES with a reset link

### Current Implementation

```java
// PasswordResetService.java lines 90-98
// Build reset link
// Format: https://app.batbern.ch/auth/reset-password?email={email}&lang={lang}
// Note: The actual verification code will be sent by Cognito via email
// We're sending a custom email with our branding that includes the code
String resetLink = String.format(
    "%s/auth/reset-password?email=%s&lang=%s",
    frontendUrl,
    URLEncoder.encode(email, StandardCharsets.UTF_8),
    language
);
```

**Issue**: The reset link does NOT include the verification code, yet the comment suggests our custom email should include it.

## Impact Assessment

### User Experience Issues
- **Dual Emails**: Users receive two separate emails, causing confusion
- **Inconsistent Branding**: Cognito email uses AWS default templates, not BATbern branding
- **Code Mismatch**: If users attempt to use code from wrong email, reset fails

### Security Considerations
- **Low Risk**: Both emails are legitimate and sent to verified email address
- **No Data Exposure**: Verification code is time-limited (typically 24 hours)
- **Audit Trail**: Both email sends are logged

## Proposed Solutions

### Option 1: Disable Cognito Email (Recommended)

**Implementation**:
1. Configure Cognito User Pool to use custom message Lambda trigger
2. Lambda returns empty message to suppress Cognito's email
3. Extract verification code from `ForgotPasswordResponse.codeDeliveryDetails()`
4. Include code in custom SES email

**Pros**:
- Single branded email to user
- Full control over email content and timing
- Consistent with UX expectations

**Cons**:
- Requires Lambda function deployment
- Additional complexity in infrastructure
- Code extraction may not be supported by all Cognito configurations

**Effort**: 4-6 hours (Lambda + infrastructure + testing)

### Option 2: Use Cognito Email Only

**Implementation**:
1. Remove custom SES email sending
2. Customize Cognito email templates via User Pool settings
3. Update Cognito templates with BATbern branding (limited HTML support)

**Pros**:
- Simpler implementation (remove code)
- Leverages built-in Cognito functionality
- One less external dependency (SES templates)

**Cons**:
- Limited email customization (Cognito template restrictions)
- Cannot achieve full brand consistency
- Loses bilingual email capability (AC15-18)

**Effort**: 2-3 hours (template configuration + testing)

**Blocker**: Violates AC15-18 (bilingual email templates)

### Option 3: Document Current Behavior (Temporary)

**Implementation**:
1. Document that two emails are sent by design
2. Update user-facing documentation to explain dual emails
3. Add warning in admin interface
4. Plan for Option 1 implementation in future story

**Pros**:
- Zero development effort
- Allows MVP to proceed
- Can be implemented properly later

**Cons**:
- Poor user experience
- Unprofessional appearance
- May confuse users during password reset

**Effort**: 1 hour (documentation only)

## Recommendation

**For MVP/Story 1.2.2 Completion**: **Option 3** (Document current behavior)

**For Production Readiness**: **Option 1** (Disable Cognito email with Lambda)

### Rationale

1. **MVP Urgency**: Option 3 allows story completion without blocking production
2. **Full Solution**: Option 1 provides best long-term UX and security
3. **Phased Approach**: Create follow-up story 1.2.2b for Option 1 implementation

## Implementation Plan (Option 1 - Future Story)

### Prerequisites
- AWS Lambda function in infrastructure stack
- Cognito User Pool custom message trigger configuration
- Updated PasswordResetService to extract and include verification code

### Code Changes Required

**1. Infrastructure (Lambda)**
```typescript
// infrastructure/lib/stacks/cognito-stack.ts
const customMessageLambda = new lambda.Function(this, 'CustomMessageLambda', {
  runtime: lambda.Runtime.NODEJS_18_X,
  handler: 'index.handler',
  code: lambda.Code.fromInline(`
    exports.handler = async (event) => {
      if (event.triggerSource === 'CustomMessage_ForgotPassword') {
        // Return empty to suppress Cognito email
        event.response.emailMessage = '';
        event.response.emailSubject = '';
      }
      return event;
    };
  `)
});

userPool.addTrigger(cognito.UserPoolOperation.CUSTOM_MESSAGE, customMessageLambda);
```

**2. PasswordResetService Enhancement**
```java
// Extract code from Cognito response
String verificationCode = response.codeDeliveryDetails().deliveryDestination();

// Include in reset link
String resetLink = String.format(
    "%s/auth/reset-password?email=%s&code=%s&lang=%s",
    frontendUrl,
    URLEncoder.encode(email, StandardCharsets.UTF_8),
    verificationCode,
    language
);
```

**3. Testing Requirements**
- Unit tests: Verify code extraction logic
- Integration tests: Confirm single email sent
- E2E tests: Validate full password reset flow with code
- Manual test: Verify Cognito email suppression

## Decision Required

**Team Input Needed**:
1. Accept Option 3 for MVP (document current behavior)?
2. Create Story 1.2.2b for Option 1 implementation?
3. Timeline for production-ready solution?

## References

- QA Gate: `/docs/qa/gates/1.2.2-implement-forgot-password-flow.yml`
- Implementation: `api-gateway/src/main/java/ch/batbern/gateway/auth/service/PasswordResetService.java:90-98`
- AWS Docs: [Cognito Custom Message Lambda Triggers](https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-lambda-custom-message.html)
- Story: `/docs/stories/1.2.2-implement-forgot-password-flow.md`

---

**Status**: ✅ SEC-001 Documented - Awaiting team decision on implementation approach
