package devflow.agent.executor;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 用 java-diff-utils 生成结构化 patch。
 */
final class StructuredPatchSupport {

    List<StructuredPatchHunk> build(String original, String updated) {
        List<String> sourceLines = splitLines(original);
        List<String> targetLines = splitLines(updated);
        Patch<String> patch = DiffUtils.diff(sourceLines, targetLines);
        List<StructuredPatchHunk> hunks = new ArrayList<>();
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            List<String> lines = new ArrayList<>();
            for (String line : delta.getSource().getLines()) {
                lines.add("-" + line);
            }
            for (String line : delta.getTarget().getLines()) {
                lines.add("+" + line);
            }
            hunks.add(new StructuredPatchHunk(
                    delta.getSource().getPosition() + 1,
                    delta.getSource().size(),
                    delta.getTarget().getPosition() + 1,
                    delta.getTarget().size(),
                    lines
            ));
        }
        return List.copyOf(hunks);
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(content.split("\\R", -1));
    }
}
