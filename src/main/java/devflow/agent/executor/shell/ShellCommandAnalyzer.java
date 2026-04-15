package devflow.agent.executor.shell;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ImplementationToolPermissionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ShellCommandAnalyzer {

    private static final Set<String> NON_PATH_READ_ONLY_COMMANDS = Set.of(
            "dirname",
            "echo",
            "env",
            "false",
            "printf",
            "pwd",
            "sleep",
            "true"
    );
    private static final Set<String> PATH_READ_ONLY_COMMANDS = Set.of(
            "cat",
            "find",
            "ls",
            "readlink",
            "realpath",
            "stat"
    );
    private static final Set<String> LITERAL_STDOUT_PRODUCERS = Set.of(
            "echo",
            "printf"
    );
    private static final Set<String> GIT_READ_ONLY_SUBCOMMANDS = Set.of(
            "branch",
            "diff",
            "grep",
            "log",
            "ls-files",
            "rev-parse",
            "show",
            "status"
    );
    private static final Set<String> CWD_MUTATOR_COMMANDS = Set.of(
            "cd",
            "pushd",
            "popd"
    );
    private static final Set<String> SHELL_STATE_COMMANDS = Set.of(
            ".",
            "alias",
            "export",
            "source",
            "unset"
    );
    private static final Set<String> WRITE_DISALLOWED_CHAIN_OPERATORS = Set.of(
            "|",
            "||",
            "&"
    );
    public ShellCommandDecision analyze(String command, ImplementationToolPermissionContext context) {
        String normalizedCommand = command == null ? "" : command.trim();
        String summary = summarizeCommand(normalizedCommand);
        if (normalizedCommand.isBlank()) {
            return ShellCommandDecision.deny(
                    summary,
                    "INVALID_COMMAND",
                    "Shell command is empty.",
                    false,
                    List.of("command is blank"),
                    List.of()
            );
        }
        if (context == null) {
            return ShellCommandDecision.deny(
                    summary,
                    "MISSING_PERMISSION_CONTEXT",
                    "Missing shell permission context.",
                    false,
                    List.of("permission context is null"),
                    List.of()
            );
        }
        try {
            List<ShellToken> tokens = tokenize(normalizedCommand);
            List<ShellSegment> segments = splitCommands(tokens);
            if (segments.isEmpty()) {
                throw new ShellAnalysisException("INVALID_COMMAND", "Shell command does not contain an executable segment.", false, "no command segments");
            }
            ShellRuntimeState runtimeState = new ShellRuntimeState(Path.of(""), new ArrayDeque<>());
            ArrayList<ShellPathIntent> pathIntents = new ArrayList<>();
            ArrayList<String> evidence = new ArrayList<>();
            int writeSegments = 0;
            for (ShellSegment segment : segments) {
                SegmentAnalysis analysis = analyzeCommand(segment.command(), context, runtimeState);
                if (!analysis.allowed()) {
                    return ShellCommandDecision.deny(
                            summary,
                            analysis.reasonCode(),
                            analysis.message(),
                            analysis.retryable(),
                            analysis.evidence().isBlank() ? List.of() : List.of(analysis.evidence()),
                            analysis.pathIntents()
                    );
                }
                if (analysis.writesWorkspace()) {
                    writeSegments++;
                }
                pathIntents.addAll(analysis.pathIntents());
                if (!analysis.evidence().isBlank()) {
                    evidence.add(analysis.evidence());
                }
            }
            if (writeSegments > 1) {
                return ShellCommandDecision.deny(
                        summary,
                        "UNSAFE_WRITE_SYNTAX",
                        "Bash write commands may contain at most one write-capable command segment. Use file tools for multi-step edits.",
                        true,
                        List.of("write command segments=" + writeSegments),
                        List.copyOf(pathIntents)
                );
            }
            if (writeSegments == 1 && segments.stream()
                    .map(ShellSegment::separatorAfter)
                    .filter(separator -> separator != null && !separator.isBlank())
                    .anyMatch(WRITE_DISALLOWED_CHAIN_OPERATORS::contains)) {
                return ShellCommandDecision.deny(
                        summary,
                        "UNSAFE_WRITE_SYNTAX",
                        "Write-capable Bash commands cannot use pipelines, background execution, or fallback chaining. Use file tools instead.",
                        true,
                        segments.stream()
                                .map(ShellSegment::separatorAfter)
                                .filter(separator -> separator != null && !separator.isBlank())
                                .toList(),
                        List.copyOf(pathIntents)
                );
            }
            if (writeSegments == 0 && !context.allowReadOnlyShell()) {
                return ShellCommandDecision.deny(
                        summary,
                        "REPAIR_MODE_READ_ONLY_SHELL_DENIED",
                        "Read-only Bash exploration is not allowed during repair/resume. Use Read/Grep/Glob instead.",
                        true,
                        List.of("repairMode=true"),
                        List.copyOf(pathIntents)
                );
            }
            return writeSegments == 0
                    ? ShellCommandDecision.allowReadOnly(summary, List.copyOf(evidence), List.copyOf(pathIntents))
                    : ShellCommandDecision.allowWrite(summary, List.copyOf(evidence), List.copyOf(pathIntents));
        } catch (ShellAnalysisException exception) {
            return ShellCommandDecision.deny(
                    summary,
                    exception.reasonCode(),
                    exception.getMessage(),
                    exception.retryable(),
                    exception.evidence().isBlank() ? List.of() : List.of(exception.evidence()),
                    exception.pathIntents()
            );
        }
    }

    private SegmentAnalysis analyzeCommand(
            SimpleShellCommand shellCommand,
            ImplementationToolPermissionContext context,
            ShellRuntimeState runtimeState
    ) throws ShellAnalysisException {
        if (shellCommand.words().isEmpty()) {
            throw new ShellAnalysisException("INVALID_COMMAND", "Shell command contains an empty segment.", false, "empty command segment");
        }
        NormalizedShellCommand normalizedCommand = normalizeCommand(shellCommand);
        if (normalizedCommand.words().isEmpty()) {
            throw new ShellAnalysisException("INVALID_COMMAND", "Shell command does not contain an executable segment.", false, "empty normalized command");
        }
        String commandName = normalizedCommand.words().getFirst();
        if (SHELL_STATE_COMMANDS.contains(commandName)) {
            throw new ShellAnalysisException(
                    "UNSUPPORTED_SHELL_STATE_COMMAND",
                    "Shell session state commands are not supported in Bash tool commands. Use explicit arguments instead.",
                    true,
                    commandName
            );
        }
        if (normalizedCommand.hasInputRedirection()) {
            throw new ShellAnalysisException(
                    "UNSUPPORTED_SHELL_SYNTAX",
                    "Shell input redirection is not supported in Bash tool commands.",
                    true,
                    commandName + " <"
            );
        }
        if (CWD_MUTATOR_COMMANDS.contains(commandName)) {
            return analyzeCwdMutation(normalizedCommand.words(), context, runtimeState);
        }
        if (!normalizedCommand.outputRedirections().isEmpty()) {
            return analyzeOutputWrite(normalizedCommand, context, runtimeState.currentDirectory());
        }
        if ("touch".equals(commandName)) {
            return analyzeTouch(normalizedCommand.words(), context, runtimeState.currentDirectory());
        }
        if ("mkdir".equals(commandName)) {
            return analyzeMkdir(normalizedCommand.words(), context, runtimeState.currentDirectory());
        }
        if ("rm".equals(commandName)) {
            return analyzeRm(normalizedCommand.words(), context, runtimeState.currentDirectory());
        }
        if ("cp".equals(commandName)) {
            return analyzeCp(normalizedCommand.words(), context, runtimeState.currentDirectory());
        }
        if ("mv".equals(commandName)) {
            return analyzeMv(normalizedCommand.words(), context, runtimeState.currentDirectory());
        }
        if ("git".equals(commandName)) {
            return analyzeGit(normalizedCommand.words());
        }
        if ("find".equals(commandName)) {
            return analyzeFind(normalizedCommand.words(), context, runtimeState.currentDirectory());
        }
        if (PATH_READ_ONLY_COMMANDS.contains(commandName)) {
            return analyzeReadOnlyPathCommand(normalizedCommand.words(), context, runtimeState.currentDirectory(), commandName);
        }
        if (NON_PATH_READ_ONLY_COMMANDS.contains(commandName)) {
            return SegmentAnalysis.allowReadOnly("read-only command: " + summarizeWords(normalizedCommand.words()));
        }
        throw new ShellAnalysisException(
                "UNSUPPORTED_SHELL_COMMAND",
                "Bash command is not in the allowed deterministic subset. Use Read/Grep/Glob or file tools instead.",
                true,
                "unsupported command: " + summarizeWords(normalizedCommand.words())
        );
    }

    private SegmentAnalysis analyzeCwdMutation(
            List<String> words,
            ImplementationToolPermissionContext context,
            ShellRuntimeState runtimeState
    ) throws ShellAnalysisException {
        String commandName = words.getFirst();
        if ("popd".equals(commandName)) {
            if (words.size() > 1) {
                throw new ShellAnalysisException(
                        "UNSAFE_WRITE_SYNTAX",
                        "popd does not accept path arguments in Bash tool commands.",
                        true,
                        summarizeWords(words)
                );
            }
            if (runtimeState.directoryStack().isEmpty()) {
                throw new ShellAnalysisException(
                        "UNRESOLVABLE_CWD",
                        "popd requires a prior pushd in the same command chain.",
                        true,
                        "popd"
                );
            }
            Path restored = runtimeState.directoryStack().removeLast();
            runtimeState.currentDirectory(restored);
            return SegmentAnalysis.allowReadOnly("cwd <- " + renderRelativePath(restored));
        }
        List<String> arguments = nonOptionArguments(words, 1);
        if (arguments.isEmpty()) {
            throw new ShellAnalysisException(
                    "UNRESOLVABLE_CWD",
                    commandName + " requires an explicit project-relative directory.",
                    true,
                    commandName
            );
        }
        if (arguments.size() != 1) {
            throw new ShellAnalysisException(
                    "UNSAFE_WRITE_SYNTAX",
                    commandName + " is only allowed with a single directory argument.",
                    true,
                    summarizeWords(words)
            );
        }
        String rawTarget = arguments.getFirst();
        if ("-".equals(rawTarget) || "~".equals(rawTarget)) {
            throw new ShellAnalysisException(
                    "UNRESOLVABLE_CWD",
                    commandName + " target cannot rely on shell directory history or HOME expansion.",
                    true,
                    rawTarget
            );
        }
        Path relativeTarget = resolveProjectRelativePath(rawTarget, context, runtimeState.currentDirectory());
        Path absoluteTarget = context.projectPath().resolve(relativeTarget).normalize();
        if (!Files.isDirectory(absoluteTarget)) {
            throw new ShellAnalysisException(
                    "INVALID_COMMAND",
                    commandName + " target must be an existing directory inside the current project root.",
                    true,
                    renderRelativePath(relativeTarget)
            );
        }
        if ("pushd".equals(commandName)) {
            runtimeState.directoryStack().addLast(runtimeState.currentDirectory());
        }
        runtimeState.currentDirectory(relativeTarget);
        return SegmentAnalysis.allowReadOnly("cwd -> " + renderRelativePath(relativeTarget));
    }

    private SegmentAnalysis analyzeOutputWrite(
            NormalizedShellCommand command,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        String commandName = command.words().getFirst();
        if (!isLiteralStdoutProducer(command.words())) {
            throw new ShellAnalysisException(
                    "UNSAFE_WRITE_SYNTAX",
                    "Bash output redirection is only allowed for literal stdout producers such as echo/printf.",
                    true,
                    "command with output redirection: " + commandName
            );
        }
        ArrayList<ShellPathIntent> intents = new ArrayList<>();
        for (String target : command.outputRedirections()) {
            intents.add(resolveOwnedFileIntent(target, ShellPathIntentKind.WRITE_FILE, context, currentDirectory, commandName));
        }
        return SegmentAnalysis.allowWrite(intents, "stdout redirect -> " + renderIntentPaths(intents));
    }

    private SegmentAnalysis analyzeTouch(
            List<String> words,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        List<String> targets = nonOptionArguments(words, 1);
        if (targets.isEmpty()) {
            throw new ShellAnalysisException("INVALID_COMMAND", "touch requires at least one target path.", true, "touch with no targets");
        }
        ArrayList<ShellPathIntent> intents = new ArrayList<>();
        for (String target : targets) {
            intents.add(resolveOwnedFileIntent(target, ShellPathIntentKind.WRITE_FILE, context, currentDirectory, "touch"));
        }
        return SegmentAnalysis.allowWrite(intents, "touch -> " + renderIntentPaths(intents));
    }

    private SegmentAnalysis analyzeMkdir(
            List<String> words,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        List<String> targets = nonOptionArguments(words, 1);
        if (targets.isEmpty()) {
            throw new ShellAnalysisException("INVALID_COMMAND", "mkdir requires at least one target path.", true, "mkdir with no targets");
        }
        ArrayList<ShellPathIntent> intents = new ArrayList<>();
        for (String target : targets) {
            intents.add(resolveOwnedDirectoryIntent(target, context, currentDirectory));
        }
        return SegmentAnalysis.allowWrite(intents, "mkdir -> " + renderIntentPaths(intents));
    }

    private SegmentAnalysis analyzeRm(
            List<String> words,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        List<String> targets = nonOptionArguments(words, 1);
        if (targets.isEmpty()) {
            throw new ShellAnalysisException("INVALID_COMMAND", "rm requires at least one target path.", true, "rm with no targets");
        }
        ArrayList<ShellPathIntent> intents = new ArrayList<>();
        for (String target : targets) {
            Path relativePath = resolveProjectRelativePath(target, context, currentDirectory);
            Path absolutePath = context.projectPath().resolve(relativePath).normalize();
            if (Files.isDirectory(absolutePath)) {
                throw new ShellAnalysisException(
                        "UNSAFE_WRITE_SYNTAX",
                        "rm is only allowed for declared file paths, not directories.",
                        true,
                        "directory delete target: " + renderRelativePath(relativePath)
                );
            }
            intents.add(requireOwnedFileIntent(relativePath, ShellPathIntentKind.DELETE_FILE, context, "rm"));
        }
        return SegmentAnalysis.allowWrite(List.copyOf(intents), "rm -> " + renderIntentPaths(intents));
    }

    private SegmentAnalysis analyzeCp(
            List<String> words,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        List<String> arguments = nonOptionArguments(words, 1);
        if (arguments.size() != 2) {
            throw new ShellAnalysisException(
                    "UNSAFE_WRITE_SYNTAX",
                    "cp is only allowed with exactly one source and one target path.",
                    true,
                    "cp args=" + arguments
            );
        }
        Path source = resolveProjectRelativePath(arguments.getFirst(), context, currentDirectory);
        Path target = resolveProjectRelativePath(arguments.get(1), context, currentDirectory);
        Path absoluteSource = context.projectPath().resolve(source).normalize();
        if (!Files.isRegularFile(absoluteSource)) {
            throw new ShellAnalysisException(
                    "INVALID_COMMAND",
                    "cp source must be an existing regular file inside the current project root.",
                    true,
                    renderRelativePath(source)
            );
        }
        if (looksLikeDirectoryTarget(arguments.get(1), context.projectPath(), target)) {
            throw new ShellAnalysisException(
                    "UNSAFE_WRITE_SYNTAX",
                    "cp target must resolve to a single declared file path, not a directory.",
                    true,
                    renderRelativePath(target)
            );
        }
        ShellPathIntent sourceIntent = new ShellPathIntent(source, ShellPathIntentKind.READ_FILE);
        ShellPathIntent targetIntent = requireOwnedFileIntent(target, ShellPathIntentKind.WRITE_FILE, context, "cp");
        return SegmentAnalysis.allowWrite(
                List.of(sourceIntent, targetIntent),
                "cp -> source=" + renderRelativePath(source) + ", target=" + renderIntentPaths(List.of(targetIntent))
        );
    }

    private SegmentAnalysis analyzeMv(
            List<String> words,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        List<String> arguments = nonOptionArguments(words, 1);
        if (arguments.size() != 2) {
            throw new ShellAnalysisException(
                    "UNSAFE_WRITE_SYNTAX",
                    "mv is only allowed with exactly one source and one target path.",
                    true,
                    "mv args=" + arguments
            );
        }
        Path source = resolveProjectRelativePath(arguments.getFirst(), context, currentDirectory);
        Path target = resolveProjectRelativePath(arguments.get(1), context, currentDirectory);
        if (looksLikeDirectoryTarget(arguments.get(1), context.projectPath(), target)) {
            throw new ShellAnalysisException(
                    "UNSAFE_WRITE_SYNTAX",
                    "mv target must resolve to a single declared file path, not a directory.",
                    true,
                    renderRelativePath(target)
            );
        }
        ShellPathIntent deleteIntent = requireOwnedFileIntent(source, ShellPathIntentKind.DELETE_FILE, context, "mv");
        ShellPathIntent writeIntent = requireOwnedFileIntent(target, ShellPathIntentKind.WRITE_FILE, context, "mv");
        return SegmentAnalysis.allowWrite(List.of(deleteIntent, writeIntent), "mv -> " + renderIntentPaths(List.of(deleteIntent, writeIntent)));
    }

    private SegmentAnalysis analyzeGit(List<String> words) throws ShellAnalysisException {
        if (words.size() < 2) {
            throw new ShellAnalysisException(
                    "UNSUPPORTED_SHELL_COMMAND",
                    "git requires a read-only subcommand in Bash tool commands.",
                    true,
                    summarizeWords(words)
            );
        }
        for (String token : words) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if ("-C".equals(token)
                    || token.startsWith("-C=")
                    || token.startsWith("--git-dir")
                    || token.startsWith("--work-tree")) {
                throw new ShellAnalysisException(
                        "UNSUPPORTED_SHELL_COMMAND",
                        "git commands cannot override repository root or working tree in Bash tool commands.",
                        true,
                        token
                );
            }
        }
        String subcommand = words.get(1);
        if (subcommand.startsWith("-")) {
            throw new ShellAnalysisException(
                    "UNSUPPORTED_SHELL_COMMAND",
                    "git global flags are not supported in Bash tool commands.",
                    true,
                    summarizeWords(words)
            );
        }
        if (!GIT_READ_ONLY_SUBCOMMANDS.contains(subcommand)) {
            throw new ShellAnalysisException(
                    "UNSUPPORTED_SHELL_COMMAND",
                    "git command is not in the allowed read-only subset.",
                    true,
                    summarizeWords(words)
            );
        }
        return SegmentAnalysis.allowReadOnly("git read-only command: " + summarizeWords(words));
    }

    private SegmentAnalysis analyzeFind(
            List<String> words,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        for (int index = 1; index < words.size(); index++) {
            String token = words.get(index);
            if ("-delete".equals(token) || "-exec".equals(token) || "-execdir".equals(token)
                    || "-ok".equals(token) || "-okdir".equals(token)
                    || "-fprint".equals(token) || "-fprintf".equals(token) || "-fls".equals(token)) {
                throw new ShellAnalysisException(
                        "UNSAFE_WRITE_SYNTAX",
                        "find write-capable actions are not supported in Bash tool commands.",
                        true,
                        token
                );
            }
        }
        ArrayList<String> roots = new ArrayList<>();
        for (int index = 1; index < words.size(); index++) {
            String token = words.get(index);
            if (token.startsWith("-")) {
                break;
            }
            roots.add(token);
        }
        if (roots.isEmpty()) {
            roots.add(".");
        }
        ArrayList<String> validatedRoots = new ArrayList<>();
        ArrayList<ShellPathIntent> pathIntents = new ArrayList<>();
        for (String root : roots) {
            Path resolved = resolveProjectRelativePath(root, context, currentDirectory);
            validatedRoots.add(renderRelativePath(resolved));
            pathIntents.add(new ShellPathIntent(resolved, ShellPathIntentKind.READ_FILE));
        }
        return SegmentAnalysis.allowReadOnly("find -> " + validatedRoots, pathIntents);
    }

    private SegmentAnalysis analyzeReadOnlyPathCommand(
            List<String> words,
            ImplementationToolPermissionContext context,
            Path currentDirectory,
            String commandName
    ) throws ShellAnalysisException {
        List<String> arguments = nonOptionArguments(words, 1);
        if (arguments.isEmpty()) {
            if ("cat".equals(commandName) || "readlink".equals(commandName) || "realpath".equals(commandName) || "stat".equals(commandName)) {
                throw new ShellAnalysisException(
                        "INVALID_COMMAND",
                        commandName + " requires at least one path argument in Bash tool commands.",
                        true,
                        commandName
                );
            }
            if ("ls".equals(commandName)) {
                Path resolved = resolveProjectRelativePath(".", context, currentDirectory);
                return SegmentAnalysis.allowReadOnly(
                        "read-only command: " + summarizeWords(words),
                        List.of(new ShellPathIntent(resolved, ShellPathIntentKind.READ_FILE))
                );
            }
            return SegmentAnalysis.allowReadOnly("read-only command: " + summarizeWords(words));
        }
        ArrayList<String> validatedPaths = new ArrayList<>();
        ArrayList<ShellPathIntent> pathIntents = new ArrayList<>();
        for (String argument : arguments) {
            Path resolved = resolveProjectRelativePath(argument, context, currentDirectory);
            validatedPaths.add(renderRelativePath(resolved));
            pathIntents.add(new ShellPathIntent(resolved, ShellPathIntentKind.READ_FILE));
        }
        return SegmentAnalysis.allowReadOnly(commandName + " -> " + validatedPaths, pathIntents);
    }

    private NormalizedShellCommand normalizeCommand(SimpleShellCommand shellCommand) throws ShellAnalysisException {
        List<String> words = shellCommand.words();
        int index = 0;
        while (index < words.size()) {
            String token = words.get(index);
            if (token == null || token.isBlank()) {
                index++;
                continue;
            }
            if (looksLikeAssignment(token)) {
                throw new ShellAnalysisException(
                        "UNSUPPORTED_SHELL_SYNTAX",
                        "Shell environment assignments are not supported in Bash tool commands.",
                        true,
                        "assignment prefix: " + token
                );
            }
            if ("env".equals(token)) {
                index++;
                while (index < words.size() && looksLikeAssignment(words.get(index))) {
                    index++;
                }
                if (index >= words.size()) {
                    return new NormalizedShellCommand(List.of("env"), shellCommand.outputRedirections(), shellCommand.hasInputRedirection());
                }
                continue;
            }
            if ("nice".equals(token) || "nohup".equals(token) || "time".equals(token)) {
                index++;
                continue;
            }
            if ("timeout".equals(token)) {
                index++;
                while (index < words.size() && words.get(index).startsWith("-")) {
                    index++;
                }
                if (index >= words.size()) {
                    throw new ShellAnalysisException(
                            "UNSUPPORTED_SHELL_SYNTAX",
                            "timeout wrapper requires a duration and a nested command.",
                            true,
                            summarizeWords(words)
                    );
                }
                index++;
                if (index >= words.size()) {
                    throw new ShellAnalysisException(
                            "UNSUPPORTED_SHELL_SYNTAX",
                            "timeout wrapper requires a nested command.",
                            true,
                            summarizeWords(words)
                    );
                }
                continue;
            }
            break;
        }
        if (index >= words.size()) {
            return new NormalizedShellCommand(List.of(), shellCommand.outputRedirections(), shellCommand.hasInputRedirection());
        }
        return new NormalizedShellCommand(
                List.copyOf(words.subList(index, words.size())),
                shellCommand.outputRedirections(),
                shellCommand.hasInputRedirection()
        );
    }

    private boolean isLiteralStdoutProducer(List<String> words) {
        return words != null && !words.isEmpty() && LITERAL_STDOUT_PRODUCERS.contains(words.getFirst());
    }

    private ShellPathIntent resolveOwnedFileIntent(
            String rawPath,
            ShellPathIntentKind kind,
            ImplementationToolPermissionContext context,
            Path currentDirectory,
            String commandName
    ) throws ShellAnalysisException {
        return requireOwnedFileIntent(
                resolveProjectRelativePath(rawPath, context, currentDirectory),
                kind,
                context,
                commandName
        );
    }

    private ShellPathIntent requireOwnedFileIntent(
            Path relativePath,
            ShellPathIntentKind kind,
            ImplementationToolPermissionContext context,
            String commandName
    ) throws ShellAnalysisException {
        if (!context.ownedPaths().contains(relativePath)) {
            throw new ShellAnalysisException(
                    "SCOPE_VIOLATION",
                    "Bash write targets must stay inside the current accepted change-set.",
                    true,
                    commandName + " -> " + renderRelativePath(relativePath),
                    List.of(new ShellPathIntent(relativePath, kind))
            );
        }
        return new ShellPathIntent(relativePath, kind);
    }

    private ShellPathIntent resolveOwnedDirectoryIntent(
            String rawPath,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        Path relativePath = resolveProjectRelativePath(rawPath, context, currentDirectory);
        if (!isOwnedDirectory(relativePath, context.ownedPaths())) {
            throw new ShellAnalysisException(
                    "SCOPE_VIOLATION",
                    "mkdir is only allowed for directories that contain current owned paths.",
                    true,
                    renderRelativePath(relativePath),
                    List.of(new ShellPathIntent(relativePath, ShellPathIntentKind.PREPARE_DIRECTORY))
            );
        }
        return new ShellPathIntent(relativePath, ShellPathIntentKind.PREPARE_DIRECTORY);
    }

    private boolean isOwnedDirectory(Path directoryPath, Set<Path> ownedPaths) {
        if (directoryPath == null || ownedPaths == null || ownedPaths.isEmpty()) {
            return false;
        }
        Path normalized = directoryPath.normalize();
        for (Path ownedPath : ownedPaths) {
            if (ownedPath == null) {
                continue;
            }
            Path parent = ownedPath.getParent() == null ? Path.of("") : ownedPath.getParent().normalize();
            if (parent.equals(normalized) || parent.startsWith(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeDirectoryTarget(String rawTarget, Path projectPath, Path resolvedTarget) {
        if (rawTarget != null && rawTarget.endsWith("/")) {
            return true;
        }
        return Files.isDirectory(projectPath.resolve(resolvedTarget).normalize());
    }

    private Path resolveProjectRelativePath(
            String rawPath,
            ImplementationToolPermissionContext context,
            Path currentDirectory
    ) throws ShellAnalysisException {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ShellAnalysisException("INVALID_COMMAND", "Shell path target is blank.", true, "blank path token");
        }
        String token = rawPath.trim();
        if (token.contains("$") || token.contains("~") || token.contains("*") || token.contains("?")
                || token.contains("[") || token.contains("]") || token.contains("{") || token.contains("}")) {
            throw new ShellAnalysisException(
                    "UNRESOLVABLE_WRITE_TARGET",
                    "Shell paths cannot use expansions, globs, or brace patterns.",
                    true,
                    token
            );
        }
        try {
            Path candidate = Path.of(token);
            Path absoluteBase = context.projectPath().resolve(currentDirectory == null ? Path.of("") : currentDirectory).normalize();
            Path absolutePath = candidate.isAbsolute()
                    ? candidate.normalize()
                    : absoluteBase.resolve(candidate).normalize();
            if (!absolutePath.startsWith(context.projectPath())) {
                throw new ShellAnalysisException(
                        "SCOPE_VIOLATION",
                        "Shell paths must stay inside the current project root.",
                        true,
                        token
                );
            }
            return context.projectPath().relativize(absolutePath).normalize();
        } catch (IllegalArgumentException exception) {
            throw new ShellAnalysisException("INVALID_COMMAND", "Shell path is invalid: " + token, true, token);
        }
    }

    private List<String> nonOptionArguments(List<String> words, int startIndex) {
        ArrayList<String> arguments = new ArrayList<>();
        boolean optionsEnded = false;
        for (int index = Math.max(0, startIndex); index < words.size(); index++) {
            String token = words.get(index);
            if ("--".equals(token)) {
                optionsEnded = true;
                continue;
            }
            if (!optionsEnded && token.startsWith("-") && !"-".equals(token)) {
                continue;
            }
            arguments.add(token);
        }
        return List.copyOf(arguments);
    }

    private List<ShellToken> tokenize(String command) throws ShellAnalysisException {
        ArrayList<ShellToken> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;
        while (index < command.length()) {
            char value = command.charAt(index);
            if (Character.isWhitespace(value)) {
                flushWord(tokens, current);
                index++;
                continue;
            }
            if (value == '`') {
                throw new ShellAnalysisException("UNSUPPORTED_SHELL_SYNTAX", "Backtick command substitution is not supported.", true, "`");
            }
            if (value == '$') {
                throw new ShellAnalysisException("UNSUPPORTED_SHELL_SYNTAX", "Shell variable or command expansion is not supported.", true, "$");
            }
            if (value == '\'') {
                index = consumeSingleQuoted(command, index + 1, current);
                continue;
            }
            if (value == '"') {
                index = consumeDoubleQuoted(command, index + 1, current);
                continue;
            }
            if (value == '\\') {
                if (index + 1 >= command.length()) {
                    throw new ShellAnalysisException("INVALID_COMMAND", "Dangling shell escape at end of command.", true, "\\");
                }
                current.append(command.charAt(index + 1));
                index += 2;
                continue;
            }
            if (value == '(' || value == ')' || value == '{' || value == '}') {
                throw new ShellAnalysisException(
                        "UNSUPPORTED_SHELL_SYNTAX",
                        "Shell grouping and subshell syntax are not supported in Bash tool commands.",
                        true,
                        Character.toString(value)
                );
            }
            if (value == '<') {
                if (index + 1 < command.length() && command.charAt(index + 1) == '<') {
                    throw new ShellAnalysisException(
                            "UNSUPPORTED_SHELL_SYNTAX",
                            "Heredoc and here-string syntax are not supported in Bash tool commands.",
                            true,
                            "<<"
                    );
                }
                if (index + 1 < command.length() && command.charAt(index + 1) == '(') {
                    throw new ShellAnalysisException(
                            "UNSAFE_WRITE_SYNTAX",
                            "Process substitution is not allowed in Bash tool commands.",
                            true,
                            "<("
                    );
                }
                flushWord(tokens, current);
                tokens.add(ShellToken.operator("<"));
                index++;
                continue;
            }
            if (value == '>') {
                if (index + 1 < command.length() && command.charAt(index + 1) == '(') {
                    throw new ShellAnalysisException(
                            "UNSAFE_WRITE_SYNTAX",
                            "Process substitution is not allowed in Bash tool commands.",
                            true,
                            ">("
                    );
                }
                flushWord(tokens, current);
                if (index + 1 < command.length() && command.charAt(index + 1) == '>') {
                    tokens.add(ShellToken.operator(">>"));
                    index += 2;
                } else {
                    tokens.add(ShellToken.operator(">"));
                    index++;
                }
                continue;
            }
            if (value == '|') {
                flushWord(tokens, current);
                if (index + 1 < command.length() && command.charAt(index + 1) == '|') {
                    tokens.add(ShellToken.operator("||"));
                    index += 2;
                } else {
                    tokens.add(ShellToken.operator("|"));
                    index++;
                }
                continue;
            }
            if (value == '&') {
                flushWord(tokens, current);
                if (index + 1 < command.length() && command.charAt(index + 1) == '&') {
                    tokens.add(ShellToken.operator("&&"));
                    index += 2;
                } else {
                    tokens.add(ShellToken.operator("&"));
                    index++;
                }
                continue;
            }
            if (value == ';') {
                flushWord(tokens, current);
                tokens.add(ShellToken.operator(";"));
                index++;
                continue;
            }
            current.append(value);
            index++;
        }
        flushWord(tokens, current);
        return List.copyOf(tokens);
    }

    private int consumeSingleQuoted(String command, int startIndex, StringBuilder current) throws ShellAnalysisException {
        for (int index = startIndex; index < command.length(); index++) {
            char value = command.charAt(index);
            if (value == '\'') {
                return index + 1;
            }
            current.append(value);
        }
        throw new ShellAnalysisException("INVALID_COMMAND", "Unterminated single-quoted string in shell command.", true, "'");
    }

    private int consumeDoubleQuoted(String command, int startIndex, StringBuilder current) throws ShellAnalysisException {
        for (int index = startIndex; index < command.length(); index++) {
            char value = command.charAt(index);
            if (value == '"') {
                return index + 1;
            }
            if (value == '$' || value == '`') {
                throw new ShellAnalysisException(
                        "UNSUPPORTED_SHELL_SYNTAX",
                        "Shell variable or command expansion is not supported in quoted strings.",
                        true,
                        Character.toString(value)
                );
            }
            if (value == '\\') {
                if (index + 1 >= command.length()) {
                    throw new ShellAnalysisException("INVALID_COMMAND", "Dangling shell escape in quoted string.", true, "\\");
                }
                current.append(command.charAt(index + 1));
                index++;
                continue;
            }
            current.append(value);
        }
        throw new ShellAnalysisException("INVALID_COMMAND", "Unterminated double-quoted string in shell command.", true, "\"");
    }

    private List<ShellSegment> splitCommands(List<ShellToken> tokens) throws ShellAnalysisException {
        ArrayList<ShellSegment> segments = new ArrayList<>();
        ArrayList<ShellToken> current = new ArrayList<>();
        boolean endedByControlOperator = false;
        String trailingOperator = "";
        for (ShellToken token : tokens) {
            if (token.operator() && token.isControlOperator()) {
                if (current.isEmpty()) {
                    throw new ShellAnalysisException(
                            "INVALID_COMMAND",
                            "Shell command contains an empty segment around control operator " + token.value(),
                            true,
                            token.value()
                    );
                }
                segments.add(new ShellSegment(parseCommand(current), token.value()));
                current.clear();
                endedByControlOperator = true;
                trailingOperator = token.value();
                continue;
            }
            current.add(token);
            endedByControlOperator = false;
            trailingOperator = "";
        }
        if (endedByControlOperator) {
            if ("&".equals(trailingOperator)) {
                return List.copyOf(segments);
            }
            throw new ShellAnalysisException(
                    "INVALID_COMMAND",
                    "Shell command cannot end with a control operator.",
                    true,
                    trailingOperator
            );
        }
        if (!current.isEmpty()) {
            segments.add(new ShellSegment(parseCommand(current), null));
        }
        return List.copyOf(segments);
    }

    private SimpleShellCommand parseCommand(List<ShellToken> tokens) throws ShellAnalysisException {
        ArrayList<String> words = new ArrayList<>();
        ArrayList<String> outputRedirections = new ArrayList<>();
        boolean hasInputRedirection = false;
        for (int index = 0; index < tokens.size(); index++) {
            ShellToken token = tokens.get(index);
            if (!token.operator()) {
                if (index + 1 < tokens.size()
                        && tokens.get(index + 1).operator()
                        && ("<".equals(tokens.get(index + 1).value())
                        || ">".equals(tokens.get(index + 1).value())
                        || ">>".equals(tokens.get(index + 1).value()))
                        && token.value().chars().allMatch(Character::isDigit)) {
                    continue;
                }
                words.add(token.value());
                continue;
            }
            if ("<".equals(token.value())) {
                if (index + 1 >= tokens.size() || tokens.get(index + 1).operator()) {
                    throw new ShellAnalysisException("INVALID_COMMAND", "Input redirection requires a path target.", true, "<");
                }
                hasInputRedirection = true;
                index++;
                continue;
            }
            if (">".equals(token.value()) || ">>".equals(token.value())) {
                if (index + 1 >= tokens.size() || tokens.get(index + 1).operator()) {
                    throw new ShellAnalysisException("INVALID_COMMAND", "Output redirection requires a path target.", true, token.value());
                }
                outputRedirections.add(tokens.get(index + 1).value());
                index++;
                continue;
            }
            throw new ShellAnalysisException(
                    "UNSUPPORTED_SHELL_SYNTAX",
                    "Unsupported shell operator in command segment: " + token.value(),
                    true,
                    token.value()
            );
        }
        return new SimpleShellCommand(List.copyOf(words), List.copyOf(outputRedirections), hasInputRedirection);
    }

    private void flushWord(List<ShellToken> tokens, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        tokens.add(ShellToken.word(current.toString()));
        current.setLength(0);
    }

    private boolean looksLikeAssignment(String token) {
        return token != null && token.contains("=") && !token.startsWith("./") && !token.startsWith("/");
    }

    private String summarizeCommand(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.replace('\n', ' ').trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    private String summarizeWords(List<String> words) {
        return summarizeCommand(String.join(" ", words == null ? List.of() : words));
    }

    private String renderIntentPaths(List<ShellPathIntent> intents) {
        LinkedHashSet<String> rendered = new LinkedHashSet<>();
        for (ShellPathIntent intent : intents) {
            if (intent == null || intent.path() == null || intent.kind() == null) {
                continue;
            }
            rendered.add(intent.kind().name().toLowerCase(Locale.ROOT) + ":" + renderRelativePath(intent.path()));
        }
        return rendered.toString();
    }

    private String renderRelativePath(Path path) {
        if (path == null) {
            return "";
        }
        String rendered = path.normalize().toString().replace('\\', '/');
        return rendered.isBlank() ? "." : rendered;
    }

    private record NormalizedShellCommand(
            List<String> words,
            List<String> outputRedirections,
            boolean hasInputRedirection
    ) {
    }

    private record ShellToken(
            String value,
            boolean operator
    ) {
        static ShellToken word(String value) {
            return new ShellToken(value, false);
        }

        static ShellToken operator(String value) {
            return new ShellToken(value, true);
        }

        boolean isControlOperator() {
            return "&&".equals(value) || "||".equals(value) || "|".equals(value) || ";".equals(value) || "&".equals(value);
        }
    }

    private record SimpleShellCommand(
            List<String> words,
            List<String> outputRedirections,
            boolean hasInputRedirection
    ) {
    }

    private record ShellSegment(
            SimpleShellCommand command,
            String separatorAfter
    ) {
    }

    private record SegmentAnalysis(
            boolean allowed,
            boolean writesWorkspace,
            String reasonCode,
            String message,
            boolean retryable,
            String evidence,
            List<ShellPathIntent> pathIntents
    ) {
        static SegmentAnalysis allowReadOnly(String evidence) {
            return allowReadOnly(evidence, List.of());
        }

        static SegmentAnalysis allowReadOnly(String evidence, List<ShellPathIntent> pathIntents) {
            return new SegmentAnalysis(
                    true,
                    false,
                    "",
                    "",
                    true,
                    evidence == null ? "" : evidence,
                    pathIntents == null ? List.of() : List.copyOf(pathIntents)
            );
        }

        static SegmentAnalysis allowWrite(List<ShellPathIntent> pathIntents, String evidence) {
            return new SegmentAnalysis(true, true, "", "", true, evidence == null ? "" : evidence, pathIntents == null ? List.of() : List.copyOf(pathIntents));
        }
    }

    private static final class ShellRuntimeState {
        private Path currentDirectory;
        private final Deque<Path> directoryStack;

        private ShellRuntimeState(Path currentDirectory, Deque<Path> directoryStack) {
            this.currentDirectory = currentDirectory == null ? Path.of("") : currentDirectory.normalize();
            this.directoryStack = directoryStack == null ? new ArrayDeque<>() : directoryStack;
        }

        Path currentDirectory() {
            return currentDirectory;
        }

        void currentDirectory(Path value) {
            this.currentDirectory = value == null ? Path.of("") : value.normalize();
        }

        Deque<Path> directoryStack() {
            return directoryStack;
        }
    }

    private static final class ShellAnalysisException extends Exception {
        private final String reasonCode;
        private final boolean retryable;
        private final String evidence;
        private final List<ShellPathIntent> pathIntents;

        private ShellAnalysisException(String reasonCode, String message, boolean retryable, String evidence) {
            this(reasonCode, message, retryable, evidence, List.of());
        }

        private ShellAnalysisException(
                String reasonCode,
                String message,
                boolean retryable,
                String evidence,
                List<ShellPathIntent> pathIntents
        ) {
            super(message);
            this.reasonCode = reasonCode;
            this.retryable = retryable;
            this.evidence = evidence == null ? "" : evidence;
            this.pathIntents = pathIntents == null ? List.of() : List.copyOf(pathIntents);
        }

        String reasonCode() {
            return reasonCode;
        }

        boolean retryable() {
            return retryable;
        }

        String evidence() {
            return evidence;
        }

        List<ShellPathIntent> pathIntents() {
            return pathIntents;
        }
    }
}
