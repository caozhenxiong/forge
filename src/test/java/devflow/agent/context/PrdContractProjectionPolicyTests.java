package devflow.agent.context;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdContractProjectionPolicyTests {

    private final PrdContractProjectionPolicy policy = new PrdContractProjectionPolicy();

    @Test
    void retainsSettledContractItems() {
        List<String> retained = policy.retainContractItems(List.of(
                "支持方块左右移动和旋转",
                "显示当前得分"
        ));

        assertEquals(2, retained.size());
        assertTrue(retained.contains("支持方块左右移动和旋转"));
        assertTrue(retained.contains("显示当前得分"));
    }

    @Test
    void routesExplicitPendingMarkersToHumanReview() {
        assertTrue(policy.shouldRouteToHumanReview("暂停/继续功能（待确认）"));
        assertTrue(policy.shouldRouteToHumanReview("Open Question: whether touch controls are needed"));
        assertFalse(policy.shouldRouteToHumanReview("支持触屏操作"));
    }

    @Test
    void routesDirectQuestionItemsToHumanReview() {
        assertTrue(policy.shouldRouteToHumanReview("是否需要支持移动端触控操作？"));
        assertTrue(policy.shouldRouteToHumanReview("Whether touch controls are required"));
        assertFalse(policy.shouldRouteToHumanReview("支持移动端触控操作"));
    }

    @Test
    void routesExplicitLowAuthorityLabelsOutOfContract() {
        assertTrue(policy.shouldRouteToHumanReview("推断：支持移动端触摸操作"));
        assertTrue(policy.shouldRouteToHumanReview("建议：添加简单的音效反馈"));
        assertTrue(policy.shouldRouteToHumanReview("Design Choice: use a single HTML file"));
        assertFalse(policy.shouldRouteToHumanReview("支持不同难度等级设置"));
    }
}
