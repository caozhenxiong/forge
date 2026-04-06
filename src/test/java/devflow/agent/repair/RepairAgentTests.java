package devflow.agent.repair;

import devflow.agent.review.FixMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairAgentTests {

    @Test
    void repairBriefMarkdownIncludesStrongConstraintSections() {
        RepairBrief brief = new RepairBrief(
                "数独结构错误",
                List.of("index.html 不是有效 HTML"),
                "入口文件被错误覆盖为样式内容",
                List.of("index.html", "main.js"),
                List.of("Playwright 无法找到页面主结构"),
                FixMode.PATCH,
                List.of("先恢复有效 HTML 页面骨架"),
                List.of("不要继续优先修性能指标"),
                List.of("不要重写无关 UI 文案"),
                List.of("页面恢复为可交互的数独主界面"),
                List.of("h1、模式切换、棋盘容器可见")
        );

        String markdown = brief.toMarkdown();

        assertTrue(markdown.contains("## Must Fix First"));
        assertTrue(markdown.contains("## Forbidden Directions"));
        assertTrue(markdown.contains("## Acceptance Checks"));
        assertTrue(markdown.contains("先恢复有效 HTML 页面骨架"));
    }

    @Test
    void repairAgentBuildsEnforcedRepairNote() {
        RepairAgent repairAgent = new RepairAgent();
        RepairBrief brief = new RepairBrief(
                "数独结构错误",
                List.of("index.html 不是有效 HTML"),
                "入口文件被错误覆盖为样式内容",
                List.of("index.html"),
                List.of("test cases 无法定位页面结构"),
                FixMode.PATCH,
                List.of("先恢复 index.html 为有效 HTML"),
                List.of("不要继续围绕性能监控做修改"),
                List.of("不要改动已通过的题库 UI 配色"),
                List.of("首页 DOM 结构恢复正常"),
                List.of("h1 和棋盘根容器都能被找到")
        );

        String note = repairAgent.buildRepairNote(
                FixMode.PATCH,
                "当前实现方向跑偏",
                "请回到页面结构问题",
                "index.html 当前不是 HTML",
                "恢复首页结构",
                brief
        );

        assertTrue(note.contains("[REPAIR_BRIEF_ENFORCED]"));
        assertTrue(note.contains("Must Fix First"));
        assertTrue(note.contains("Forbidden Directions"));
        assertTrue(note.contains("如果本轮没有覆盖上述关键项，视为修复未完成"));
    }
}
