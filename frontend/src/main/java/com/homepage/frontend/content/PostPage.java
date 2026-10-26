package com.homepage.frontend.content;

import java.time.LocalDate;
import java.util.List;

/** Page {@code page} of {@code pages} of the blog list, newest first, from the backend ({@code /api/posts?page=}). */
public record PostPage(int page, int pages, List<Entry> posts) {

    public PostPage {
        posts = posts == null ? List.of() : posts;
    }

    /**
     * A post in the list with its number of {@code comments}. An entry with an {@code externalUrl} links to another
     * site instead of a page of its own.
     */
    public record Entry(String slug, String title, LocalDate date, String externalUrl, long comments) {}
}
