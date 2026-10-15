package com.homepage.publication;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A small BibTeX reader, enough for files as exported by DBLP or Google Scholar: entries with braced, quoted or bare
 * values, {@code #} concatenation, {@code @string} macros and the common LaTeX escapes and accents. A broken entry
 * is skipped with a warning; the rest of the file is still read.
 */
final class BibTeX {

    private static final Logger log = LoggerFactory.getLogger(BibTeX.class);

    /** One entry; the type and the field names are lower case, the values are still LaTeX. */
    record Entry(String type, String key, Map<String, String> fields) {
        String get(String field) {
            return fields.get(field);
        }
    }

    private final String in;
    private final Map<String, String> macros = new LinkedHashMap<>();
    private int pos;

    private BibTeX(String in) {
        this.in = in;
    }

    static List<Entry> parse(String text) {
        return new BibTeX(text).entries();
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        while ((pos = in.indexOf('@', pos)) >= 0) {
            int start = pos;
            try {
                pos++;
                String type = identifier().toLowerCase(Locale.ROOT);
                skipSpace();
                char close = switch (next()) {
                    case '{' -> '}';
                    case '(' -> ')';
                    default -> throw error("expected { or ( after @" + type);
                };
                switch (type) {
                    case "comment", "preamble" -> skipBalanced(close);
                    case "string" -> {
                        Map<String, String> fields = fields(close);
                        fields.forEach(macros::put);
                    }
                    default -> {
                        String key = until(',', close).strip();
                        entries.add(new Entry(type, key, fields(close)));
                    }
                }
            } catch (IllegalArgumentException e) {
                int line = (int) in.substring(0, start).chars().filter(c -> c == '\n').count() + 1;
                log.warn("Skipping the BibTeX entry in line {}: {}", line, e.getMessage());
                pos = start + 1;
            }
        }
        return entries;
    }

    /** The fields up to and including the closing bracket of the entry. */
    private Map<String, String> fields(char close) {
        Map<String, String> fields = new LinkedHashMap<>();
        while (true) {
            skipSpace();
            if (peek() == ',') {
                pos++;
                continue;
            }
            if (peek() == close) {
                pos++;
                return fields;
            }
            String name = identifier().toLowerCase(Locale.ROOT);
            skipSpace();
            if (next() != '=') {
                throw error("expected = after " + name);
            }
            StringBuilder value = new StringBuilder(value());
            skipSpace();
            while (peek() == '#') {
                pos++;
                value.append(value());
                skipSpace();
            }
            fields.put(name, value.toString());
        }
    }

    private String value() {
        skipSpace();
        char c = peek();
        if (c == '{') {
            pos++;
            int start = pos;
            skipBalanced('}');
            return in.substring(start, pos - 1);
        }
        if (c == '"') {
            pos++;
            int start = pos;
            int depth = 0;
            while (true) {
                char d = next();
                if (d == '{') {
                    depth++;
                } else if (d == '}') {
                    depth--;
                } else if (d == '"' && depth == 0) {
                    return in.substring(start, pos - 1);
                }
            }
        }
        String word = identifier();
        return word.chars().allMatch(Character::isDigit) ? word
                : macros.getOrDefault(word.toLowerCase(Locale.ROOT), word);
    }

    /** Moves past the bracket that closes the one just read, ignoring nested braces. */
    private void skipBalanced(char close) {
        int depth = 0;
        while (true) {
            char c = next();
            if (c == '{') {
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
            } else if (c == close && depth == 0) {
                return;
            }
        }
    }

    private String until(char stop, char close) {
        int start = pos;
        while (peek() != stop) {
            if (peek() == close) {
                throw error("entry without fields");
            }
            pos++;
        }
        pos++;
        return in.substring(start, pos - 1);
    }

    private String identifier() {
        skipSpace();
        int start = pos;
        while (pos < in.length() && !Character.isWhitespace(in.charAt(pos)) && "{}()\",=#%".indexOf(in.charAt(pos)) < 0) {
            pos++;
        }
        if (pos == start) {
            throw error("unexpected " + (pos < in.length() ? "'" + in.charAt(pos) + "'" : "end of file"));
        }
        return in.substring(start, pos);
    }

    private void skipSpace() {
        while (pos < in.length() && Character.isWhitespace(in.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= in.length()) {
            throw error("unexpected end of file");
        }
        return in.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message);
    }

    // ---- LaTeX to plain text ----

    private static final Map<String, String> ACCENTS = Map.of(
            "\"", "̈", "'", "́", "`", "̀", "^", "̂", "~", "̃",
            "=", "̄", ".", "̇", "c", "̧", "v", "̌", "u", "̆");
    /** {@code \"o}, {@code \"{o}}, {@code {\"o}}, {@code \c{c}}, ... */
    private static final Pattern ACCENT = Pattern.compile(
            "\\{?\\\\([\"'`^~=.]|[cvu](?=[{\\s]))\\s*(?:\\{([A-Za-z]|\\\\i)\\}|([A-Za-z]))\\}?");
    private static final Map<String, String> SYMBOLS = Map.of(
            "\\ss", "ß", "\\o", "ø", "\\O", "Ø", "\\aa", "å", "\\AA", "Å", "\\ae", "æ", "\\l", "ł", "\\L", "Ł");

    /** Turns a LaTeX field value into display text: accents, escapes, dashes and braces resolved. */
    static String text(String latex) {
        if (latex == null) {
            return null;
        }
        Matcher m = ACCENT.matcher(latex);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String letter = m.group(2) != null ? m.group(2) : m.group(3);
            letter = letter.equals("\\i") ? "i" : letter;
            m.appendReplacement(sb, Matcher.quoteReplacement(letter + ACCENTS.get(m.group(1))));
        }
        String s = m.appendTail(sb).toString();
        for (Map.Entry<String, String> e : SYMBOLS.entrySet()) {
            s = s.replaceAll(Pattern.quote(e.getKey()) + "(?![A-Za-z])\\s?", e.getValue());
        }
        s = s.replace("\\&", "&").replace("\\%", "%").replace("\\#", "#").replace("\\_", "_").replace("\\$", "$")
                .replace("---", "—").replace("--", "–").replace("~", " ")
                .replace("{", "").replace("}", "");
        return Normalizer.normalize(s.replaceAll("\\s+", " ").strip(), Normalizer.Form.NFC);
    }
}
