import { Hono } from "hono";
import { eq, desc } from "drizzle-orm";
import { nanoid } from "nanoid";
import { db, schema } from "../db/index.js";
import { uploadFile, deleteFile, getDownloadUrl } from "../services/r2.js";
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

      return {
        id: a.id,
        name: a.name,
        packageName: a.packageName,
        description: a.description,
        iconUrl: a.iconKey ? await getDownloadUrl(a.iconKey) : null,
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

  return c.json({
    id: a.id,
    name: a.name,
    packageName: a.packageName,
    description: a.description,
    iconUrl: a.iconKey ? await getDownloadUrl(a.iconKey) : null,
    versions: appVersions.map((v) => ({
      id: v.id,
      versionName: v.versionName,
      versionCode: v.versionCode,
      apkSize: v.apkSize,
      releaseNotes: v.releaseNotes,
      createdAt: v.createdAt,
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

  const url = await getDownloadUrl(latestVersion[0].apkKey);
  return c.redirect(url);
});

// Download specific version APK
app.get("/:id/download/:versionId", async (c) => {
  const { versionId } = c.req.param();

  const version = await db
    .select()
    .from(schema.versions)
    .where(eq(schema.versions.id, versionId))
    .limit(1);

  if (!version[0]) {
    return c.json({ error: "Version not found" }, 404);
  }

  const url = await getDownloadUrl(version[0].apkKey);
  return c.redirect(url);
});

// --- Admin routes ---

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
