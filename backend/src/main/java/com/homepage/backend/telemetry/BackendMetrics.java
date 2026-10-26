package com.homepage.backend.telemetry;

import com.homepage.backend.comment.CommentRepository;
import com.homepage.backend.post.PostService;
import com.homepage.backend.publication.PublicationService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The backend's own metrics, next to the HTTP, JDBC and JVM metrics of the OpenTelemetry starter: what happens to
 * comments, and what content was loaded at startup. Like all telemetry they carry the service name, environment and
 * version, so they can be filtered by them. With the SDK switched off they cost nothing.
 *
 * <ul>
 *   <li>{@code homepage.comment.submissions} (counter): comments submitted, by {@code outcome} and {@code post.slug}
 *   <li>{@code homepage.comment.deletions} (counter): delete requests, by {@code outcome}
 *   <li>{@code homepage.comment.stored} (gauge): comments in the database
 *   <li>{@code homepage.content.items} (gauge): posts and publications loaded, by {@code kind}
 *   <li>{@code homepage.content.skipped} (gauge): Markdown files skipped because of a mistake, by {@code kind}
 * </ul>
 */
@Component
public class BackendMetrics {

    /** What became of a submitted comment. */
    public enum Submission {
        /** Stored and shown. */
        ACCEPTED,
        /** The honeypot was filled in: answered like a success, but dropped. */
        SPAM,
        /** Over the per-address limit. */
        RATE_LIMITED,
        /** A reply to a comment that does not exist or belongs to another post. */
        UNKNOWN_PARENT
    }

    /** What became of a request to delete a comment. */
    public enum Deletion {
        DELETED,
        /** No or a wrong admin token, or deleting is switched off. */
        FORBIDDEN,
        NOT_FOUND
    }

    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");
    private static final AttributeKey<String> POST = AttributeKey.stringKey("post.slug");
    private static final AttributeKey<String> KIND = AttributeKey.stringKey("kind");

    private final LongCounter submissions;
    private final LongCounter deletions;
    private final List<ObservableLongGauge> gauges;

    BackendMetrics(OpenTelemetry openTelemetry, PostService posts, PublicationService publications,
                   CommentRepository comments) {
        Meter meter = openTelemetry.getMeter("com.homepage.backend");
        submissions = meter.counterBuilder("homepage.comment.submissions").setUnit("{comment}")
                .setDescription("Comments submitted, by outcome and post").build();
        deletions = meter.counterBuilder("homepage.comment.deletions").setUnit("{request}")
                .setDescription("Requests to delete a comment, by outcome").build();
        gauges = List.of(
                meter.gaugeBuilder("homepage.comment.stored").ofLongs().setUnit("{comment}")
                        .setDescription("Comments in the database")
                        .buildWithCallback(m -> m.record(comments.count())),
                meter.gaugeBuilder("homepage.content.items").ofLongs().setUnit("{item}")
                        .setDescription("Posts and publications loaded at startup")
                        .buildWithCallback(m -> {
                            m.record(posts.all().size(), Attributes.of(KIND, "post"));
                            m.record(publications.all().size(), Attributes.of(KIND, "publication"));
                        }),
                meter.gaugeBuilder("homepage.content.skipped").ofLongs().setUnit("{file}")
                        .setDescription("Content files skipped at startup because of a mistake")
                        .buildWithCallback(m -> m.record(posts.skipped(), Attributes.of(KIND, "post"))));
    }

    public void commentSubmitted(String slug, Submission outcome) {
        submissions.add(1, Attributes.of(POST, slug, OUTCOME, key(outcome)));
    }

    public void commentDeletion(Deletion outcome) {
        deletions.add(1, Attributes.of(OUTCOME, key(outcome)));
    }

    /** The gauges read the database and the services, so they stop before those do. */
    @PreDestroy
    void close() {
        gauges.forEach(ObservableLongGauge::close);
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
