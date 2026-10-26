# Personal Site Engine

## Product and Technical Specification — v1.0 (backend/frontend split)

**Status:** Draft

**Date:** October 26, 2026

**Audience:** Site owner, contributors, reviewers and coding agents working on this repository

**Scope:** The system as built on branch `split-backend-frontend` (commits `85c5324`, `b743627`), not yet merged into
`main`.

## 1. Product summary

Personal Site Engine is a small, self-hosted engine for a personal academic or developer website. The site has a
start page with a profile card and introduction, a blog written in Markdown with TeX formulas, and a publication list
generated from a BibTeX file. Visitors can comment on every blog post without an account.

All content lives in plain files in a folder `info/` next to the application. The owner edits these files, pushes
them to the repository, and the CI/CD pipeline deploys them to the server. Content changes need no rebuild, only a
restart of the backend.

The system runs as two services. The **backend** reads the content, keeps the comments in a database and serves both
as a JSON API. The **frontend** renders the pages with Thymeleaf from that API and is the only service visitors talk
to.

## 2. Goals

v1.0 must:

1. Show the owner's profile, blog and publications as fast, server-rendered HTML pages that work without JavaScript
   (except comments).
2. Keep all content in plain, version-controlled files: YAML for the site, Markdown for posts, BibTeX for
   publications.
3. Render Markdown posts with TeX formulas correctly, without the owner having to escape formula characters.
4. Turn a DBLP-style BibTeX export into a readable publication list, including arXiv preprints and links between
   conference papers and their extended journal versions.
5. Let visitors comment on posts, with basic spam protection and a way for the owner to delete comments.
6. Expose the site's content and comments as a REST API.
7. Separate content and data (backend) from presentation (frontend), so either can change or be replaced on its own.
8. Deploy automatically from `main`, with deployment switchable on and off.
9. Keep running with sensible defaults when a content file is missing or broken.

## 3. Non-goals for v1.0

The following are intentionally excluded:

- A web-based editor or admin interface for content
- User accounts, logins or comment moderation queues
- Tag, category or author archive pages
- An RSS or Atom feed
- Syntax highlighting of code blocks
- Diagram rendering (e.g. Mermaid)
- Translations; the site is English only
- Live reloading of content without a restart
- Search
- Horizontal scaling of the backend (the rate limiter and H2 database assume one instance)

These can be considered once the two-service setup runs reliably in production.

## 4. Users and roles

### 4.1 Visitor

An anonymous person using a browser. A visitor can:

- Read the start page, the blog list, posts and the publication list
- Switch the grouping of the publication list
- Switch between light and dark mode
- Read all comments of a post
- Post a comment or a reply under a name of their choice
- Use the public REST API (`/api/**` through the frontend)

A visitor cannot delete or edit comments, including their own.

### 4.2 Site owner

The person who maintains the repository. The owner:

- Edits `info/site.yaml`, the posts in `info/posts/`, `info/publications.bib` and `info/images/`
- Pushes changes to `main`, which builds, tests and (if enabled) deploys them
- Switches automatic deployment on and off (`DEPLOY_ENABLED`) and can deploy manually
- Holds the admin token (`COMMENTS_ADMIN_TOKEN`) and deletes comments through the API

### 4.3 Frontend service

The frontend is a trusted client of the backend. It is the only caller of the backend in production, and its
`X-Forwarded-For` header is the only one the backend trusts (from private and loopback addresses).

## 5. Core user flows

### 5.1 Publish a post

1. The owner adds `info/posts/my-post.md` with front matter (`title`, `date`, optionally `authors`, `categories`,
   `tags`, `draft`, `link`).
2. The owner commits and pushes to `main`.
3. CI runs the tests, builds both images and, if deployment is enabled, copies `compose.yaml` and `info/` to the
   server and recreates the containers.
4. The backend reads the new post at startup; the frontend shows it at `/posts/my-post/` at once, because it caches
   nothing.

Locally the same is achieved with `docker compose restart backend`.

### 5.2 Read a page

1. The visitor opens a page on the frontend.
2. The frontend fetches the site configuration (`/api/site`) and the page's content (e.g. `/api/posts/{slug}`) from
   the backend.
3. The frontend renders the HTML with Thymeleaf and returns it.
4. Posts that contain formulas load MathJax from the frontend (no CDN).

### 5.3 Comment on a post

1. The visitor opens a post. `comments.js` loads `GET /api/posts/{slug}/comments` from the frontend, which forwards it
   to the backend.
2. The visitor enters a name and a comment, optionally as a reply, and submits.
3. The frontend forwards `POST /api/posts/{slug}/comments` with the visitor's address in `X-Forwarded-For`.
4. The backend checks the honeypot, the rate limit and the input, stores the comment and returns it.
5. The comment appears immediately; there is no moderation.

### 5.4 Delete a comment

1. The owner sends `DELETE /api/comments/{id}` with the header `X-Admin-Token`.
2. The backend deletes the comment and its replies.

### 5.5 Update the publication list

1. The owner saves the `.bib` export of their DBLP page in the browser.
2. The owner adds `eprint`/`eprinttype` fields for arXiv preprints and `related` fields for extended versions where
   wanted.
3. The owner replaces `info/publications.bib` (or copies in new entries, to keep earlier edits) and publishes as in
   5.1.

## 6. Functional requirements

### 6.1 Site configuration

- The site configuration is read once at backend startup from `info/site.yaml` (env `SITE_CONFIG`).
- Every key is optional. `title` defaults to `author`, and to "Homepage" without either.
- `posts-per-page` defaults to 6; `external-links-in-new-tab` is on unless set to `false`.
- `home` holds `headline`, `avatar`, `links` (each with `name`, `url` and either `icon` or `icon-class`) and `text`
  (Markdown, rendered to HTML by the backend).
- Image paths are relative to `info/images/` and served under `/images/`; values starting with `/` or `http` are used
  as they are.
- Unknown keys are logged with their line and ignored. A missing or broken file is logged and the defaults apply; the
  backend still starts.

### 6.2 Start page

The start page (`/`) must show:

- The site author as heading, unless empty
- A profile card with avatar, headline and profile links, left out when all three are empty
- The introduction text as HTML

### 6.3 Blog

- Posts are the `*.md` files in `info/posts/` (env `POSTS_DIR`), read once at backend startup. Without the folder the
  blog is empty.
- The file name is the slug and the address: `my-post.md` becomes `/posts/my-post/`. Slugs consist of lower case
  letters, digits and hyphens; other file names are skipped with a warning.
- Front matter must contain `title` and `date` (ISO, e.g. `2026-10-06`); a file without them is skipped with a
  warning.
- `draft: true` hides a post. `link` makes an entry that only appears in the list and points to another site.
- `authors` (or `author`) defaults to the site author. Inline lists (`[a, b]`) and YAML lists are both accepted.
- The list is sorted newest first, then by title, and paginated (`/posts/`, `/posts/page/2/`); pages past the last
  return 404.
- Each list entry shows its date, title and number of comments.
- Each post shows title, date, reading time (212 words per minute, at least 1 minute), authors, categories, tags,
  body and the comment section.
- All routes work with and without a trailing slash.
- Dates are formatted in English ("October 6, 2026") whatever the browser's language.

### 6.4 Markdown and formulas

- Markdown is CommonMark plus tables, strikethrough, autolinks, footnotes and heading anchors.
- Raw HTML in posts is passed through; posts are trusted content.
- Fenced code is shown in a code box without colouring.
- Formulas are recognised before Markdown parsing and protected from it: `$…$` and `\(…\)` inline, `$$…$$` and
  `\[…\]` display, `\begin{env}…\end{env}` environments.
- Inline `$` follows the usual rules: the opening `$` is followed and the closing `$` preceded by a non-space, and the
  closing `$` is not followed by a digit ("costs $5 and $10" stays text). `\$` is a literal dollar.
- Formulas inside code spans and fenced blocks are left as written.
- Every formula is written back with `\( \)` / `\[ \]`, HTML-escaped, so MathJax in the browser never treats `$` as a
  delimiter.
- A post with at least one formula is marked `math: true`; only those pages load MathJax.
- With `external-links-in-new-tab`, Markdown links to other sites get `target="_blank" rel="noopener"`; links written
  as raw HTML keep what was written.

### 6.5 Publications

- The list comes from `info/publications.bib` (env `PUBLICATIONS_FILE`), read once at backend startup. Without the
  file the page shows "Coming soon.".
- The parser handles braced, quoted and bare values, `#` concatenation, `@string` macros, LaTeX accents and escapes.
  A broken entry is skipped with a warning.
- Each entry shows title (linked to `url`, else to `doi`), authors and venue (journal with volume, number and pages;
  or booktitle with series, volume and pages).
- `publications.authors`: `all` shows every name; `coauthors` leaves out the site author and shows the others as
  "(with …)". DBLP's disambiguation numbers ("Jane Roe 0001") are never shown.
- arXiv versions (journal `CoRR` or `arXiv…`, or an arXiv DOI) count as published if a published entry names them as
  `eprint` or has the same normalised title. `publications.arxiv-papers` (`always`, `unpublished`, `never`) decides
  which get an entry of their own; `arxiv-links` adds an "arXiv" link to published entries that name a preprint.
- `related = {key, …}` on a journal article adds "extended version of DLT 2023, Completely Distingui…" and, on the
  conference paper, "extended in Theory Comput. Syst. 2026". The title excerpt length is
  `extended-version-title-length` (default 20, 0 for none). Unknown keys are logged.
- The list is newest first; entries of the same year keep the file's order; undated entries come last.
- Grouping is `year`, `type` (Journal articles, Conference papers, Preprints, Other) or `none`, as configured;
  visitors switch with `?group=`. Unknown values fall back to the configured one.
- Every entry and group has an anchor.

### 6.6 Comments

- Comments belong to a post slug; the post must exist (external link entries cannot be commented).
- Fields: name (1–50 characters) and comment (1–2000 characters), both trimmed; blank values are rejected.
- Threads are one level deep: a reply to a reply is attached to the top-level comment. The parent must belong to the
  same post.
- Comments are plain text. The browser renders them with `textContent`, never as HTML; the backend stores them
  verbatim.
- Spam protection:
  - A hidden honeypot field (`website`). A filled honeypot is answered with 201 but nothing is stored.
  - A per-address sliding-window rate limit, `site.comments.max-per-minute` (default 5), answered with 429.
- There is no moderation queue; comments appear immediately.
- Deleting requires the admin token, compared in constant time, and removes the replies too. Without a configured
  token deleting is disabled.
- Comments are stored in H2 (`./data/comments`) by default; any JDBC database can be configured.

### 6.7 Images and static assets

- The backend serves `info/images/` under `/images/`, with a one-hour cache lifetime.
- The frontend forwards `/images/**` to the backend and serves its own assets: theme CSS, fonts, scripts, favicons
  at the root (`/favicon.svg` etc.) and MathJax under `/webjars/`.

### 6.8 Frontend/backend integration

- The frontend reads the backend at `frontend.backend-url` (env `BACKEND_URL`, default `http://localhost:8081`).
- Connect timeout 2 s, read timeout 10 s.
- The frontend caches nothing; every page makes two backend calls (site and page content).
- `/api/**` (all methods) and `/images/**` (GET) are forwarded unchanged, including the query string and the
  backend's status code.
- Forwarded request headers: `Content-Type`, `Accept`, `If-None-Match`, `If-Modified-Since`, `X-Admin-Token`, plus
  `X-Forwarded-For` set to the visitor's address (any incoming value is replaced). Cookies are not forwarded.
- Forwarded response headers: `Content-Type`, `Cache-Control`, `ETag`, `Last-Modified`. Cookies are not forwarded.
- Paths with `.` or `..` segments, also percent-encoded, or with encoded slashes or backslashes are rejected with 400.

### 6.9 Failure behaviour

- A missing or broken content file never stops the backend from starting (see 6.1, 6.3, 6.5).
- If the backend is unreachable or answers with a server error, every page returns 503 with a "Temporarily
  unavailable" page, and forwarded API calls return 503 as an RFC 9457 problem detail.
- The frontend recovers by itself as soon as the backend is reachable again.
- Unknown pages return the 404 page.

## 7. UX layout

The layout follows the Hugo Coder theme:

- **Menu bar:** site title (link to `/`), Home, Blog, Publications; collapses into a menu button on narrow screens
- **Content:** one centred column
- **Start page:** profile card (240 px wide) to the left of the introduction; stacked on narrow screens
- **Post page:** title, metadata line (date, reading time, authors, categories, tags), body, comment section with
  list and form
- **Footer:** credits for Spring Boot, Thymeleaf and Hugo Coder
- **Light/dark switch:** floating button; follows the system setting until switched

## 8. Accessibility and compatibility

- Pages are server-rendered HTML and readable without JavaScript; only the comment section needs it (with a
  `<noscript>` notice).
- Icons have `aria-hidden` and text alternatives; heading links have screen-reader text.
- The comment list is an `aria-live` region; status messages use `role="status"`.
- The site must work in current desktop and mobile versions of Chrome, Edge, Firefox and Safari.
- Formulas are rendered as SVG, so they follow the light/dark theme.

## 9. Permissions matrix

| Capability | Visitor | Admin token holder | Site owner (repository) | Frontend service |
| --- | ---: | ---: | ---: | ---: |
| Read pages and content API | Yes | Yes | Yes | Yes |
| Read comments | Yes | Yes | Yes | Yes |
| Post a comment | Yes, rate-limited | Yes, rate-limited | Yes, rate-limited | Forwards only |
| Delete a comment | No | Yes | If holding the token | Forwards only |
| Change content | No | No | Yes | No |
| Deploy | No | No | Yes | No |
| Set `X-Forwarded-For` trusted by the backend | No | No | No | Yes |
| Reach the backend directly in production | No | No | Via the server | Yes |

## 10. Architecture

### 10.1 Overview

```
browser ──► frontend :8080 ──HTTP/JSON──► backend :8081 ──► info/ (site.yaml, posts/, publications.bib, images/)
            Thymeleaf pages,              content API,      data/ (comments, H2)
            CSS/JS, MathJax               comments API
```

Both services are Spring Boot 3.5 applications on Java 21, built as modules of one Maven project.

### 10.2 Backend (`backend/`)

- `site/`: `SiteProperties` (technical settings), `Site` (what the site shows), `SiteLoader` (reads `site.yaml`)
- `post/`: `PostService` (loads posts), `MarkdownRenderer` (CommonMark), `MathProtector` (shields formulas)
- `publication/`: `BibTeX` (parser), `PublicationService` (display entries, arXiv handling, grouping)
- `comment/`: entity, repository, service, REST controller, rate limiter
- `web/`: `ContentController` (content API), `ApiExceptionHandler`, `ImageConfig`

All content is loaded into memory at startup. Comments are in a JPA database; the schema is managed by Hibernate
(`ddl-auto=update`).

### 10.3 Frontend (`frontend/`)

- `content/`: `ContentClient` (reads the API) and the records it returns (`Site`, `PostPage`, `Post`,
  `Publications`); `BackendConfig` builds the HTTP client
- `web/`: `PageController` (pages), `SiteAttributes` (provides `site` to every template, turns backend failures into
  503), `BackendProxy` (forwards `/api/**` and `/images/**`)
- `templates/`: `layout.html` and one template per page; `error/404.html`, `error/5xx.html`
- `static/`: theme CSS and fonts, `comments.js`, `mathjax-config.js`, favicons

The frontend defines its own records for the JSON it reads and ignores fields it does not need, so the two services
share no code.

### 10.4 Deployment

- One `Dockerfile` builds either image (`--build-arg MODULE=backend|frontend`); the build stage runs the tests.
- `compose.yaml` runs both: only the frontend publishes a port (8080); the backend mounts `./info` read-only and
  `./data` read-write.
- CI (`.github/workflows/ci.yml`): tests on every push to `main`, every pull request and manual runs; images
  `ghcr.io/hoffmannstefan/personal-site-engine-backend` and `…-frontend`, published on pushes to `main` and manual
  runs; deployment from `main` only, when `DEPLOY_ENABLED` is `true` or a manual run asks for it.
- Deployment copies `compose.yaml` and `info/` (mirrored) via rsync, pulls the images and recreates the containers,
  removing orphans. `data/` and `.env` on the server are never touched.

### 10.5 Scaling

v1.0 assumes one instance of each service. The frontend is stateless and could run several instances. The backend
cannot yet: the rate limiter is in memory and H2 is a local file.

## 11. Data model

### Comment (database table `comments`)

- `id` (generated)
- `post_slug` (up to 100 characters, required)
- `parent_id` (nullable; always a top-level comment)
- `author` (up to 50 characters, required)
- `body` (up to 2000 characters, required)
- `created_at` (required)

Index `idx_comments_post` on (`post_slug`, `created_at`).

### Post (in memory, from `info/posts/*.md`)

- `slug`, `title`, `date`, `readingTime`
- `authors`, `categories`, `tags` (lists)
- `body` (HTML)
- `externalUrl` (nullable; set for link entries)
- `math` (whether the post contains formulas)

### Site (in memory, from `info/site.yaml`)

- `title`, `author`, `description`, `keywords`, `postsPerPage`, `externalLinksInNewTab`
- `home`: `headline`, `avatar`, `links` (`name`, `url`, `icon`, `iconClass`), `html`
- `publications`: `arxivPapers`, `arxivLinks`, `groupBy`, `authors`, `extendedVersionLinks`,
  `extendedVersionTitleLength`

### Publication (in memory, from `info/publications.bib`)

- `authors`, `title`, `venue`, `year` (nullable), `link` (nullable), `preprint` (nullable)
- `type` (`JOURNAL`, `CONFERENCE`, `PREPRINT`, `OTHER`)
- `id` (anchor, from the BibTeX key)
- `extensionOf`, `extendedIn` (references: `text`, `id`, `title`)

## 12. API surface

The backend serves these endpoints; the frontend forwards them unchanged, so they work on the public site too.

| Method | Endpoint | Purpose | Status codes |
| --- | --- | --- | --- |
| `GET` | `/api/site` | Site configuration, start page text as HTML | 200 |
| `GET` | `/api/posts?page=N` | One page of the blog list (`page`, `pages`, `posts` with comment counts) | 200, 404 |
| `GET` | `/api/posts/{slug}` | One post with HTML body | 200, 404 |
| `GET` | `/api/publications?group=` | Publication list in groups (`groupBy`, `groups`) | 200 |
| `GET` | `/api/posts/{slug}/comments` | All comments of a post, oldest first | 200, 404 |
| `POST` | `/api/posts/{slug}/comments` | `{"author","body","parentId?","website?"}` | 201, 400, 404, 429 |
| `DELETE` | `/api/comments/{id}` | Delete a comment and its replies; header `X-Admin-Token` | 204, 403, 404 |
| `GET` | `/images/{path}` | A file from `info/images/` | 200, 404 |

- JSON uses camelCase; dates are ISO (`2026-10-06`), timestamps ISO instants.
- Validation errors are RFC 9457 problem details (`detail` names the field); other errors use Spring Boot's error
  format (`status`, `message`).
- The API is read-only apart from comments, and unversioned.

## 13. Security and privacy requirements

- The backend must not be reachable from the internet; in Compose it has no published port.
- The backend trusts `X-Forwarded-For` only from private and loopback addresses
  (`server.forward-headers-strategy=native`).
- The frontend replaces any incoming `X-Forwarded-For`. Behind a reverse proxy, the frontend must be configured to
  trust it (`SERVER_FORWARD_HEADERS_STRATEGY=native`) only if the proxy overwrites the header.
- Comments are never interpreted as HTML in the browser.
- Posts, `home.text` and raw HTML in them are trusted, owner-written content and are not sanitised.
- The admin token must be long and random (`openssl rand -hex 32`), kept in `.env` on the server (ignored by Git) and
  compared in constant time.
- The proxy forwards only a fixed set of headers in each direction and rejects path traversal.
- Comment input is length-limited and rate-limited; bots are caught by the honeypot.
- Visitors give only a name; no e-mail or IP address is stored with a comment. The rate limiter keeps the addresses
  of recent commenters in memory only, never on disk. An address is dropped the next time it comments after its
  window has passed; otherwise it stays until the backend restarts.
- Deployment uses a dedicated SSH key and a pinned `known_hosts`, both from repository secrets; the key is removed
  after each run.

## 14. Performance and reliability targets

These are proposed targets; none has been measured yet.

| Metric | Target |
| --- | --- |
| Server time for a page (frontend incl. two backend calls) | p95 under 100 ms on the production server |
| Start page transfer size | under 100 KB without MathJax |
| 503 page while the backend is down | within 5 s (two connect timeouts) |
| Backend startup with current content | under 15 s |
| Comments lost on container recreation | none (database on the `./data` volume) |
| Availability | best effort; brief 503s during deploys are accepted |

## 15. Observability

Current state (logs only):

- Backend: number of posts and publications loaded, skipped posts and BibTeX entries with reasons, unknown keys in
  `site.yaml`, unknown `related` keys
- Frontend: a warning for every failed backend call, with the reason
- Both: Spring Boot startup and error logs, readable with `docker compose logs`

Not yet available: health endpoints, metrics, request logs, alerting.

Logs must not contain the admin token or comment bodies.

## 16. Acceptance criteria

v1.0 is accepted when all of the following are true. Items 1–9 are covered by automated tests (`./mvnw verify`,
72 tests); items 10–12 were checked manually with Docker Compose.

1. The start page, blog list, posts and publications render from the backend's content, with and without a trailing
   slash (`PagesTest`, `ContentApiTest`).
2. Formulas survive Markdown unchanged, including `_`, `*`, `|`, `<`, `&`, and "$5 and $10" stays text
   (`MarkdownRendererTest`).
3. Only posts with formulas load MathJax, and MathJax is served by the frontend (`PagesTest`).
4. Broken posts, BibTeX entries and site configuration are skipped or replaced by defaults without stopping startup
   (`PostServiceTest`, `SiteLoaderTest`, `PublicationServiceTest`).
5. arXiv versions, preprint links and extended-version links follow 6.5 (`PublicationServiceTest`).
6. Comments are created, listed, threaded one level deep, validated and protected by the honeypot; deleting needs the
   token and removes replies (`CommentApiTest`).
7. The rate limit allows `max-per-minute` comments per address in a sliding window (`RateLimiterTest`).
8. The proxy forwards method, path, query, body, status and the allowed headers, sets `X-Forwarded-For`, drops
   cookies and rejects path traversal (`BackendTest`).
9. Pages and forwarded API calls return 503 while the backend is down (`PagesTest`).
10. In Docker Compose, only the frontend has a port, and comments posted through it are stored by the backend.
11. Two visitors with different addresses have separate rate limits through the frontend.
12. After the backend is stopped and started again, the site recovers without restarting the frontend.

## 17. Delivery phases

### Phase 1 — Single application (done, `44fc6ec`)

- Start page, blog, publications and comments in one Spring Boot application
- Markdown with formulas, BibTeX parsing, light/dark mode

### Phase 2 — CI/CD (done, `f8eed9f`)

- Tests and Docker image on every push to `main`
- Image on GHCR, switchable deployment via rsync and SSH

### Phase 3 — Backend/frontend split (done on branch, `85c5324`)

- Maven modules `backend` and `frontend`
- Content API, proxy for `/api/**` and `/images/**`, 503 handling
- Two images, Compose with a private backend, deploy with `--remove-orphans`

### Phase 4 — Production rollout of the split (next)

- Check that the server can pull the two new GHCR packages (visibility or `docker login`)
- Merge into `main` and deploy; check that the old `homepage` container is removed and comments are kept
- Health checks so the frontend starts after the backend is ready (avoids brief 503s on deploy)
- Delete the old `personal-site-engine` image on GHCR

## 18. Decisions still required

1. Whether the public site should expose the content API (`/api/site`, `/api/posts`, `/api/publications`) or only the
   comment endpoints.
2. Whether the frontend should cache backend responses, and how a content change would then reach it.
3. How TLS is terminated in front of the frontend on the server, and whether that proxy overwrites
   `X-Forwarded-For`.
4. Whether the H2 database stays, or comments move to PostgreSQL before the site grows.
5. Whether comments need moderation before they appear, or notifications to the owner.
6. Whether the API should be versioned (`/api/v1/…`) before anyone else depends on it.
7. What to do with sample content that shows unsupported features (`info/posts/mermaid-support.md`) and with the
   "under construction" note in `info/site.yaml`.
8. Which of the non-goals (RSS feed, syntax highlighting, archive pages) come next.

## 19. Recommended next step

Complete Phase 4: open a pull request for `split-backend-frontend` so CI runs on it, make sure the server can pull
both images, then merge with `DEPLOY_ENABLED` switched off and deploy manually while watching the logs. In parallel,
decide 18.1 and 18.3, since both affect what the public site exposes and whether the comment rate limit works behind
the production proxy.
