package com.homepage.backend.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static com.homepage.backend.publication.Publication.Type.CONFERENCE;
import static com.homepage.backend.publication.Publication.Type.JOURNAL;
import static com.homepage.backend.publication.Publication.Type.OTHER;
import static com.homepage.backend.publication.Publication.Type.PREPRINT;

import com.homepage.backend.site.Site;
import com.homepage.backend.site.SiteProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicationServiceTest {

    private static final Site.Publications BOTH =
            new Site.Publications(Site.Publications.ArxivPapers.UNPUBLISHED, true,
                    Site.Publications.GroupBy.NONE, Site.Publications.Authors.ALL, true, 20);
    private static final Site SITE = new Site("t", "a", null, null, 6, true, new Site.Home(null, null, List.of(), ""), BOTH);

    /**
     * A conference paper naming its preprint, the journal version with the same title (no eprint), the arXiv version
     * of both (hidden by its title), an arXiv version with another title (hidden by its id), an arXiv-only paper and
     * an EPTCS paper with archivePrefix instead of eprinttype.
     */
    private static final String WITH_ARXIV = """
            @article{DBLP:journals/ijfcs/Hoffmann23,
              author = {Stefan Hoffmann},
              title  = {Regularity Conditions},
              journal = {Int. J. Found. Comput. Sci.}, volume = {34}, year = {2023},
              doi    = {10.1142/S0129054122440014},
              related = {DBLP:conf/wia/Hoffmann21}
            }
            @article{DBLP:journals/corr/abs-2512-11007,
              author = {Stefan Hoffmann},
              title  = {Only on arXiv},
              journal = {CoRR}, volume = {abs/2512.11007}, year = {2025},
              url    = {https://doi.org/10.48550/ARXIV.2512.11007}
            }
            @inproceedings{DBLP:journals/corr/abs-2409-06971,
              author = {Stefan Hoffmann},
              title  = {Winning Strategies},
              booktitle = {NCMA 2024}, series = {EPTCS}, volume = {407}, year = {2024},
              doi    = {10.4204/EPTCS.407.6},
              archivePrefix = {arXiv}, eprint = {2409.06971}
            }
            @inproceedings{DBLP:conf/wia/Hoffmann21,
              author = {Stefan Hoffmann},
              title  = {Regularity conditions},
              booktitle = {CIAA 2021}, year = {2021},
              doi    = {10.1007/978-3-030-79121-6_7},
              eprinttype = {arXiv}, eprint = {2103.09587}
            }
            @article{DBLP:journals/corr/abs-2103-09587,
              author = {Stefan Hoffmann},
              title  = {Regularity Conditions},
              journal = {CoRR}, volume = {abs/2103.09587}, year = {2021},
              doi    = {10.48550/ARXIV.2103.09587}
            }
            @inproceedings{DBLP:conf/lata/Hoffmann21,
              author = {Stefan Hoffmann},
              title  = {Completely Reachable Automata},
              booktitle = {LATA 2021}, year = {2021},
              eprinttype = {arXiv}, eprint = {2007.09104}
            }
            @article{DBLP:journals/corr/abs-2007-09104,
              author = {Stefan Hoffmann},
              title  = {Another Title on arXiv},
              journal = {CoRR}, volume = {abs/2007.09104}, year = {2020}
            }
            """;

    @Test
    void onlyAnEprintFieldMakesALinkAndHidesTheArxivVersion() {
        assertThat(PublicationService.fromBibTeX(WITH_ARXIV, BOTH, null)).containsExactly(
                new Publication("Stefan Hoffmann", "Regularity Conditions", "Int. J. Found. Comput. Sci. 34", 2023,
                        "https://doi.org/10.1142/S0129054122440014", null, JOURNAL, "dblp-journals-ijfcs-hoffmann23",
                        List.of(new Publication.Reference("CIAA 2021, Regularity condition…", "dblp-conf-wia-hoffmann21",
                                "Regularity conditions")), List.of()),
                new Publication("Stefan Hoffmann", "Only on arXiv", "arXiv:2512.11007", 2025,
                        "https://arxiv.org/abs/2512.11007", null, PREPRINT, "dblp-journals-corr-abs-2512-11007", List.of(),
                        List.of()),
                new Publication("Stefan Hoffmann", "Winning Strategies", "NCMA 2024, EPTCS 407", 2024,
                        "https://doi.org/10.4204/EPTCS.407.6", "https://arxiv.org/abs/2409.06971", CONFERENCE,
                        "dblp-journals-corr-abs-2409-06971", List.of(), List.of()),
                new Publication("Stefan Hoffmann", "Regularity conditions", "CIAA 2021", 2021,
                        "https://doi.org/10.1007/978-3-030-79121-6_7", "https://arxiv.org/abs/2103.09587", CONFERENCE,
                        "dblp-conf-wia-hoffmann21", List.of(), List.of(new Publication.Reference(
                                "Int. J. Found. Comput. Sci. 2023", "dblp-journals-ijfcs-hoffmann23", "Regularity Conditions"))),
                new Publication("Stefan Hoffmann", "Completely Reachable Automata", "LATA 2021", 2021,
                        null, "https://arxiv.org/abs/2007.09104", CONFERENCE, "dblp-conf-lata-hoffmann21", List.of(),
                        List.of()));
    }

    @Test
    void coauthorsLeaveOutTheSiteAuthor() {
        String bib = """
                @article{a, author = {Henning Fernau and Hoffmann, Stefan and Mikhail V. Volkov}, title = {A}}
                @article{b, author = {Stefan Hoffmann 0001}, title = {B}}
                @article{c, author = {Jane Roe}, title = {C}}
                @article{d, author = {Jane Roe 0002 and Stefan Hoffmann}, title = {D}}
                """;
        var all = new Site.Publications(Site.Publications.ArxivPapers.ALWAYS, true, Site.Publications.GroupBy.NONE,
                Site.Publications.Authors.ALL, true, 20);
        assertThat(PublicationService.fromBibTeX(bib, all, "Stefan Hoffmann")).extracting(Publication::authors)
                .containsExactly("Henning Fernau, Stefan Hoffmann, Mikhail V. Volkov", "Stefan Hoffmann", "Jane Roe",
                        "Jane Roe, Stefan Hoffmann");
        var coauthors = new Site.Publications(Site.Publications.ArxivPapers.ALWAYS, true,
                Site.Publications.GroupBy.NONE, Site.Publications.Authors.COAUTHORS, true, 20);
        assertThat(PublicationService.fromBibTeX(bib, coauthors, "stefan  hoffmann")).extracting(Publication::authors)
                .containsExactly("(with Henning Fernau, Mikhail V. Volkov)", null, "Jane Roe", "(with Jane Roe)");
    }

    @Test
    void extendedVersionsLinkToTheRelatedEntries() {
        String bib = """
                @article{j, title = {J}, journal = {Theory Comput. Syst.}, year = {2026}, related = {c1, c2 ,missing}}
                @inproceedings{c1, title = {C1}, year = {2023},
                  booktitle = {Developments in Language Theory - 27th International Conference, DLT 2023, Umea}}
                @inproceedings{c2, title = {C2}, year = {2021}, booktitle = {Prague Stringology Conference 2021, Prague}}
                @inproceedings{c3, title = {C3}, year = {2020}, booktitle = {Workshop on Things, Somewhere}}
                @article{k, title = {K}, journal = {X}, related = {c3}}
                """;
        assertThat(PublicationService.fromBibTeX(bib, BOTH, null))
                .extracting(Publication::id, Publication::extensionOf, Publication::extendedIn)
                .containsExactly(
                        tuple("j", List.of(new Publication.Reference("DLT 2023, C1", "c1", "C1"),
                                new Publication.Reference("Prague Stringology Conference 2021, C2", "c2", "C2")), List.of()),
                        tuple("c1", List.of(), List.of(new Publication.Reference("Theory Comput. Syst. 2026", "j", "J"))),
                        tuple("c2", List.of(), List.of(new Publication.Reference("Theory Comput. Syst. 2026", "j", "J"))),
                        tuple("c3", List.of(), List.of(new Publication.Reference("X", "k", "K"))),
                        tuple("k", List.of(new Publication.Reference("Workshop on Things 2020, C3", "c3", "C3")),
                                List.of()));
        var off = new Site.Publications(Site.Publications.ArxivPapers.ALWAYS, true, Site.Publications.GroupBy.NONE,
                Site.Publications.Authors.ALL, false, 20);
        assertThat(PublicationService.fromBibTeX(bib, off, null))
                .allMatch(p -> p.extensionOf().isEmpty() && p.extendedIn().isEmpty());
        assertThat(PublicationService.anchor("DBLP:conf/dlt/000123a")).isEqualTo("dblp-conf-dlt-000123a");
    }

    @Test
    void theTitleStartIsCutAfterTheConfiguredLength() {
        String title = "Completely Distinguishable Automata";
        assertThat(PublicationService.withTitleStart("DLT 2023", title, 15)).isEqualTo("DLT 2023, Completely Dist…");
        assertThat(PublicationService.withTitleStart("DLT 2023", title, 16)).isEqualTo("DLT 2023, Completely Disti…");
        assertThat(PublicationService.withTitleStart("DLT 2023", title, 11)).isEqualTo("DLT 2023, Completely…");
        assertThat(PublicationService.withTitleStart("DLT 2023", title, 35)).isEqualTo("DLT 2023, " + title);
        assertThat(PublicationService.withTitleStart("DLT 2023", title, 0)).isEqualTo("DLT 2023");
        assertThat(PublicationService.withTitleStart("DLT 2023", null, 20)).isEqualTo("DLT 2023");
    }

    @Test
    void groupsByYearOrTypeNewestFirst() {
        List<Publication> list = List.of(
                pub("A", 2021, CONFERENCE), pub("B", 2024, JOURNAL), pub("C", null, OTHER),
                pub("D", 2021, JOURNAL), pub("E", 2025, PREPRINT), pub("F", 2024, CONFERENCE));

        assertThat(PublicationService.group(list, Site.Publications.GroupBy.YEAR))
                .extracting(Publication.Group::heading, Publication.Group::id, g -> titles(g))
                .containsExactly(tuple("2025", "2025", "E"), tuple("2024", "2024", "BF"), tuple("2021", "2021", "AD"),
                        tuple("Undated", "undated", "C"));
        assertThat(PublicationService.group(list, Site.Publications.GroupBy.TYPE))
                .extracting(Publication.Group::heading, Publication.Group::id, g -> titles(g))
                .containsExactly(tuple("Journal articles", "journal-articles", "BD"),
                        tuple("Conference papers", "conference-papers", "FA"), tuple("Preprints", "preprints", "E"),
                        tuple("Other", "other", "C"));
        assertThat(PublicationService.group(list, Site.Publications.GroupBy.NONE))
                .extracting(Publication.Group::heading, g -> titles(g))
                .containsExactly(tuple(null, "EBFADC"));
        assertThat(PublicationService.group(List.of(), Site.Publications.GroupBy.NONE)).isEmpty();
    }

    private static Publication pub(String title, Integer year, Publication.Type type) {
        return new Publication(null, title, null, year, null, null, type, title.toLowerCase(), List.of(), List.of());
    }

    private static String titles(Publication.Group group) {
        return group.entries().stream().map(Publication::title).reduce("", String::concat);
    }

    @Test
    void arxivPapersCanAlsoBeListedAlwaysOrNever() {
        assertThat(PublicationService.fromBibTeX(WITH_ARXIV,
                        new Site.Publications(Site.Publications.ArxivPapers.ALWAYS, true,
                                Site.Publications.GroupBy.NONE, Site.Publications.Authors.ALL, true, 20), null))
                .extracting(Publication::title, Publication::venue)
                .containsExactly(tuple("Regularity Conditions", "Int. J. Found. Comput. Sci. 34"),
                        tuple("Only on arXiv", "arXiv:2512.11007"), tuple("Winning Strategies", "NCMA 2024, EPTCS 407"),
                        tuple("Regularity conditions", "CIAA 2021"), tuple("Regularity Conditions", "arXiv:2103.09587"),
                        tuple("Completely Reachable Automata", "LATA 2021"),
                        tuple("Another Title on arXiv", "arXiv:2007.09104"));
    }

    @Test
    void noArxivPapersAndNoArxivLinks() {
        assertThat(PublicationService.fromBibTeX(WITH_ARXIV,
                        new Site.Publications(Site.Publications.ArxivPapers.NEVER, false,
                                Site.Publications.GroupBy.NONE, Site.Publications.Authors.ALL, true, 20), null))
                .extracting(Publication::title, Publication::preprint)
                .containsExactly(tuple("Regularity Conditions", null), tuple("Winning Strategies", null),
                        tuple("Regularity conditions", null), tuple("Completely Reachable Automata", null));
    }

    @Test
    void readsDblpStyleEntries() {
        List<Publication> list = PublicationService.fromBibTeX("""
                @article{DBLP:journals/mst/Hoffmann26,
                  author       = {Stefan Hoffmann and
                                  Volkov, Mikhail V.},
                  title        = {Completely Distinguishable Automata and the Set of Synchronizing Words},
                  journal      = {Theory Comput. Syst.},
                  volume       = {70},
                  number       = {2},
                  pages        = {20},
                  year         = {2026},
                  doi          = {10.1007/S00224-024-10204-9}
                }

                @inproceedings{DBLP:conf/sofsem/Hoffmann24,
                  author       = {Stefan Hoffmann},
                  title        = {Automata Classes Accepting {L}anguages},
                  booktitle    = {SOFSEM 2024, Cochem, Germany, Proceedings},
                  series       = {Lect. Notes Comput. Sci.},
                  volume       = {14519},
                  pages        = {311--325},
                  year         = {2024},
                  url          = {https://doi.org/10.1007/978-3-031-52113-3_22}
                }
                """, BOTH, null);

        assertThat(list).containsExactly(
                new Publication("Stefan Hoffmann, Mikhail V. Volkov",
                        "Completely Distinguishable Automata and the Set of Synchronizing Words",
                        "Theory Comput. Syst. 70(2), 20", 2026, "https://doi.org/10.1007/S00224-024-10204-9", null, JOURNAL,
                        "dblp-journals-mst-hoffmann26", List.of(), List.of()),
                new Publication("Stefan Hoffmann", "Automata Classes Accepting Languages",
                        "SOFSEM 2024, Cochem, Germany, Proceedings, Lect. Notes Comput. Sci. 14519, 311–325", 2024,
                        "https://doi.org/10.1007/978-3-031-52113-3_22", null, CONFERENCE, "dblp-conf-sofsem-hoffmann24",
                        List.of(), List.of()));
    }

    @Test
    void resolvesLatexAndOtherBibTeXSyntax() {
        List<Publication> list = PublicationService.fromBibTeX("""
                @comment{ignored @article{x, title={no}} }
                @string{ tcs = "Theor. Comput. Sci." }
                @ARTICLE(key,
                  AUTHOR = "J{\\"u}rgen M{\\"u}ller and Fran\\c{c}ois {\\'E}tienne and Stra\\ss e, A.",
                  title = {Sets {\\&} W\\"orter: a {$n$}-ary view},
                  journal = tcs # " B",
                  year = 1999,
                )
                @article{broken, title = {missing closing brace}
                @misc{last, title = {Still read}, howpublished = {Talk}}
                """, BOTH, null);

        assertThat(list).hasSize(2);
        Publication p = list.get(0);
        assertThat(p.authors()).isEqualTo("Jürgen Müller, François Étienne, A. Straße");
        assertThat(p.title()).isEqualTo("Sets & Wörter: a $n$-ary view");
        assertThat(p.venue()).isEqualTo("Theor. Comput. Sci. B");
        assertThat(p.year()).isEqualTo(1999);
        assertThat(p.link()).isNull();
        assertThat(list.get(1)).isEqualTo(new Publication(null, "Still read", "Talk", null, null, null, OTHER, "last", List.of(), List.of()));
    }

    @Test
    void missingFileGivesEmptyList(@TempDir Path dir) throws Exception {
        assertThat(new PublicationService(properties(dir.resolve("none.bib")), SITE).all()).isEmpty();

        Path file = dir.resolve("p.bib");
        Files.writeString(file, "@article{a, title={T}, journal={J}, year={2020}}");
        assertThat(new PublicationService(properties(file), SITE).all())
                .containsExactly(new Publication(null, "T", "J", 2020, null, null, JOURNAL, "a", List.of(), List.of()));
    }

    private static SiteProperties properties(Path publications) {
        return new SiteProperties(null, null, new SiteProperties.Comments(5, ""), publications.toString(), null);
    }
}
