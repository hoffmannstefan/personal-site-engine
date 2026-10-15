package com.homepage.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request and response shapes of the comments API. */
public final class CommentDto {

    private CommentDto() {
    }

    public record Create(
            @NotBlank @Size(max = Comment.MAX_AUTHOR) String author,
            @NotBlank @Size(max = Comment.MAX_BODY) String body,
            Long parentId,
            /** Honeypot: hidden in the form, so only bots fill it in. */
            String website) {
    }

    public record View(Long id, Long parentId, String author, String body, Instant createdAt) {

        static View of(Comment c) {
            return new View(c.getId(), c.getParentId(), c.getAuthor(), c.getBody(), c.getCreatedAt());
        }
    }
}
