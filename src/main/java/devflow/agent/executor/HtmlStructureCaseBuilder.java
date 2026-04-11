package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.quality.CapabilitySurface;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 HTML 结构与运行时快照生成保守交互用例。
 */
final class HtmlStructureCaseBuilder {

    private final UiRuntimeObservationPolicy observationPolicy = new UiRuntimeObservationPolicy();

    void appendPrimarySurfaceCases(
            List<TestCaseSpec> cases,
            String entry,
            UiRuntimeContract runtimeContract,
            DocumentLanguage language
    ) {
        UiObservationTarget target = observationPolicy.requiredTarget(runtimeContract, CapabilitySurface.PRIMARY_VISUAL_SURFACE);
        if (target == null) {
            return;
        }
        cases.add(new TestCaseSpec(
                "TC-SMOKE-SURFACE",
                language.choose("主观测面存在", "Primary observation surface exists"),
                "smoke",
                true,
                entry,
                "",
                language.choose("页面应渲染主观测面并保持可运行。", "The page should render the primary observation surface and remain runnable."),
                List.of(
                        observationPolicy.presenceAssertion(target),
                        new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false)
                ),
                List.of(CapabilitySurface.PRIMARY_VISUAL_SURFACE, CapabilitySurface.RUNTIME_STABILITY)
        ));
    }

    void appendButtonCases(
            List<TestCaseSpec> cases,
            String entry,
            HtmlStructureSnapshot htmlSnapshot,
            RuntimeSnapshot runtimeSnapshot,
            UiRuntimeContract runtimeContract,
            DocumentLanguage language
    ) {
        Set<String> selectors = collectSelectors(htmlSnapshot, runtimeSnapshot);
        int index = 1;
        for (String selector : selectors) {
            if (selectors.size() > 3 && index > 3) {
                break;
            }
            cases.add(new TestCaseSpec(
                    "TC-FUNC-BTN-" + index,
                    language.choose("按钮 " + selector + " 点击不报错", "Clicking button " + selector + " does not throw"),
                    "functional",
                    false,
                    entry,
                    "",
                    language.choose("点击按钮后页面仍保持可运行，无运行时错误。", "After clicking the button, the page remains usable with no runtime errors."),
                    List.of(
                            new TestStepSpec(TestStepAction.ASSERT_SELECTOR, selector, null, null, null, null, true, TestStepSemantic.PRIMARY_CONTROL),
                        new TestStepSpec(TestStepAction.CLICK, selector, null, null, null, null, true, TestStepSemantic.PRIMARY_CONTROL),
                        new TestStepSpec(TestStepAction.WAIT, null, null, null, TestPlanningPolicy.defaultStepWaitMs(), null, false),
                        new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false)
                    ),
                    List.of(CapabilitySurface.PRIMARY_INTERACTION, CapabilitySurface.RUNTIME_STABILITY)
            ));
            index++;
        }

        String interactionSelector = selectInteractionSelector(selectors);
        if (interactionSelector != null) {
            TestCaseSpec observedCase = buildObservedInteractionCase(entry, interactionSelector, runtimeContract, language);
            if (observedCase != null) {
                cases.add(observedCase);
            }
        }
    }

    private Set<String> collectSelectors(HtmlStructureSnapshot htmlSnapshot, RuntimeSnapshot runtimeSnapshot) {
        Set<String> selectors = new LinkedHashSet<>();
        if (runtimeSnapshot != null && runtimeSnapshot.usable()) {
            for (String selector : runtimeSnapshot.selectors()) {
                if (selector.startsWith("#") || selector.startsWith(".") || "button".equals(selector)) {
                    selectors.add(selector);
                }
            }
        }
        selectors.addAll(htmlSnapshot.buttonSelectors());
        return selectors;
    }

    private String selectInteractionSelector(Set<String> selectors) {
        if (selectors == null || selectors.isEmpty()) {
            return null;
        }
        if (selectors.contains("button")) {
            return "button";
        }
        return selectors.stream()
                .filter(selector -> selector.startsWith("#") || selector.startsWith("."))
                .findFirst()
                .orElse(null);
    }

    private TestCaseSpec buildObservedInteractionCase(
            String entry,
            String selector,
            UiRuntimeContract runtimeContract,
            DocumentLanguage language
    ) {
        UiObservationTarget target = observationPolicy.requiredTarget(runtimeContract, CapabilitySurface.PRIMARY_INTERACTION);
        if (target == null) {
            return null;
        }
        List<TestStepSpec> steps = new ArrayList<>();
        steps.add(new TestStepSpec(TestStepAction.ASSERT_SELECTOR, selector, null, null, null, null, false, TestStepSemantic.PRIMARY_CONTROL));
        steps.add(observationPolicy.presenceAssertion(target));
        steps.add(observationPolicy.snapshotStep(target, "interactive-surface", false));
        steps.add(new TestStepSpec(TestStepAction.CLICK, selector, null, null, null, null, false, TestStepSemantic.PRIMARY_CONTROL));
        steps.add(new TestStepSpec(
                TestStepAction.WAIT,
                null,
                null,
                null,
                observationPolicy.observationWaitMs(List.of(CapabilitySurface.PRIMARY_INTERACTION)),
                null,
                false
        ));
        steps.add(observationPolicy.changedAssertion(target, "interactive-surface", false));
        steps.add(new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false));
        return new TestCaseSpec(
                "TC-FUNC-INTERACTIVE-STATE",
                language.choose("交互后状态发生变化", "Interaction causes an observable state change"),
                "functional",
                true,
                entry,
                "",
                language.choose("交互后页面状态必须发生可观察变化，而不只是保持不报错。", "After interaction, the page state must change observably rather than merely avoid errors."),
                List.copyOf(steps),
                List.of(CapabilitySurface.PRIMARY_INTERACTION)
        );
    }
}
