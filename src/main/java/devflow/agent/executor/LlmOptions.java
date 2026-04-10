package devflow.agent.executor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * 统一构造 LLM options，避免流程代码继续手工拼 Map key。
 */
public final class LlmOptions {

    private LlmOptions() {
    }

    public static Map<String, Object> numPredict(int value) {
        return Map.of(LlmOptionKeys.NUM_PREDICT, value);
    }

    public static Map<String, Object> numCtx(int value) {
        return Map.of(LlmOptionKeys.NUM_CTX, value);
    }

    public static Map<String, Object> outputBudgetRatio(double ratio) {
        return Map.of(LlmOptionKeys.OUTPUT_BUDGET_RATIO, ratio);
    }

    public static Map<String, Object> mergeOutputBudgetRatio(Map<String, Object> base, double fallbackRatio) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put(LlmOptionKeys.OUTPUT_BUDGET_RATIO, fallbackRatio);
        if (base != null) {
            merged.putAll(base);
        }
        return merged;
    }

    public static OptionalInt readNumPredict(Map<String, Object> options) {
        if (options == null || !options.containsKey(LlmOptionKeys.NUM_PREDICT)) {
            return OptionalInt.empty();
        }
        Object value = options.get(LlmOptionKeys.NUM_PREDICT);
        if (value instanceof Number number) {
            return OptionalInt.of(number.intValue());
        }
        if (value instanceof String stringValue) {
            try {
                return OptionalInt.of(Integer.parseInt(stringValue));
            } catch (NumberFormatException ignored) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    public static OptionalInt readNumCtx(Map<String, Object> options) {
        if (options == null || !options.containsKey(LlmOptionKeys.NUM_CTX)) {
            return OptionalInt.empty();
        }
        Object value = options.get(LlmOptionKeys.NUM_CTX);
        if (value instanceof Number number) {
            return OptionalInt.of(number.intValue());
        }
        if (value instanceof String stringValue) {
            try {
                return OptionalInt.of(Integer.parseInt(stringValue));
            } catch (NumberFormatException ignored) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    public static java.util.OptionalDouble readOutputBudgetRatio(Map<String, Object> options) {
        if (options == null || !options.containsKey(LlmOptionKeys.OUTPUT_BUDGET_RATIO)) {
            return java.util.OptionalDouble.empty();
        }
        Object value = options.get(LlmOptionKeys.OUTPUT_BUDGET_RATIO);
        if (value instanceof Number number) {
            return java.util.OptionalDouble.of(number.doubleValue());
        }
        if (value instanceof String stringValue) {
            try {
                return java.util.OptionalDouble.of(Double.parseDouble(stringValue));
            } catch (NumberFormatException ignored) {
                return java.util.OptionalDouble.empty();
            }
        }
        return java.util.OptionalDouble.empty();
    }

    public static Map<String, Object> withNumPredict(Map<String, Object> base, int value) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        merged.put(LlmOptionKeys.NUM_PREDICT, value);
        return merged;
    }

    public static Map<String, Object> withNumCtx(Map<String, Object> base, int value) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        merged.put(LlmOptionKeys.NUM_CTX, value);
        return merged;
    }

    public static Map<String, Object> withOutputBudgetRatio(Map<String, Object> base, double ratio) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        merged.put(LlmOptionKeys.OUTPUT_BUDGET_RATIO, ratio);
        return merged;
    }
}
