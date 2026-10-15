package com.homepage.publication;

import com.homepage.site.Site;
import com.homepage.site.SiteProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The list on the Publications page, read once at startup from the BibTeX file {@code site.publications-file}
 * (e.g. the export of the DBLP page). Without the file the list is empty.
 *
 * <p>A journal article that extends a conference paper names it in {@code related = {DBLP:conf/dlt/000123}} (several
 * keys separated by commas). With {@link Site.Publications#extendedVersionLinks()} its entry gets the note
 * "extended version of DLT 2023, Completely Distinguisha…" (the start of the title, as long as
 * {@link Site.Publications#extendedVersionTitleLength()}), linked to that entry on the page, and that entry gets
 * "extended in Theory Comput. Syst. 2026" back. Both links show the full title as a tooltip. Each entry's anchor is
 * made from its key.
 *
 * <p>The authors are shown in full or, with {@link Site.Publications.Authors#COAUTHORS}, without the site's author
 * as "(with Jane Roe, John Doe)". Names are compared ignoring case and the number DBLP adds to tell apart people of
 * the same name ("Stefan Hoffmann 0001"); that number is never shown.
 *
 * <p>The page shows the entries newest first, grouped by year, by {@link Publication.Type} or not at all
 * ({@link #grouped}); entries of the same year keep the order of the file.
 *
 * <p>A published paper names its arXiv preprint with {@code eprint = {2103.09587}} and
 * {@code eprinttype = {arXiv}} (or {@code archivePrefix}); with {@link Site.Publications#arxivLinks()} its entry
 * links to it. Only these fields make a link, so a journal version with the title of the conference version links
 * nowhere unless it names a preprint itself.
 *
 * <p>An arXiv version (journal "CoRR" or "arXiv…", or an arXiv DOI) counts as published if a published entry names
 * it as its eprint or has the same title (compared ignoring case, spaces and punctuation).
 * {@link Site.Publications#arxivPapers()} decides which arXiv versions get an entry of their own: all, only those
 * that are not published, or none.
 */
@Service
public class PublicationService {

    private static final Logger log = LoggerFactory.getLogger(PublicationService.class);
    private static final Pattern ARXIV_DOI = Pattern.compile("10\\.48550/arxiv\\.(.+)", Pattern.CASE_INSENSITIVE);
    /** A venue's acronym with the year, as in "Developments in Language Theory - 27th ..., DLT 2023, Umeå". */
    private static final Pattern ACRONYM_YEAR =
            Pattern.compile("\\b([A-Z][A-Z0-9]*[A-Z](?:-[A-Z0-9]+)?) ((?:19|20)\\d{2})\\b");
    /** The volume and number after a journal's name: "Theory Comput. Syst. 70(2)". */
    private static final Pattern JOURNAL_VOLUME = Pattern.compile("\\s+\\d[\\w-]*(\\(.*\\))?$");
    /** DBLP tells apart people of the same name by a number: "Stefan Hoffmann 0001". */
    private static final Pattern DBLP_NUMBER = Pattern.compile("\\s+\\d{4}$");
    private static final Pattern ARXIV_URL = Pattern.compile("arxiv\\.org/(?:abs|pdf)/([^?#]+?)(?:\\.pdf)?$");

    /** Newest first; a stable sort, so entries of the same year keep the order of the file. Undated ones last. */
    private static final Comparator<Publication> NEWEST_FIRST =
            Comparator.comparing(Publication::year, Comparator.nullsLast(Comparator.reverseOrder()));

    private final List<Publication> all;
    private final Map<Site.Publications.GroupBy, List<Publication.Group>> grouped =
            new EnumMap<>(Site.Publications.GroupBy.class);

    public PublicationService(SiteProperties properties, Site site) {
        this.all = load(properties.publicationsFile(), site.publications(), site.author());
        for (Site.Publications.GroupBy by : Site.Publications.GroupBy.values()) {
            grouped.put(by, group(all, by));
        }
    }

    /** All entries in the order of the file. */
    public List<Publication> all() {
        return all;
    }

    /** The entries for the page: groups in order (newest year first, or by type), newest first within each. */
    public List<Publication.Group> grouped(Site.Publications.GroupBy by) {
        return grouped.get(by);
    }

    static List<Publication.Group> group(List<Publication> publications, Site.Publications.GroupBy by) {
        List<Publication> newestFirst = publications.stream().sorted(NEWEST_FIRST).toList();
        return switch (by) {
            case NONE -> newestFirst.isEmpty() ? List.of() : List.of(new Publication.Group(null, null, newestFirst));
            case YEAR -> groups(newestFirst, p -> p.year() == null ? "Undated" : p.year().toString());
            case TYPE -> groups(newestFirst.stream().sorted(Comparator.comparing(Publication::type)).toList(),
                    p -> p.type().heading());
        };
    }

    /** Consecutive runs of the same heading; the list is already in the order of the groups. */
    private static List<Publication.Group> groups(List<Publication> sorted, Function<Publication, String> heading) {
        Map<String, List<Publication>> byHeading = sorted.stream()
                .collect(Collectors.groupingBy(heading, LinkedHashMap::new, Collectors.toList()));
        return byHeading.entrySet().stream()
                .map(g -> new Publication.Group(g.getKey(), g.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"),
                        g.getValue()))
                .toList();
    }

    private static List<Publication> load(String file, Site.Publications options, String me) {
        if (file == null || file.isBlank() || !Files.isRegularFile(Path.of(file))) {
            log.info("No publications file at {}; the Publications page shows \"Coming soon.\"", file);
            return List.of();
        }
        try {
            List<Publication> list = fromBibTeX(Files.readString(Path.of(file), StandardCharsets.UTF_8), options, me);
            log.info("Read {} publications from {}", list.size(), file);
            return list;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the publications file " + file, e);
        }
    }

    /** {@code me}: the site's author, left out with {@link Site.Publications.Authors#COAUTHORS}. */
    static List<Publication> fromBibTeX(String bibtex, Site.Publications options, String me) {
        List<BibTeX.Entry> entries = BibTeX.parse(bibtex);
        Set<String> publishedTitles = new HashSet<>();
        Set<String> linkedPreprints = new HashSet<>();
        for (BibTeX.Entry e : entries) {
            if (!isArxiv(e)) {
                publishedTitles.add(titleKey(e));
                if (eprint(e) != null) {
                    linkedPreprints.add(eprint(e));
                }
            }
        }
        List<BibTeX.Entry> shown = entries.stream()
                .filter(e -> !isArxiv(e) || switch (options.arxivPapers()) {
                    case ALWAYS -> true;
                    case UNPUBLISHED -> !publishedTitles.contains(titleKey(e)) && !linkedPreprints.contains(arxivId(e));
                    case NEVER -> false;
                })
                .toList();
        Map<String, BibTeX.Entry> shownByKey = new HashMap<>();
        shown.forEach(e -> shownByKey.putIfAbsent(e.key(), e));
        Set<String> allKeys = new HashSet<>();
        entries.forEach(e -> allKeys.add(e.key()));
        return addExtendedIn(shown.stream()
                .map(e -> {
                    String authors = authors(e.get("author"), options.authors(), me);
                    List<Publication.Reference> extensionOf =
                            options.extendedVersionLinks()
                                    ? extensionOf(e, shownByKey, allKeys, options.extendedVersionTitleLength())
                                    : List.of();
                    return isArxiv(e)
                            ? toPublication(e, authors, arxivId(e) == null ? venue(e) : "arXiv:" + arxivId(e),
                                    arxivUrl(e), null, Publication.Type.PREPRINT, extensionOf)
                            : toPublication(e, authors, venue(e), link(e),
                                    options.arxivLinks() && eprint(e) != null
                                            ? "https://arxiv.org/abs/" + eprint(e) : null,
                                    type(e), extensionOf);
                })
                .toList());
    }

    /** The other direction of {@code extensionOf}: each entry links to the entries that extend it. */
    private static List<Publication> addExtendedIn(List<Publication> publications) {
        Map<String, List<Publication.Reference>> byTarget = new HashMap<>();
        publications.stream().sorted(NEWEST_FIRST).forEach(p -> p.extensionOf().forEach(r -> byTarget
                .computeIfAbsent(r.id(), k -> new ArrayList<>())
                .add(new Publication.Reference(shortName(p), p.id(), p.title()))));
        return publications.stream().map(p -> p.withExtendedIn(byTarget.getOrDefault(p.id(), List.of()))).toList();
    }

    /** "Theory Comput. Syst. 2026": the acronym of the venue with the year, or else the journal without volume. */
    private static String shortName(Publication p) {
        String venue = p.venue() == null ? "" : p.venue();
        Matcher m = ACRONYM_YEAR.matcher(venue);
        if (m.find()) {
            return m.group(1) + " " + m.group(2);
        }
        String name = venue.isEmpty() ? p.title() : JOURNAL_VOLUME.matcher(venue.split(",")[0]).replaceFirst("").strip();
        return p.year() == null ? name : name + " " + p.year();
    }

    private static Publication toPublication(BibTeX.Entry e, String authors, String venue, String link,
                                             String preprint, Publication.Type type,
                                             List<Publication.Reference> extensionOf) {
        return new Publication(authors, text(e, "title"), venue, year(text(e, "year")), link, preprint, type,
                anchor(e.key()), extensionOf, List.of());
    }

    /**
     * The entries named in {@code related} that are on the page. A key that is not in the file is reported in the
     * log; one of an entry that is not shown (e.g. a hidden arXiv version) is left out.
     */
    private static List<Publication.Reference> extensionOf(BibTeX.Entry e, Map<String, BibTeX.Entry> shownByKey,
                                                           Set<String> allKeys, int titleLength) {
        String related = e.get("related");
        if (related == null || related.isBlank()) {
            return List.of();
        }
        List<Publication.Reference> references = new ArrayList<>();
        for (String key : related.split(",")) {
            String k = key.strip();
            BibTeX.Entry target = shownByKey.get(k);
            if (target != null) {
                String title = text(target, "title");
                references.add(new Publication.Reference(
                        withTitleStart(shortVenue(target), title, titleLength), anchor(k), title));
            } else if (!k.isEmpty() && !allKeys.contains(k)) {
                log.warn("Entry {} names {} in 'related', but there is no such entry", e.key(), k);
            }
        }
        return references;
    }

    /** "DLT 2023" from the acronym in the venue, or else the venue up to its first comma, with the year. */
    private static String shortVenue(BibTeX.Entry e) {
        String venue = Objects.requireNonNullElse(
                Objects.requireNonNullElse(text(e, "booktitle"), text(e, "journal")), text(e, "title"));
        Matcher m = ACRONYM_YEAR.matcher(venue);
        if (m.find()) {
            return m.group(1) + " " + m.group(2);
        }
        String name = venue.split(",")[0].strip();
        String year = text(e, "year");
        return year == null || name.contains(year) ? name : name + " " + year;
    }

    /** "DLT 2023, Completely Distinguisha…": the title cut after {@code length} characters; nothing for 0. */
    static String withTitleStart(String venue, String title, int length) {
        if (length <= 0 || title == null || title.isBlank()) {
            return venue;
        }
        String start = title.length() <= length ? title : title.substring(0, length).stripTrailing() + "…";
        return venue + ", " + start;
    }

    /** "DBLP:conf/dlt/000123a" becomes "dblp-conf-dlt-000123a". */
    static String anchor(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static Publication.Type type(BibTeX.Entry e) {
        return switch (e.type()) {
            case "article" -> Publication.Type.JOURNAL;
            case "inproceedings", "conference" -> Publication.Type.CONFERENCE;
            default -> Publication.Type.OTHER;
        };
    }

    private static String venue(BibTeX.Entry e) {
        return switch (e.type()) {
            case "article" -> join(", ",
                    join(" ", text(e, "journal"), text(e, "volume") == null ? null
                            : text(e, "volume") + (text(e, "number") == null ? "" : "(" + text(e, "number") + ")")),
                    text(e, "pages"));
            case "inproceedings", "incollection" -> join(", ",
                    text(e, "booktitle"), join(" ", text(e, "series"), text(e, "volume")), text(e, "pages"));
            default -> join(", ", text(e, "journal"), text(e, "booktitle"), text(e, "howpublished"),
                    text(e, "school"), text(e, "publisher"));
        };
    }

    private static String link(BibTeX.Entry e) {
        if (text(e, "url") != null) {
            return text(e, "url");
        }
        return text(e, "doi") == null ? null : "https://doi.org/" + text(e, "doi");
    }

    /** Whether the entry is an arXiv version rather than a paper published elsewhere. */
    private static boolean isArxiv(BibTeX.Entry e) {
        String journal = text(e, "journal");
        return journal != null && (journal.equalsIgnoreCase("CoRR") || journal.toLowerCase(Locale.ROOT).startsWith("arxiv"))
                || text(e, "doi") != null && ARXIV_DOI.matcher(text(e, "doi")).matches();
    }

    /** The arXiv identifier in {@code eprint}, if {@code eprinttype} (or {@code archivePrefix}) says arXiv. */
    private static String eprint(BibTeX.Entry e) {
        boolean arxiv = "arxiv".equalsIgnoreCase(text(e, "eprinttype")) || "arxiv".equalsIgnoreCase(text(e, "archiveprefix"));
        return arxiv ? text(e, "eprint") : null;
    }

    /** The arXiv identifier of an arXiv version (e.g. {@code 2512.11007}), or null if the entry does not say. */
    private static String arxivId(BibTeX.Entry e) {
        if (text(e, "eprint") != null) {
            return text(e, "eprint");
        }
        Matcher m = text(e, "doi") == null ? null : ARXIV_DOI.matcher(text(e, "doi"));
        if (m != null && m.matches()) {
            return m.group(1);
        }
        String volume = text(e, "volume");
        if (volume != null && volume.startsWith("abs/")) {
            return volume.substring("abs/".length());
        }
        m = text(e, "url") == null ? null : ARXIV_URL.matcher(text(e, "url"));
        return m != null && m.find() ? m.group(1) : null;
    }

    private static String arxivUrl(BibTeX.Entry e) {
        return arxivId(e) == null ? link(e) : "https://arxiv.org/abs/" + arxivId(e);
    }

    /** "Synchronization Under Sparse Constraints" and "Synchronization under sparse constraints" are the same paper. */
    private static String titleKey(BibTeX.Entry e) {
        String title = text(e, "title");
        return title == null ? e.key() : title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /**
     * "Hoffmann, Stefan and Jane Roe" becomes "Stefan Hoffmann, Jane Roe", or "(with Jane Roe)" with
     * {@link Site.Publications.Authors#COAUTHORS}. Null without authors, and for one's own paper without coauthors.
     */
    private static String authors(String latex, Site.Publications.Authors style, String me) {
        if (latex == null) {
            return null;
        }
        List<String> names = Arrays.stream(latex.split("\\s+and\\s+"))
                .map(BibTeX::text)
                .map(name -> {
                    int comma = name.indexOf(',');
                    return comma < 0 ? name : name.substring(comma + 1).strip() + " " + name.substring(0, comma).strip();
                })
                .map(name -> DBLP_NUMBER.matcher(name).replaceFirst(""))
                .toList();
        if (style == Site.Publications.Authors.COAUTHORS && me != null && !me.isBlank()) {
            List<String> others = names.stream().filter(name -> !sameName(name, me)).toList();
            if (others.size() < names.size()) {
                return others.isEmpty() ? null : "(with " + String.join(", ", others) + ")";
            }
        }
        return String.join(", ", names);
    }

    private static boolean sameName(String a, String b) {
        return normalName(a).equalsIgnoreCase(normalName(b));
    }

    private static String normalName(String name) {
        return DBLP_NUMBER.matcher(name.strip()).replaceFirst("").replaceAll("\\s+", " ");
    }

    private static Integer year(String year) {
        return year != null && year.matches("\\d{1,4}") ? Integer.valueOf(year) : null;
    }

    private static String text(BibTeX.Entry e, String field) {
        String value = BibTeX.text(e.get(field));
        return value == null || value.isEmpty() ? null : value;
    }

    private static String join(String separator, String... parts) {
        String joined = Stream.of(parts).filter(Objects::nonNull).collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }
}
