package com.homepage.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A reader comment on a post. Threads are one level deep: a reply always points
 * at a top-level comment.
 */
@Entity
@Table(name = "comments", indexes = @Index(name = "idx_comments_post", columnList = "postSlug, createdAt"))
public class Comment {

    public static final int MAX_AUTHOR = 50;
    public static final int MAX_BODY = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String postSlug;

    private Long parentId;

    @Column(nullable = false, length = MAX_AUTHOR)
    private String author;

    @Column(nullable = false, length = MAX_BODY)
    private String body;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Comment() {
    }

    public Comment(String postSlug, Long parentId, String author, String body) {
        this.postSlug = postSlug;
        this.parentId = parentId;
        this.author = author;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public String getPostSlug() {
        return postSlug;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
