package com.homepage.frontend.content;

import java.util.List;

/**
 * Name, texts and start page of the site, from the backend ({@code /api/site}, read there from {@code info/site.yaml}).
 * Available in every template as {@code site}. {@code author} is never null, but may be empty; {@code description}
 * and {@code keywords} may be null.
 */
public record Site(String title, String author, String description, String keywords, boolean externalLinksInNewTab,
                   Home home) {

    /** Shown on the error page while the backend is not available. */
    public static final Site UNAVAILABLE = new Site("Homepage", "", null, null, true, null);

    public Site {
        author = author == null ? "" : author;
        home = home == null ? new Home(null, null, List.of(), "") : home;
    }

    /**
     * The start page. {@code avatar} and the link icons are URLs; {@code html} is the introduction, already rendered
     * from Markdown. {@code headline} and {@code avatar} may be null.
     */
    public record Home(String headline, String avatar, List<Link> links, String html) {

        public Home {
            links = links == null ? List.of() : links;
            html = html == null ? "" : html;
        }

        /** Whether the card left of the text (avatar, headline, links) has anything to show. */
        public boolean hasCard() {
            return avatar != null || headline != null || !links.isEmpty();
        }
    }

    /** A profile link with either an image {@code icon} (URL) or a Font Awesome {@code iconClass}, or neither. */
    public record Link(String name, String url, String icon, String iconClass) {}
}
