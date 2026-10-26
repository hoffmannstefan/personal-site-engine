# Backlog

Remaining tasks for Personal Site Engine as described in [spec.md](spec.md). Finished tasks are in
[tasks-done.md](tasks-done.md); the numbers continue from there. Each task fits in one session and says what it
expects to exist in the code, so it can be read on its own.

Conventions for every task: Spring Boot 3.5, Java 21, Maven wrapper; `./mvnw verify` stays green; new behaviour comes
with tests.

Suggested order: 35, 39, 37, 36, 34, 38.

## 34. Clean up the rate limiter
Goal: The rate limiter does not keep addresses longer than needed.
Description: The in-memory limiter removes an address only when that address comments again, so addresses of
one-time commenters stay until the backend restarts. Remove expired windows periodically or on access so memory and
retained addresses stay bounded (spec §13).
Acceptance criterion: A unit test with a controllable clock shows that an address that commented once is gone from
the limiter after its window has passed, without that address commenting again.

## 35. Set up the vServer and switch production over
Goal: Production, development and the dashboards run on the vServer behind its Caddy.
Description: Everything the repository needs is in place: memory limits, health checks, the extra Compose files, the
Caddy sites and the deploy of `main` to production and `develop` to development. What remains are the one-time steps
on the server and in GitHub in `deploy/README.md`: swap, the network `edge`, Caddy, folders and `.env` files,
GitHub environments and secrets, then the stack, the switch of production and the development site. Afterwards
delete the old `personal-site-engine` package on GHCR.
Acceptance criterion: https://www.stefan-hoffmann.de is served by `site-production-frontend` with the comments from
before the switch; https://dev.stefan-hoffmann.de asks for the password and shows the development site;
https://grafana.stefan-hoffmann.de asks for a login and shows both environments; `free -h` on the server shows
memory left with the development site running.

## 36. Check comments, formulas and persistence in a browser
Goal: The parts that automated tests cannot see are confirmed by hand.
Description: Start the site with `docker compose up -d --build` and use it in a browser. Post a comment and a reply,
trigger an error (e.g. an empty name), and post a comment containing `<b>markup</b>`; open a post with formulas in
light and dark mode. Then run `docker compose down` and `docker compose up -d` and reload.
Acceptance criterion: Comments and replies appear without reloading the page, the error message is shown, markup
appears as text, formulas are typeset and readable in both modes, and all comments are still there after `down` and
`up`.

## 37. Close the backend's test gaps
Goal: Every rule of the comment and publication requirements has a test.
Description: Four behaviours are implemented but untested: BibTeX authors written as "Last, First" are shown as
"First Last"; a reply whose parent belongs to another post is rejected with 400; a comment on a `link` entry (which
has no page) is rejected with 404; and deleting a comment is refused when no admin token is configured. Add tests for
these, and fix the code only if a test shows a bug.
Acceptance criterion: `./mvnw -pl backend test` passes with a new test for each of the four cases.

## 38. Walk through the README as a newcomer
Goal: The README is confirmed to be enough to run and use the site.
Description: Ask someone who has not worked on the project to follow only the README: start the site locally with
Maven and with Docker Compose, add a post, and delete a comment with the admin token. Note every place where they get
stuck or need to ask, and fix the README there.
Acceptance criterion: The newcomer completes all four steps without help, or the README is changed until a second
attempt succeeds.

## 39. Measure the Nextcloud's memory on the server
Goal: The memory budget for the vServer rests on numbers, not estimates.
Description: The budget in spec §10.5 measures everything except the Nextcloud on the same server, which is estimated
at 300–500 MB. Measure it on the server (`free -h`, `ps -eo rss,comm --sort=-rss | head`, with and without the
development site running) and adjust the memory limits or the development site's sleep schedule if the budget does
not hold.
Acceptance criterion: Spec §10.5 states the measured value, and with production, development, the stack and the
Nextcloud running, `free -h` shows memory available and the swap nearly unused.
