# Tick Ideas App Store

An internal app store for distributing Android APKs to clients. Clients download one APK (the app store), and all other apps are available for download from within it.

## Architecture

```
┌─────────────┐        ┌─────────────────┐        ┌──────────┐
│  Android     │──API──▶│  Hono Backend    │──S3───▶│  R2      │
│  App Store   │        │  (Dokploy)       │        │  (APKs)  │
└─────────────┘        └─────────────────┘        └──────────┘
                              │
                          SQLite DB
                        (app metadata)
```

## Backend

### Tech Stack
- **Hono** — lightweight HTTP framework
- **SQLite** (better-sqlite3 + Drizzle ORM) — app/version metadata
- **Cloudflare R2** — APK and icon file storage
- **Docker** — deployment via Dokploy

### API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/apps` | Public | List all apps with latest version |
| `GET` | `/api/apps/:id` | Public | App detail with all versions |
| `GET` | `/api/apps/:id/download` | Public | Download latest APK |
| `GET` | `/api/apps/:id/download/:versionId` | Public | Download specific version APK |
| `POST` | `/api/apps` | Admin | Create a new app |
| `PATCH` | `/api/apps/:id` | Admin | Update app metadata |
| `POST` | `/api/apps/:id/versions` | Admin | Upload a new version (APK) |
| `DELETE` | `/api/apps/:id` | Admin | Delete app and all versions |
| `GET` | `/api/store/version` | Public | App store self-update check |
| `GET` | `/api/store/download` | Public | Download latest app store APK |

### Quick Start

```bash
cd backend
cp .env.example .env
# Edit .env with your R2 credentials and admin API key
npm install
npm run dev
```

### Deploy to Dokploy

1. Push to your Git repo
2. In Dokploy, create a new service pointing to the repo
3. Set build path to `backend/`
4. Add environment variables from `.env.example`
5. Mount a volume at `/app/data` for SQLite persistence

### Upload an App (curl)

```bash
# Create an app
curl -X POST https://your-store.example.com/api/apps \
  -H "X-API-Key: your-admin-key" \
  -F "name=My App" \
  -F "packageName=com.tickideas.myapp" \
  -F "description=A cool app" \
  -F "icon=@icon.png"

# Upload a version
curl -X POST https://your-store.example.com/api/apps/<app-id>/versions \
  -H "X-API-Key: your-admin-key" \
  -F "versionName=1.0.0" \
  -F "versionCode=1" \
  -F "releaseNotes=Initial release" \
  -F "apk=@app-release.apk"
```

## Android App

The app store client for Android. See `android/` directory.

## Project Structure

```
tick-app-store/
├── backend/
│   ├── src/
│   │   ├── index.ts              # Hono app + server
│   │   ├── db/
│   │   │   ├── schema.ts         # Drizzle schema
│   │   │   └── index.ts          # DB connection
│   │   ├── routes/
│   │   │   ├── apps.ts           # App CRUD + download routes
│   │   │   └── store.ts          # Store self-update routes
│   │   ├── services/
│   │   │   └── r2.ts             # R2 storage operations
│   │   └── middleware/
│   │       └── auth.ts           # Admin API key auth
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── package.json
└── android/                       # App store Android client
```
