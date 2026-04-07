# Tick Ideas App Store

An internal app store for distributing Android APKs to clients. Clients download one APK (the app store), and all other apps are available for download and update from within it.

**Live:** `https://apps-api.tikd.dev` · **Admin:** `https://apps-api.tikd.dev/admin`

## Architecture

```
┌─────────────┐        ┌─────────────────┐        ┌──────────┐
│  Android     │──API──▶│  Hono Backend    │──S3───▶│  R2      │
│  App Store   │        │  (Dokploy)       │        │  (APKs)  │
└─────────────┘        └─────────────────┘        └──────────┘
                              │
                          SQLite DB
                        (app metadata,
                         downloads log,
                         version history)
```

## Features

See [FEATURES.md](FEATURES.md) for a detailed feature list and roadmap.

### Backend
- **Hono** HTTP framework + **SQLite** (Drizzle ORM) + **Cloudflare R2** storage
- APK upload with **automatic metadata extraction** (app name, package name, version, icon)
- New versions **replace** old ones — each app always has one active version
- **Download tracking** — every download is logged with timestamp and version
- **Version history** — all published versions are recorded permanently
- Admin authentication via `X-API-Key` header

### Admin Dashboard (`/admin`)
- Single-page web dashboard — no build tools, served inline from the backend
- **Drag-and-drop APK upload** — drop an APK to publish, metadata is extracted automatically
- App list with download counts
- App detail with stats (downloads, releases), icon upload, version management
- Release history timeline

### Android App
- Browse and search published apps
- **Install state detection** — shows "Install", "Update", or "Installed" per app
- Download and install APKs via Android's DownloadManager
- App icons with colored initials fallback
- Store self-update support

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/apps` | Public | List all apps with latest version + download count |
| `GET` | `/api/apps/:id` | Public | App detail with versions, history, download count |
| `GET` | `/api/apps/:id/download` | Public | Download latest APK (logs download) |
| `GET` | `/api/apps/:id/download/:versionId` | Public | Download specific version APK (logs download) |
| `POST` | `/api/apps/upload` | Admin | Upload APK with auto-extraction (create or update) |
| `POST` | `/api/apps` | Admin | Create a new app manually |
| `PATCH` | `/api/apps/:id` | Admin | Update app metadata / icon |
| `POST` | `/api/apps/:id/versions` | Admin | Upload a new version manually |
| `DELETE` | `/api/apps/:id` | Admin | Delete app and all versions |
| `GET` | `/api/store/version` | Public | App store self-update check |
| `GET` | `/api/store/download` | Public | Download latest app store APK |

## Quick Start

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env with your R2 credentials and admin API key
npm install
npm run dev
```

### Upload an App

**Option 1: Admin Dashboard**

Go to `http://localhost:3000/admin`, sign in with your API key, and drop an APK.

**Option 2: curl**

```bash
# Smart upload — auto-extracts metadata from APK
curl -X POST https://apps-api.tikd.dev/api/apps/upload \
  -H "X-API-Key: your-admin-key" \
  -F "apk=@app-release.apk" \
  -F "releaseNotes=Bug fixes and improvements"
```

### Android

Open `android/` in Android Studio. Version is managed via `android/version.properties` and auto-increments on release builds.

## Deploy to Dokploy

1. Push to your Git repo
2. In Dokploy, create a new service pointing to the repo
3. Set build path to `backend/`
4. Add environment variables from `.env.example`
5. Mount a volume at `/app/data` for SQLite persistence

## Project Structure

```
tick-app-store/
├── backend/
│   ├── src/
│   │   ├── index.ts              # Hono app, server, DB migrations
│   │   ├── db/
│   │   │   ├── schema.ts         # Drizzle schema (apps, versions, downloads, history)
│   │   │   └── index.ts          # DB connection
│   │   ├── routes/
│   │   │   ├── apps.ts           # App CRUD, download (with tracking), upload
│   │   │   ├── store.ts          # Store self-update routes
│   │   │   └── admin.ts          # Admin dashboard serving
│   │   ├── services/
│   │   │   ├── r2.ts             # R2 storage operations
│   │   │   └── apk.ts            # APK metadata extraction
│   │   ├── middleware/
│   │   │   └── auth.ts           # Admin API key auth
│   │   └── admin/
│   │       └── dashboard.html    # Admin SPA
│   ├── Dockerfile
│   └── package.json
├── android/
│   ├── app/src/main/java/com/tickideas/appstore/
│   │   ├── MainActivity.kt
│   │   ├── TickAppStore.kt       # Application class
│   │   ├── data/
│   │   │   ├── Api.kt            # Retrofit API client
│   │   │   └── AppRepository.kt  # Data layer
│   │   ├── download/
│   │   │   └── DownloadHelper.kt # APK download + install
│   │   └── ui/
│   │       ├── AppListScreen.kt  # App grid with install state
│   │       ├── AppDetailScreen.kt # App detail + download
│   │       └── Navigation.kt
│   └── version.properties        # Auto-incrementing version
├── FEATURES.md                   # Feature list and roadmap
└── DEPLOYMENT.md                 # Deployment guide
```
