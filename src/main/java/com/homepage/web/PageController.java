package com.homepage.web;

import com.homepage.comment.CommentService;
import com.homepage.post.Post;
import com.homepage.post.PostService;
import com.homepage.publication.PublicationService;
import com.homepage.site.Site;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Server-rendered pages. Every route works with and without a trailing slash, as on the Hugo original. */
@Controller
class PageController {

    private final PostService posts;
    private final CommentService comments;
    private final PublicationService publications;
    private final Site site;

    PageController(PostService posts, CommentService comments, PublicationService publications, Site site) {
        this.posts = posts;
        this.comments = comments;
        this.publications = publications;
        this.site = site;
    }

    @GetMapping("/")
    String home() {
        return "home";
    }

    /** {@code ?group=year|type|none} overrides the grouping of the site configuration; anything else is ignored. */
    @GetMapping({"/publications", "/publications/"})
    String publications(@RequestParam(required = false) String group, Model model) {
        Site.Publications.GroupBy by = Site.Publications.GroupBy.parse(group).orElse(site.publications().groupBy());
        model.addAttribute("groups", publications.grouped(by));
        model.addAttribute("groupBy", by);
        model.addAttribute("groupings", Site.Publications.GroupBy.values());
        return "publications";
    }

    @GetMapping({"/posts", "/posts/"})
    String postList(Model model) {
        return postPage(1, model);
    }

    @GetMapping({"/posts/page/{page:\\d+}", "/posts/page/{page:\\d+}/"})
    String postPage(@PathVariable int page, Model model) {
        List<Post> all = posts.all();
        int perPage = site.postsPerPage();
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        if (page < 1 || page > pages) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("nav", "posts");
        model.addAttribute("pageTitle", "Posts");
        model.addAttribute("posts", all.subList((page - 1) * perPage, Math.min(all.size(), page * perPage)));
        model.addAttribute("commentCounts", comments.countsByPost());
        model.addAttribute("page", page);
        model.addAttribute("pages", pages);
        return "posts";
    }

    @GetMapping({"/posts/{slug}", "/posts/{slug}/"})
    String post(@PathVariable String slug, Model model) {
        Post post = posts.find(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("nav", "posts");
        model.addAttribute("pageTitle", post.title());
        model.addAttribute("post", post);
        return "post";
    }
}
