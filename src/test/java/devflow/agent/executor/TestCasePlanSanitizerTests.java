package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCasePlanSanitizerTests {

    private final TestCasePlanSanitizer sanitizer = new TestCasePlanSanitizer();

    @Test
    void sanitizeRepairsPauseCaseByInjectingStartAndMovingSnapshotsBeforeInteractions() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                1,
                List.of("body", "#game-canvas", "#start-btn", "#pause-btn", "#score-display"),
                List.of(),
                List.of(),
                List.of()
        );
        PlannedTestCasePayload startGuide = new PlannedTestCasePayload(
                "TC-START",
                "开始游戏",
                "functional",
                true,
                "index.html",
                "",
                "点击后进入运行态",
                List.of(
                        step("ASSERT_SELECTOR", "#start-btn", null, null, null, null, false, "run-state-entry"),
                        step("CLICK", "#start-btn", null, null, null, null, false, "run-state-entry")
                )
        );
        PlannedTestCasePayload raw = new PlannedTestCasePayload(
                "TC-003",
                "暂停与继续功能测试",
                "functional",
                true,
                "index.html",
                "游戏已开始运行",
                "点击暂停后停止，再点击恢复",
                List.of("pause-freeze"),
                List.of(
                        step("CLICK", "#pause-btn", null, null, null, null, false, "pause-toggle"),
                        step("SNAPSHOT_CANVAS_HASH", "#game-canvas", null, null, null, null, false, "primary-surface"),
                        step("WAIT", null, null, null, 100, null, false, null),
                        step("ASSERT_CANVAS_HASH_CHANGED", "#game-canvas", null, null, null, null, true, "primary-surface"),
                        step("CLICK", "#pause-btn", null, null, null, null, false, "pause-toggle"),
                        step("SNAPSHOT_CANVAS_HASH", "#game-canvas", null, null, null, null, false, "primary-surface"),
                        step("WAIT", null, null, null, 100, null, false, null),
                        step("ASSERT_CANVAS_HASH_CHANGED", "#game-canvas", null, null, null, null, false, "primary-surface")
                )
        );

        List<TestCaseSpec> cases = sanitizer.sanitize(List.of(startGuide, raw), List.of(), "index.html", snapshot);

        TestCaseSpec repaired = cases.stream().filter(testCase -> "TC-003".equals(testCase.id())).findFirst().orElseThrow();
        List<TestStepSpec> steps = repaired.steps();
        int startClickIndex = indexOf(steps, TestStepAction.CLICK, "#start-btn", 0);
        assertTrue(startClickIndex >= 0);

        int firstPauseIndex = indexOf(steps, TestStepAction.CLICK, "#pause-btn", 0);
        int firstSnapshotIndex = indexOf(steps, TestStepAction.SNAPSHOT_CANVAS_HASH, "#game-canvas", 0);
        int firstAssertChangedIndex = indexOf(steps, TestStepAction.ASSERT_CANVAS_HASH_CHANGED, "#game-canvas", 0);
        assertTrue(startClickIndex < firstPauseIndex);
        assertTrue(firstSnapshotIndex < firstPauseIndex);
        assertTrue(firstPauseIndex < firstAssertChangedIndex);

        int secondPauseIndex = indexOf(steps, TestStepAction.CLICK, "#pause-btn", firstPauseIndex + 1);
        int secondSnapshotIndex = indexOf(steps, TestStepAction.SNAPSHOT_CANVAS_HASH, "#game-canvas", firstSnapshotIndex + 1);
        int secondAssertChangedIndex = indexOf(steps, TestStepAction.ASSERT_CANVAS_HASH_CHANGED, "#game-canvas", firstAssertChangedIndex + 1);
        assertTrue(secondSnapshotIndex < secondPauseIndex);
        assertTrue(secondPauseIndex < secondAssertChangedIndex);
    }

    @Test
    void sanitizeInjectsStartBeforeKeyboardInteractionCase() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                1,
                List.of("body", "#game-canvas", "#start-btn"),
                List.of(),
                List.of(),
                List.of()
        );
        PlannedTestCasePayload startGuide = new PlannedTestCasePayload(
                "TC-START",
                "开始游戏",
                "functional",
                true,
                "index.html",
                "",
                "进入运行态",
                List.of(
                        step("ASSERT_SELECTOR", "#start-btn", null, null, null, null, false, "run-state-entry"),
                        step("CLICK", "#start-btn", null, null, null, null, false, "run-state-entry")
                )
        );
        PlannedTestCasePayload raw = new PlannedTestCasePayload(
                "TC-005",
                "方向键控制功能测试",
                "functional",
                true,
                "index.html",
                "游戏已开始运行",
                "方向键后画布变化",
                List.of(
                        step("PRESS_KEY", null, "ArrowRight", null, null, null, false, null),
                        step("SNAPSHOT_CANVAS_HASH", "#game-canvas", null, null, null, null, false, "primary-surface"),
                        step("WAIT", null, null, null, 100, null, false, null),
                        step("ASSERT_CANVAS_HASH_CHANGED", "#game-canvas", null, null, null, null, false, "primary-surface")
                )
        );

        List<TestCaseSpec> cases = sanitizer.sanitize(List.of(startGuide, raw), List.of(), "index.html", snapshot);

        List<TestStepSpec> steps = cases.stream().filter(testCase -> "TC-005".equals(testCase.id())).findFirst().orElseThrow().steps();
        assertEquals(TestStepAction.ASSERT_SELECTOR, steps.get(0).action());
        assertEquals("#start-btn", steps.get(0).selector());
        assertEquals(TestStepAction.CLICK, steps.get(1).action());
        assertEquals("#start-btn", steps.get(1).selector());

        int keyIndex = indexOf(steps, TestStepAction.PRESS_KEY, null, 0);
        int snapshotIndex = indexOf(steps, TestStepAction.SNAPSHOT_CANVAS_HASH, "#game-canvas", 0);
        assertTrue(snapshotIndex < keyIndex);
    }

    @Test
    void sanitizeRemovesNonControlClicksBeforeKeyboardInteraction() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                1,
                List.of("body", "#game-canvas", "#start-button", "#score-display"),
                List.of(),
                List.of(),
                List.of()
        );
        PlannedTestCasePayload startGuide = new PlannedTestCasePayload(
                "TC-START",
                "开始游戏",
                "functional",
                true,
                "index.html",
                "",
                "进入运行态",
                List.of(
                        step("ASSERT_SELECTOR", "#start-button", null, null, null, null, false, "run-state-entry"),
                        step("CLICK", "#start-button", null, null, null, null, false, "run-state-entry")
                )
        );
        PlannedTestCasePayload raw = new PlannedTestCasePayload(
                "TC-006",
                "方向键控制功能测试",
                "functional",
                true,
                "index.html",
                "游戏已启动",
                "方向键后画布变化",
                List.of(
                        step("ASSERT_SELECTOR", "#score-display", null, null, null, null, false, "progress-signal"),
                        step("CLICK", "#score-display", null, null, null, null, false, "progress-signal"),
                        step("SNAPSHOT_CANVAS_HASH", "#game-canvas", null, null, null, null, false, "primary-surface"),
                        step("PRESS_KEY", null, "ArrowRight", null, null, null, false, null),
                        step("WAIT", null, null, null, 100, null, false, null),
                        step("ASSERT_CANVAS_HASH_CHANGED", "#game-canvas", null, null, null, null, false, "primary-surface")
                )
        );

        List<TestCaseSpec> cases = sanitizer.sanitize(List.of(startGuide, raw), List.of(), "index.html", snapshot);

        List<TestStepSpec> steps = cases.stream().filter(testCase -> "TC-006".equals(testCase.id())).findFirst().orElseThrow().steps();
        assertFalse(steps.stream().anyMatch(step -> "#score-display".equals(step.selector()) && step.action() == TestStepAction.CLICK));
        assertFalse(steps.stream().anyMatch(step -> "#score-display".equals(step.selector()) && step.action() == TestStepAction.ASSERT_SELECTOR));
        assertTrue(steps.stream().anyMatch(step -> "#start-button".equals(step.selector()) && step.action() == TestStepAction.CLICK));
    }

    @Test
    void sanitizeDowngradesCanvasObservationToDomWhenRuntimeHasNoCanvas() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                0,
                List.of("body", "#app-root", "#game-board", "#start-btn"),
                List.of(),
                List.of(),
                List.of()
        );
        PlannedTestCasePayload raw = new PlannedTestCasePayload(
                "TC-005",
                "方向键控制功能测试",
                "functional",
                true,
                "index.html",
                "游戏已开始运行",
                "方向键后棋盘区域变化",
                List.of(
                        new PlannedTestStepPayload("PRESS_KEY", null, "ArrowRight", null, null, null, false),
                        new PlannedTestStepPayload("SNAPSHOT_CANVAS_HASH", null, null, null, null, null, false),
                        new PlannedTestStepPayload("WAIT", null, null, null, 100, null, false),
                        new PlannedTestStepPayload("ASSERT_CANVAS_HASH_CHANGED", null, null, null, null, null, false)
                )
        );

        List<TestCaseSpec> cases = sanitizer.sanitize(List.of(raw), List.of(), "index.html", snapshot);

        List<TestStepSpec> steps = cases.getFirst().steps();
        assertTrue(steps.stream().noneMatch(step -> step.action() == TestStepAction.SNAPSHOT_CANVAS_HASH));
        assertTrue(steps.stream().noneMatch(step -> step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED));
        assertTrue(steps.stream().anyMatch(step ->
                step.action() == TestStepAction.SNAPSHOT_DOM_SIGNATURE && "#app-root".equals(step.selector())
        ));
        assertTrue(steps.stream().anyMatch(step ->
                step.action() == TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED && "#app-root".equals(step.selector())
        ));
    }

    @Test
    void sanitizeReplacesBrittleNumericScoreAssertionsWithSelectorCheck() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                1,
                List.of("body", "#game-canvas", "#start-btn", "#score-display"),
                List.of(),
                List.of(),
                List.of()
        );
        PlannedTestCasePayload raw = new PlannedTestCasePayload(
                "TC-004",
                "得分显示功能测试",
                "functional",
                true,
                "index.html",
                "游戏已开始并运行一段时间",
                "得分增加",
                List.of(
                        step("SNAPSHOT_CANVAS_HASH", "canvas", null, null, null, "observed-TC-004", false, "primary-surface"),
                        step("CLICK", "#start-btn", null, null, null, null, false, "run-state-entry"),
                        step("WAIT", null, null, null, 500, null, false, null),
                        step("ASSERT_TEXT_CONTAINS", "#score-display", null, null, null, "0", false, "progress-signal"),
                        step("WAIT", null, null, null, 1000, null, false, null),
                        step("ASSERT_TEXT_CONTAINS", "#score-display", null, null, null, "100", false, "progress-signal"),
                        step("ASSERT_CANVAS_HASH_CHANGED", "canvas", null, null, null, "observed-TC-004", false, "primary-surface")
                )
        );

        List<TestCaseSpec> cases = sanitizer.sanitize(List.of(raw), List.of(), "index.html", snapshot);

        List<TestStepSpec> steps = cases.getFirst().steps();
        assertTrue(steps.stream().anyMatch(step ->
                step.action() == TestStepAction.ASSERT_SELECTOR && "#score-display".equals(step.selector())
        ));
        assertFalse(steps.stream().anyMatch(step ->
                step.action() == TestStepAction.ASSERT_TEXT_CONTAINS && "#score-display".equals(step.selector())
        ));
        assertTrue(steps.stream().anyMatch(step -> step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED));
        assertTrue(cases.getFirst().capabilities().contains(CapabilitySurface.VISIBLE_PROGRESS_SIGNAL));
    }

    @Test
    void sanitizeSoftensSingleStartCaseByMakingSurfaceChangeOptional() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                2,
                List.of("body", "#game-canvas", "#start-button"),
                List.of(),
                List.of(),
                List.of()
        );
        PlannedTestCasePayload raw = new PlannedTestCasePayload(
                "TC-002",
                "游戏启动功能",
                "functional",
                true,
                "index.html",
                "页面已加载",
                "点击开始按钮后进入运行态",
                List.of(
                        step("SNAPSHOT_CANVAS_HASH", "#game-canvas", null, null, null, null, false, "primary-surface"),
                        step("CLICK", "#start-button", null, null, null, null, false, "run-state-entry"),
                        step("ASSERT_TEXT_CONTAINS", "#start-button", null, null, null, "暂停游戏", false, "run-state-entry"),
                        step("WAIT", null, null, null, 250, null, false, null),
                        step("ASSERT_CANVAS_HASH_CHANGED", "#game-canvas", null, null, null, null, false, "primary-surface")
                )
        );

        List<TestCaseSpec> cases = sanitizer.sanitize(List.of(raw), List.of(), "index.html", snapshot);

        List<TestStepSpec> steps = cases.getFirst().steps();
        TestStepSpec changed = steps.stream()
                .filter(step -> step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED)
                .findFirst()
                .orElseThrow();
        assertTrue(Boolean.TRUE.equals(changed.optional()));
        TestStepSpec text = steps.stream()
                .filter(step -> step.action() == TestStepAction.ASSERT_TEXT_CONTAINS)
                .findFirst()
                .orElseThrow();
        assertFalse(Boolean.TRUE.equals(text.optional()));
    }

    @Test
    void sanitizeSoftensRepeatedControlTextAssertions() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                2,
                List.of("body", "#game-canvas", "#start-button"),
                List.of(),
                List.of(),
                List.of()
        );
        PlannedTestCasePayload raw = new PlannedTestCasePayload(
                "TC-004",
                "暂停与继续功能",
                "functional",
                true,
                "index.html",
                "游戏已启动",
                "暂停后可恢复",
                List.of(
                        step("SNAPSHOT_CANVAS_HASH", "canvas", null, null, null, "observed-TC-004", false, "primary-surface"),
                        step("CLICK", "#start-button", null, null, null, null, false, "run-state-entry"),
                        step("ASSERT_TEXT_CONTAINS", "#start-button", null, null, null, "暂停游戏", false, "run-state-entry"),
                        step("CLICK", "#start-button", null, null, null, null, false, "pause-toggle"),
                        step("ASSERT_TEXT_CONTAINS", "#start-button", null, null, null, "继续游戏", false, "pause-toggle"),
                        step("CLICK", "#start-button", null, null, null, null, false, "pause-toggle"),
                        step("ASSERT_TEXT_CONTAINS", "#start-button", null, null, null, "暂停游戏", false, "pause-toggle"),
                        step("WAIT", null, null, null, 250, null, false, null),
                        step("ASSERT_CANVAS_HASH_CHANGED", "canvas", null, null, null, "observed-TC-004", false, "primary-surface")
                )
        );

        List<TestCaseSpec> cases = sanitizer.sanitize(List.of(raw), List.of(), "index.html", snapshot);

        List<TestStepSpec> steps = cases.getFirst().steps();
        assertTrue(steps.stream()
                .filter(step -> step.action() == TestStepAction.ASSERT_TEXT_CONTAINS)
                .allMatch(step -> Boolean.TRUE.equals(step.optional())));
        assertTrue(steps.stream().anyMatch(step ->
                step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED && !Boolean.TRUE.equals(step.optional())
        ));
    }

    private int indexOf(List<TestStepSpec> steps, TestStepAction action, String selector, int start) {
        for (int index = start; index < steps.size(); index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() != action) {
                continue;
            }
            if (selector == null || selector.equals(step.selector())) {
                return index;
            }
        }
        return -1;
    }

    private PlannedTestStepPayload step(
            String action,
            String selector,
            String key,
            Integer count,
            Integer ms,
            String text,
            boolean optional,
            String semantic
    ) {
        return new PlannedTestStepPayload(action, selector, key, count, ms, text, optional, semantic);
    }
}
