package com.homepage.backend.site;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.homepage.backend.post.MarkdownRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reads {@code info/site.yaml} into {@link Site} once at startup: fills in defaults, reports typos, renders the
 * Markdown text and turns image paths into URLs. The file's path is {@link SiteProperties#configFile()}.
 *
 * <p>See {@code info/site.yaml} for all keys. Every key is optional. Image paths are relative to
 * {@code site.images-dir} (served under {@code /images/}); a value starting with {@code /} or {@code http} is used
 * as it is. Unknown keys are reported in the log and ignored. Without the file, or with a broken one, the defaults
 * apply, so a typo does not keep the site from starting.
 */
@Configuration
public class SiteLoader {

    private static final Logger log = LoggerFactory.getLogger(SiteLoader.class);
    private static final int DEFAULT_POSTS_PER_PAGE = 6;
    /** Characters of the title in "extended version of DLT 2023, Completely Distinguisha…". */
    private static final int DEFAULT_TITLE_LENGTH = 20;

    /** The file as written; every field may be missing. */
    record Yaml(String title, String author, String description, String keywords, Integer postsPerPage,
                Boolean externalLinksInNewTab, HomeYaml home, PublicationsYaml publications) {}

    record HomeYaml(String headline, String avatar, List<LinkYaml> links, String text) {}

    record LinkYaml(String name, String url, String icon, String iconClass) {}

    record PublicationsYaml(String arxivPapers, Boolean arxivLinks, String groupBy, String authors,
                            Boolean extendedVersionLinks, Integer extendedVersionTitleLength) {}

    @Bean
    Site site(SiteProperties properties, MarkdownRenderer markdown) {
        return load(properties.configFile(), markdown);
    }

    public static Site load(String file, MarkdownRenderer markdown) {
        return toSite(read(file), markdown);
    }

    private static Yaml read(String file) {
        if (file == null || file.isBlank() || !Files.isRegularFile(Path.of(file))) {
            log.warn("No site configuration at {}; the site has no name and an empty start page", file);
            return null;
        }
        YAMLMapper mapper = YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .addHandler(new DeserializationProblemHandler() {
                    @Override
                    public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                            JsonDeserializer<?> deserializer, Object bean, String key) throws IOException {
                        log.warn("Ignoring the unknown key '{}' in {} (line {})", key, file,
                                p.currentTokenLocation().getLineNr());
                        p.skipChildren();
                        return true;
                    }
                })
                .build();
        try {
            Yaml yaml = mapper.readValue(Path.of(file).toFile(), Yaml.class);
            log.info("Read the site configuration from {}", file);
            return yaml;
        } catch (IOException e) {
            log.warn("Cannot read the site configuration from {}; using the defaults: {}", file, e.getMessage());
            return null;
        }
    }

    static Site toSite(Yaml yaml, MarkdownRenderer markdown) {
        Yaml y = yaml != null ? yaml : new Yaml(null, null, null, null, null, null, null, null);
        String author = blank(y.author()) ? "" : y.author().strip();
        String title = !blank(y.title()) ? y.title().strip() : !author.isEmpty() ? author : "Homepage";
        int perPage = y.postsPerPage() != null && y.postsPerPage() > 0 ? y.postsPerPage() : DEFAULT_POSTS_PER_PAGE;
        boolean newTab = !Boolean.FALSE.equals(y.externalLinksInNewTab());
        return new Site(title, author, orNull(y.description()), orNull(y.keywords()), perPage, newTab,
                toHome(y.home(), markdown, newTab), toPublications(y.publications()));
    }

    /**
     * Without the keys: arXiv versions only for papers that are not published, links to the preprints, no grouping,
     * all authors, "extended version of"/"extended in" notes with 20 characters of the title. An unknown value is
     * reported in the log and the default applies.
     */
    private static Site.Publications toPublications(PublicationsYaml p) {
        PublicationsYaml y = p != null ? p : new PublicationsYaml(null, null, null, null, null, null);
        Integer titleLength = y.extendedVersionTitleLength();
        if (titleLength != null && titleLength < 0) {
            log.warn("publications.extended-version-title-length must not be negative; using {}", DEFAULT_TITLE_LENGTH);
            titleLength = null;
        }
        return new Site.Publications(
                choice("arxiv-papers", y.arxivPapers(), Site.Publications.ArxivPapers::parse,
                        Site.Publications.ArxivPapers.UNPUBLISHED, "always, unpublished or never"),
                !Boolean.FALSE.equals(y.arxivLinks()),
                choice("group-by", y.groupBy(), Site.Publications.GroupBy::parse,
                        Site.Publications.GroupBy.NONE, "year, type or none"),
                choice("authors", y.authors(), Site.Publications.Authors::parse,
                        Site.Publications.Authors.ALL, "all or coauthors"),
                !Boolean.FALSE.equals(y.extendedVersionLinks()),
                titleLength == null ? DEFAULT_TITLE_LENGTH : titleLength);
    }

    private static <E> E choice(String key, String value, Function<String, Optional<E>> parse, E fallback, String allowed) {
        if (blank(value)) {
            return fallback;
        }
        return parse.apply(value).orElseGet(() -> {
            log.warn("Unknown publications.{} '{}' ({}); using {}", key, value, allowed, fallback.toString().toLowerCase(Locale.ROOT));
            return fallback;
        });
    }

    private static Site.Home toHome(HomeYaml home, MarkdownRenderer markdown, boolean newTab) {
        if (home == null) {
            return new Site.Home(null, null, List.of(), "");
        }
        List<Site.Link> links = home.links() == null ? List.of() : home.links().stream()
                .filter(Objects::nonNull)
                .filter(l -> !blank(l.name()) && !blank(l.url()))
                .map(l -> new Site.Link(l.name(), l.url(), imageUrl(l.icon()), orNull(l.iconClass())))
                .toList();
        String html = blank(home.text()) ? "" : markdown.render(home.text()).html();
        if (newTab) {
            html = MarkdownRenderer.externalLinksInNewTab(html);
        }
        return new Site.Home(orNull(home.headline()), imageUrl(home.avatar()), links, html);
    }

    private static String imageUrl(String path) {
        if (blank(path)) {
            return null;
        }
        return path.startsWith("/") || path.startsWith("http://") || path.startsWith("https://") ? path : "/images/" + path;
    }

    private static String orNull(String s) {
        return blank(s) ? null : s.strip();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}