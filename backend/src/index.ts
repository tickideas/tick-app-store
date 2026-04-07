import { serve } from "@hono/node-server";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import appsRoutes from "./routes/apps.js";
import storeRoutes from "./routes/store.js";
import { db } from "./db/index.js";

const app = new Hono();

// Middleware
app.use("*", logger());
app.use("*", cors());

// Health check
app.get("/", (c) => {
  return c.json({
    name: "Tick Ideas App Store",
    status: "running",
  });
});

// Routes
app.route("/api/apps", appsRoutes);
app.route("/api/store", storeRoutes);

// Run migrations inline (create tables if they don't exist)
const sqlite = (db as any).$client;
sqlite.exec(`
  CREATE TABLE IF NOT EXISTS apps (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    package_name TEXT NOT NULL UNIQUE,
    description TEXT DEFAULT '',
    icon_key TEXT,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_at INTEGER NOT NULL DEFAULT (unixepoch())
  );

  CREATE TABLE IF NOT EXISTS versions (
    id TEXT PRIMARY KEY,
    app_id TEXT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    version_name TEXT NOT NULL,
    version_code INTEGER NOT NULL,
    apk_key TEXT NOT NULL,
    apk_size INTEGER,
    release_notes TEXT DEFAULT '',
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
  );

  CREATE INDEX IF NOT EXISTS idx_versions_app_id ON versions(app_id);
  CREATE INDEX IF NOT EXISTS idx_versions_version_code ON versions(app_id, version_code DESC);
`);

const PORT = parseInt(process.env.PORT || "3000", 10);

serve({ fetch: app.fetch, port: PORT }, (info) => {
  console.log(`🏪 Tick Ideas App Store running on http://localhost:${info.port}`);
});
