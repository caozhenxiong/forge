package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * TEST 规划与修复阶段唯一允许使用的观测 target 入口。
 *
 * <p>这层只消费已经解析好的 contract，不再根据 canvasCount、
 * hasCanvas 或 selector 顺序重新猜主观测面。
 */
final class UiRuntimeObservationPolicy {

    UiObservationTarget requiredTarget(UiRuntimeContract contract, String capabilityId) {
        if (contract == null) {
            return null;
        }
        return contract.targetFor(capabilityId);
    }

    TestStepSpec presenceAssertion(UiObservationTarget target) {
        if (target == null) {
            return null;
        }
        return new TestStepSpec(
                TestStepAction.ASSERT_SELECTOR,
                target.selector(),
                null,
                null,
                null,
                null,
                false,
                TestStepSemantic.PRIMARY_SURFACE
        );
    }

    TestStepSpec snapshotStep(UiObservationTarget target, String snapshotKey, boolean optional) {
        if (target == null) {
            return null;
        }
        if (target.mode() == UiObservationMode.CANVAS_HASH) {
            return new TestStepSpec(
                    TestStepAction.SNAPSHOT_CANVAS_HASH,
                    target.selector(),
                    null,
                    null,
                    null,
                    snapshotKey,
                    optional,
                    TestStepSemantic.PRIMARY_SURFACE
            );
        }
        return new TestStepSpec(
                TestStepAction.SNAPSHOT_DOM_SIGNATURE,
                target.selector(),
                null,
                null,
                null,
                snapshotKey,
                optional,
                TestStepSemantic.PRIMARY_SURFACE
        );
    }

    TestStepSpec changedAssertion(UiObservationTarget target, String snapshotKey, boolean optional) {
        if (target == null) {
            return null;
        }
        if (target.mode() == UiObservationMode.CANVAS_HASH) {
            return new TestStepSpec(
                    TestStepAction.ASSERT_CANVAS_HASH_CHANGED,
                    target.selector(),
                    null,
                    null,
                    null,
                    snapshotKey,
                    optional,
                    TestStepSemantic.PRIMARY_SURFACE
            );
        }
        return new TestStepSpec(
                TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED,
                target.selector(),
                null,
                null,
                null,
                snapshotKey,
                optional,
                TestStepSemantic.PRIMARY_SURFACE
        );
    }

    TestStepSpec unchangedAssertion(UiObservationTarget target, String snapshotKey, boolean optional) {
        if (target == null) {
            return null;
        }
        if (target.mode() == UiObservationMode.CANVAS_HASH) {
            return new TestStepSpec(
                    TestStepAction.ASSERT_CANVAS_HASH_UNCHANGED,
                    target.selector(),
                    null,
                    null,
                    null,
                    snapshotKey,
                    optional,
                    TestStepSemantic.PRIMARY_SURFACE
            );
        }
        return new TestStepSpec(
                TestStepAction.ASSERT_DOM_SIGNATURE_UNCHANGED,
                target.selector(),
                null,
                null,
                null,
                snapshotKey,
                optional,
                TestStepSemantic.PRIMARY_SURFACE
        );
    }

    TestStepAction snapshotAction(UiObservationTarget target) {
        if (target == null) {
            return null;
        }
        return target.mode() == UiObservationMode.CANVAS_HASH
                ? TestStepAction.SNAPSHOT_CANVAS_HASH
                : TestStepAction.SNAPSHOT_DOM_SIGNATURE;
    }

    TestStepAction changedAssertionAction(UiObservationTarget target) {
        if (target == null) {
            return null;
        }
        return target.mode() == UiObservationMode.CANVAS_HASH
                ? TestStepAction.ASSERT_CANVAS_HASH_CHANGED
                : TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED;
    }

    TestStepAction unchangedAssertionAction(UiObservationTarget target) {
        if (target == null) {
            return null;
        }
        return target.mode() == UiObservationMode.CANVAS_HASH
                ? TestStepAction.ASSERT_CANVAS_HASH_UNCHANGED
                : TestStepAction.ASSERT_DOM_SIGNATURE_UNCHANGED;
    }

    TestStepSpec comparisonAssertion(UiObservationTarget target, TestObservationComparison comparison, String snapshotKey, boolean optional) {
        if (comparison == TestObservationComparison.UNCHANGED) {
            return unchangedAssertion(target, snapshotKey, optional);
        }
        if (comparison == TestObservationComparison.CHANGED) {
            return changedAssertion(target, snapshotKey, optional);
        }
        return null;
    }

    TestStepAction comparisonAssertionAction(UiObservationTarget target, TestObservationComparison comparison) {
        if (comparison == TestObservationComparison.UNCHANGED) {
            return unchangedAssertionAction(target);
        }
        if (comparison == TestObservationComparison.CHANGED) {
            return changedAssertionAction(target);
        }
        return null;
    }

    TestStepSpec waitStep(TestObservationTrigger trigger) {
        return new TestStepSpec(
                TestStepAction.WAIT,
                null,
                null,
                null,
                observationWaitMs(trigger),
                null,
                false
        );
    }

    int observationWaitMs(TestObservationTrigger trigger) {
        return TestPlanningPolicy.observationWaitMs(trigger);
    }
}
