package com.homepage;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagesTest {

    @Autowired MockMvc mvc;

    @Test
    void siteAndHomeComeFromTheConfigurationFile() throws Exception {
        // from src/test/resources/info/site.yaml
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Test Site</title>")))
                .andExpect(content().string(containsString("<a class=\"navigation-title\" href=\"/\">Test Site</a>")))
                .andExpect(content().string(containsString("<meta name=\"author\" content=\"Jane Roe\">")))
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"A site for the tests\">")))
                .andExpect(content().string(containsString("<meta name=\"keywords\" content=\"tests, examples\">")))
                .andExpect(content().string(containsString("<h1>Jane Roe</h1>")))
                .andExpect(content().string(containsString("<h2>Test Headline</h2>")))
                .andExpect(content().string(containsString("<img src=\"/images/icons/test.svg\" alt=\"avatar\">")))
                .andExpect(content().string(containsString("href=\"https://example.org/profile\"")))
                .andExpect(content().string(containsString("<i class=\"fa-brands fa-github icon\" aria-hidden=\"true\"></i>")))
                .andExpect(content().string(not(containsString("No URL"))))
                .andExpect(content().string(containsString("<p>Hello from <em>Markdown</em> with a "
                        + "<a target=\"_blank\" rel=\"noopener\" href=\"https://example.org/more\">link</a>.</p>")))
                .andExpect(content().string(containsString("<p>Second paragraph.</p>")))
                .andExpect(content().string(containsString(
                        "<a href=\"https://spring.io/projects/spring-boot\" target=\"_blank\" rel=\"noopener\">Spring Boot</a>")))
                .andExpect(content().string(containsString(
                        "<a href=\"https://example.org/profile\" target=\"_blank\" rel=\"noopener\">")));
        mvc.perform(get("/images/icons/test.svg")).andExpect(status().isOk());
        mvc.perform(get("/images/../application-test.properties")).andExpect(status().is4xxClientError());
    }

    @Test
    void staticPagesWorkWithAndWithoutTrailingSlash() throws Exception {
        for (String p : new String[] {"/publications", "/publications/", "/posts", "/posts/"}) {
            mvc.perform(get(p)).andExpect(status().isOk());
        }
    }

    @Test
    void menuHasHomeBlogAndPublications() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(containsString("navigation-link active\" href=\"/\">Home")));
        mvc.perform(get("/publications/"))
                .andExpect(content().string(containsString("<title>Publications | Test Site</title>")))
                .andExpect(content().string(containsString("navigation-link active\" href=\"/publications/\"")))
                .andExpect(content().string(containsString("href=\"/posts/\">Blog")))
                // from src/test/resources/publications-test.bib: the first entry is linked, the second has no link
                .andExpect(content().string(containsString("href=\"https://example.org/notes\"")))
                .andExpect(content().string(containsString("<span>Ada Lovelace, Charles Babbage</span>")))
                .andExpect(content().string(containsString("<em>Sci. Mem. 3(1), 666–731</em>, 1843")))
                .andExpect(content().string(containsString("href=\"https://arxiv.org/abs/1843.00001\" class=\"external-link\" target=\"_blank\" rel=\"noopener\">arXiv</a>")))
                .andExpect(content().string(not(containsString("arXiv:1843.00001"))))
                .andExpect(content().string(containsString(
                        "extended version of <a href=\"#lovelace44\" title=\"A paper without a link\">"
                        + "Proceedings of the Example Conference 1844, A paper without a li…</a> and "
                        + "<a href=\"#lovelace42\" title=\"An early talk\">Example Workshop 1842, An early talk</a>")))
                .andExpect(content().string(containsString("extended in <a href=\"#dblp-journals-sm-lovelace43\" "
                        + "title=\"Notes on the Analytical Engine\">Sci. Mem. 1843</a>")))
                .andExpect(content().string(containsString("<li id=\"lovelace44\">")))
                .andExpect(content().string(containsString("<strong>A paper without a link</strong><br>")))
                .andExpect(content().string(containsString(
                        "<em>Proceedings of the Example Conference, Lect. Notes Comput. Sci. 1, 1–2</em>, 1844")));
        // grouped by type as set in the test site.yaml; ?group= switches, an unknown value is ignored
        mvc.perform(get("/publications/"))
                .andExpect(content().string(containsString("<h2 id=\"journal-articles\">Journal articles</h2>")))
                .andExpect(content().string(containsString("<h2 id=\"conference-papers\">Conference papers</h2>")))
                .andExpect(content().string(containsString("<strong aria-current=\"true\">type</strong>")))
                .andExpect(content().string(containsString("<a href=\"/publications/?group=year\">year</a>")));
        mvc.perform(get("/publications/?group=year"))
                .andExpect(content().string(containsString("<h2 id=\"1844\">1844</h2>")))
                .andExpect(content().string(not(containsString("Journal articles"))));
        mvc.perform(get("/publications/?group=none")).andExpect(content().string(not(containsString("<h2"))));
        mvc.perform(get("/publications/?group=nonsense"))
                .andExpect(content().string(containsString("<h2 id=\"journal-articles\">")));
        for (String p : new String[] {"/about/", "/projects/", "/contact/"}) {
            mvc.perform(get(p)).andExpect(status().isNotFound());
        }
    }

    @Test
    void postListIsPaginatedNewestFirst() throws Exception {
        mvc.perform(get("/posts/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Math with MathJax")))
                .andExpect(content().string(containsString("October 6, 2026")))
                .andExpect(content().string(not(containsString("Placeholder Text"))));
        mvc.perform(get("/posts/page/2/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Placeholder Text")))
                .andExpect(content().string(containsString("https://github.com/luizdepra/hugo-coder/wiki")));
        mvc.perform(get("/posts/page/3/")).andExpect(status().isNotFound());
    }

    @Test
    void datesAreEnglishWhateverTheBrowserLanguage() throws Exception {
        mvc.perform(get("/posts/").header("Accept-Language", "de-DE,de;q=0.9"))
                .andExpect(content().string(containsString("October 6, 2026")));
    }

    @Test
    void postPageHasCommentSection() throws Exception {
        mvc.perform(get("/posts/markdown-syntax/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"comments\"")))
                .andExpect(content().string(containsString("data-slug=\"markdown-syntax\"")))
                .andExpect(content().string(containsString("Markdown Syntax Guide")));
    }

    @Test
    void mathPostsLoadMathJaxFromThisServer() throws Exception {
        mvc.perform(get("/posts/math-with-mathjax/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/webjars/mathjax/3.2.2/es5/tex-svg.js")))
                .andExpect(content().string(containsString("\\[x = \\frac{-b")));
        mvc.perform(get("/posts/math-typesetting/")).andExpect(content().string(containsString("tex-svg.js")));
        mvc.perform(get("/webjars/mathjax/3.2.2/es5/tex-svg.js")).andExpect(status().isOk());
    }

    @Test
    void markdownPostIsRenderedWithFormulasAndMetadata() throws Exception {
        mvc.perform(get("/posts/markdown-with-latex/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Writing posts in Markdown, with LaTeX")))
                .andExpect(content().string(containsString("<strong>strong text</strong>")))
                .andExpect(content().string(containsString("\\(x_i^2 + y_i^2 = r_i^2\\)")))
                .andExpect(content().string(containsString("\\begin{align}")))
                .andExpect(content().string(containsString("costs $5 and the shipping $10")))
                .andExpect(content().string(containsString("tex-svg.js")));
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
        mvc.perform(get("/posts/hugo-coder-wiki/")).andExpect(status().isNotFound());
    }

    @Test
    void servesThemeAssets() throws Exception {
        mvc.perform(get("/css/coder.min.css")).andExpect(status().isOk());
        mvc.perform(get("/js/comments.js")).andExpect(status().isOk());
        mvc.perform(get("/images/favicon.svg")).andExpect(status().isOk());
    }
}
