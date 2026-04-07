package devflow.agent.validation;

import devflow.agent.executor.SelfCheckResult;
import devflow.agent.project.CommandResult;
import devflow.agent.project.FileProjectWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidationExecutor {

    private static final Pattern HTML_SCRIPT_SRC_PATTERN = Pattern.compile("<script[^>]*src\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_STYLESHEET_HREF_PATTERN = Pattern.compile("<link[^>]*href\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_SCRIPT_PATTERN = Pattern.compile("<script(?![^>]*src=)[^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final FileProjectWorkspace workspace;

    public ValidationExecutor(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    public SelfCheckResult execute(Path projectPath, ProjectFingerprint fingerprint, ValidationPlan plan) {
        List<String> detailLines = new ArrayList<>();
        for (ValidationStep step : plan.steps()) {
            ValidationStepResult result = runStep(projectPath, fingerprint, step);
            detailLines.add("""
                    [%s] %s
                    %s
                    """.formatted(result.status(), result.capability(), trim(result.details())));
            if (result.status() == ValidationStatus.FAILED && step.required()) {
                return new SelfCheckResult(false, result.summary(), String.join("\n", detailLines));
            }
        }
        return new SelfCheckResult(true, "项目自测通过。", plan.summary() + "\n\n" + String.join("\n", detailLines));
    }

    private ValidationStepResult runStep(Path projectPath, ProjectFingerprint fingerprint, ValidationStep step) {
        return switch (step.capability()) {
            case MAVENW_TEST -> runCommandStep(projectPath, ValidationCapability.MAVENW_TEST, List.of("./mvnw", "-q", "test"), step.reason(), true);
            case MAVEN_TEST -> runCommandStep(projectPath, ValidationCapability.MAVEN_TEST, List.of("mvn", "-q", "test"), step.reason(), true);
            case GRADLEW_TEST -> runCommandStep(projectPath, ValidationCapability.GRADLEW_TEST, List.of("./gradlew", "test"), step.reason(), true);
            case GRADLE_TEST -> runCommandStep(projectPath, ValidationCapability.GRADLE_TEST, List.of("gradle", "test"), step.reason(), true);
            case NPM_BUILD -> runCommandStep(projectPath, ValidationCapability.NPM_BUILD, List.of("npm", "run", "build"), step.reason(), false);
            case NPM_TEST -> runCommandStep(projectPath, ValidationCapability.NPM_TEST, List.of("npm", "run", "test"), step.reason(), false);
            case PNPM_BUILD -> runCommandStep(projectPath, ValidationCapability.PNPM_BUILD, List.of("pnpm", "run", "build"), step.reason(), false);
            case PNPM_TEST -> runCommandStep(projectPath, ValidationCapability.PNPM_TEST, List.of("pnpm", "run", "test"), step.reason(), false);
            case YARN_BUILD -> runCommandStep(projectPath, ValidationCapability.YARN_BUILD, List.of("yarn", "build"), step.reason(), false);
            case YARN_TEST -> runCommandStep(projectPath, ValidationCapability.YARN_TEST, List.of("yarn", "test"), step.reason(), false);
            case WEB_RESOURCE_LINK_CHECK -> runWebResourceLinkCheck(projectPath, step.reason());
            case WEB_JAVASCRIPT_SYNTAX_CHECK -> runJavaScriptSyntaxCheck(projectPath, step.reason());
            case WEB_PLAYWRIGHT_SMOKE -> runPlaywrightSmoke(projectPath, fingerprint, step.reason());
        };
    }

    private ValidationStepResult runCommandStep(
            Path projectPath,
            ValidationCapability capability,
            List<String> command,
            String reason,
            boolean failOnMissingScript
    ) {
        CommandResult result = workspace.runCommand(projectPath, command, Duration.ofMinutes(10));
        if (result.exitCode() == 0) {
            return new ValidationStepResult(capability, ValidationStatus.PASSED, "命令执行通过。", renderCommandDetails(command, reason, result));
        }
        if (!failOnMissingScript && isMissingPackageScript(command, result)) {
            return new ValidationStepResult(capability, ValidationStatus.SKIPPED, "项目未定义该脚本，跳过。", renderCommandDetails(command, reason, result));
        }
        return new ValidationStepResult(capability, ValidationStatus.FAILED, "命令执行失败，exitCode=" + result.exitCode(), renderCommandDetails(command, reason, result));
    }

    private ValidationStepResult runWebResourceLinkCheck(Path projectPath, String reason) {
        List<String> missing = new ArrayList<>();
        for (Path relativePath : workspace.listProjectFiles(projectPath)) {
            String fileName = relativePath.getFileName().toString();
            if (!fileName.endsWith(".html")) {
                continue;
            }
            String content = workspace.readFile(projectPath, relativePath);
            collectMissingLocalAssets(projectPath, relativePath, content, missing);
        }
        if (!missing.isEmpty()) {
            return new ValidationStepResult(
                    ValidationCapability.WEB_RESOURCE_LINK_CHECK,
                    ValidationStatus.FAILED,
                    "页面引用的本地资源不存在。",
                    reason + "\nMissing local assets: " + String.join(", ", missing)
            );
        }
        return new ValidationStepResult(
                ValidationCapability.WEB_RESOURCE_LINK_CHECK,
                ValidationStatus.PASSED,
                "网页资源引用检查通过。",
                reason + "\nAll local assets referenced by HTML exist."
        );
    }

    private ValidationStepResult runJavaScriptSyntaxCheck(Path projectPath, String reason) {
        List<String> checked = new ArrayList<>();
        for (Path relativePath : workspace.listProjectFiles(projectPath)) {
            String fileName = relativePath.getFileName().toString();
            if (isJavaScriptFile(fileName)) {
                CommandResult result = workspace.runCommand(projectPath, List.of("node", "--check", relativePath.toString()), Duration.ofMinutes(1));
                if (result.exitCode() != 0) {
                    return new ValidationStepResult(
                            ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                            ValidationStatus.FAILED,
                            "JavaScript 语法检查失败。",
                            reason + "\nJS syntax check failed for " + relativePath + "\n" + trim(result.stderr())
                    );
                }
                checked.add("js syntax ok: " + relativePath);
                continue;
            }
            if (fileName.endsWith(".html")) {
                ValidationStepResult inlineResult = checkInlineScripts(projectPath, relativePath, workspace.readFile(projectPath, relativePath), reason);
                if (inlineResult.status() == ValidationStatus.FAILED) {
                    return inlineResult;
                }
                if (!inlineResult.details().isBlank()) {
                    checked.add(inlineResult.details());
                }
            }
        }
        return new ValidationStepResult(
                ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                ValidationStatus.PASSED,
                "JavaScript 语法检查通过。",
                reason + "\n" + (checked.isEmpty() ? "No JavaScript files detected." : String.join("\n", checked))
        );
    }

    private ValidationStepResult runPlaywrightSmoke(Path projectPath, ProjectFingerprint fingerprint, String reason) {
        String entry = fingerprint == null ? "" : fingerprint.resolvedHtmlEntryPath();
        if (entry.isBlank()) {
            return new ValidationStepResult(
                    ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                    ValidationStatus.SKIPPED,
                    "未探测到 HTML 入口，跳过浏览器 smoke test。",
                    reason
            );
        }
        Path entryPath = projectPath.resolve(entry);
        if (!Files.exists(entryPath)) {
            return new ValidationStepResult(
                    ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                    ValidationStatus.SKIPPED,
                    "探测到的 HTML 入口不存在，跳过浏览器 smoke test。",
                    reason + "\nentry=" + entry
            );
        }
        Path scriptPath = Path.of("tools", "playwright-smoke", "run-smoke.mjs").toAbsolutePath();
        CommandResult result = workspace.runCommand(
                projectPath,
                List.of("node", scriptPath.toString(), projectPath.toString(), entry),
                Duration.ofMinutes(2)
        );
        if (result.exitCode() != 0) {
            return new ValidationStepResult(
                    ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                    ValidationStatus.FAILED,
                    "浏览器级 smoke test 失败。",
                    reason + "\n" + trim(result.stdout()) + "\n" + trim(result.stderr())
            );
        }
        return new ValidationStepResult(
                ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                ValidationStatus.PASSED,
                "浏览器级 smoke test 通过。",
                reason + "\n" + trim(result.stdout())
        );
    }

    private ValidationStepResult checkInlineScripts(Path projectPath, Path htmlFile, String content, String reason) {
        Matcher matcher = INLINE_SCRIPT_PATTERN.matcher(content);
        List<String> checked = new ArrayList<>();
        int index = 0;
        while (matcher.find()) {
            String scriptBody = matcher.group(1).trim();
            if (scriptBody.isBlank()) {
                continue;
            }
            index++;
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("devflow-inline-script-", ".js");
                Files.writeString(tempFile, scriptBody);
                CommandResult result = workspace.runCommand(projectPath, List.of("node", "--check", tempFile.toString()), Duration.ofMinutes(1));
                if (result.exitCode() != 0) {
                    return new ValidationStepResult(
                            ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                            ValidationStatus.FAILED,
                            "内联脚本语法检查失败。",
                            reason + "\nInline script syntax check failed for " + htmlFile + "#" + index + "\n" + trim(result.stderr())
                    );
                }
                checked.add("inline script ok: " + htmlFile + "#" + index);
            } catch (IOException exception) {
                return new ValidationStepResult(
                        ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                        ValidationStatus.FAILED,
                        "无法检查内联脚本。",
                        reason + "\n" + exception.getMessage()
                );
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return new ValidationStepResult(
                ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                ValidationStatus.PASSED,
                "内联脚本语法检查通过。",
                String.join("\n", checked)
        );
    }

    private void collectMissingLocalAssets(Path projectPath, Path htmlFile, String content, List<String> missingResources) {
        Matcher scriptMatcher = HTML_SCRIPT_SRC_PATTERN.matcher(content);
        while (scriptMatcher.find()) {
            collectMissingLocalAsset(projectPath, htmlFile, scriptMatcher.group(1), missingResources);
        }
        Matcher styleMatcher = HTML_STYLESHEET_HREF_PATTERN.matcher(content);
        while (styleMatcher.find()) {
            collectMissingLocalAsset(projectPath, htmlFile, styleMatcher.group(1), missingResources);
        }
    }

    private void collectMissingLocalAsset(Path projectPath, Path htmlFile, String rawRef, List<String> missingResources) {
        if (rawRef == null || rawRef.isBlank() || isExternalReference(rawRef)) {
            return;
        }
        Path baseDir = htmlFile.getParent() == null ? Path.of("") : htmlFile.getParent();
        Path resolved = projectPath.resolve(baseDir).resolve(rawRef).normalize();
        if (!resolved.startsWith(projectPath.normalize()) || !Files.exists(resolved)) {
            missingResources.add(rawRef);
        }
    }

    private boolean isJavaScriptFile(String fileName) {
        return fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs");
    }

    private boolean isExternalReference(String rawRef) {
        return rawRef.startsWith("http://")
                || rawRef.startsWith("https://")
                || rawRef.startsWith("//")
                || rawRef.startsWith("data:")
                || rawRef.startsWith("mailto:")
                || rawRef.startsWith("#");
    }

    private boolean isMissingPackageScript(List<String> command, CommandResult result) {
        if (result.exitCode() == 0 || command.size() < 2) {
            return false;
        }
        String joined = String.join(" ", command);
        String combined = (result.stdout() + "\n" + result.stderr()).toLowerCase();
        return (joined.startsWith("npm run") || joined.startsWith("pnpm run") || joined.startsWith("yarn "))
                && (combined.contains("missing script") || combined.contains("command \"build\" not found") || combined.contains("command \"test\" not found"));
    }

    private String renderCommandDetails(List<String> command, String reason, CommandResult result) {
        return """
                reason: %s
                command: `%s`
                exitCode: %d

                stdout:
                %s

                stderr:
                %s
                """.formatted(reason, String.join(" ", command), result.exitCode(), trim(result.stdout()), trim(result.stderr()));
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return value.length() > 12000 ? value.substring(0, 12000) + "\n...<truncated>" : value;
    }
}
