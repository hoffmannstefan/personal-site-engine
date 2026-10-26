# Observability stack

Collects the telemetry of Personal Site Engine and shows it in Grafana. It is a Compose project of its own
(`observability`), separate from the application.

```
frontend ─┐            ┌─► Tempo       traces ─┐
          ├─ OTLP ─► OpenTelemetry ─► Prometheus  metrics ├─► Grafana :3000
backend ──┘           Collector  └─► Loki        logs ───┘
```

| Service | Image | Role |
|---|---|---|
| `otel-collector` | `otel/opentelemetry-collector:0.162.0` | Receives OTLP (gRPC 4317, HTTP 4318) and passes each signal on |
| `prometheus` | `prom/prometheus:v3.15.0` | Metrics, received over OTLP |
| `loki` | `grafana/loki:3.7.8` | Logs, received over OTLP |
| `tempo` | `grafana/tempo:2.10.8` | Traces, received over OTLP |
| `grafana` | `grafana/grafana:13.2.3` | Dashboards and Explore, with the three data sources provisioned |

Data is kept for 30 days in the volumes `prometheus-data`, `loki-data`, `tempo-data` and `grafana-data`.

## Start

From the project folder:

```bash
docker compose -f observability/compose.yaml up -d                                  # 1. the stack
docker compose -f compose.yaml -f observability/connect-app.yaml up -d --build      # 2. the app, sending to it
```

Then open http://localhost:3000. The dashboard **Personal Site** is the home page and can be viewed without logging
in; the admin login is `admin` / `GRAFANA_ADMIN_PASSWORD` (default `admin`).

`connect-app.yaml` adds the app's services to the network `observability` and switches their OpenTelemetry export
on, to `http://otel-collector:4318`, with metrics every 15 seconds. The app stays its own Compose project; without
that file it runs as before, sending nothing. To report a real version for a local build, pass
`VERSION=$(git rev-parse --short HEAD)` to the second command.

Stop with `docker compose -f observability/compose.yaml down` (add `-v` to delete the stored data).

Only Grafana has a port on the host, bound to `127.0.0.1`. Set `GRAFANA_ADDRESS=0.0.0.0` (and a real
`GRAFANA_ADMIN_PASSWORD`) to open it to the network; on a server, an SSH tunnel (`ssh -L 3000:localhost:3000 …`)
keeps it private.

## The dashboard

Two variables at the top filter every panel: **Environment** (`deployment.environment.name`) and **Version**
(`service.version`), both multi-select with "All". The version list only shows versions of the selected
environments.

| Row | Panels |
|---|---|
| Overview | Page views, comments accepted, comments in the database, pages failed because the backend was down, deployed versions per service and environment |
| Traffic | Page views per minute by page, most viewed posts, requests per second, response time (p95), server errors (5xx) |
| Comments | Submissions by outcome, comments by post, delete requests by outcome |
| Content and health | Posts and publications loaded, broken post files, pages failed by source, JVM heap, warnings and errors from the logs |

A log line with a trace links to it in Tempo, and a span links to its logs in Loki.

Panels that count events in a time range (page views, comments, outages) use Prometheus' `increase()`, which
estimates the growth of a counter from its samples every 15 seconds and extrapolates to the edges of the range. The
panels round the result to whole numbers, but it stays approximate, especially with little traffic: a counter's first
event (when it appears with the value 1) is not counted. Page views count every delivered page, including reloads and
bots; they are no reading time and no unique visitors.

## Metrics

Besides the HTTP and JVM metrics of the OpenTelemetry starter, the application records these (Prometheus names in
brackets):

| Metric | Service | Attributes | Meaning |
|---|---|---|---|
| `homepage.page.views` (`homepage_page_views_total`) | frontend | `page`, `post.slug` | Pages shown; 404 and 503 answers do not count |
| `homepage.backend.unavailable` (`homepage_backend_unavailable_total`) | frontend | `source` (`page`, `proxy`) | Answers with 503 because the backend could not be reached |
| `homepage.comment.submissions` (`homepage_comment_submissions_total`) | backend | `outcome`, `post.slug` | `accepted`, `spam` (honeypot), `rate_limited`, `unknown_parent` |
| `homepage.comment.deletions` (`homepage_comment_deletions_total`) | backend | `outcome` | `deleted`, `forbidden` (no or wrong token), `not_found` |
| `homepage.comment.stored` (`homepage_comment_stored`) | backend | | Comments in the database |
| `homepage.content.items` (`homepage_content_items`) | backend | `kind` (`post`, `publication`) | Content loaded at startup |
| `homepage.content.skipped` (`homepage_content_skipped`) | backend | `kind` (`post`) | Markdown files skipped because of a wrong name, title or date |

Every series also has the labels `job` (the service name), `deployment_environment_name` and `service_version`:
Prometheus copies the last two from the resource attributes (`promote_resource_attributes` in `prometheus.yaml`).
In Loki they are stream labels as well.

## Files

- `compose.yaml`: the five services and the network `observability`
- `otel-collector.yaml`: receivers, processors and the three exporters
- `prometheus.yaml`, `loki.yaml`, `tempo.yaml`: storage, retention and the promoted labels
- `grafana/provisioning/`: data sources and the dashboard folder
- `grafana/dashboards/personal-site.json`: the dashboard
- `connect-app.yaml`: the extra Compose file that connects the application
