# Review V16 Progress

## Current Status

- `PHASE_1_CANONICAL_SCOPE_OWNER`: done
- `PHASE_2_ACTIVE_RETRY_CARRIER`: done
- `PHASE_3_OWNER_BOUNDARY_REGRESSIONS`: done
- `PHASE_4_INTEGRATION`: pending-review

## Scope Checklist

### Scope 1. Canonical Patch Scope Owner

- [x] `SubtaskRepairDirectiveResolver`
  - generic structured `PATCH_EXISTING_IMPLEMENTATION` without explicit safe scope no longer falls back to whole `allowedScope`
  - deterministic current-scope producer still resolves to current active scope
- [x] existing structured/runtime missing-scope flows remain blocked as `REQUEST_HUMAN`

### Scope 2. Active Retry Carrier

- [x] `SubtaskRetryFeedbackRenderer`
  - active retry feedback now preserves `implementationPatchTarget + overrideChanges`
- [x] `ExecutionDirectiveFeedbackSupport`
  - active feedback payload no longer strips concrete patch package
  - repair brief path still strips concrete package
- [x] `ExecutionDirectivePayload.mergeCanonicalPatchPackage(...)`
  - fresh feedback without concrete package no longer revives base concrete package
- [x] `ImplementationPlanRunner`
  - first active subtask still receives active retry carrier
  - later subtasks still receive repair brief only

### Scope 3. Owner / Derived View Boundary

- [x] machine owner remains `SubtaskRevisionDirective -> SubtaskExecutionState.effectiveChanges`
- [x] no code path reintroduced feedback as machine owner
- [x] `TaskPackage` / prompt side still consume active scope derived from execution state, not merged feedback

### Scope 4. Regression Tests

- [x] `SubtaskRepairDirectiveResolverTests`
  - generic structured patch without explicit scope => `REQUEST_HUMAN`
- [x] `SubtaskRetryFeedbackRendererTests`
  - active retry feedback preserves concrete package
- [x] `ExecutionDirectiveFeedbackSupportTests`
  - fresh active retry package preserved
  - base concrete package not revived when fresh feedback lacks scope
  - repair brief still strips concrete package
- [x] `ExecutionDirectivePayloadTests`
  - direct owner regression for `mergeCanonicalPatchPackage(...)`
- [x] `ImplementationPlanRunnerTests`
  - first active subtask gets active retry carrier
  - later subtasks still do not get concrete package

## Verification

- [x] `mvn -q -Dtest=SubtaskRepairDirectiveResolverTests,SubtaskRetryFeedbackRendererTests,ExecutionDirectiveFeedbackSupportTests,ExecutionDirectivePayloadTests,ImplementationPlanRunnerTests,SubtaskVerificationSupportTests,TestExecutorTests test`
- [ ] integration test
  - blocked by process discipline: review first, integration after review

## Notes

- 本轮没有扩到 `ArtifactContextSanitizer`、outline runtime split、`ImplementationResumePolicy` 其他残留问题。
- 本轮没有引入 fallback、兼容层、双轨 owner。
