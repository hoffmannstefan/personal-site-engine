package com.homepage.post;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps TeX formulas away from the Markdown parser, which would otherwise mangle them
 * ({@code _} and {@code *} become emphasis, {@code \\} and {@code \(} are escapes, {@code |} splits table cells).
 *
 * <p>{@link #protect} swaps every formula in a Markdown source for an inert placeholder; {@link #restore} puts
 * the formulas back into the rendered HTML, HTML-escaped so the browser hands MathJax the original TeX.
 * Formulas are always written back with the {@code \( \)} / {@code \[ \]} delimiters, so a plain {@code $}
 * elsewhere in the text (as in "costs $5 and $10") can never be mistaken for math.
 *
 * <p>Recognised: {@code $...$} (the opening {@code $} must be followed, and the closing one preceded, by
 * non-space, and the closing one must not be followed by a digit), {@code $$...$$}, {@code \(...\)},
 * {@code \[...\]} and {@code \begin{env}...\end{env}}. Code (fenced blocks at any indentation and inline code spans) is left
 * alone, and {@code \$} stays a literal dollar sign. Indented code blocks are not recognised; use fences.
 */
public final class MathProtector {

    private static final char OPEN = '';
    private static final char CLOSE = '';
    /** Digits of the placeholder index are private-use characters, so they never end up in heading ids. */
    private static final char DIGIT_BASE = '';

    private static final Pattern FENCE = Pattern.compile("^[ \\t]*(`{3,}|~{3,})");
    private static final Pattern BEGIN_ENV = Pattern.compile("\\\\begin\\{([A-Za-z]+\\*?)}");

    private final List<String> formulas = new ArrayList<>();
    private int count;

    /** Number of formulas found so far. */
    public int count() {
        return count;
    }

    public String protect(String markdown) {
        StringBuilder out = new StringBuilder(markdown.length());
        StringBuilder text = new StringBuilder();
        String fenceMarker = null;
        for (String line : markdown.split("(?<=\n)", -1)) {
            Matcher fence = FENCE.matcher(line);
            boolean isFence = fence.find();
            if (fenceMarker != null) {
                out.append(line);
                if (isFence && fence.group(1).charAt(0) == fenceMarker.charAt(0)
                        && fence.group(1).length() >= fenceMarker.length() && line.strip().length() == fence.group(1).length()) {
                    fenceMarker = null;
                }
            } else if (isFence) {
                out.append(protectText(text.toString()));
                text.setLength(0);
                fenceMarker = fence.group(1);
                out.append(line);
            } else {
                text.append(line);
            }
        }
        out.append(protectText(text.toString()));
        return out.toString();
    }

    public String restore(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            char c = html.charAt(i);
            int end;
            if (c == OPEN && (end = html.indexOf(CLOSE, i)) > 0) {
                int index = 0;
                for (int k = i + 1; k < end; k++) {
                    index = index * 10 + (html.charAt(k) - DIGIT_BASE);
                }
                out.append(formulas.get(index));
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Protects formulas in text that contains no fenced code; inline code spans are skipped. */
    private String protectText(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '`') {
                int run = runLength(s, i, '`');
                int close = findBacktickRun(s, i + run, run);
                if (close < 0 || hasBlankLine(s, i, close)) {
                    out.append(s, i, i + run);
                    i += run;
                } else {
                    out.append(s, i, close + run);
                    i = close + run;
                }
            } else if (c == '\\' && i + 1 < n) {
                i = backslash(s, i, out);
            } else if (c == '$') {
                i = dollar(s, i, out);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private int backslash(String s, int i, StringBuilder out) {
        char next = s.charAt(i + 1);
        if (next == '(' || next == '[') {
            String close = next == '(' ? "\\)" : "\\]";
            int end = s.indexOf(close, i + 2);
            if (end > 0 && (next == '[' || !hasBlankLine(s, i, end))) {
                out.append(token(next == '(' ? "\\(" : "\\[", s.substring(i + 2, end), close));
                return end + 2;
            }
        } else if (next == 'b') {
            Matcher m = BEGIN_ENV.matcher(s).region(i, s.length());
            if (m.lookingAt()) {
                String end = "\\end{" + m.group(1) + "}";
                int close = s.indexOf(end, m.end());
                if (close > 0) {
                    out.append(token("", s.substring(i, close + end.length()), ""));
                    return close + end.length();
                }
            }
        }
        // Any other escape (including \$) is left for Markdown; skip both characters so \$ is not read as math.
        out.append('\\').append(next);
        return i + 2;
    }

    private int dollar(String s, int i, StringBuilder out) {
        int n = s.length();
        if (i + 1 < n && s.charAt(i + 1) == '$') {
            int end = s.indexOf("$$", i + 2);
            if (end > 0) {
                out.append(token("\\[", s.substring(i + 2, end), "\\]"));
                return end + 2;
            }
            out.append("$$");
            return i + 2;
        }
        if (i + 1 < n && !Character.isWhitespace(s.charAt(i + 1))) {
            int end = closingDollar(s, i + 1);
            if (end > 0) {
                out.append(token("\\(", s.substring(i + 1, end), "\\)"));
                return end + 1;
            }
        }
        out.append('$');
        return i + 1;
    }

    /** Finds the {@code $} that ends an inline formula starting at {@code from}, or -1. */
    private static int closingDollar(String s, int from) {
        for (int j = from; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '\\') {
                j++;
            } else if (c == '`') {
                // a code span cannot contain the end of a formula
                int run = runLength(s, j, '`');
                int close = findBacktickRun(s, j + run, run);
                j = close < 0 || hasBlankLine(s, j, close) ? j + run - 1 : close + run - 1;
            } else if (c == '\n' && j + 1 < s.length() && isBlankFrom(s, j + 1)) {
                return -1;
            } else if (c == '$') {
                boolean spaceBefore = Character.isWhitespace(s.charAt(j - 1));
                boolean digitAfter = j + 1 < s.length() && Character.isDigit(s.charAt(j + 1));
                return spaceBefore || digitAfter || j == from ? -1 : j;
            }
        }
        return -1;
    }

    private static boolean isBlankFrom(String s, int from) {
        int eol = s.indexOf('\n', from);
        return s.substring(from, eol < 0 ? s.length() : eol).isBlank();
    }

    private static boolean hasBlankLine(String s, int from, int to) {
        return s.substring(from, to).matches("(?s).*\n[ \t]*\n.*");
    }

    private static int runLength(String s, int from, char c) {
        int j = from;
        while (j < s.length() && s.charAt(j) == c) {
            j++;
        }
        return j - from;
    }

    private static int findBacktickRun(String s, int from, int length) {
        int j = from;
        while ((j = s.indexOf('`', j)) >= 0) {
            int run = runLength(s, j, '`');
            if (run == length) {
                return j;
            }
            j += run;
        }
        return -1;
    }

    private String token(String open, String tex, String close) {
        formulas.add(open + escapeHtml(tex) + close);
        count++;
        StringBuilder sb = new StringBuilder().append(OPEN);
        for (char d : Integer.toString(formulas.size() - 1).toCharArray()) {
            sb.append((char) (DIGIT_BASE + (d - '0')));
        }
        return sb.append(CLOSE).toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
