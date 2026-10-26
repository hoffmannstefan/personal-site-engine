# Done tasks

Tasks from the backlog whose code is written. Remaining work, including the open verifications below, is in
[tasks.md](tasks.md); the system is described in [spec.md](spec.md).

Each task keeps its number from the backlog and has three extra lines:

- **Status:** "Done", or "Done, verification open" (see Verified).
- **Done in:** the commit(s) with the work. `44fc6ec` is the first single-module version, `f8eed9f` added CI/CD,
  `85c5324` split it into backend and frontend (branch `split-backend-frontend`, not yet merged into `main`).
- **Verified:** how the acceptance criterion was checked. For "Done, verification open" the code is written, but part
  of the criterion has not been checked yet; the line names the task in `tasks.md` that covers it.

Status on October 26, 2026: 22 tasks done, 10 done with verification open.

## 1. Empty project with a passing test
Goal: A buildable Maven project with a backend and a frontend module, each with one passing test.
Description: Create a parent `pom.xml` (packaging `pom`, Spring Boot 3.5 parent, Java 21) with the modules `backend`
and `frontend`, plus the Maven wrapper. Each module gets a minimal Spring Boot application class and a test that
starts its application context. The backend listens on port 8081, the frontend on 8080.
Acceptance criterion: `./mvnw verify` from the project folder builds both modules and runs two tests, both passing.
Status: Done
Done in: 44fc6ec (one module); split into `backend` and `frontend` modules in 85c5324.
Verified: `./mvnw verify` builds both modules and runs 72 passing tests (the project is no longer empty).

## 2. Render Markdown to HTML
Goal: The backend turns a Markdown text into the HTML the site's theme expects.
Description: Add a Markdown renderer to the backend based on commonmark-java with tables, strikethrough, autolinks,
footnotes, heading anchors and YAML front matter. Headings get the theme's anchor link, fenced code is written as
`div.highlight > pre.chroma > code` without colouring, and raw HTML is passed through. The result also contains the
front matter as key/value lists and the word count (spec §6.4).
Acceptance criterion: Unit tests show that a sample with a table, strikethrough, a footnote, an autolink, a heading,
fenced code and front matter (including inline lists like `[a, b]`) renders to the expected HTML and front matter.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `MarkdownRendererTest` (heading anchors, highlight markup, tables/strikethrough/footnotes/autolinks, front matter).

## 3. Protect TeX formulas from the Markdown parser
Goal: Formulas in Markdown reach the browser exactly as written.
Description: Before Markdown parsing, replace every formula (`$…$`, `$$…$$`, `\(…\)`, `\[…\]`,
`\begin{env}…\end{env}`) with an inert placeholder, and after rendering put it back HTML-escaped with `\( \)` /
`\[ \]` delimiters. Follow the `$` rules of spec §6.4: code spans and fenced blocks are left alone, `\$` is a literal
dollar, and "costs $5 and $10" stays text. Report whether a text contained any formula.
Acceptance criterion: Unit tests cover `_`, `*`, `\\`, `<`, `&` and `|` inside formulas (also in table cells), lone
and escaped dollars, formulas in code, and heading ids without gaps; all pass.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `MarkdownRendererTest` (emphasis and escapes, display math and environments, pipes in tables, lone and escaped dollars, code, heading ids).

## 4. Load the site configuration
Goal: The backend reads `info/site.yaml` into a site object with defaults.
Description: Read the YAML file at `site.config-file` (default `./info/site.yaml`, env `SITE_CONFIG`) once at
startup into a `Site` record with the keys of spec §6.1, including `home` and `publications`. Fill in the defaults,
render `home.text` with the Markdown renderer, turn image paths into `/images/…` URLs, and log unknown keys with their
line. A missing or broken file must not stop startup.
Acceptance criterion: Unit tests cover the defaults without a file, a broken file, an unknown key, image URL rules,
links without `name` or `url` being skipped, and new-tab links in `home.text` being switchable.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `SiteLoaderTest` (7 tests) and the link checks in `ContentApiTest`.

## 5. Serve the site configuration as JSON
Goal: `GET /api/site` returns the site configuration.
Description: Add a REST endpoint to the backend that returns the `Site` loaded at startup as JSON in camelCase, with
the start page text as HTML. Use a test copy of `site.yaml` in `src/test/resources` so the tests do not depend on
`info/` (spec §12).
Acceptance criterion: A MockMvc test calls `GET /api/site` and checks title, author, description, keywords, the
headline, avatar URL, links and the rendered introduction from the test configuration.
Status: Done
Done in: 85c5324.
Verified: `ContentApiTest.siteComesFromTheConfigurationFile`.

## 6. Load blog posts from Markdown files
Goal: The backend reads the posts in `info/posts/` at startup.
Description: Load every `*.md` file in `site.posts-dir` (default `./info/posts`, env `POSTS_DIR`) into a `Post`
record using the Markdown renderer, following spec §6.3: slug from the file name, required `title` and `date`,
`draft`, `link` entries, default author, reading time at 212 words per minute, and newest-first order. Files with
invalid names or headers are skipped with a warning; a missing folder means an empty blog.
Acceptance criterion: Unit tests with files in a temporary folder cover a valid post, each skip reason, drafts, a
`link` entry, the default author, the reading time and the sort order.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `PostServiceTest` (6 tests).

## 7. Serve posts as JSON
Goal: `GET /api/posts?page=N` and `GET /api/posts/{slug}` return the blog.
Description: Add endpoints that return one page of the blog list (`page`, `pages`, `posts` without bodies,
`posts-per-page` from the site configuration) and one post with its HTML body and `math` flag. Pages outside the range
and unknown slugs return 404, and so do `link` entries, which have no page of their own (spec §12).
Acceptance criterion: MockMvc tests with test posts in `src/test/resources` check paging, newest-first order, 404 for
page 0, a page past the last, an unknown slug and a `link` entry, and a post's body and metadata.
Status: Done
Done in: 85c5324 (before that the same data went straight into server-rendered pages, 44fc6ec).
Verified: `ContentApiTest` (paging, order, 404 cases, post body and metadata).

## 8. Parse BibTeX files
Goal: The backend reads BibTeX entries as exported by DBLP or Google Scholar.
Description: Write a small BibTeX parser that returns entries with type, key and fields. It handles braced, quoted and
bare values, `#` concatenation, `@string` macros, and turns LaTeX accents, escapes and `--` into plain text. A broken
entry is skipped with a warning and the rest of the file is still read (spec §6.5).
Acceptance criterion: Unit tests parse a sample with each value style, a macro, concatenation, accents such as
`{\"u}` and `\&`, and one broken entry, and get the expected entries.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `PublicationServiceTest.resolvesLatexAndOtherBibTeXSyntax`, `readsDblpStyleEntries`.

## 9. Build publication entries
Goal: Turn BibTeX entries into display entries for the Publications page.
Description: Load `site.publications-file` (default `./info/publications.bib`, env `PUBLICATIONS_FILE`) at startup
and build entries with title, link (`url`, else `doi`), venue for `@article` and `@inproceedings`, year, type and an
anchor from the key. Show authors as configured by `publications.authors`: all names, or "(with …)" without the site
author, ignoring DBLP's numbers like "Jane Roe 0001" (spec §6.5). Without the file the list is empty.
Acceptance criterion: Unit tests cover both venue formats, "Last, First" names, both author styles, DBLP numbers,
the link fallback and a missing file.
Status: Done, verification open
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `PublicationServiceTest` (DBLP entries, coauthors, missing file). Gap: no test uses "Last, First" names; see task 37.

## 10. Handle arXiv versions
Goal: arXiv preprints are linked or listed according to the configuration.
Description: Recognise arXiv versions (journal `CoRR` or `arXiv…`, or an arXiv DOI) and decide which count as
published: named as `eprint` by a published entry, or with the same normalised title. Apply
`publications.arxiv-papers` (`always`, `unpublished`, `never`) and `arxiv-links`, which adds an arXiv link to published
entries that name a preprint via `eprint` with `eprinttype` or `archivePrefix` (spec §6.5).
Acceptance criterion: Unit tests cover each `arxiv-papers` value, matching by eprint and by title, both prefix field
names, arXiv entries shown as "arXiv:<id>", and `arxiv-links` on and off.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `PublicationServiceTest` (eprint links, `always`/`never`, arXiv papers and links switched off).

## 11. Link extended versions
Goal: Journal articles and the conference papers they extend link to each other.
Description: Read the BibTeX field `related` (comma-separated keys) and add "extended version of …" references to the
article and "extended in …" references to each named paper, as described in spec §6.5. The link text is the venue's
acronym with the year (or the venue up to its first comma), followed by the start of the title cut to
`extended-version-title-length`. Unknown keys are logged; keys of hidden entries are left out.
Acceptance criterion: Unit tests cover one and several related keys, both directions, acronym and fallback link
texts, title lengths 0, 20 and longer than the title, an unknown key and a hidden target.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `PublicationServiceTest.extendedVersionsLinkToTheRelatedEntries`, `theTitleStartIsCutAfterTheConfiguredLength`.

## 12. Group publications and serve them as JSON
Goal: `GET /api/publications?group=` returns the grouped list.
Description: Sort entries newest first (same year keeps file order, undated last) and group them by `year`, `type`
(Journal articles, Conference papers, Preprints, Other) or `none`, each group with a heading and an anchor. The
endpoint uses `group` from the query, falling back to `publications.group-by` for a missing or unknown value, and
returns the grouping used and the groups (spec §6.5, §12).
Acceptance criterion: MockMvc tests with a test `.bib` file check each grouping, the order, group anchors, and the
fallback for an unknown `group`.
Status: Done
Done in: Grouping 44fc6ec; JSON endpoint 85c5324.
Verified: `PublicationServiceTest.groupsByYearOrTypeNewestFirst`, `ContentApiTest.publicationsAreGroupedAsConfiguredOrAsAsked`.

## 13. Store and list comments
Goal: Visitors can post and read comments on a post through the API.
Description: Add a JPA entity `Comment` (table `comments`, fields as in spec §11) with an H2 file database at
`./data/comments` by default, plus `GET` and `POST /api/posts/{slug}/comments`. Validate name (1–50) and comment
(1–2000) after trimming, keep threads one level deep, and return 404 for unknown posts and `link` entries. Validation
errors are RFC 9457 problem details.
Acceptance criterion: MockMvc tests (in-memory H2) cover creating and listing in order, reply-to-reply flattening, a
parent from another post, each validation error and unknown posts.
Status: Done, verification open
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `CommentApiTest` (create and list, reply flattening, invalid input incl. unknown parent, unknown post). Gap: no test for a parent from another post or a comment on a `link` entry; see task 37.

## 14. Protect comments from spam
Goal: Bots and floods are kept out of the comments.
Description: Add a hidden honeypot field `website`: when it is filled, answer 201 as usual but store nothing. Add an
in-memory sliding-window rate limiter per client address with `site.comments.max-per-minute` (default 5) that answers
429 with a readable message. The limiter takes a clock, so tests can move time forward (spec §6.6).
Acceptance criterion: Unit tests show the limiter allows exactly the limit per window and key and recovers after the
window; a MockMvc test shows a honeypot comment returns 201 and is not listed.
Status: Done
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `RateLimiterTest`, `CommentApiTest.honeypotSubmissionsAreDropped`.

## 15. Delete comments with an admin token
Goal: The site owner can delete comments.
Description: Add `DELETE /api/comments/{id}` that requires the header `X-Admin-Token` to match
`site.comments.admin-token` (env `COMMENTS_ADMIN_TOKEN`), compared in constant time. Deleting a comment also deletes
its replies. Without a configured token deleting is always refused (spec §6.6).
Acceptance criterion: MockMvc tests show 403 without, with a wrong and with no configured token, 204 with the right
one (replies gone), and 404 for an unknown id.
Status: Done, verification open
Done in: 44fc6ec; moved to `backend/` in 85c5324.
Verified: `CommentApiTest.deleteNeedsAdminTokenAndRemovesReplies`. Gap: the case without a configured token is handled in the code but not tested; see task 37.

## 16. Show comment counts in the blog list
Goal: Each entry of `GET /api/posts` carries its number of comments.
Description: Count the comments per post slug with one query and add a `comments` field to every entry of the blog
list response, 0 for posts without comments. The post list endpoint and the comment storage already exist in the
backend.
Acceptance criterion: A MockMvc test posts two comments to one post and checks that the list shows 2 for that post
and 0 for another.
Status: Done
Done in: Counts in the list page 44fc6ec; in the JSON 85c5324.
Verified: `ContentApiTest.postListCountsTheComments`.

## 17. Serve images from the content folder
Goal: The backend serves `info/images/` under `/images/`.
Description: Register a resource handler for `/images/**` on the folder `site.images-dir` (default `./info/images`,
env `IMAGES_DIR`) with a one-hour cache lifetime, so images change without a rebuild (spec §6.7).
Acceptance criterion: A MockMvc test with a test image gets 200 with the right content type, 404 for a missing file,
and a 4xx for a path with `..`.
Status: Done
Done in: 44fc6ec; backend only since 85c5324.
Verified: `ContentApiTest.servesTheImagesFolder` (200, 404, traversal); content types checked by hand through Docker Compose (`image/jpeg`, `image/png`).

## 18. Read the backend from the frontend
Goal: The frontend has a client for the backend's content API.
Description: Add `frontend.backend-url` (env `BACKEND_URL`, default `http://localhost:8081`) and a `RestClient` with a
2 s connect and 10 s read timeout. Write a `ContentClient` with methods for site, post page, post and publications,
returning the frontend's own records (spec §6.8, §11); 404 becomes an empty result and other failures throw. The
frontend must not share code with the backend.
Acceptance criterion: A test against a small fake HTTP server checks that each method sends the right request,
reads the JSON into records (including ISO dates) and maps 404 to empty.
Status: Done
Done in: 85c5324.
Verified: `BackendTest.readsTheContentApi` against a fake HTTP server.

## 19. Page layout and theme assets
Goal: Every frontend page shares the theme's skeleton.
Description: Add the Hugo Coder CSS, fonts and light/dark script (MIT, with credit in the footer), the site's own
`site.css` and the favicons at the root. Write `layout.html` with head and meta tags from the site configuration,
the menu (Home, Blog, Publications, active entry marked) and the footer, and make the site available to every template
as `site` (spec §7).
Acceptance criterion: A MockMvc test with the content client stubbed checks the title, meta tags and menu of a page
and that the CSS, script and favicon URLs return 200.
Status: Done
Done in: 44fc6ec; adapted to the backend's data in 85c5324.
Verified: `PagesTest` (title, meta tags, menu, theme assets and favicon).

## 20. Start page
Goal: `/` shows the profile card and the introduction.
Description: Render the start page from the site configuration: the author as heading, the card with avatar,
headline and profile links (image icon or Font Awesome class) and the introduction HTML (spec §6.2). Links to other
sites open in a new tab when `externalLinksInNewTab` is set.
Acceptance criterion: MockMvc tests with a stubbed site check the heading, card, both icon kinds and the text, that
the card is left out when empty, and the `target`/`rel` attributes with the setting on and off.
Status: Done
Done in: 44fc6ec; adapted in 85c5324.
Verified: `PagesTest.siteAndHomeComeFromTheBackend`, `linksToOtherSitesStayInTheTabIfSwitchedOff`.

## 21. Blog list and post pages
Goal: `/posts/`, `/posts/page/N/` and `/posts/{slug}/` render the blog.
Description: Render the list with date, title (external entries link to their URL), comment count and pagination
links, and the post page with title, metadata line (date, reading time, authors, categories, tags) and body. Routes
work with and without a trailing slash, dates are English whatever the browser language, and unknown pages return the
404 page (spec §6.3).
Acceptance criterion: MockMvc tests with a stubbed content client check both list pages, the pagination links,
comment count texts, an external entry, a post page, a German `Accept-Language` and 404s.
Status: Done
Done in: 44fc6ec; adapted in 85c5324.
Verified: `PagesTest` (paging and comment counts, post page, trailing slashes, English dates, 404s).

## 22. Publications page
Goal: `/publications/` renders the grouped publication list.
Description: Render each group with heading and anchor, and each entry with title link, authors, venue and year,
arXiv link and the "extended version of"/"extended in" links with tooltips. Above the list, "Group by: year · type ·
none" marks the current grouping and links to the others; without entries the page shows "Coming soon." (spec §6.5).
Acceptance criterion: MockMvc tests with a stubbed content client check each element of an entry, the group headings,
the grouping switch for two groupings, and "Coming soon.".
Status: Done
Done in: 44fc6ec; adapted in 85c5324.
Verified: `PagesTest.publicationsPage`, `publicationsWithoutEntriesAreComingSoon`.

## 23. Forward the API and images to the backend
Goal: The browser reaches `/api/**` and `/images/**` through the frontend.
Description: Add a proxy in the frontend that forwards these paths, all methods for `/api/**` and GET for `/images/**`,
with query and body, and returns the backend's status and body. Pass on only the headers listed in spec §6.8, set
`X-Forwarded-For` to the visitor's address and never forward cookies. Reject `.`/`..` segments, also encoded, with 400.
Acceptance criterion: Tests against a fake backend check a GET, a POST with body, a backend error status, an image
with its cache headers, the forwarded and dropped headers, and 400 for each kind of traversal path.
Status: Done
Done in: 85c5324.
Verified: `BackendTest` (GET, POST with body, error status, images, headers, traversal paths).

## 24. Comment section on post pages
Goal: Visitors can read and write comments in the browser.
Description: Add the comment section to the post page and a `comments.js` that loads the comments from
`/api/posts/{slug}/comments`, shows them threaded with replies, and posts new comments and replies. User text is
written with `textContent` only, the name is remembered in `localStorage`, errors from the API are shown, and a
`<noscript>` note explains that comments need JavaScript (spec §6.6, §8).
Acceptance criterion: A MockMvc test checks the section and script on a post page; a manual check in a browser
against a running backend shows posting, replying, an error message and markup shown as text.
Status: Done, verification open
Done in: 44fc6ec.
Verified: `PagesTest.postPageHasMetadataAndCommentSection`; posting, listing and errors checked through the frontend with `curl`. Open: the browser check; see task 36.

## 25. Load MathJax on pages with formulas
Goal: Formulas are typeset in the browser without a CDN.
Description: Serve MathJax 3 from the frontend through the `org.webjars.npm:mathjax` dependency and load it, with a
configuration that uses only `\( \)` and `\[ \]` delimiters and SVG output, only on posts the backend marks with
`math: true` (spec §6.4).
Acceptance criterion: MockMvc tests show the MathJax script on a post with `math: true`, not on one without, and 200
for the script URL; a manual browser check shows a formula typeset in light and dark mode.
Status: Done, verification open
Done in: 44fc6ec.
Verified: `PagesTest.mathPostsLoadMathJaxFromThisServer`, `otherPostsDoNotLoadMathJax`. Open: the browser check in light and dark mode; see task 36.

## 26. Error pages and backend outages
Goal: The frontend fails gracefully when the backend is down.
Description: Add a 404 page and a 5xx page in the site's layout. When a backend call fails or times out, pages answer
503 with the 5xx page (using a fallback site configuration for the layout) and forwarded API calls answer 503 as a
problem detail; the frontend recovers by itself once the backend is back (spec §6.9).
Acceptance criterion: Tests with a failing content client and a proxy pointed at a closed port show 503 for every
page type and the API, and 404 for an unknown page.
Status: Done
Done in: 85c5324.
Verified: `PagesTest` (503 for pages and API, 404); also checked in Docker Compose: backend stopped gives 503, the site recovers after it starts again.

## 27. Trust the visitor's address in the backend
Goal: The comment rate limit counts per visitor, not per frontend.
Description: Configure the backend to take the client address from `X-Forwarded-For` only when the request comes from
a private or loopback address (`server.forward-headers-strategy=native`), and document the setting needed when a
reverse proxy sits in front of the frontend (spec §13).
Acceptance criterion: In Docker Compose, two clients with different addresses posting through the frontend have
separate rate limits, while repeated posts from one client get 429.
Status: Done
Done in: 85c5324.
Verified: Checked by hand in Docker Compose: one client got 429, a second client in the same network still got 201.

## 28. Docker images and Compose
Goal: Both services run with one `docker compose up`.
Description: Write one multi-stage `Dockerfile` that builds and tests either module (`--build-arg MODULE=…`) and runs
it on a JRE, a `.dockerignore`, and a `compose.yaml` where only the frontend publishes port 8080, the backend mounts
`./info` read-only and `./data` read-write, and both run as user 1000 (spec §10.4).
Acceptance criterion: `docker compose up -d --build` serves the site on `http://localhost:8080`, the backend has no
host port, and a comment survives `docker compose down` and `up`.
Status: Done, verification open
Done in: 44fc6ec (one service); two services in 85c5324.
Verified: Checked by hand: the site answers on the host, the backend has no host port. Open: a comment surviving `docker compose down` and `up`; see task 36.

## 29. CI: build and test
Goal: Every change is built and tested on GitHub.
Description: Add a GitHub Actions workflow that runs `./mvnw verify` with Java 21 on pushes to `main`, on pull
requests and on manual runs, uploads test reports on failure, ignores changes to `README.md` and `TODO.md` alone, and
cancels outdated runs except on `main` (spec §10.4).
Acceptance criterion: A pull request shows a green "Build and test" check, and a deliberately failing test turns it
red with the reports attached.
Status: Done, verification open
Done in: f8eed9f; report path adapted to the modules in 85c5324.
Verified: Not checked from here (no `gh` CLI). Open: a green check on a pull request of the two-module version; see task 35.

## 30. CI: publish Docker images
Goal: Pushes to `main` publish both images to GHCR.
Description: Add a job after the tests that builds the backend and frontend images from the shared `Dockerfile`,
pushes them as `ghcr.io/<owner>/personal-site-engine-backend` and `…-frontend` with the tags `latest` and the commit
hash on `main` and manual runs, and only builds them for pull requests. Use the build cache per module.
Acceptance criterion: After a push to `main` both packages show the new tags on GHCR; a pull request builds both
images without pushing.
Status: Done, verification open
Done in: f8eed9f (one image); two images in 85c5324.
Verified: Open: the two new packages are only published once 85c5324 is on `main`; see task 35.

## 31. CI: switchable deployment
Goal: `main` deploys to the server when deployment is switched on.
Description: Add a deploy job that runs on `main` only, for pushes when the repository variable `DEPLOY_ENABLED` is
`true` or for manual runs with "Deploy" ticked. It checks the required secrets, copies `compose.yaml` and `info/` via
rsync over SSH with a pinned host key, pulls the images and recreates the containers with `--remove-orphans`, never
touching `data/` or `.env` on the server (spec §10.4, §13).
Acceptance criterion: With `DEPLOY_ENABLED` off a push skips the job and stays green; with it on, a changed post
appears on the live site after the run; missing secrets fail the job with their names.
Status: Done, verification open
Done in: f8eed9f; adapted to two services in 85c5324.
Verified: Open: a deployment of the two-service version; see task 35.

## 32. Documentation
Goal: A newcomer can run, configure and deploy the site from the README.
Description: Write the README: what the site does, the two-service architecture, quick start locally and with Docker,
the content folder and every `site.yaml` key, writing posts and formulas, publications and DBLP exports, the REST
API, comments and the admin token, CI/CD with its secrets, environment variables and the project layout.
Acceptance criterion: Someone who has not seen the project starts it locally and with Docker, adds a post and
deletes a comment by following only the README.
Status: Done, verification open
Done in: 44fc6ec; extended in f8eed9f and 85c5324.
Verified: Open: a walkthrough by someone new to the project; see task 38.
