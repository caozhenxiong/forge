package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
import java.util.List;

/**
 * TEST 规划与修复阶段唯一允许使用的观测 target 入口。
 *
 * <p>这层只消费已经解析好的 contract，不再根据 canvasCount、
 * hasCanvas 或 selector 顺序重新猜主观测面。
 */
final class UiRuntimeObservationPolicy {

    UiObservationTarget requiredTarget(UiRuntimeContract contract, CapabilitySurface surface) {
        if (contract == null) {
            return null;
        }
        UiObservationTarget direct = contract.targetFor(surface);
        if (direct != null) {
            return direct;
        }
        if (surface == CapabilitySurface.PRIMARY_INTERACTION || surface == CapabilitySurface.TIMED_STATE_PROGRESSION) {
            return contract.targetFor(CapabilitySurface.PRIMARY_VISUAL_SURFACE);
        }
        return null;
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

    int observationWaitMs(List<CapabilitySurface> capabilities) {
        return TestPlanningPolicy.observationWaitMs(capabilities);
    }
}
