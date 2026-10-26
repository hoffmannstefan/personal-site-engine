package com.homepage.frontend.content;

import java.util.List;

/**
 * The Publications page from the backend ({@code /api/publications}): the grouping used ({@code year}, {@code type}
 * or {@code none}) and the groups in order.
 */
public record Publications(String groupBy, List<Group> groups) {

    /** The groupings a visitor can switch between, as written in the address ({@code ?group=year}). */
    public static final List<String> GROUPINGS = List.of("year", "type", "none");

    public Publications {
        groups = groups == null ? List.of() : groups;
    }

    /** A group with its heading (null when the list is not grouped) and its anchor. */
    public record Group(String heading, String id, List<Entry> entries) {}

    /**
     * One entry, as display text. {@code year}, {@code link} and {@code preprint} (the arXiv version of a published
     * paper) may be null. {@code id} is the anchor of the entry on the page; {@code extensionOf} are the entries this
     * one is an extended version of, {@code extendedIn} the other way round.
     */
    public record Entry(String authors, String title, String venue, Integer year, String link, String preprint,
                        String id, List<Reference> extensionOf, List<Reference> extendedIn) {

        public Entry {
            extensionOf = extensionOf == null ? List.of() : extensionOf;
            extendedIn = extendedIn == null ? List.of() : extendedIn;
        }
    }

    /** A link to another entry on the page: its text, its anchor and its full title, shown as a tooltip. */
    public record Reference(String text, String id, String title) {}
}
