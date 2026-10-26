package com.homepage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homepage.backend.comment.CommentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentApiTest {

    private static final String URL = "/api/posts/markdown-syntax/comments";

    @Autowired MockMvc mvc;
    @Autowired CommentRepository repository;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private long create(String body) throws Exception {
        String res = mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        return node.get("id").asLong();
    }

    @Test
    void createsAndListsComments() throws Exception {
        create("{\"author\":\"  Ada \",\"body\":\"Hello <b>world</b>\"}");

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].author", is("Ada")))
                // stored verbatim; the client renders it as text, never as HTML
                .andExpect(jsonPath("$[0].body", is("Hello <b>world</b>")))
                .andExpect(jsonPath("$[0].parentId", nullValue()));
    }

    @Test
    void repliesToRepliesAttachToTheTopLevelComment() throws Exception {
        long top = create("{\"author\":\"A\",\"body\":\"top\"}");
        long reply = create("{\"author\":\"B\",\"body\":\"reply\",\"parentId\":" + top + "}");
        create("{\"author\":\"C\",\"body\":\"nested\",\"parentId\":" + reply + "}");

        mvc.perform(get(URL))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].parentId", is((int) top)))
                .andExpect(jsonPath("$[2].parentId", is((int) top)));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"author\":\"\",\"body\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"author\":\"A\",\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"A\",\"body\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"A\",\"body\":\"x\",\"parentId\":999999}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unknownPostIs404() throws Exception {
        mvc.perform(get("/api/posts/nope/comments")).andExpect(status().isNotFound());
        mvc.perform(post("/api/posts/nope/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"A\",\"body\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void honeypotSubmissionsAreDropped() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Bot\",\"body\":\"buy now\",\"website\":\"http://spam\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get(URL)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteNeedsAdminTokenAndRemovesReplies() throws Exception {
        long top = create("{\"author\":\"A\",\"body\":\"top\"}");
        create("{\"author\":\"B\",\"body\":\"reply\",\"parentId\":" + top + "}");

        mvc.perform(delete("/api/comments/" + top)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + top).header("X-Admin-Token", "wrong")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + top).header("X-Admin-Token", "secret")).andExpect(status().isNoContent());
        mvc.perform(get(URL)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/comments/" + top).header("X-Admin-Token", "secret")).andExpect(status().isNotFound());
    }
}
