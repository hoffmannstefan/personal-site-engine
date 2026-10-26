package com.homepage.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepage.backend.post.MarkdownRenderer;
import com.homepage.backend.site.Site;
import com.homepage.backend.site.SiteLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteLoaderTest {

    @TempDir Path dir;

    private Site load(String yaml) throws Exception {
        Path file = dir.resolve("site.yaml");
        Files.writeString(file, yaml);
        return SiteLoader.load(file.toString(), new MarkdownRenderer());
    }

    @Test
    void withoutTheFileTheDefaultsApply() {
        Site s = SiteLoader.load(dir.resolve("missing.yaml").toString(), new MarkdownRenderer());
        assertThat(s.title()).isEqualTo("Homepage");
        assertThat(s.author()).isEmpty();
        assertThat(s.description()).isNull();
        assertThat(s.postsPerPage()).isEqualTo(6);
        assertThat(s.home().hasCard()).isFalse();
        assertThat(s.home().html()).isEmpty();
        assertThat(s.publications()).isEqualTo(
                new Site.Publications(Site.Publications.ArxivPapers.UNPUBLISHED, true,
                        Site.Publications.GroupBy.NONE, Site.Publications.Authors.ALL, true, 20));
    }

    @Test
    void linksInTheTextOpenInANewTabUnlessSwitchedOff() throws Exception {
        String text = "home:\n  text: \"[a](https://a.org)\"\n";
        Site s = load(text);
        assertThat(s.externalLinksInNewTab()).isTrue();
        assertThat(s.home().html()).contains("<a target=\"_blank\" rel=\"noopener\" href=\"https://a.org\">");
        Site off = load("external-links-in-new-tab: false\n" + text);
        assertThat(off.externalLinksInNewTab()).isFalse();
        assertThat(off.home().html()).isEqualTo("<p><a href=\"https://a.org\">a</a></p>\n");
    }

    @Test
    void readsThePublicationSettings() throws Exception {
        assertThat(load("publications:\n  arxiv-papers: Always\n  arxiv-links: false\n  group-by: type\n").publications())
                .isEqualTo(new Site.Publications(Site.Publications.ArxivPapers.ALWAYS, false,
                        Site.Publications.GroupBy.TYPE, Site.Publications.Authors.ALL, true, 20));
        assertThat(load("publications:\n  extended-version-links: false\n").publications().extendedVersionLinks())
                .isFalse();
        assertThat(load("publications:\n  extended-version-title-length: 0\n").publications()
                .extendedVersionTitleLength()).isZero();
        assertThat(load("publications:\n  extended-version-title-length: -3\n").publications()
                .extendedVersionTitleLength()).isEqualTo(20);
        assertThat(load("publications:\n  arxiv-papers: never\n").publications().arxivPapers())
                .isEqualTo(Site.Publications.ArxivPapers.NEVER);
        assertThat(load("publications:\n  authors: coauthors\n").publications().authors())
                .isEqualTo(Site.Publications.Authors.COAUTHORS);
        Site.Publications unknown = load("publications:\n  arxiv-papers: sometimes\n  group-by: decade\n").publications();
        assertThat(unknown.arxivPapers()).isEqualTo(Site.Publications.ArxivPapers.UNPUBLISHED);
        assertThat(unknown.groupBy()).isEqualTo(Site.Publications.GroupBy.NONE);
    }

    @Test
    void aBrokenFileDoesNotKeepTheSiteFromStarting() throws Exception {
        assertThat(load("title: [unclosed\n").title()).isEqualTo("Homepage");
    }

    @Test
    void theTitleDefaultsToTheAuthorAndUnknownKeysAreIgnored() throws Exception {
        Site s = load("author: Ada\nposts-per-page: 0\ntypo: x\nhome:\n  headlin: y\n  headline: Hi\n");
        assertThat(s.title()).isEqualTo("Ada");
        assertThat(s.postsPerPage()).isEqualTo(6);
        assertThat(s.home().headline()).isEqualTo("Hi");
    }

    @Test
    void theTextIsMarkdownWithParagraphs() throws Exception {
        Site s = load("home:\n  text: |\n    One *line*\n    still one.\n\n    Two.\n");
        assertThat(s.home().html()).isEqualTo("<p>One <em>line</em>\nstill one.</p>\n<p>Two.</p>\n");
    }

    @Test
    void imagePathsAreRelativeToTheImageFolderUnlessAbsolute() throws Exception {
        Site s = load("""
                home:
                  avatar: https://example.org/me.png
                  links:
                    - { name: A, url: "https://a.org", icon: a.png }
                    - { name: B, url: "https://b.org", icon: /static/b.png }
                    - { name: C, url: "https://c.org", icon-class: fa-brands fa-github }
                """);
        assertThat(s.home().avatar()).isEqualTo("https://example.org/me.png");
        assertThat(s.home().links()).extracting(Site.Link::icon).containsExactly("/images/a.png", "/static/b.png", null);
        assertThat(s.home().links().get(2).iconClass()).isEqualTo("fa-brands fa-github");
    }
}
