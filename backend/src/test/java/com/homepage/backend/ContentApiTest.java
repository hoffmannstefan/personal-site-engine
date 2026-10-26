package com.homepage.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homepage.backend.comment.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The content API the frontend renders the pages from, with the test content in src/test/resources. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentApiTest {

    @Autowired MockMvc mvc;
    @Autowired CommentRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void siteComesFromTheConfigurationFile() throws Exception {
        // from src/test/resources/info/site.yaml
        mvc.perform(get("/api/site")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Test Site")))
                .andExpect(jsonPath("$.author", is("Jane Roe")))
                .andExpect(jsonPath("$.description", is("A site for the tests")))
                .andExpect(jsonPath("$.keywords", is("tests, examples")))
                .andExpect(jsonPath("$.externalLinksInNewTab", is(true)))
                .andExpect(jsonPath("$.home.headline", is("Test Headline")))
                .andExpect(jsonPath("$.home.avatar", is("/images/icons/test.svg")))
                .andExpect(jsonPath("$.home.links", hasSize(2)))
                .andExpect(jsonPath("$.home.links[0].url", is("https://example.org/profile")))
                .andExpect(jsonPath("$.home.links[1].iconClass", is("fa-brands fa-github")))
                .andExpect(jsonPath("$.home.html", containsString("<p>Hello from <em>Markdown</em> with a "
                        + "<a target=\"_blank\" rel=\"noopener\" href=\"https://example.org/more\">link</a>.</p>")))
                .andExpect(jsonPath("$.home.html", containsString("<p>Second paragraph.</p>")));
    }

    @Test
    void servesTheImagesFolder() throws Exception {
        mvc.perform(get("/images/icons/test.svg")).andExpect(status().isOk());
        mvc.perform(get("/images/../application-test.properties")).andExpect(status().is4xxClientError());
        mvc.perform(get("/images/nope.png")).andExpect(status().isNotFound());
    }

    @Test
    void postListIsPaginatedNewestFirst() throws Exception {
        mvc.perform(get("/api/posts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.pages", is(2)))
                .andExpect(jsonPath("$.posts", hasSize(6)))
                .andExpect(jsonPath("$.posts[0].title", is("Math with MathJax")))
                .andExpect(jsonPath("$.posts[0].date", is("2026-10-06")))
                .andExpect(jsonPath("$.posts[0].body").doesNotExist())
                .andExpect(jsonPath("$.posts[*].title", not(hasItem("Placeholder Text"))));
        mvc.perform(get("/api/posts?page=2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.posts[*].title", hasItem("Placeholder Text")))
                .andExpect(jsonPath("$.posts[*].externalUrl", hasItem("https://github.com/luizdepra/hugo-coder/wiki")));
        mvc.perform(get("/api/posts?page=3")).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts?page=0")).andExpect(status().isNotFound());
    }

    @Test
    void postListCountsTheComments() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/posts/markdown-syntax/comments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"author\":\"A\",\"body\":\"x\"}")).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/posts?page=1"))
                .andExpect(jsonPath("$.posts[?(@.slug == 'markdown-syntax')].comments", hasItem(2)))
                .andExpect(jsonPath("$.posts[?(@.slug == 'rich-content')].comments", hasItem(0)));
    }

    @Test
    void markdownPostIsRenderedWithFormulasAndMetadata() throws Exception {
        mvc.perform(get("/api/posts/markdown-with-latex")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Writing posts in Markdown, with LaTeX")))
                .andExpect(jsonPath("$.date", is("2026-10-06")))
                .andExpect(jsonPath("$.authors[0]", is("John Doe")))
                .andExpect(jsonPath("$.math", is(true)))
                .andExpect(jsonPath("$.body", containsString("<strong>strong text</strong>")))
                .andExpect(jsonPath("$.body", containsString("\\(x_i^2 + y_i^2 = r_i^2\\)")))
                .andExpect(jsonPath("$.body", containsString("\\begin{align}")))
                .andExpect(jsonPath("$.body", containsString("costs $5 and the shipping $10")));
        mvc.perform(get("/api/posts/markdown-syntax"))
                .andExpect(jsonPath("$.math", is(false)))
                .andExpect(jsonPath("$.externalUrl", nullValue()));
    }

    @Test
    void unknownPostsAndExternalEntriesAre404() throws Exception {
        mvc.perform(get("/api/posts/nope")).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/hugo-coder-wiki")).andExpect(status().isNotFound());
    }

    @Test
    void publicationsAreGroupedAsConfiguredOrAsAsked() throws Exception {
        // from src/test/resources/publications-test.bib, grouped by type as set in the test site.yaml
        mvc.perform(get("/api/publications")).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy", is("type")))
                .andExpect(jsonPath("$.groups[0].heading", is("Journal articles")))
                .andExpect(jsonPath("$.groups[0].id", is("journal-articles")))
                .andExpect(jsonPath("$.groups[1].heading", is("Conference papers")))
                .andExpect(jsonPath("$.groups[0].entries[0].id", is("dblp-journals-sm-lovelace43")))
                .andExpect(jsonPath("$.groups[0].entries[0].link", is("https://example.org/notes")))
                .andExpect(jsonPath("$.groups[0].entries[0].authors", is("Ada Lovelace, Charles Babbage")))
                .andExpect(jsonPath("$.groups[0].entries[0].venue", is("Sci. Mem. 3(1), 666–731")))
                .andExpect(jsonPath("$.groups[0].entries[0].preprint", is("https://arxiv.org/abs/1843.00001")))
                .andExpect(jsonPath("$.groups[0].entries[0].extensionOf[0].text",
                        is("Proceedings of the Example Conference 1844, A paper without a li…")))
                .andExpect(jsonPath("$.groups[0].entries[0].extensionOf[1].id", is("lovelace42")));
        mvc.perform(get("/api/publications?group=year"))
                .andExpect(jsonPath("$.groupBy", is("year")))
                .andExpect(jsonPath("$.groups[*].heading", hasItem("1844")));
        mvc.perform(get("/api/publications?group=none"))
                .andExpect(jsonPath("$.groups", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].heading", nullValue()));
        mvc.perform(get("/api/publications?group=nonsense")).andExpect(jsonPath("$.groupBy", is("type")));
    }
}
