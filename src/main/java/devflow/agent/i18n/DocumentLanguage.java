package devflow.agent.i18n;

public enum DocumentLanguage {
    ZH,
    EN;

    public static DocumentLanguage detect(String... inputs) {
        return detectHumanLanguage(inputs);
    }

    public static DocumentLanguage detectHumanLanguage(String... inputs) {
        int cjkCount = 0;
        int latinCount = 0;
        if (inputs != null) {
            for (String input : inputs) {
                if (input == null || input.isBlank()) {
                    continue;
                }
                for (int index = 0; index < input.length(); index++) {
                    char current = input.charAt(index);
                    if (isCjk(current)) {
                        cjkCount++;
                    } else if (isLatin(current)) {
                        latinCount++;
                    }
                }
            }
        }
        if (cjkCount == 0 && latinCount == 0) {
            return null;
        }
        return cjkCount >= latinCount ? ZH : EN;
    }

    public String choose(String zh, String en) {
        return this == ZH ? zh : en;
    }

    public boolean isChinese() {
        return this == ZH;
    }

    public String proseInstruction() {
        return choose(
                "文档中面向人的正文、标题和说明必须使用中文；仅 `Contract Metadata` 标题和其中的键名保留英文。",
                "All human-readable headings, prose, and explanations must be written in English; keep the `Contract Metadata` heading and its keys in English."
        );
    }

    public static boolean containsHumanLanguage(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (isCjk(current) || isLatin(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCjk(char value) {
        return Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN;
    }

    private static boolean isLatin(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }
}
