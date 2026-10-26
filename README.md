# Personal Site Engine

[![CI](https://github.com/hoffmannstefan/personal-site-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/hoffmannstefan/personal-site-engine/actions/workflows/ci.yml)

A small, self-hosted engine for a personal academic or developer website, built with **Spring Boot 3.5 / Java 21**
and server-side **Thymeleaf** templates. All content lives in plain files next to the application: a YAML file for
the site, Markdown files for the blog and a BibTeX file for the publication list. Visitors can comment on every post.

It runs as two services:

* the **backend** (`backend/`, port 8081) reads the content in `info/`, renders the Markdown, keeps the comments
  in its database and serves all of it as a [JSON API](#rest-api);
* the **frontend** (`frontend/`, port 8080) renders the pages with Thymeleaf from that API and passes `/api/` and
  `/images/` on to the backend, so visitors only ever talk to the frontend.

```
browser ──► frontend :8080 ──HTTP/JSON──► backend :8081 ──► info/ (site.yaml, posts/, publications.bib, images/)
            Thymeleaf pages,              content API,      data/ (comments, H2)
            CSS/JS, MathJax               comments API
```

## Features

* **Start page** (`/`): profile card with avatar, headline and profile links next to a short introduction in
  Markdown.
* **Blog** (`/posts/`): posts written in Markdown with front matter, paginated, with reading time, TeX formulas and
  optional external link entries.
* **Publications** (`/publications/`): generated from a BibTeX file (e.g. a DBLP export), grouped by year or type,
  with arXiv preprint links and links between conference papers and their extended journal versions.
* **Comments**: threaded comments under every post, stored in a database, with a JSON API, spam protection and
  token-protected deletion.
* **Math**: MathJax 3 served by the application itself (no CDN), loaded only on pages that contain formulas.
* **Light/dark mode** with a switch in the menu bar.
* **No rebuild for content changes**: the backend reads `info/` at startup, so restarting it publishes changes.
* **REST API**: site, posts, publications and comments as JSON, see [REST API](#rest-api).
* **Telemetry**: both services send traces, metrics and logs via OpenTelemetry, with their own metrics for page
  views, comments and content; a separate observability stack with a Grafana dashboard is in `observability/`, see
  [Telemetry](#telemetry).

Not included: tag/category/author archive pages, an RSS feed, syntax highlighting for code blocks, diagram rendering
(e.g. Mermaid) and translations.

## Quick start

With a JDK 21, in two terminals from the project folder:

```bash
./mvnw -pl backend spring-boot:run     # http://localhost:8081/api/site
./mvnw -pl frontend spring-boot:run    # then open http://localhost:8080
```

```bash
./mvnw test                            # both modules
./mvnw -pl backend test                # one module
./mvnw package && java -jar backend/target/backend-0.0.1-SNAPSHOT.jar   # and frontend/target/frontend-…
```

The backend's content paths (`./info/…`, `./data/`) are relative to the working directory, so start it from the
project folder (`spring-boot:run` does that by itself). Without a running backend the frontend keeps trying for 20
seconds (`BACKEND_RETRY`), so start the backend first; after that, pages answer with "Temporarily unavailable"
(503).

With Docker (the image build compiles the application and runs the tests):

```bash
docker compose up -d --build    # http://localhost:8080
docker compose logs -f
docker compose down
```

Compose starts both services. Only the frontend gets a port on the host (`SITE_PORT`, default 8080); the backend is
reachable only by the frontend, inside the Compose network. Compose mounts `./info` read-only for the content and
`./data` for the comment database into the backend.

* **Memory**: each service has a fixed limit (backend 320 MB, frontend 256 MB) and Java settings that stay well
  inside it; measured under load, the backend uses about 240 MB and the frontend about 150 MB. `BACKEND_MEMORY`,
  `FRONTEND_MEMORY`, `BACKEND_JAVA_OPTIONS` and `FRONTEND_JAVA_OPTIONS` in `.env` change them.
* **Health checks**: a service is healthy once its port is open, which Spring Boot does only after everything is
  loaded. The frontend starts only after the backend is healthy, so a fresh start never shows 503 pages.
* **Restarts**: while the frontend cannot reach the backend (e.g. while it restarts), it tries again for up to 20
  seconds (`BACKEND_RETRY`) before a page fails, so a restart of the backend only makes pages wait. Only a failed
  connection is retried, so a comment is never sent twice.

`./mvnw` needs a full JDK (with `javac`); with only a JRE it stops with "release version 21 not supported". To run
the tests without a local JDK:

```bash
docker run --rm -v "$PWD":/src -v homepage-m2:/root/.m2 -w /src eclipse-temurin:21-jdk ./mvnw -B test
```

This runs as root, so `target/` then belongs to root and a later local `./mvnw` fails with "Permission denied";
remove it with `sudo rm -rf target`.

## Content folder

Everything that makes the site yours lives in `info/`:

```
info/
├── site.yaml          name, texts, start page and Publications settings
├── images/            avatar and link icons, served under /images/
├── posts/             Markdown posts
└── publications.bib   Publications page
```

Only the backend reads `info/`. It is neither bundled into the jar nor copied into the Docker image
(`.dockerignore`): Docker Compose mounts it, so on the server it has to sit next to `compose.yaml`. Changes need no
rebuild, only `docker compose restart backend` (images not even that). The repository ships with sample content to
start from.

## Site configuration

Name, texts, start page and the settings of the Publications page come from `info/site.yaml` (env `SITE_CONFIG`);
the sample file explains every key:

```yaml
title: Jane Roe                   # menu bar and browser tab
author: Jane Roe                  # heading of the start page, <meta name="author">, default author of posts
description: Jane Roe's personal website   # <meta name="description">
keywords: data engineering, blog           # <meta name="keywords">
posts-per-page: 6
external-links-in-new-tab: true   # links to other sites open in a new tab

publications:
  arxiv-papers: unpublished       # own entries for arXiv versions: always, unpublished or never
  arxiv-links: true               # link published papers to their arXiv preprint
  group-by: year                  # year, type or none; visitors can switch on the page
  authors: all                    # all, or coauthors: "(with John Doe)" without the site's author
  extended-version-links: true    # "extended version of DLT 2023, …" / "extended in …" from the BibTeX field related
  extended-version-title-length: 20   # characters of the title after "DLT 2023, "; 0 for none

home:
  headline: Data Engineer
  avatar: avatar.jpg
  links:
    - name: ORCID
      url: https://orcid.org/…
      icon: icons/orcid.png
    - name: GitHub
      url: https://github.com/…
      icon-class: fa-brands fa-github
  text: |
    First paragraph in *Markdown*, with [links](https://example.org).

    Second paragraph.
```

* Every key is optional. `title` defaults to `author`; without `avatar`, `headline` and `links` the card on the left
  of the start page is left out.
* `avatar` and a link's `icon` are paths inside `info/images/` (env `IMAGES_DIR`), which is served under `/images/`;
  a value starting with `/` or `http` is used as it is. Instead of an image, a link can have a Font Awesome icon
  (`icon-class`, e.g. `fa-brands fa-youtube`; the solid, regular and brands sets are included). A link without
  `name` or `url` is skipped.
* `text` is Markdown (raw HTML allowed); an empty line starts a new paragraph.
* `external-links-in-new-tab` (on unless set to `false`) opens every link to another site in a new tab: profile
  links, publications, footer, external entries in the blog list, and the Markdown links in `home.text` and in
  posts. Links written as raw HTML in a post keep the `target` written there.
* The `publications:` keys are described under [Publications](#publications).
* The file is read at startup, so restart after a change; images are served directly and change immediately.
  Unknown keys (typos) are reported in the log with their line and ignored. A missing or broken file is reported
  too and the defaults apply (title "Homepage", empty start page), so the site still starts.

## Writing posts

Add a `.md` file with a front matter header to `info/posts/`:

```markdown
---
title: "My post"
date: 2026-10-06
authors: [Jane Roe]          # optional, defaults to author in info/site.yaml
categories: [Notes]          # optional
tags: [java, math]           # optional
draft: true                  # optional: hides the post
link: https://example.org    # optional: makes a list entry that points to another site (no body)
---

Text with *Markdown* and a formula $e^{i\pi} + 1 = 0$.
```

* **Where:** `info/posts/` (`site.posts-dir`, or env `POSTS_DIR`). Restart to publish (`docker compose restart backend`).
  The file name is the address (`my-post.md` becomes `/posts/my-post/`; lower case letters, digits and hyphens).
  Files with a missing title or date are skipped with a warning in the log. Without the folder the blog is empty.
* **Markdown:** CommonMark plus tables, ~~strikethrough~~, autolinks, footnotes and heading anchors. Headings should
  start at `##`: the title comes from the front matter. Fenced code is shown in a code box, without colouring.
  Raw HTML is passed through, so only put trusted text in these files.
* **Formulas:** `$a_1$` or `\(a_1\)` inline, `$$ ... $$` or `\[ ... \]` for display, `\begin{align} ... \end{align}`
  for numbered equations (`\label` / `\eqref` work). The Markdown parser never sees them, so `_`, `*`, `\\`, `<`,
  `&` and `|` (even inside table cells) need no escaping. The rules are the usual ones for `$`: the opening `$` is
  followed and the closing `$` preceded by a non-space, and the closing `$` is not followed by a digit, so "costs
  $5 and $10" stays text. A literal dollar can also be written `\$`. Formulas inside `code` and fenced blocks are
  shown as written. Use fences, not indentation, for code blocks.
* The reading time ("3-minute read") is computed from the text.
* Example: `info/posts/markdown-with-latex.md`.

## Publications

The list on `/publications/` comes from the BibTeX file `info/publications.bib` (env `PUBLICATIONS_FILE`). Each
entry is shown as title (linked to `url`, or else to `doi`), authors and venue: journal with volume(number) and pages
for `@article`, booktitle with series, volume and pages for `@inproceedings`. LaTeX accents (`{\"u}`), escapes
(`\&`) and `--` are turned into plain text; `author` names may be written as "First Last" or "Last, First". A broken
entry is skipped with a warning in the log.

**Authors.** `publications.authors` in `info/site.yaml` sets how the author line looks:

* `all` (the default): all names, "John Doe, Jane Roe, Max Mustermann";
* `coauthors`: without the site's `author`, the others in brackets, "(with John Doe, Max Mustermann)"; a paper
  without coauthors has no author line. An entry that does not list the site's author shows all names.

Names are compared ignoring case and the number DBLP adds to tell apart people of the same name ("Jane Roe 0001");
that number is never shown.

**arXiv preprints.** A published paper names its arXiv preprint in its own entry, with the usual BibTeX fields:

```bibtex
  eprinttype   = {arXiv},
  eprint       = {2103.09587}
```

(`archivePrefix = {arXiv}` works instead of `eprinttype` too.) Only these fields make a link, so you decide per
entry: the journal version of a conference paper with the same title gets no link unless it names a preprint itself.

* With `publications.arxiv-links` such an entry gets a link "arXiv" to `arxiv.org/abs/…`.
* An arXiv version is an entry with journal `CoRR` (as in DBLP) or `arXiv…`, or with an arXiv DOI
  (`10.48550/arXiv.…`). It counts as published if a published entry names it as `eprint` (whatever its title) or
  has the same title (compared ignoring case, spaces and punctuation).
* `publications.arxiv-papers` decides which arXiv versions get an entry of their own, shown as "arXiv:2512.11007"
  and linked to arxiv.org:
  * `always`: all of them, also next to their published version;
  * `unpublished` (the default): only those that do not count as published, i.e. papers that are only on arXiv;
  * `never`: none.
* `arxiv-links` is on unless set to `false`; an unknown `arxiv-papers`, `group-by` or `authors` value is reported in
  the log and the default applies.

**Extended versions.** A journal article that extends a conference paper names it by its BibTeX key:

```bibtex
  related      = {DBLP:conf/dlt/000123}
```

(several keys separated by commas). With `publications.extended-version-links` (on unless set to `false`):

* the article's entry ends with "extended version of DLT 2023, Completely Distingui…", linked to the conference
  paper's entry on the same page; two or more are joined with "and" ("A, B and C");
* the conference paper's entry ends with "extended in Theory Comput. Syst. 2026", linked back to the article.

The link text is the venue's acronym with the year, or else the venue up to its first comma ("Prague Stringology
Conference 2021"); for a journal it is its name without volume. After "extended version of" follow the first
`publications.extended-version-title-length` characters of the title (default 20; a shorter title in full, `0` for
none), cut with "…". Both links show the full title as a tooltip. Every entry has an anchor made from its key
(`#dblp-conf-dlt-000123`); the entry a link jumps to is highlighted. A key that is not in the file is reported in
the log; a key of an entry that is not shown (e.g. a hidden arXiv version) is left out.

**Grouping.** The newest papers always come first; papers of the same year keep the order of the file, undated ones
come last. `publications.group-by` sets how the page starts out:

* `year`: a heading per year (newest first), "Undated" last;
* `type`: "Journal articles" (`@article`), "Conference papers" (`@inproceedings`), "Preprints" (the arXiv versions
  that are listed, see `arxiv-papers`) and "Other" (any other entry type), in this order;
* `none` (also the default without the key): one list without headings.

A line "Group by: year · type · none" above the list lets visitors switch (`/publications/?group=type`); an unknown
value falls back to the configured one. Empty groups are left out, and each heading has an anchor (`#2024`,
`#journal-articles`).

The file is read at startup, so restart after a change (`docker compose restart backend`). Without the file the page shows
"Coming soon.".

**Using a DBLP export.** Save the `.bib` link of your DBLP author page from the browser (DBLP's bot protection
blocks `curl`). The export has no `eprint` or `related` fields, so add them by hand where wanted. Once you have
edited the file, do not replace it with a fresh export, or the edits are lost: copy new entries over instead.

## REST API

The backend serves everything as JSON. The frontend passes `/api/**` on unchanged, so the same paths work on the
public site (`https://…/api/posts`) and on the backend itself (`http://localhost:8081/api/posts`).

| Method | Path | |
|---|---|---|
| `GET` | `/api/site` | Name, texts, start page (text as HTML) and settings from `info/site.yaml` |
| `GET` | `/api/posts?page=1` | One page of the blog list, newest first, with the number of comments: `{"page","pages","posts"}`; 404 past the last page |
| `GET` | `/api/posts/{slug}` | A post with its body rendered to HTML; 404 for unknown posts and external link entries |
| `GET` | `/api/publications?group=year` | The publication list in groups (`year`, `type` or `none`; without `group` as configured) |
| `GET` | `/api/posts/{slug}/comments` | All comments of a post, oldest first |
| `POST` | `/api/posts/{slug}/comments` | `{"author","body","parentId?"}` → 201 |
| `DELETE` | `/api/comments/{id}` | Header `X-Admin-Token`; also removes the replies |
| `GET` | `/images/…` | The files in `info/images/` |

The content changes only with a restart of the backend; the frontend caches nothing, so it shows the change at once.

## Comments

* Visitors give a name and a comment (no account, no e-mail). Replies are one level deep.
* Comments are plain text: the page renders them with `textContent`, so markup is never interpreted.
* Spam protection: hidden honeypot field, per-address rate limit (`site.comments.max-per-minute`, default 5),
  length limits (name 50, comment 2000). There is no moderation queue: comments appear immediately.
* Deleting is disabled until you set `COMMENTS_ADMIN_TOKEN`, e.g. in a `.env` file next to `compose.yaml` (ignored by
  Git). Use a long random value (`openssl rand -hex 32`), then:
  `curl -X DELETE -H 'X-Admin-Token: …' http://localhost:8080/api/comments/12`
* The frontend passes on the visitor's address as `X-Forwarded-For`, and the backend trusts it from private and
  loopback addresses only (`server.forward-headers-strategy=native`), so the rate limit counts per visitor.
* If a reverse proxy sits in front of the frontend, set `SERVER_FORWARD_HEADERS_STRATEGY=native` for the frontend too
  (only if the proxy overwrites `X-Forwarded-For`), otherwise every visitor shares the proxy's rate limit. On the
  server, `deploy/site-edge.yaml` does this for Caddy, which overwrites the header.

Comments go to the backend's file-based H2 database in `./data/` by default; for PostgreSQL add its JDBC driver to
`backend/pom.xml` and set `DB_URL` etc.

## Math formulas

Formulas are typeset by [MathJax](https://www.mathjax.org/) 3 (SVG output, so they follow the light/dark theme),
which comes from the frontend's `org.webjars.npm:mathjax` dependency and is served by the frontend itself: no CDN,
works offline. The backend marks posts that contain a formula (`"math": true`), and their pages load MathJax. The
backend writes every formula as `\( \)` / `\[ \]`, so `$` is not a delimiter in the browser (see
`frontend/src/main/resources/static/js/mathjax-config.js`).

## Telemetry

Both services are instrumented with OpenTelemetry (the `opentelemetry-spring-boot-starter`): traces of every HTTP
request, of the frontend's calls to the backend and of the backend's database queries, HTTP and JVM metrics, and their
log records, sent over OTLP (http/protobuf). The frontend passes the trace on to the backend in the W3C `traceparent`
header, so one trace covers a page request in both services:

```
[homepage-frontend] GET /posts/{slug}/
├── [homepage-frontend] GET (call to the backend)
│   └── [homepage-backend] GET /api/site
└── [homepage-frontend] GET (call to the backend)
    └── [homepage-backend] GET /api/posts/{slug}
```

Calls forwarded to the backend (comments, images) continue the trace the same way. Every span, metric and log record
names

| Attribute | Value |
|---|---|
| `service.name` | `homepage-frontend` or `homepage-backend` (`spring.application.name`) |
| `deployment.environment.name` | `DEPLOYMENT_ENVIRONMENT`: `local` by default, `production` when deployed by CI |
| `service.version` | `SERVICE_VERSION`: the commit hash in images built by CI (as in the image tag `sha-…`), `dev` in images built locally unless the build argument `VERSION` is given (e.g. `VERSION=$(git rev-parse --short HEAD) docker compose build`), the project version (`0.0.1-SNAPSHOT`) with `./mvnw` |

Export is **off by default**, so nothing is sent and nothing is logged about a missing receiver. To switch it on, point
both services at an OTLP receiver (an OpenTelemetry Collector, Grafana, Jaeger, …), e.g. in `.env` next to
`compose.yaml`:

```bash
OTEL_SDK_DISABLED=false
OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4318
```

Besides the HTTP and JVM metrics, the services record their own: page views per page and post, comments by outcome
(accepted, spam, rate-limited, unknown parent), delete requests by outcome, stored comments, content loaded and
skipped at startup, and 503 answers while the backend is down. The list is in
[observability/README.md](observability/README.md#metrics).

**Local observability stack.** `observability/` holds a separate Compose project with an OpenTelemetry Collector,
Prometheus, Loki, Tempo and Grafana, including a dashboard filterable by environment and version:

```bash
docker compose -f observability/compose.yaml up -d                                  # the stack
docker compose -f compose.yaml -f observability/connect-app.yaml up -d --build      # the app, sending to it
```

Grafana is then at http://localhost:3000; see [observability/README.md](observability/README.md).

All standard `OTEL_*` variables work (e.g. `OTEL_METRIC_EXPORT_INTERVAL`, `OTEL_EXPORTER_OTLP_HEADERS`). The deploy job
sets `DEPLOYMENT_ENVIRONMENT` (`production` or `development`) itself, and each site's `.env` on the server names it as
well. Compose passes these variables to both services.

**Privacy.** The collector removes the visitor's IP address and browser (`client.address`, `user_agent.original` and
the like) from every span, log record and metric before anything is stored. Query parameters of SQL statements are
not recorded, so comment texts do not end up in the telemetry either.

## Continuous integration and deployment

The site runs on one vServer in two environments, **production** (https://www.stefan-hoffmann.de, from `main`) and
**development** (https://dev.stefan-hoffmann.de, from `develop`, behind a password), next to the observability stack
(https://grafana.stefan-hoffmann.de) and behind the server's Caddy. The one-time setup of the server and of GitHub
is in [deploy/README.md](deploy/README.md).

The GitHub Actions workflow `.github/workflows/ci.yml` runs on every push to `main` and `develop`, on pull requests
and by hand:

1. **Build and test**: `./mvnw verify` with Java 21, both modules.
2. **Docker images**: built after green tests, one per module from the same `Dockerfile` (`--build-arg
   MODULE=backend|frontend`, and `VERSION` with the short commit hash for the telemetry); pushes publish them as
   `ghcr.io/hoffmannstefan/personal-site-engine-backend` and `…-frontend`, tagged with the commit (`sha-1a2b3c4`),
   plus `latest` for `main` and `develop` for `develop`.
3. **Deploy**: `main` to production, `develop` to development, each into the folder named by the secret
   `DEPLOY_PATH` of its GitHub environment. It copies `compose.yaml`, `info/` (mirrored, so files deleted in the
   repository disappear on the server too) and the two extra Compose files, pins the images of this commit
   (`IMAGE_TAG=sha-…`), recreates the backend (it reads the content at startup) and the frontend if its image
   changed, and waits until both are healthy. `data/` and `.env` on the server are not touched. Visitors see no
   errors meanwhile: the frontend waits for the restarting backend, Caddy for the restarting frontend (tested: every
   request answered with 200, a comment posted during the restart arrived).

The observability stack has a workflow of its own, `.github/workflows/observability.yml`: it deploys `observability/`
to the folder `OBSERVABILITY_PATH` when it changes on `main`.

Deployment is off by default. Pushes deploy only while the repository variable `DEPLOY_ENABLED` is `true`
(Settings → Secrets and variables → Actions → Variables, or `gh variable set DEPLOY_ENABLED --body true`). A manual
run (Actions → CI or Observability → Run workflow) with "Deploy" ticked deploys regardless of the variable. Changes to
`README.md` and `TODO.md` alone start no run at all.

The server access comes from the repository secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` (a private key used
only for deployment) and `DEPLOY_KNOWN_HOSTS` (output of `ssh-keyscan <host>`). The server needs Docker 25 with
Compose 2.24 or newer and rsync; if the packages on ghcr.io are private, log in there once with `docker login ghcr.io`.

## Technical configuration

Three places, kept apart on purpose:

* `info/site.yaml`: what the site shows, see [Site configuration](#site-configuration).
* `backend/src/main/resources/application.properties`: where the content lives (`site.config-file`,
  `site.posts-dir`, `site.publications-file`, `site.images-dir`), the comment settings and the database.
* `frontend/src/main/resources/application.properties`: where the backend runs (`frontend.backend-url`) and how long
  the frontend waits for it.

Environment variables:

| Variable | Service | Purpose |
|---|---|---|
| `SITE_CONFIG`, `POSTS_DIR`, `PUBLICATIONS_FILE`, `IMAGES_DIR` | backend | Content locations |
| `COMMENTS_ADMIN_TOKEN` | backend | Enables deleting comments |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | backend | Comment database |
| `DEPLOYMENT_ENVIRONMENT`, `SERVICE_VERSION` | both | Environment and version in the telemetry, see [Telemetry](#telemetry) |
| `OTEL_SDK_DISABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_*` | both | Switch on and configure the OpenTelemetry export |
| `BACKEND_URL` | frontend | Address of the backend (default `http://localhost:8081`; `http://site-backend:8081` in Compose, a name only the own project knows) |
| `BACKEND_RETRY` | frontend | How long a call tries again while the backend cannot be reached (default `20s`) |
| `THYMELEAF_CACHE=false` | frontend | Live template editing |
| `PORT` | both | Port to listen on (backend 8081, frontend 8080) |

In `.env` next to `compose.yaml`, read by Compose:

| Variable | Purpose |
|---|---|
| `SITE_PORT` | Host port of the frontend (default `8080`; `127.0.0.1:18080` on the server, behind Caddy) |
| `BACKEND_MEMORY`, `FRONTEND_MEMORY` | Memory limits (default `320m`, `256m`) |
| `BACKEND_JAVA_OPTIONS`, `FRONTEND_JAVA_OPTIONS` | Java settings, replacing the measured defaults in `compose.yaml` |
| `IMAGE_TAG` | Image version (default `latest`; the deploy pins `sha-…`) |
| `COMPOSE_FILE`, `SITE_NAME` | On the server: the extra Compose files and the frontend's name for Caddy, see [deploy/README.md](deploy/README.md) |

## Project layout

A Maven project with two modules; the parent `pom.xml` builds both.

**Backend**, Java packages under `backend/src/main/java/com/homepage/backend/`:

* `post/` loads the posts from `info/posts/` and renders Markdown; `MathProtector` shields formulas from the
  Markdown parser.
* `site/` holds the configuration: `SiteProperties` (the technical `site.*` settings from `application.properties`),
  `Site` (what the site shows) and `SiteLoader`, which reads `info/site.yaml` into `Site`.
* `publication/` reads the BibTeX file: `BibTeX` parses it and turns LaTeX into text, `PublicationService` makes the
  display entries, links the arXiv preprints and groups the list.
* `comment/` entity, repository, service, REST controller, rate limiter.
* `web/` the content API (`ContentController`), error handling; `ImageConfig` serves `info/images/` under `/images/`.
* `telemetry/` the backend's own metrics (`BackendMetrics`): comments by outcome, delete requests, stored comments,
  content loaded and skipped at startup.

The backend's tests use their own content in `backend/src/test/resources/` (`info/site.yaml`, `info/posts/`,
`publications-test.bib`), so the content in `info/` can change freely.

**Frontend**, Java packages under `frontend/src/main/java/com/homepage/frontend/`:

* `content/` reads the backend's API: `ContentClient` and the records it returns (`Site`, `PostPage`, `Post`,
  `Publications`); `RetryWhileBackendStarts` tries a call again while the backend cannot be reached.
* `web/` page controller; `SiteAttributes` provides `site` to every template and turns a backend that is down into
  a 503 page; `BackendProxy` passes `/api/**` and `/images/**` on to the backend; `FrontendMetrics` counts page
  views and 503 answers.

Resources under `frontend/src/main/resources/`:

* `templates/`: `layout.html` is the skeleton of every page (head, menu, footer), each page template only holds its
  own content. The site from `info/site.yaml` is available in every template as `site`. `error/` has the 404 and
  5xx pages.
* `static/js/comments.js` is the comment UI; `static/css/site.css` holds the site's own styles (colours for
  light/dark, comments, publications); the favicons sit at the top of `static/`.

The frontend's tests replace the backend with a stub (`PagesTest`, `FrontendMetricsTest`) or with a small fake HTTP
server (`BackendTest`, `TraceTest`, `RetryTest`, the last one starting only after the request), so they run without
a backend.

The telemetry tests switch the OpenTelemetry SDK on and read what would be exported from memory: the resource
attributes (`TelemetryTest`, `TelemetryDefaultsTest`), the backend's and frontend's own metrics (`BackendMetricsTest`,
`FrontendMetricsTest`) and the trace passed from the frontend to the backend (`TraceTest`).

**Observability**, `observability/`: a separate Compose project with the OpenTelemetry Collector, Prometheus, Loki,
Tempo and Grafana, its configuration, the dashboard `grafana/dashboards/personal-site.json` and `connect-app.yaml`,
which connects the application to it; see [observability/README.md](observability/README.md).

**Deployment**, `deploy/`: what the server needs besides the images: the extra Compose files for Caddy
(`site-edge.yaml`, `observability-edge.yaml`), the sites for Caddy's Caddyfile, templates of the three `.env` files,
and the setup guide [deploy/README.md](deploy/README.md).

## Credits

The base stylesheet, the light/dark switch script and the Font Awesome subset come from the
[Hugo Coder](https://github.com/luizdepra/hugo-coder/) theme (MIT, © Luiz de Prá) and are included unchanged
(`frontend/src/main/resources/static/css/coder*.css`, `static/js/coder.min.js`, `static/fonts/`). The footer
credits them in `templates/layout.html`.
