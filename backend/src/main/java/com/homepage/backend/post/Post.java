package com.homepage.backend.post;

import java.time.LocalDate;
import java.util.List;

/**
 * A blog post. {@code body} is HTML rendered from the post's Markdown. {@code math} marks posts that contain
 * formulas, so the page loads MathJax. Entries with an {@code externalUrl} only appear in the list and link elsewhere.
 */
public record Post(
        String slug,
        String title,
        LocalDate date,
        String readingTime,
        List<String> authors,
        List<String> categories,
        List<String> tags,
        String body,
        String externalUrl,
        boolean math) {
}
