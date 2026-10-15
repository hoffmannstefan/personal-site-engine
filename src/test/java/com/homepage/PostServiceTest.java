package com.homepage;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepage.post.MarkdownRenderer;
import com.homepage.post.Post;
import com.homepage.post.PostService;
import com.homepage.site.Site;
import com.homepage.site.SiteProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostServiceTest {

    @TempDir Path dir;

    private PostService service() {
        return new PostService(new MarkdownRenderer(), properties(dir), site("Jane Roe"));
    }

    static SiteProperties properties(Path postsDir) {
        return new SiteProperties(null, postsDir.toString(), new SiteProperties.Comments(5, ""), null, null);
    }

    static Site site(String author) {
        return site(author, true);
    }

    static Site site(String author, boolean newTab) {
        return new Site("t", author, null, null, 6, newTab, new Site.Home(null, null, List.of(), ""),
                new Site.Publications(Site.Publications.ArxivPapers.UNPUBLISHED, true,
                        Site.Publications.GroupBy.NONE, Site.Publications.Authors.ALL, true, 20));
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void loadsMarkdownPostsFromTheFolder() throws Exception {
        write("hello-world.md", "---\ntitle: Hello\ndate: 2030-05-06\ntags: [x]\n---\nSome text with $a_1$.\n");

        Post p = service().find("hello-world").orElseThrow();
        assertThat(p.title()).isEqualTo("Hello");
        assertThat(p.date().toString()).isEqualTo("2030-05-06");
        assertThat(p.authors()).containsExactly("Jane Roe");       // defaults to the site author
        assertThat(p.tags()).containsExactly("x");
        assertThat(p.readingTime()).isEqualTo("1-minute read");
        assertThat(p.math()).isTrue();
        assertThat(p.body()).contains("\\(a_1\\)");
        assertThat(service().all().get(0).slug()).isEqualTo("hello-world");   // newest first
    }

    @Test
    void invalidFilesAreSkippedInsteadOfBreakingStartup() throws Exception {
        write("no-title.md", "---\ndate: 2030-01-01\n---\nx\n");
        write("bad-date.md", "---\ntitle: T\ndate: yesterday\n---\nx\n");
        write("Bad Name.md", "---\ntitle: T\ndate: 2030-01-01\n---\nx\n");
        write("no-front-matter.md", "Just text\n");
        write("draft.md", "---\ntitle: D\ndate: 2030-01-01\ndraft: true\n---\nx\n");
        write("notes.txt", "ignored");

        PostService s = service();
        for (String slug : new String[] {"no-title", "bad-date", "Bad Name", "no-front-matter", "draft", "notes"}) {
            assertThat(s.exists(slug)).as(slug).isFalse();
        }
    }

    @Test
    void aLinkInTheFrontMatterMakesAnExternalEntryWithoutAPage() throws Exception {
        write("elsewhere.md", "---\ntitle: Elsewhere\ndate: 2030-01-01\nlink: https://example.org/x\n---\n");
        PostService s = service();
        assertThat(s.all().get(0).externalUrl()).isEqualTo("https://example.org/x");
        assertThat(s.exists("elsewhere")).isFalse();
    }

    @Test
    void withoutASiteAuthorPostsWithoutAuthorsHaveNone() throws Exception {
        write("anonymous.md", "---\ntitle: A\ndate: 2030-01-01\n---\nx\n");
        var s = new PostService(new MarkdownRenderer(), properties(dir), site(""));
        assertThat(s.find("anonymous").orElseThrow().authors()).isEmpty();
    }

    @Test
    void linksToOtherSitesOpenInANewTabIfConfigured() throws Exception {
        write("links.md", "---\ntitle: L\ndate: 2030-01-01\n---\n[out](https://example.org) and [in](/posts/)\n");
        assertThat(service().find("links").orElseThrow().body()).isEqualTo(
                "<p><a target=\"_blank\" rel=\"noopener\" href=\"https://example.org\">out</a> and <a href=\"/posts/\">in</a></p>\n");
        var sameTab = new PostService(new MarkdownRenderer(), properties(dir), site("a", false));
        assertThat(sameTab.find("links").orElseThrow().body()).doesNotContain("_blank");
    }

    @Test
    void aMissingFolderIsFine() {
        var s = new PostService(new MarkdownRenderer(), properties(dir.resolve("does-not-exist")), site("a"));
        assertThat(s.all()).isEmpty();
    }
}
