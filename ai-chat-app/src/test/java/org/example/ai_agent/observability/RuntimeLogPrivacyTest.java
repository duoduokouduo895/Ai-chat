package org.example.ai_agent.observability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLogPrivacyTest {

    private static final List<Pattern> FORBIDDEN_ARGUMENT_PATTERNS = List.of(
            Pattern.compile("\\b(query|question|prompt|username|userId|requesterUserId|guestUsername|creatorId|uploaderId)\\b"),
            Pattern.compile("\\bcontent\\b(?!\\s*\\()"),
            Pattern.compile("\\b(jti|oldJti|newJti|fileName|ossPath|jsonQueryString|errorMessage|errorStack)\\b"),
            Pattern.compile("\\b(details|knowledgeBaseIds)\\b(?!\\s*\\.getClass\\(\\)|\\s*==\\s*null|\\s*\\.size\\(\\))"),
            Pattern.compile("\\.getMessage\\(\\)"),
            Pattern.compile("\\.getReason\\(\\)"),
            Pattern.compile("(?:^|,)\\s*(?:e|ex|error|throwable|exception)\\s*$"),
            Pattern.compile("\\bconversationIds\\b(?!\\s*\\.size\\(\\))"),
            Pattern.compile("\\w+\\.get(Name|FileName|OriginalFilename|OssUrl|UserId|CreatorId|UploaderId|Username|Description)\\(\\)(?!\\.length\\(\\)|\\.getClass\\(\\))"),
            Pattern.compile("\\.filename\\(\\)(?!\\.length\\(\\))"));
    private static final List<Pattern> FORBIDDEN_TEMPLATE_PATTERNS = List.of(
            Pattern.compile("tokenPreview|内容: \\{\\}|\\[检索结果] 获得相关文档"));

    @Test
    void runtimeLogTemplatesMustNotExposeUserTextOrTokenFragments() throws IOException {
        var violations = new java.util.ArrayList<String>();
        try (var files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var source = Files.readString(file);
                for (LogCall statement : extractLogCalls(source)) {
                    for (Pattern forbidden : FORBIDDEN_ARGUMENT_PATTERNS) {
                        if (forbidden.matcher(argumentsOf(statement.source())).find()) {
                            violations.add(file + ":" + statement.line() + " matches " + forbidden.pattern());
                        }
                    }
                    for (Pattern forbidden : FORBIDDEN_TEMPLATE_PATTERNS) {
                        if (forbidden.matcher(statement.source()).find()) {
                            violations.add(file + ":" + statement.line() + " matches " + forbidden.pattern());
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("runtime logs must retain metadata, never user text or credential fragments")
                .isEmpty();
    }

    private static List<LogCall> extractLogCalls(String source) {
        var calls = new ArrayList<LogCall>();
        int searchFrom = 0;
        while (searchFrom < source.length()) {
            int start = source.indexOf("log.", searchFrom);
            if (start < 0) {
                break;
            }
            int open = source.indexOf('(', start);
            if (open < 0) {
                break;
            }

            int depth = 0;
            boolean inString = false;
            boolean inCharacter = false;
            boolean escaped = false;
            int end = -1;
            for (int index = open; index < source.length(); index++) {
                char current = source.charAt(index);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if ((inString || inCharacter) && current == '\\') {
                    escaped = true;
                    continue;
                }
                if (!inCharacter && current == '"') {
                    inString = !inString;
                    continue;
                }
                if (!inString && current == '\'') {
                    inCharacter = !inCharacter;
                    continue;
                }
                if (inString || inCharacter) {
                    continue;
                }
                if (current == '(') {
                    depth++;
                } else if (current == ')' && --depth == 0) {
                    end = index + 1;
                    break;
                }
            }

            if (end < 0) {
                break;
            }
            int line = 1 + Math.toIntExact(source.substring(0, start).chars().filter(ch -> ch == '\n').count());
            calls.add(new LogCall(line, source.substring(start, end)));
            searchFrom = end;
        }
        return calls;
    }

    private static String argumentsOf(String call) {
        int open = call.indexOf('(');
        int depth = 0;
        boolean inString = false;
        boolean inCharacter = false;
        boolean escaped = false;
        for (int index = open; index < call.length(); index++) {
            char current = call.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if ((inString || inCharacter) && current == '\\') {
                escaped = true;
                continue;
            }
            if (!inCharacter && current == '"') {
                inString = !inString;
                continue;
            }
            if (!inString && current == '\'') {
                inCharacter = !inCharacter;
                continue;
            }
            if (inString || inCharacter) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 1) {
                return call.substring(index + 1, call.length() - 1);
            }
        }
        return "";
    }

    private record LogCall(int line, String source) {
    }
}
