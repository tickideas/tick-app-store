# Tick App Store — Features

## ✅ Shipped

### Backend & API
- [x] Hono HTTP framework with SQLite (Drizzle ORM) and Cloudflare R2 storage
- [x] App CRUD — create, read, update, delete apps
- [x] APK upload with automatic metadata extraction (name, package, version, icon)
- [x] New version replaces old version — one active version per app
- [x] Download tracking — every download logged with app, version, timestamp
- [x] Version history — permanent record of all published versions
- [x] Presigned R2 URLs for APK downloads (with public URL fallback)
- [x] Admin API key authentication
- [x] R2_PUBLIC_URL validation (warns on invalid scheme)
- [x] Docker deployment via Dokploy

### Admin Dashboard (`/admin`)
- [x] Single-file SPA — no build tools, served inline from backend
- [x] API key login with session persistence
- [x] Drag-and-drop APK upload with progress bar
- [x] Auto-extraction feedback — shows extracted app name, package, version, size
- [x] App list with icons, versions, and download counts
- [x] App detail page with stats (downloads, current versions, total releases)
- [x] Icon upload — click icon on detail page to change
- [x] Colored initials fallback for apps without icons
- [x] Version upload modal from detail page
- [x] Release history timeline
- [x] Delete app with confirmation

### Android App
- [x] App list grid with icons, names, versions
- [x] Install state detection — "Install", "Update", "Installed" per app
- [x] APK download via Android DownloadManager with notification
- [x] Download error handling with Toast feedback
- [x] Download button shows spinner while downloading
- [x] App detail page with download count, stats, and release history
- [x] Colored initials fallback for apps without icons
- [x] Package visibility (`QUERY_ALL_PACKAGES`) for install detection on Android 11+
- [x] Auto-incrementing version (code + name) on release builds
- [x] FileProvider for secure APK install
- [x] Pull-to-refresh on app list
- [x] Shimmer loading placeholders (list + detail)
- [x] Empty state with icon and messaging
- [x] Install state refreshes on resume (after installing/uninstalling)
- [x] Animated store update banner (expand/collapse)
- [x] Dark mode support with Material You dynamic colors (Android 12+)
- [x] Polished error states with retry buttons

### Store Self-Update
- [x] Backend `/api/store/version` returns current store version
- [x] Backend `/api/store/download` serves latest store APK
- [x] Backend `/api/store/upload` — admin uploads new store APK (auto-extracts version)
- [x] Store version managed in DB (no env var changes needed for updates)
- [x] Android checks for store update on launch (compares `versionCode`)
- [x] Update banner on app list — shows current → new version, "Update" / "Later" buttons
- [x] Admin dashboard — "Store App" section with version display and APK upload

### Infrastructure
- [x] Deployed to Dokploy at `apps-api.tikd.dev`
- [x] SQLite with WAL mode for performance
- [x] R2 bucket for APK and icon storage
- [x] Docker build with multi-stage (build → runtime)

---

## 🔲 Planned

### Store Self-Update
- [ ] Background update check with periodic interval
- [ ] Force-update option for critical versions

### UI Polish
- [ ] Search / filter apps
- [ ] App screenshots on detail page
- [ ] Loading skeleton states (admin dashboard)

### Admin Dashboard Enhancements
- [ ] Download analytics — chart over time, per-version breakdown
- [ ] App edit modal (change name, description from dashboard)
- [ ] Bulk operations
- [ ] Version comparison (diff between releases)

### Android App
- [ ] Background update check with notification
- [ ] Download progress in-app (not just notification)
- [ ] Open app after install
- [ ] Uninstall detection refresh

### Backend
- [ ] Pagination for app list
- [ ] Rate limiting on download endpoints
- [ ] Webhook notifications on new version publish
- [ ] Audit log for admin actions
- [ ] Multiple admin accounts / roles
