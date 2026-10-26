# Backlog

Remaining tasks for Personal Site Engine as described in [spec.md](spec.md). Finished tasks are in
[tasks-done.md](tasks-done.md); the numbers continue from there. Each task fits in one session and says what it
expects to exist in the code, so it can be read on its own.

Conventions for every task: Spring Boot 3.5, Java 21, Maven wrapper; `./mvnw verify` stays green; new behaviour comes
with tests.

Suggested order: 37, 36, 33, 35, 34, 38.

## 33. Start the frontend after the backend is ready
Goal: Deploys no longer show 503 pages while the backend starts.
Description: Add a health endpoint to the backend and a Compose health check that uses it, and make the frontend
depend on the backend being healthy. The runtime image has no `curl`, so the check needs a tool that is available or
an alternative such as a small Java-based probe (spec §17, Phase 4).
Acceptance criterion: During `docker compose up -d --force-recreate`, repeated requests to the start page never
return 503 once the frontend container is running.

## 34. Clean up the rate limiter
Goal: The rate limiter does not keep addresses longer than needed.
Description: The in-memory limiter removes an address only when that address comments again, so addresses of
one-time commenters stay until the backend restarts. Remove expired windows periodically or on access so memory and
retained addresses stay bounded (spec §13).
Acceptance criterion: A unit test with a controllable clock shows that an address that commented once is gone from
the limiter after its window has passed, without that address commenting again.

## 35. Roll out the backend/frontend split
Goal: The two-service version runs on the production server.
Description: The split into backend and frontend is on branch `split-backend-frontend`, not yet on `main`. Open a
pull request so CI runs, make sure the server can pull the new packages
`ghcr.io/hoffmannstefan/personal-site-engine-backend` and `…-frontend` (package visibility or `docker login
ghcr.io`), then merge with `DEPLOY_ENABLED` off and deploy with a manual run while watching the logs. Afterwards
delete the old `personal-site-engine` package on GHCR.
Acceptance criterion: The pull request has a green "Build and test" check; both packages have the tags `latest` and
the commit hash; on the server only the backend and frontend containers run (the old `homepage` container is gone),
the site answers, and comments from before the rollout are still shown.

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
