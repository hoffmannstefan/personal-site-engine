package com.homepage.frontend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homepage.frontend.content.ContentClient;
import com.homepage.frontend.content.Post;
import com.homepage.frontend.content.PostPage;
import com.homepage.frontend.content.Publications;
import com.homepage.frontend.content.Site;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

/** The pages as rendered from the backend's content, with the backend replaced by a stub. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagesTest {

    static final Site SITE = new Site("Test Site", "Jane Roe", "A site for the tests", "tests, examples", true,
            new Site.Home("Test Headline", "/images/icons/test.svg", List.of(
                    new Site.Link("Example", "https://example.org/profile", "/images/icons/test.svg", null),
                    new Site.Link("GitHub", "https://github.com/example", null, "fa-brands fa-github")),
                    "<p>Hello from <em>Markdown</em>.</p>\n<p>Second paragraph.</p>\n"));

    static final Post MATH_POST = new Post("math-with-mathjax", "Math with MathJax", LocalDate.of(2026, 10, 6),
            "1-minute read", List.of("Jane Roe"), List.of("Notes"), List.of("math", "tex"),
            "<p>\\[x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\\]</p>", true);

    static final Post PLAIN_POST = new Post("markdown-syntax", "Markdown Syntax Guide", LocalDate.of(2023, 7, 13),
            "4-minute read", List.of("Jane Roe"), List.of(), List.of(), "<p><strong>strong text</strong></p>", false);

    static final Publications.Entry JOURNAL = new Publications.Entry("Ada Lovelace, Charles Babbage",
            "Notes on the Analytical Engine", "Sci. Mem. 3(1), 666–731", 1843, "https://example.org/notes",
            "https://arxiv.org/abs/1843.00001", "dblp-journals-sm-lovelace43",
            List.of(new Publications.Reference("Example Conference 1844, A paper without a li…", "lovelace44",
                            "A paper without a link"),
                    new Publications.Reference("Example Workshop 1842, An early talk", "lovelace42", "An early talk")),
            List.of());

    static final Publications.Entry CONFERENCE = new Publications.Entry(null, "A paper without a link",
            "Proceedings of the Example Conference, Lect. Notes Comput. Sci. 1, 1–2", 1844, null, null, "lovelace44",
            List.of(), List.of(new Publications.Reference("Sci. Mem. 1843", "dblp-journals-sm-lovelace43",
                    "Notes on the Analytical Engine")));

    @Autowired MockMvc mvc;
    @MockitoBean ContentClient content;

    @BeforeEach
    void backend() {
        when(content.site()).thenReturn(SITE);
        when(content.posts(anyInt())).thenReturn(Optional.empty());
        when(content.posts(1)).thenReturn(Optional.of(new PostPage(1, 2, List.of(
                new PostPage.Entry("math-with-mathjax", "Math with MathJax", LocalDate.of(2026, 10, 6), null, 0),
                new PostPage.Entry("markdown-syntax", "Markdown Syntax Guide", LocalDate.of(2023, 7, 13), null, 2)))));
        when(content.posts(2)).thenReturn(Optional.of(new PostPage(2, 2, List.of(
                new PostPage.Entry("placeholder-text", "Placeholder Text", LocalDate.of(2023, 7, 5), null, 1),
                new PostPage.Entry("hugo-coder-wiki", "External Page: Hugo Coder Wiki", LocalDate.of(2023, 7, 6),
                        "https://github.com/luizdepra/hugo-coder/wiki", 0)))));
        when(content.post(anyString())).thenReturn(Optional.empty());
        when(content.post("math-with-mathjax")).thenReturn(Optional.of(MATH_POST));
        when(content.post("markdown-syntax")).thenReturn(Optional.of(PLAIN_POST));
        when(content.publications(any())).thenReturn(new Publications("type", List.of(
                new Publications.Group("Journal articles", "journal-articles", List.of(JOURNAL)),
                new Publications.Group("Conference papers", "conference-papers", List.of(CONFERENCE)))));
        when(content.publications(eq("year"))).thenReturn(new Publications("year", List.of(
                new Publications.Group("1844", "1844", List.of(CONFERENCE)),
                new Publications.Group("1843", "1843", List.of(JOURNAL)))));
    }

    @Test
    void siteAndHomeComeFromTheBackend() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Test Site</title>")))
                .andExpect(content().string(containsString("<a class=\"navigation-title\" href=\"/\">Test Site</a>")))
                .andExpect(content().string(containsString("<meta name=\"author\" content=\"Jane Roe\">")))
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"A site for the tests\">")))
                .andExpect(content().string(containsString("<meta name=\"keywords\" content=\"tests, examples\">")))
                .andExpect(content().string(containsString("<h1>Jane Roe</h1>")))
                .andExpect(content().string(containsString("<h2>Test Headline</h2>")))
                .andExpect(content().string(containsString("<img src=\"/images/icons/test.svg\" alt=\"avatar\">")))
                .andExpect(content().string(containsString("<i class=\"fa-brands fa-github icon\" aria-hidden=\"true\"></i>")))
                .andExpect(content().string(containsString("<p>Hello from <em>Markdown</em>.</p>")))
                .andExpect(content().string(containsString(
                        "<a href=\"https://spring.io/projects/spring-boot\" target=\"_blank\" rel=\"noopener\">Spring Boot</a>")))
                .andExpect(content().string(containsString(
                        "<a href=\"https://example.org/profile\" target=\"_blank\" rel=\"noopener\">")));
    }

    @Test
    void linksToOtherSitesStayInTheTabIfSwitchedOff() throws Exception {
        when(content.site()).thenReturn(new Site("T", "", null, null, false, null));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<a href=\"https://spring.io/projects/spring-boot\">Spring Boot</a>")))
                .andExpect(content().string(not(containsString("home-card"))))
                .andExpect(content().string(not(containsString("<h1>"))));
    }

    @Test
    void pagesWorkWithAndWithoutTrailingSlash() throws Exception {
        for (String p : new String[] {"/publications", "/publications/", "/posts", "/posts/", "/posts/page/2",
                "/posts/markdown-syntax", "/posts/markdown-syntax/"}) {
            mvc.perform(get(p)).andExpect(status().isOk());
        }
    }

    @Test
    void publicationsPage() throws Exception {
        mvc.perform(get("/publications/"))
                .andExpect(content().string(containsString("<title>Publications | Test Site</title>")))
                .andExpect(content().string(containsString("navigation-link active\" href=\"/publications/\"")))
                .andExpect(content().string(containsString("href=\"/posts/\">Blog")))
                .andExpect(content().string(containsString("href=\"https://example.org/notes\"")))
                .andExpect(content().string(containsString("<span>Ada Lovelace, Charles Babbage</span>")))
                .andExpect(content().string(containsString("<em>Sci. Mem. 3(1), 666–731</em>, 1843")))
                .andExpect(content().string(containsString("href=\"https://arxiv.org/abs/1843.00001\" class=\"external-link\" target=\"_blank\" rel=\"noopener\">arXiv</a>")))
                .andExpect(content().string(containsString(
                        "extended version of <a href=\"#lovelace44\" title=\"A paper without a link\">"
                        + "Example Conference 1844, A paper without a li…</a> and "
                        + "<a href=\"#lovelace42\" title=\"An early talk\">Example Workshop 1842, An early talk</a>")))
                .andExpect(content().string(containsString("extended in <a href=\"#dblp-journals-sm-lovelace43\" "
                        + "title=\"Notes on the Analytical Engine\">Sci. Mem. 1843</a>")))
                .andExpect(content().string(containsString("<li id=\"lovelace44\">")))
                .andExpect(content().string(containsString("<strong>A paper without a link</strong><br>")))
                .andExpect(content().string(containsString("<h2 id=\"journal-articles\">Journal articles</h2>")))
                .andExpect(content().string(containsString("<strong aria-current=\"true\">type</strong>")))
                .andExpect(content().string(containsString("<a href=\"/publications/?group=year\">year</a>")));
        mvc.perform(get("/publications/?group=year"))
                .andExpect(content().string(containsString("<h2 id=\"1844\">1844</h2>")))
                .andExpect(content().string(containsString("<strong aria-current=\"true\">year</strong>")))
                .andExpect(content().string(not(containsString("Journal articles"))));
    }

    @Test
    void publicationsWithoutEntriesAreComingSoon() throws Exception {
        when(content.publications(isNull())).thenReturn(new Publications("none", List.of()));
        mvc.perform(get("/publications/"))
                .andExpect(content().string(containsString("Coming soon.")))
                .andExpect(content().string(not(containsString("Group by"))));
    }

    @Test
    void postListIsPaginatedWithCommentCounts() throws Exception {
        mvc.perform(get("/posts/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Math with MathJax")))
                .andExpect(content().string(containsString("October 6, 2026")))
                .andExpect(content().string(containsString("href=\"/posts/markdown-syntax/\"")))
                .andExpect(content().string(containsString("<span class=\"comment-count\">2 comments</span>")))
                .andExpect(content().string(containsString("<span class=\"comment-count\"></span>")))
                .andExpect(content().string(containsString("<a href=\"/posts/page/2/\">2</a>")))
                .andExpect(content().string(not(containsString("Placeholder Text"))));
        mvc.perform(get("/posts/page/2/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Placeholder Text")))
                .andExpect(content().string(containsString("<span class=\"comment-count\">1 comment</span>")))
                .andExpect(content().string(containsString("href=\"https://github.com/luizdepra/hugo-coder/wiki\"")))
                .andExpect(content().string(containsString("<a href=\"/posts/\">&laquo;</a>")));
        mvc.perform(get("/posts/page/3/")).andExpect(status().isNotFound());
    }

    @Test
    void datesAreEnglishWhateverTheBrowserLanguage() throws Exception {
        mvc.perform(get("/posts/").header("Accept-Language", "de-DE,de;q=0.9"))
                .andExpect(content().string(containsString("October 6, 2026")));
    }

    @Test
    void postPageHasMetadataAndCommentSection() throws Exception {
        mvc.perform(get("/posts/markdown-syntax/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Markdown Syntax Guide | Test Site</title>")))
                .andExpect(content().string(containsString("<strong>strong text</strong>")))
                .andExpect(content().string(containsString("4-minute read")))
                .andExpect(content().string(containsString("id=\"comments\"")))
                .andExpect(content().string(containsString("data-slug=\"markdown-syntax\"")))
                .andExpect(content().string(containsString("<script src=\"/js/comments.js\"></script>")));
    }

    @Test
    void mathPostsLoadMathJaxFromThisServer() throws Exception {
        mvc.perform(get("/posts/math-with-mathjax/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/webjars/mathjax/3.2.2/es5/tex-svg.js")))
                .andExpect(content().string(containsString("\\[x = \\frac{-b")))
                .andExpect(content().string(containsString("<span class=\"tag\">math</span>")));
        mvc.perform(get("/webjars/mathjax/3.2.2/es5/tex-svg.js")).andExpect(status().isOk());
    }

    @Test
    void otherPostsDoNotLoadMathJax() throws Exception {
        mvc.perform(get("/posts/markdown-syntax/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("mathjax"))))
                .andExpect(content().string(not(containsString("math.css"))));
    }

    @Test
    void unknownPagesAre404() throws Exception {
        mvc.perform(get("/posts/nope/")).andExpect(status().isNotFound());
        for (String p : new String[] {"/about/", "/projects/", "/contact/"}) {
            mvc.perform(get(p)).andExpect(status().isNotFound());
        }
    }

    @Test
    void pagesAre503WhileTheBackendIsDown() throws Exception {
        when(content.site()).thenThrow(new ResourceAccessException("Connection refused"));
        when(content.post(anyString())).thenThrow(new ResourceAccessException("Connection refused"));
        when(content.posts(anyInt())).thenThrow(new ResourceAccessException("Connection refused"));
        when(content.publications(any())).thenThrow(new ResourceAccessException("Connection refused"));
        for (String p : new String[] {"/", "/posts/", "/posts/markdown-syntax/", "/publications/"}) {
            mvc.perform(get(p)).andExpect(status().isServiceUnavailable());
        }
    }

    @Test
    void apiIs503WhileTheBackendIsDown() throws Exception {
        // The test configuration points the proxy at a port where nothing listens.
        mvc.perform(get("/api/posts/markdown-syntax/comments")).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(content().string(containsString("temporarily unavailable")));
        mvc.perform(get("/images/me.jpg")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void servesThemeAssets() throws Exception {
        mvc.perform(get("/css/coder.min.css")).andExpect(status().isOk());
        mvc.perform(get("/js/comments.js")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(content().string(containsString("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/favicon.svg\"")));
    }
}
