package com.homepage.frontend.web;

import com.homepage.frontend.content.ContentClient;
import com.homepage.frontend.content.Post;
import com.homepage.frontend.content.PostPage;
import com.homepage.frontend.content.Publications;
import com.homepage.frontend.content.Site;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-rendered pages, with their content from the backend. Every route works with and without a trailing slash,
 * as on the Hugo original.
 */
@Controller
class PageController {

    private final ContentClient content;

    PageController(ContentClient content) {
        this.content = content;
    }

    /** The start page only needs the site, which {@link SiteAttributes} has already fetched. */
    @GetMapping("/")
    String home(@ModelAttribute("site") Site site) {
        if (site == Site.UNAVAILABLE) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        return "home";
    }

    /** {@code ?group=year|type|none} overrides the grouping of the site configuration; anything else is ignored. */
    @GetMapping({"/publications", "/publications/"})
    String publications(@RequestParam(required = false) String group, Model model) {
        Publications publications = content.publications(group);
        model.addAttribute("groups", publications.groups());
        model.addAttribute("groupBy", publications.groupBy());
        model.addAttribute("groupings", Publications.GROUPINGS);
        return "publications";
    }

    @GetMapping({"/posts", "/posts/"})
    String postList(Model model) {
        return postPage(1, model);
    }

    @GetMapping({"/posts/page/{page:\\d+}", "/posts/page/{page:\\d+}/"})
    String postPage(@PathVariable int page, Model model) {
        PostPage posts = content.posts(page).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("nav", "posts");
        model.addAttribute("pageTitle", "Posts");
        model.addAttribute("posts", posts.posts());
        model.addAttribute("page", posts.page());
        model.addAttribute("pages", posts.pages());
        return "posts";
    }

    @GetMapping({"/posts/{slug}", "/posts/{slug}/"})
    String post(@PathVariable String slug, Model model) {
        Post post = content.post(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("nav", "posts");
        model.addAttribute("pageTitle", post.title());
        model.addAttribute("post", post);
        return "post";
    }
}
