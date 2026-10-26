# Deploying to the vServer

Production, development and the observability stack on one 2 GB vServer, next to the Caddy and the Nextcloud that
already run there. This guide covers the one-time setup on the server and in GitHub; after that, every push deploys
by itself.

```
Internet ──► Caddy :443 ── network "edge" ──┬── www.stefan-hoffmann.de      ─► site-production-frontend  ─► backend ─► data/, info/
                                            ├── dev.stefan-hoffmann.de      ─► site-development-frontend ─► backend ─► data/, info/
                                            └── grafana.stefan-hoffmann.de  ─► observability-grafana
site-production, site-development ── network "observability" ─► collector ─► Prometheus, Loki, Tempo ─► Grafana
```

| Folder on the server | Compose project | Deployed by |
|---|---|---|
| `DEPLOY_PATH` of production (the existing one) | the site, production | `ci.yml`, pushes to `main` |
| `DEPLOY_PATH` of development, e.g. `/srv/site/development` | the site, development | `ci.yml`, pushes to `develop` |
| `OBSERVABILITY_PATH`, e.g. `/srv/observability` | `observability` | `observability.yml`, changes to `observability/` on `main` |

## Memory

Measured locally with the same images and limits under load (production and development at the same time):

| | Peak | Limit |
|---|---|---|
| Production: backend + frontend | about 390 MB | 320 + 256 MB |
| Development: backend + frontend | about 390 MB | 320 + 256 MB |
| Observability: collector, Prometheus, Loki, Tempo, Grafana | about 580 MB | 128 + 192 + 192 + 192 + 288 MB |
| System, Docker, Caddy | about 250 MB (estimate) | |
| Nextcloud | measure on the server | |

Production, observability and the Nextcloud fit into 2 GB. The development site fits on top only tightly, so it
**sleeps when it is not needed** (step 8) and the server gets **2 GB of swap** as a safety net (step 1). Every
container has a fixed limit: if one ever needs more, only that one restarts, not the production site or the Nextcloud.

## Requirements

On the server: Docker Engine 25 or newer and Docker Compose 2.24 or newer (`docker version`, `docker compose
version`), Caddy 2.8 or newer (`docker exec <caddy container> caddy version`), and rsync, which the deploys copy
the files with (`rsync --version`; on Debian `sudo apt install rsync`). The DNS records `dev` and `grafana` point to
the server.

## 1. Swap

Only if `swapon --show` shows none yet:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
# Use the swap only when memory is really short, not for things that are merely idle:
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf
sudo sysctl -p /etc/sysctl.d/99-swappiness.conf
```

## 2. The network "edge" and Caddy

Caddy reaches the sites and Grafana over a Docker network of their own:

```bash
docker network create edge
```

Add it to the Caddy container in Caddy's `compose.yaml`, then `docker compose up -d` in Caddy's folder:

```yaml
services:
  caddy:            # the name of the Caddy service there
    networks: [default, edge]
networks:
  edge:
    external: true
```

The new sites come from [Caddyfile](Caddyfile): copy its blocks into Caddy's Caddyfile, but **leave the block for
`www.stefan-hoffmann.de` out for now**, so the current site keeps running until step 6. Then reload Caddy:
`docker exec <caddy container> caddy reload --config /etc/caddy/Caddyfile`.

`dev.stefan-hoffmann.de` is open by default; search engines are only asked to stay away. For a password, create
`dev-auth.caddy` next to Caddy's Caddyfile, with the user name of your choice and the hash of the password, and
reload Caddy:

```bash
docker exec -it <caddy container> caddy hash-password       # prints the hash
```

```
basic_auth {
	stefan $2a$14$…the hash…
}
```

Delete the file and reload Caddy to open the site again. Without the file, Caddy logs the warning "No files matching
import glob pattern"; that is expected.

## 3. Folders and .env files

```bash
sudo mkdir -p /srv/site/development /srv/observability
sudo chown "$USER": /srv/site/development /srv/observability
mkdir -p /srv/site/development/data
```

Copy the templates from this folder to the server and fill them in (each `.env` is kept by every deploy):

| Template | Goes to |
|---|---|
| [production.env.example](production.env.example) | `.env` in the production folder (`DEPLOY_PATH`) |
| [development.env.example](development.env.example) | `/srv/site/development/.env` |
| [observability.env.example](observability.env.example) | `/srv/observability/.env` |

Set a long random `COMMENTS_ADMIN_TOKEN` for each site (`openssl rand -hex 32`) and a `GRAFANA_ADMIN_PASSWORD`.
The production folder already has `data/` with the comments; it stays as it is.

If the packages on ghcr.io are private, log in once: `docker login ghcr.io` (user name: your GitHub name, password:
a token with the scope `read:packages`).

## 4. GitHub

In the repository settings:

1. **Branch:** create `develop` from `main` and push it.
2. **Environments** (Settings → Environments): `production` and `development`, each with the secret `DEPLOY_PATH`
   (its folder on the server). Optionally, a protection rule "Required reviewers" on `production`, so every
   production deploy waits for your approval.
3. **Repository secrets**: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` and `DEPLOY_KNOWN_HOSTS` stay as they are;
   new is `OBSERVABILITY_PATH` (`/srv/observability`). The old repository secret `DEPLOY_PATH` can be removed once
   both environments have their own.
4. **Variable** `DEPLOY_ENABLED`: `true` for automatic deploys.

## 5. Start the observability stack

Actions → Observability → Run workflow, with "Deploy" ticked. Afterwards on the server:

```bash
cd /srv/observability && docker compose ps     # five services, all running
```

https://grafana.stefan-hoffmann.de shows the dashboard without a login; editing needs the admin login (`admin` /
`GRAFANA_ADMIN_PASSWORD`). For a login to view as well, set `GRAFANA_ANONYMOUS=false` in `/srv/observability/.env`
and run `docker compose up -d` there.

## 6. Switch production over

Merge into `main` (or Actions → CI → Run workflow on `main` with "Deploy"). The deploy replaces the old single
`homepage` container with backend and frontend, keeps `data/` and waits until both are healthy. **Right after it**,
add the block for `www.stefan-hoffmann.de` from [Caddyfile](Caddyfile) to Caddy's Caddyfile in place of the old one,
and reload Caddy. Between the deploy and the reload the site is offline, so do both right after each other.

```bash
cd <production folder> && docker compose ps     # backend and frontend "healthy"
curl -sI https://www.stefan-hoffmann.de | head -1
```

## 7. Start development

Push to `develop`: the deploy sets up the development site in `/srv/site/development`. It is at
https://dev.stefan-hoffmann.de, open or behind the optional password from step 2.

Production gets the code that ran on development: merge `develop` into `main` as a fast-forward
(`git checkout main && git merge --ff-only develop && git push`), so the commit stays the same. CI builds it once more
for `main` under the same tag `sha-…`, from the same code, and the dashboard shows the same version in both
environments.

## 8. Let development sleep

The development site uses about 390 MB while it runs. A nightly job stops it; the next deploy to `develop` starts it
again, or by hand `docker compose start` in its folder:

```bash
crontab -e
# add:
0 3 * * * cd /srv/site/development && docker compose stop >/dev/null 2>&1
```

While it sleeps, https://dev.stefan-hoffmann.de says so. Stopped, it uses no memory; its comments and content stay.

## Checks and everyday commands

```bash
free -h                                                     # memory and swap
docker stats --no-stream --format '{{.Name}}: {{.MemUsage}}'
cd <folder> && docker compose ps                            # health of a site or the stack
docker compose logs -f --tail 50                            # logs
docker compose start | stop                                 # development awake or asleep
```

In Grafana, the variable Environment switches between `production` and `development`; Version shows which commit
runs where.
