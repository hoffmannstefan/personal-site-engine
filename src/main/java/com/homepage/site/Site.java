package com.homepage.site;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the site shows (i.e., content related non-technical configuration): name, texts, start page, the settings of 
 * the Publications page and whether links to other sites open in a new tab. Available in every template as {@code site}.
 *
 * <p>Read from the YAML file {@code info/site.yaml} by {@link SiteLoader}; the technical settings are in
 * {@link SiteProperties}. {@code author} is never null, but may be empty; {@code description} and {@code keywords}
 * may be null.
 */
public record Site(String title, String author, String description, String keywords, int postsPerPage,
                   boolean externalLinksInNewTab, Home home, Publications publications) {

    /**
     * The start page, ready for display. {@code avatar} and the link icons are URLs; {@code html} is the
     * introduction rendered from Markdown. {@code headline} and {@code avatar} may be null.
     */
    public record Home(String headline, String avatar, List<Link> links, String html) {

        /** Whether the card left of the text (avatar, headline, links) has anything to show. */
        public boolean hasCard() {
            return avatar != null || headline != null || !links.isEmpty();
        }
    }

    /**
     * What the Publications page shows. {@code arxivPapers}: which arXiv versions get an entry of their own;
     * {@code arxivLinks}: an extra link to the arXiv preprint of a published paper. {@code groupBy}: the grouping the
     * page starts with; visitors can switch. {@code authors}: how the authors of an entry are shown.
     * {@code extendedVersionLinks}: a note "extended version of …" with a link to the entries named in the BibTeX
     * field {@code related}, and "extended in …" the other way round. {@code extendedVersionTitleLength}: how many
     * characters of the linked entry's title follow its venue in "extended version of DLT 2023, Completely Dis…";
     * 0 for none.
     */
    public record Publications(ArxivPapers arxivPapers, boolean arxivLinks, GroupBy groupBy, Authors authors,
                               boolean extendedVersionLinks, int extendedVersionTitleLength) {

        /** How the authors of an entry are shown. */
        public enum Authors {
            /** All of them: "Henning Fernau, Stefan Hoffmann". */
            ALL,
            /** Without the site's author, the others as "(with Henning Fernau)"; nothing for a paper of one's own. */
            COAUTHORS;

            public static Optional<Authors> parse(String key) {
                return fromKey(values(), key);
            }
        }

        /** Which arXiv versions (CoRR entries) get an entry of their own on the Publications page. */
        public enum ArxivPapers {
            /** All of them, also those of papers that are listed as published. */
            ALWAYS,
            /** Only those without a published version in the list: papers that are only on arXiv. */
            UNPUBLISHED,
            /** None. */
            NEVER;

            public static Optional<ArxivPapers> parse(String key) {
                return fromKey(values(), key);
            }
        }

        /** How the Publications page groups the entries; within a group the newest come first. */
        public enum GroupBy {
            YEAR, TYPE, NONE;

            /** As written in the configuration and the address ({@code ?group=year}). */
            public String key() {
                return name().toLowerCase(Locale.ROOT);
            }

            public static Optional<GroupBy> parse(String key) {
                return fromKey(values(), key);
            }
        }

        /** The constant whose name is {@code key}, ignoring case and surrounding spaces. */
        private static <E extends Enum<E>> Optional<E> fromKey(E[] values, String key) {
            return Arrays.stream(values).filter(v -> v.name().equalsIgnoreCase(key == null ? "" : key.strip())).findFirst();
        }
    }

    /** A profile link with either an image {@code icon} (URL) or a Font Awesome {@code iconClass}, or neither. */
    public record Link(String name, String url, String icon, String iconClass) {}
}
