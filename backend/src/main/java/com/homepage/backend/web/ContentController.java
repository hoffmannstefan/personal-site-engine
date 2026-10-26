package com.homepage.backend.web;

import com.homepage.backend.comment.CommentService;
import com.homepage.backend.post.Post;
import com.homepage.backend.post.PostService;
import com.homepage.backend.publication.PublicationService;
import com.homepage.backend.site.Site;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The content read from {@code info/} as JSON: site configuration, blog posts (rendered to HTML) and publications.
 * Read-only; the content changes with a restart. The comments are in {@code CommentController}.
 */
@RestController
@RequestMapping("/api")
class ContentController {

    private final PostService posts;
    private final CommentService comments;
    private final PublicationService publications;
    private final Site site;

    ContentController(PostService posts, CommentService comments, PublicationService publications, Site site) {
        this.posts = posts;
        this.comments = comments;
        this.publications = publications;
        this.site = site;
    }

    /** Name, texts and start page from {@code info/site.yaml}; the start page text is HTML. */
    @GetMapping("/site")
    Site site() {
        return site;
    }

    /** Page {@code page} of the blog list, with {@code posts-per-page} posts each; 404 past the last page. */
    @GetMapping("/posts")
    ContentDto.PostPage postPage(@RequestParam(defaultValue = "1") int page) {
        List<Post> all = posts.all();
        int perPage = site.postsPerPage();
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        if (page < 1 || page > pages) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such page");
        }
        Map<String, Long> counts = comments.countsByPost();
        return new ContentDto.PostPage(page, pages, all.subList((page - 1) * perPage, Math.min(all.size(), page * perPage))
                .stream().map(p -> ContentDto.PostSummary.of(p, counts.getOrDefault(p.slug(), 0L))).toList());
    }

    /** A post with its body as HTML; 404 for unknown slugs and for entries that only link to another site. */
    @GetMapping("/posts/{slug}")
    Post post(@PathVariable String slug) {
        return posts.find(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    /** {@code ?group=year|type|none} overrides the grouping of the site configuration; anything else is ignored. */
    @GetMapping("/publications")
    ContentDto.Publications publications(@RequestParam(required = false) String group) {
        Site.Publications.GroupBy by = Site.Publications.GroupBy.parse(group).orElse(site.publications().groupBy());
        return new ContentDto.Publications(by.key(), publications.grouped(by));
    }
}
