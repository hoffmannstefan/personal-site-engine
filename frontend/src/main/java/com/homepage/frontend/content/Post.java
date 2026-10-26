package com.homepage.frontend.content;

import java.time.LocalDate;
import java.util.List;

/**
 * A blog post from the backend ({@code /api/posts/{slug}}). {@code body} is HTML rendered from the post's Markdown.
 * {@code math} marks posts that contain formulas, so the page loads MathJax.
 */
public record Post(String slug, String title, LocalDate date, String readingTime, List<String> authors,
                   List<String> categories, List<String> tags, String body, boolean math) {

    public Post {
        authors = authors == null ? List.of() : authors;
        categories = categories == null ? List.of() : categories;
        tags = tags == null ? List.of() : tags;
    }
}
