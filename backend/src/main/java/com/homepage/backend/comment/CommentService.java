package com.homepage.backend.comment;

import com.homepage.backend.post.PostService;
import com.homepage.backend.site.SiteProperties;
import com.homepage.backend.telemetry.BackendMetrics;
import com.homepage.backend.telemetry.BackendMetrics.Deletion;
import com.homepage.backend.telemetry.BackendMetrics.Submission;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommentService {

    private final CommentRepository comments;
    private final PostService posts;
    private final RateLimiter rateLimiter;
    private final BackendMetrics metrics;
    private final String adminToken;

    public CommentService(CommentRepository comments, PostService posts, SiteProperties site, RateLimiter rateLimiter,
                          BackendMetrics metrics) {
        this.comments = comments;
        this.posts = posts;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.adminToken = site.comments().adminToken();
    }

    @Transactional(readOnly = true)
    public List<CommentDto.View> list(String slug) {
        requirePost(slug);
        return comments.findByPostSlugOrderByCreatedAtAscIdAsc(slug).stream().map(CommentDto.View::of).toList();
    }

    @Transactional
    public CommentDto.View create(String slug, CommentDto.Create request, String clientKey) {
        requirePost(slug);
        String author = request.author().strip();
        String body = request.body().strip();
        if (request.website() != null && !request.website().isBlank()) {
            // Honeypot tripped. Answer like a success so bots learn nothing.
            metrics.commentSubmitted(slug, Submission.SPAM);
            return new CommentDto.View(null, request.parentId(), author, body, Instant.now());
        }
        if (!rateLimiter.tryAcquire(clientKey)) {
            metrics.commentSubmitted(slug, Submission.RATE_LIMITED);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You are commenting too fast. Please wait a moment.");
        }
        Long parentId = resolveParent(slug, request.parentId());
        CommentDto.View created = CommentDto.View.of(comments.save(new Comment(slug, parentId, author, body)));
        metrics.commentSubmitted(slug, Submission.ACCEPTED);
        return created;
    }

    @Transactional
    public void delete(Long id, String token) {
        if (adminToken == null || adminToken.isBlank() || token == null || !constantTimeEquals(adminToken, token)) {
            metrics.commentDeletion(Deletion.FORBIDDEN);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        Comment comment = comments.findById(id).orElseThrow(() -> {
            metrics.commentDeletion(Deletion.NOT_FOUND);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        });
        comments.deleteByParentId(comment.getId()); // replies go with their parent
        comments.delete(comment);
        metrics.commentDeletion(Deletion.DELETED);
    }

    /** Number of comments per post slug. */
    @Transactional(readOnly = true)
    public Map<String, Long> countsByPost() {
        return comments.countByPost().stream()
                .collect(Collectors.toMap(CommentRepository.CommentCount::getPostSlug,
                        CommentRepository.CommentCount::getTotal));
    }

    /** Replies to replies are attached to the top-level comment, keeping threads one level deep. */
    private Long resolveParent(String slug, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = comments.findById(parentId)
                .filter(c -> c.getPostSlug().equals(slug))
                .orElseThrow(() -> {
                    metrics.commentSubmitted(slug, Submission.UNKNOWN_PARENT);
                    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown comment to reply to");
                });
        return parent.getParentId() != null ? parent.getParentId() : parent.getId();
    }

    private void requirePost(String slug) {
        if (!posts.exists(slug)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
