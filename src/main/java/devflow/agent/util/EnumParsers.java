package devflow.agent.util;

import java.util.Locale;

/**
 * 集中处理 wire value 到枚举的稳定解析。
 *
 * <p>这样可以避免各模块反复散落 `trim().toUpperCase()` 这类协议转换细节，
 * 也减少因为大小写或空白处理不一致而出现的隐蔽分叉。
 */
public final class EnumParsers {

    private EnumParsers() {
    }

    public static <E extends Enum<E>> E parseIgnoreCase(Class<E> enumType, String value, E fallback) {
        if (enumType == null || value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
