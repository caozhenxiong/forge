package devflow.agent.interfaceadapter.cli;

import devflow.agent.domain.StageType;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliArgumentSupportTests {

    private final CliArgumentSupport support = new CliArgumentSupport();

    @Test
    void parsesJoinedOptionValueUntilNextFlag() {
        String[] args = {"run", "bootstrap", "--goal", "build", "a", "game", "--project", "./demo"};

        assertEquals("build a game", support.requiredOption(args, "--goal"));
        assertTrue(support.optionPath(args, "--project", Path.of(".")).endsWith("demo"));
    }

    @Test
    void parsesStageAndFlags() {
        String[] args = {"run", "approve", "123", "design", "--auto-approve"};

        assertEquals(StageType.DESIGN, support.parseStage(support.requiredPositional(args, 3)));
        assertTrue(support.hasFlag(args, "--auto-approve"));
    }
}
