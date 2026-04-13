package devflow.agent.interfaceadapter.cli;

import devflow.agent.domain.StageType;
import devflow.agent.util.EnumParsers;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * CLI 参数解析支撑。
 */
final class CliArgumentSupport {

    StageType parseStage(String value) {
        StageType stageType = EnumParsers.parseIgnoreCase(StageType.class, value, null);
        if (stageType == null) {
            throw new IllegalArgumentException("Unknown stage: " + value);
        }
        return stageType;
    }

    Path optionPath(String[] args, String optionName, Path defaultValue) {
        return Path.of(option(args, optionName, defaultValue.toString())).toAbsolutePath().normalize();
    }

    String option(String[] args, String optionName, String defaultValue) {
        for (int index = 0; index < args.length - 1; index++) {
            if (optionName.equals(args[index])) {
                StringBuilder value = new StringBuilder();
                for (int next = index + 1; next < args.length; next++) {
                    String token = args[next];
                    if (token.startsWith("--")) {
                        break;
                    }
                    if (!value.isEmpty()) {
                        value.append(' ');
                    }
                    value.append(token);
                }
                return value.isEmpty() ? defaultValue : value.toString();
            }
        }
        return defaultValue;
    }

    String requiredOption(String[] args, String optionName) {
        String value = option(args, optionName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option: " + optionName);
        }
        return value;
    }

    String requiredPositional(String[] args, int position) {
        if (args.length <= position) {
            throw new IllegalArgumentException(
                    "Missing required positional argument at index " + position + ": " + Arrays.toString(args)
            );
        }
        return args[position];
    }

    boolean hasFlag(String[] args, String flag) {
        return Arrays.stream(args).anyMatch(flag::equals);
    }
}
