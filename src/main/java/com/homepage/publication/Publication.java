package com.homepage.publication;

import java.util.List;

/**
 * One entry on the Publications page, as display text. {@code venue} is the journal or proceedings with volume and
 * pages; {@code year}, {@code link} and {@code preprint} (the arXiv version of a published paper) may be null.
 * {@code id} is the anchor of the entry on the page; {@code extensionOf} are the entries this one is an extended
 * version of (e.g. the conference version of a journal article), {@code extendedIn} the other way round.
 */
public record Publication(String authors, String title, String venue, Integer year, String link, String preprint,
                          Type type, String id, List<Reference> extensionOf, List<Reference> extendedIn) {

    /**
     * A link to another entry on the page: its text ("DLT 2023, Completely Distinguisha…" or "Theory Comput. Syst.
     * 2026"), its anchor and its full title, shown as a tooltip.
     */
    public record Reference(String text, String id, String title) {}

    public Publication withExtendedIn(List<Reference> extendedIn) {
        return new Publication(authors, title, venue, year, link, preprint, type, id, extensionOf, extendedIn);
    }

    /** The kind of publication, in the order of the groups on the page. */
    public enum Type {
        JOURNAL("Journal articles"), CONFERENCE("Conference papers"), PREPRINT("Preprints"), OTHER("Other");

        private final String heading;

        Type(String heading) {
            this.heading = heading;
        }

        public String heading() {
            return heading;
        }
    }

    /** A group on the page with its heading (null when the list is not grouped) and its anchor. */
    public record Group(String heading, String id, List<Publication> entries) {}
}
