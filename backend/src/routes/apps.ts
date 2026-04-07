import { Hono } from "hono";
import { eq, desc, and, lt, sql, count } from "drizzle-orm";
import { nanoid } from "nanoid";
import { db, schema } from "../db/index.js";
import { uploadFile, deleteFile, getDownloadUrl } from "../services/r2.js";
import { parseApk } from "../services/apk.js";
import { adminAuth } from "../middleware/auth.js";

const app = new Hono();

// --- Public routes ---

// List all apps with their latest version
app.get("/", async (c) => {
  const allApps = await db
    .select()
    .from(schema.apps)
    .orderBy(desc(schema.apps.updatedAt));

  const result = await Promise.all(
    allApps.map(async (a) => {
      const latestVersion = await db
        .select()
        .from(schema.versions)
        .where(eq(schema.versions.appId, a.id))
        .orderBy(desc(schema.versions.versionCode))
        .limit(1);

      const latest = latestVersion[0] || null;

      // Get total download count
      const dlCount = await db
        .select({ total: count() })
        .from(schema.downloads)
        .where(eq(schema.downloads.appId, a.id));

      return {
        id: a.id,
        name: a.name,
        packageName: a.packageName,
        description: a.description,
        iconUrl: a.iconKey ? await getDownloadUrl(a.iconKey) : null,
        downloadCount: dlCount[0]?.total ?? 0,
        latestVersion: latest
          ? {
              versionName: latest.versionName,
              versionCode: latest.versionCode,
              apkSize: latest.apkSize,
              releaseNotes: latest.releaseNotes,
              createdAt: latest.createdAt,
            }
          : null,
        updatedAt: a.updatedAt,
      };
    })
  );

  return c.json({ apps: result });
});

// Get single app with all versions
app.get("/:id", async (c) => {
  const { id } = c.req.param();

  const appRecord = await db
    .select()
    .from(schema.apps)
    .where(eq(schema.apps.id, id))
    .limit(1);

  if (!appRecord[0]) {
    return c.json({ error: "App not found" }, 404);
  }

  const a = appRecord[0];

  const appVersions = await db
    .select()
    .from(schema.versions)
    .where(eq(schema.versions.appId, id))
    .orderBy(desc(schema.versions.versionCode));

  // Download count
  const dlCount = await db
    .select({ total: count() })
    .from(schema.downloads)
    .where(eq(schema.downloads.appId, id));

  // Version history (all versions ever published)
  const history = await db
    .select()
    .from(schema.versionHistory)
    .where(eq(schema.versionHistory.appId, id))
    .orderBy(desc(schema.versionHistory.versionCode));

  return c.json({
    id: a.id,
    name: a.name,
    packageName: a.packageName,
    description: a.description,
    iconUrl: a.iconKey ? await getDownloadUrl(a.iconKey) : null,
    downloadCount: dlCount[0]?.total ?? 0,
    versions: appVersions.map((v) => ({
      id: v.id,
      versionName: v.versionName,
      versionCode: v.versionCode,
      apkSize: v.apkSize,
      releaseNotes: v.releaseNotes,
      createdAt: v.createdAt,
    })),
    versionHistory: history.map((h) => ({
      versionName: h.versionName,
      versionCode: h.versionCode,
      apkSize: h.apkSize,
      releaseNotes: h.releaseNotes,
      publishedAt: h.publishedAt,
    })),
    createdAt: a.createdAt,
    updatedAt: a.updatedAt,
  });
});

// Download latest APK (redirect to R2)
app.get("/:id/download", async (c) => {
  const { id } = c.req.param();

  const latestVersion = await db
    .select()
    .from(schema.versions)
    .where(eq(schema.versions.appId, id))
    .orderBy(desc(schema.versions.versionCode))
    .limit(1);

  if (!latestVersion[0]) {
    return c.json({ error: "No versions found" }, 404);
  }

  // Log the download
  await db.insert(schema.downloads).values({
    appId: id,
    versionName: latestVersion[0].versionName,
    versionCode: latestVersion[0].versionCode,
  });

  const url = await getDownloadUrl(latestVersion[0].apkKey);
  return c.redirect(url);
});

// Download specific version APK
app.get("/:id/download/:versionId", async (c) => {
  const { id, versionId } = c.req.param();

  const version = await db
    .select()
    .from(schema.versions)
    .where(eq(schema.versions.id, versionId))
    .limit(1);

  if (!version[0]) {
    return c.json({ error: "Version not found" }, 404);
  }

  // Log the download
  await db.insert(schema.downloads).values({
    appId: version[0].appId,
    versionName: version[0].versionName,
    versionCode: version[0].versionCode,
  });

  const url = await getDownloadUrl(version[0].apkKey);
  return c.redirect(url);
});

// --- Admin routes ---

// Upload APK — auto-detect app metadata, create or update
app.post("/upload", adminAuth, async (c) => {
  const form = await c.req.formData();
  const apk = form.get("apk") as File;
  const releaseNotes = (form.get("releaseNotes") as string) || "";
  // Allow optional overrides
  const nameOverride = form.get("name") as string | null;
  const descOverride = form.get("description") as string | null;

  if (!apk) {
    return c.json({ error: "apk file is required" }, 400);
  }

  const apkBuffer = Buffer.from(await apk.arrayBuffer());

  let info;
  try {
    info = await parseApk(apkBuffer);
  } catch (err: any) {
    return c.json({ error: "Failed to parse APK: " + err.message }, 400);
  }

  // Check if app with this package name already exists
  const existing = await db
    .select()
    .from(schema.apps)
    .where(eq(schema.apps.packageName, info.packageName))
    .limit(1);

  let appId: string;
  let appName: string;
  let isNew = false;

  if (existing[0]) {
    // Existing app — replace old versions with this one
    appId = existing[0].id;
    appName = existing[0].name;

    // Delete all old versions from R2 and database
    const oldVersions = await db
      .select()
      .from(schema.versions)
      .where(eq(schema.versions.appId, appId));

    for (const old of oldVersions) {
      await deleteFile(old.apkKey);
    }

    await db
      .delete(schema.versions)
      .where(eq(schema.versions.appId, appId));
  } else {
    // New app — create it
    isNew = true;
    appId = nanoid(12);
    appName = nameOverride || info.appName || info.packageName;

    let iconKey: string | null = null;
    if (info.icon) {
      iconKey = `icons/${appId}/icon.png`;
      await uploadFile(iconKey, info.icon, "image/png");
    }

    await db.insert(schema.apps).values({
      id: appId,
      name: appName,
      packageName: info.packageName,
      description: descOverride || "",
      iconKey,
    });
  }

  // Upload APK and create version
  const versionId = nanoid(12);
  const apkKey = `apks/${appId}/${info.versionName}-${info.versionCode}.apk`;
  await uploadFile(apkKey, apkBuffer, "application/vnd.android.package-archive");

  await db.insert(schema.versions).values({
    id: versionId,
    appId,
    versionName: info.versionName,
    versionCode: info.versionCode,
    apkKey,
    apkSize: apkBuffer.length,
    releaseNotes,
  });

  // Record in version history
  await db.insert(schema.versionHistory).values({
    appId,
    versionName: info.versionName,
    versionCode: info.versionCode,
    apkSize: apkBuffer.length,
    releaseNotes,
  });

  await db
    .update(schema.apps)
    .set({ updatedAt: new Date() })
    .where(eq(schema.apps.id, appId));

  return c.json(
    {
      appId,
      appName,
      packageName: info.packageName,
      versionName: info.versionName,
      versionCode: info.versionCode,
      apkSize: apkBuffer.length,
      isNew,
    },
    201
  );
});

// Create a new app
app.post("/", adminAuth, async (c) => {
  const form = await c.req.formData();
  const name = form.get("name") as string;
  const packageName = form.get("packageName") as string;
  const description = (form.get("description") as string) || "";
  const icon = form.get("icon") as File | null;

  if (!name || !packageName) {
    return c.json({ error: "name and packageName are required" }, 400);
  }

  const id = nanoid(12);
  let iconKey: string | null = null;

  if (icon) {
    iconKey = `icons/${id}/${icon.name}`;
    const buffer = Buffer.from(await icon.arrayBuffer());
    await uploadFile(iconKey, buffer, icon.type);
  }

  await db.insert(schema.apps).values({
    id,
    name,
    packageName,
    description,
    iconKey,
  });

  return c.json({ id, name, packageName }, 201);
});

// Update app metadata
app.patch("/:id", adminAuth, async (c) => {
  const { id } = c.req.param();
  const form = await c.req.formData();

  const updates: Record<string, unknown> = { updatedAt: new Date() };

  const name = form.get("name") as string | null;
  const description = form.get("description") as string | null;
  const icon = form.get("icon") as File | null;

  if (name) updates.name = name;
  if (description !== null) updates.description = description;

  if (icon) {
    const iconKey = `icons/${id}/${icon.name}`;
    const buffer = Buffer.from(await icon.arrayBuffer());
    await uploadFile(iconKey, buffer, icon.type);
    updates.iconKey = iconKey;
  }

  await db.update(schema.apps).set(updates).where(eq(schema.apps.id, id));

  return c.json({ success: true });
});

// Upload a new version (APK)
app.post("/:id/versions", adminAuth, async (c) => {
  const { id: appId } = c.req.param();

  // Verify app exists
  const appRecord = await db
    .select()
    .from(schema.apps)
    .where(eq(schema.apps.id, appId))
    .limit(1);

  if (!appRecord[0]) {
    return c.json({ error: "App not found" }, 404);
  }

  const form = await c.req.formData();
  const versionName = form.get("versionName") as string;
  const versionCode = parseInt(form.get("versionCode") as string, 10);
  const releaseNotes = (form.get("releaseNotes") as string) || "";
  const apk = form.get("apk") as File;

  if (!versionName || !versionCode || !apk) {
    return c.json(
      { error: "versionName, versionCode, and apk file are required" },
      400
    );
  }

  const versionId = nanoid(12);
  const apkKey = `apks/${appId}/${versionName}-${versionCode}.apk`;
  const buffer = Buffer.from(await apk.arrayBuffer());

  await uploadFile(apkKey, buffer, "application/vnd.android.package-archive");

  await db.insert(schema.versions).values({
    id: versionId,
    appId,
    versionName,
    versionCode,
    apkKey,
    apkSize: buffer.length,
    releaseNotes,
  });

  // Update the app's updatedAt
  await db
    .update(schema.apps)
    .set({ updatedAt: new Date() })
    .where(eq(schema.apps.id, appId));

  return c.json(
    { id: versionId, versionName, versionCode, apkSize: buffer.length },
    201
  );
});

// Delete an app and all its versions
app.delete("/:id", adminAuth, async (c) => {
  const { id } = c.req.param();

  // Get all version APK keys to delete from R2
  const appVersions = await db
    .select()
    .from(schema.versions)
    .where(eq(schema.versions.appId, id));

  const appRecord = await db
    .select()
    .from(schema.apps)
    .where(eq(schema.apps.id, id))
    .limit(1);

  if (!appRecord[0]) {
    return c.json({ error: "App not found" }, 404);
  }

  // Delete files from R2
  for (const v of appVersions) {
    await deleteFile(v.apkKey);
  }
  if (appRecord[0].iconKey) {
    await deleteFile(appRecord[0].iconKey);
  }

  // Cascade delete handles versions
  await db.delete(schema.apps).where(eq(schema.apps.id, id));

  return c.json({ success: true });
});

export default app;
