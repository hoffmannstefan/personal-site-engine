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
| `otel-collector` | `otel/opentelemetry-collector-contrib:0.162.0` | Receives OTLP (gRPC 4317, HTTP 4318), removes visitor data and passes each signal on |
| `prometheus` | `prom/prometheus:v3.15.0` | Metrics, received over OTLP |
| `loki` | `grafana/loki:3.7.8` | Logs, received over OTLP |
| `tempo` | `grafana/tempo:2.10.8` | Traces, received over OTLP |
| `grafana` | `grafana/grafana:13.2.3` | Dashboards and Explore, with the three data sources provisioned |

Data is kept for 30 days in the volumes `prometheus-data`, `loki-data`, `tempo-data` and `grafana-data`. Every
service has a memory limit (collector 128 MB, Prometheus, Loki and Tempo 192 MB each, Grafana 288 MB; measured under
load all five together use about 580 MB); `COLLECTOR_MEMORY`, `GRAFANA_MEMORY` etc. in `observability/.env` change
them (see [Settings](#settings)).

**Privacy.** The collector removes the visitor's IP address, port and browser (`client.address`, `network.peer.*`,
`user_agent.original`) and any recorded HTTP headers from every span, log record and metric before passing anything
on (`attributes/privacy` in `otel-collector.yaml`). This is why the stack uses the contrib build of the collector.

**How much is kept.** `COLLECTOR_KEEP` chooses a file that is laid over `otel-collector.yaml`:

| `COLLECTOR_KEEP` | Traces | Logs | For |
|---|---|---|---|
| `all` (default) | all | all | local use |
| `some` | every one with an error or slower than 500 ms, and 10 % of the others | WARN and above | the 2 GB server |

Metrics, and so the dashboard, are complete either way: the applications compute them before anything is left out.
Measured with `some` under load, about 13 % of the traces were kept, every error among them.

**On the server**, Caddy reaches Grafana at https://grafana.stefan-hoffmann.de, with anonymous viewing switched off;
see [deploy/README.md](../deploy/README.md).

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
`GRAFANA_ADMIN_PASSWORD`) to open it to the network. On the vServer, Caddy makes it reachable at
https://grafana.stefan-hoffmann.de instead, with the login required (see [deploy/README.md](../deploy/README.md)).

## Settings

In `observability/.env`, read by Compose (on the server see
[deploy/observability.env.example](../deploy/observability.env.example)):

| Variable | Default | Purpose |
|---|---|---|
| `COLLECTOR_KEEP` | `all` | How many traces and logs are kept: `all` or `some` (see below) |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Admin password; only taken on Grafana's very first start |
| `GRAFANA_ANONYMOUS` | `true` | Dashboards viewable without login; `false` on a server reachable from the internet |
| `GRAFANA_ROOT_URL` | `http://localhost:3000` | The address Grafana is reached at, for its links and redirects |
| `GRAFANA_ADDRESS`, `GRAFANA_PORT` | `127.0.0.1`, `3000` | Where Grafana's port is published on the host |
| `METRICS_RETENTION` | `30d` | How long Prometheus keeps metrics (Loki and Tempo: 30 days, in their config files) |
| `COLLECTOR_MEMORY`, `PROMETHEUS_MEMORY`, `LOKI_MEMORY`, `TEMPO_MEMORY`, `GRAFANA_MEMORY` | `128m`, `192m`, `192m`, `192m`, `288m` | Memory limits |
| `COLLECTOR_GOMEMLIMIT`, `LOKI_GOMEMLIMIT`, `TEMPO_GOMEMLIMIT`, `GRAFANA_GOMEMLIMIT` | `100MiB`, `160MiB`, `160MiB`, `230MiB` | Where these Go services' garbage collector starts working harder; keep it below the limit (Prometheus derives its own from the limit) |

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
- `otel-collector.yaml`: receivers, the privacy processor and the three exporters
- `collector-keep-all.yaml`, `collector-keep-some.yaml`: how many traces and logs are kept (`COLLECTOR_KEEP`)
- `prometheus.yaml`, `loki.yaml`, `tempo.yaml`: storage, retention and the promoted labels
- `grafana/provisioning/`: data sources and the dashboard folder
- `grafana/dashboards/personal-site.json`: the dashboard
- `connect-app.yaml`: the extra Compose file that connects the application
