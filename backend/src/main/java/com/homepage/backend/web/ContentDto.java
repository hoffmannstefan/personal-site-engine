package com.homepage.backend.web;

import com.homepage.backend.post.Post;
import com.homepage.backend.publication.Publication;
import java.time.LocalDate;
import java.util.List;

/** Response shapes of the content API that are not domain records themselves. */
public final class ContentDto {

    private ContentDto() {
    }

    /** One page of the blog list: page {@code page} of {@code pages}, newest first. */
    public record PostPage(int page, int pages, List<PostSummary> posts) {}

    /** A post in the blog list, without its body. {@code comments} is the number of comments. */
    public record PostSummary(String slug, String title, LocalDate date, String readingTime, List<String> authors,
                              List<String> categories, List<String> tags, String externalUrl, long comments) {

        static PostSummary of(Post p, long comments) {
            return new PostSummary(p.slug(), p.title(), p.date(), p.readingTime(), p.authors(), p.categories(),
                    p.tags(), p.externalUrl(), comments);
        }
    }

    /** The Publications page: the grouping used ({@code year}, {@code type} or {@code none}) and the groups. */
    public record Publications(String groupBy, List<Publication.Group> groups) {}
}
