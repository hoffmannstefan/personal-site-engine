package com.homepage.post;

import com.homepage.site.Site;
import com.homepage.site.SiteProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serves the blog posts, which are the Markdown files with YAML front matter ({@code *.md}) in the folder
 * {@code site.posts-dir}, read once at startup. Without the folder the blog is empty.
 * The slug of a post is its file name without {@code .md}. A file with a {@code link} in its front matter is an
 * entry that points to another site instead of having a page of its own. A file with a broken header is skipped
 * with a warning instead of keeping the whole site from starting.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    /** Words per minute used for the "N-minute read" label; the value Hugo uses. */
    private static final int WORDS_PER_MINUTE = 212;

    private final Map<String, Post> bySlug = new LinkedHashMap<>();
    private final MarkdownRenderer markdown;
    private final String defaultAuthor;
    private final boolean externalLinksInNewTab;

    public PostService(MarkdownRenderer markdown, SiteProperties properties, Site site) {
        this.markdown = markdown;
        this.defaultAuthor = site.author();
        this.externalLinksInNewTab = site.externalLinksInNewTab();

        loadMarkdownFolder(properties.postsDir()).stream()
                .sorted(Comparator.comparing(Post::date).reversed().thenComparing(Post::title))
                .forEach(p -> bySlug.put(p.slug(), p));
    }

    /** All posts, newest first. */
    public List<Post> all() {
        return List.copyOf(bySlug.values());
    }

    /** A readable post; external-link entries have no page of their own. */
    public Optional<Post> find(String slug) {
        return Optional.ofNullable(bySlug.get(slug)).filter(p -> p.externalUrl() == null);
    }

    public boolean exists(String slug) {
        return find(slug).isPresent();
    }

    private List<Post> loadMarkdownFolder(String dir) {
        if (dir == null || dir.isBlank() || !Files.isDirectory(Path.of(dir))) {
            log.info("No posts folder at {}; the blog is empty", dir);
            return List.of();
        }
        List<Post> posts = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of(dir))) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".md")).sorted().toList()) {
                fromMarkdown(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)).ifPresent(posts::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Markdown posts from " + dir, e);
        }
        log.info("Loaded {} Markdown post(s) from {}", posts.size(), dir);
        return posts;
    }

    private Optional<Post> fromMarkdown(String filename, String source) {
        String slug = filename.substring(0, filename.length() - ".md".length());
        if (!SLUG.matcher(slug).matches()) {
            log.warn("Skipping {}: the file name must be lower case letters, digits and hyphens (it becomes the URL)", filename);
            return Optional.empty();
        }
        MarkdownRenderer.Rendered r = markdown.render(source.replace("\r\n", "\n"));
        Map<String, List<String>> front = r.frontMatter();
        if ("true".equalsIgnoreCase(first(front, "draft"))) {
            return Optional.empty();
        }
        String title = first(front, "title");
        if (title == null || title.isBlank()) {
            log.warn("Skipping {}: the front matter needs a 'title'", filename);
            return Optional.empty();
        }
        LocalDate date;
        try {
            date = LocalDate.parse(String.valueOf(first(front, "date")));
        } catch (DateTimeParseException e) {
            log.warn("Skipping {}: the front matter needs a 'date' like 2026-10-06", filename);
            return Optional.empty();
        }
        List<String> authors = front.getOrDefault("authors", front.getOrDefault("author", List.of()));
        int minutes = Math.max(1, (r.words() + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE);
        return Optional.of(new Post(slug, title, date, minutes + "-minute read",
                authors.isEmpty() && !defaultAuthor.isEmpty() ? List.of(defaultAuthor) : authors,
                front.getOrDefault("categories", List.of()), front.getOrDefault("tags", List.of()),
                externalLinksInNewTab ? MarkdownRenderer.externalLinksInNewTab(r.html()) : r.html(),
                first(front, "link"), r.math()));
    }

    private static String first(Map<String, List<String>> front, String key) {
        List<String> values = front.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
