# SmartStudy – Deployment Guide

> This project has been **prepared** for deployment (production profile, environment-based
> configuration, build commands, hosting guide). It has **not** been deployed anywhere.

## 1. Recommended production architecture

```
Browser ──HTTPS──▶ Reverse proxy (Nginx / Caddy) ── serves frontend/dist (static files)
                         │
                         └── /api/* ──▶ Spring Boot (java -jar, port 8080, profile "prod")
                                              │
                                              └── MySQL 8.4 (database "smartstudy")
```

Serving the React build and the API from the **same domain** is the simplest and safest setup:
the session cookie stays first-party (`SameSite=Lax`), no CORS is needed, and the frontend keeps
the default `VITE_API_BASE_URL=/api/v1`.

## 2. Build

Run in the project root (IntelliJ terminal, PowerShell or bash).

**Backend** (Java 25 JDK required):

```powershell
cd backend
.\mvnw.cmd clean package          # Windows PowerShell
# ./mvnw clean package            # macOS / Linux
```

Result: `backend/target/smartstudy-backend-1.0.0.jar` (tests run during `package`; add
`-DskipTests` only if you have already run them).

**Frontend** (Node.js 22.22+ or 24 LTS):

```powershell
cd frontend
npm ci
npm run build
```

Result: static files in `frontend/dist/` (type check + production bundle, code-split per page).

## 3. Production environment variables

| Variable | Required | Example | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `prod` | Enables `application-prod.yml` |
| `DB_URL` | yes | `jdbc:mysql://db.internal:3306/smartstudy?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&sslMode=REQUIRED` | JDBC URL. Keep the two time-zone parameters. |
| `DB_USERNAME` | yes | `smartstudy` | Database user |
| `DB_PASSWORD` | yes | *(secret)* | Database password – never commit it |
| `SERVER_PORT` | no | `8080` | HTTP port of the backend |
| `APP_CORS_ALLOWED_ORIGINS` | only for split domains | `https://app.example.com` | Comma-separated origins allowed to call the API with cookies |
| `COOKIE_SECURE` | no (default `true` in prod) | `true` | Session cookie only over HTTPS |
| `SESSION_TIMEOUT` | no | `12h` | Idle session timeout |
| `DB_POOL_SIZE` | no | `20` | Hikari pool size |
| `API_DOCS_ENABLED` | no (default `false` in prod) | `false` | Swagger UI / OpenAPI in production |
| `SCHEDULING_ENABLED` | no | `true` | Background job (missed sessions, reminders, readiness snapshots). Keep it `true` on exactly one instance if you run several. |

The `prod` profile has **no** usable database defaults – the application refuses to start
without `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. Flyway `clean` is disabled, Hibernate never
alters the schema (`ddl-auto: none`), and errors never include stack traces or internal messages.

## 4. Database

```sql
CREATE DATABASE smartstudy CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'smartstudy'@'%' IDENTIFIED BY '<strong-unique-password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
      ON smartstudy.* TO 'smartstudy'@'%';
```

Restrict `'%'` to the backend host (e.g. `'smartstudy'@'10.0.0.5'`) where possible. On the first
start Flyway creates all tables (`V1__create_core_schema.sql`) and records the version in
`flyway_schema_history`. Do **not** load `database/sample-data.sql` in production.

Back up with `mysqldump --single-transaction --routines smartstudy > backup.sql` (or your
provider's automated backups).

## 5. Linux server (VPS) example

**Backend as a systemd service** – `/etc/systemd/system/smartstudy.service`:

```ini
[Unit]
Description=SmartStudy API
After=network.target

[Service]
User=smartstudy
WorkingDirectory=/opt/smartstudy
EnvironmentFile=/etc/smartstudy/smartstudy.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/smartstudy/smartstudy-backend-1.0.0.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

`/etc/smartstudy/smartstudy.env` (permissions `600`, owner root):

```
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://localhost:3306/smartstudy?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8
DB_USERNAME=smartstudy
DB_PASSWORD=change-me
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now smartstudy
journalctl -u smartstudy -f          # logs
curl http://localhost:8080/api/v1/health
```

**Nginx** – `/etc/nginx/sites-available/smartstudy` (TLS via `certbot --nginx`):

```nginx
server {
    listen 80;
    server_name study.example.com;

    root /var/www/smartstudy;            # contents of frontend/dist
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Single-page app: unknown paths return index.html (React Router handles them)
    location / {
        try_files $uri /index.html;
    }

    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

The `prod` profile sets `server.forward-headers-strategy=framework`, so the backend sees the
original HTTPS scheme from `X-Forwarded-Proto`.

## 6. Frontend and API on different domains (optional)

If the frontend is hosted separately (e.g. a static host) and the API on another domain:

1. Build the frontend with `VITE_API_BASE_URL=https://api.example.com/api/v1`.
2. Set `APP_CORS_ALLOWED_ORIGINS=https://app.example.com` on the backend.
3. The session cookie becomes a cross-site cookie: set
   `SERVER_SERVLET_SESSION_COOKIE_SAMESITE=none` and keep `COOKIE_SECURE=true` (HTTPS required).
   Some browsers block third-party cookies entirely – prefer a shared parent domain or the
   same-domain setup from section 1.

## 7. Managed hosting options

Any platform that runs a Java 25 JAR and a MySQL 8.4 database works, for example a VPS
(section 5), a container platform, or a PaaS that supports Java. Typical steps:

1. Create a managed MySQL 8.4 database and the `smartstudy` user.
2. Deploy the backend JAR with the environment variables from section 3
   (health check path: `/api/v1/health`).
3. Deploy `frontend/dist` as static files behind the same domain, with `/api/*` routed to the
   backend and all other paths falling back to `index.html`.

## 8. Hosted demo of the frontend (Vercel)

For a public demo link without a server, deploy the **demo build** of the frontend:
import the GitHub repository in Vercel with *Root Directory* `frontend` and *Application Preset*
`Vite`. `frontend/vercel.json` sets `npm run build:demo`, the `dist` output folder and the
single-page-app rewrites. The demo build simulates the API in the browser with sample data
(`src/demo/`); it is not connected to MySQL and resets on reload. Production deployments use
the normal `npm run build` together with the real backend (sections 1–5).

## 9. Go-live checklist

- [ ] HTTPS enabled; `COOKIE_SECURE=true`
- [ ] Strong, unique database password stored only in the environment / secret store
- [ ] `API_DOCS_ENABLED=false` (or protected by the proxy)
- [ ] `/api/v1/health` returns `"status":"UP"` with `"schemaVersion":"1"`
- [ ] Database backups scheduled
- [ ] Sample data **not** loaded
- [ ] Only one instance with `SCHEDULING_ENABLED=true`
