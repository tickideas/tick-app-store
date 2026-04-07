import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { db, schema } from "../db/index.js";
import { uploadFile, getDownloadUrl } from "../services/r2.js";
import { parseApk } from "../services/apk.js";
import { adminAuth } from "../middleware/auth.js";

const app = new Hono();

// In-memory store version (loaded from DB on startup, updated on upload)
let storeVersion = {
  versionName: process.env.STORE_APP_VERSION_NAME || "1.0.0",
  versionCode: parseInt(process.env.STORE_APP_VERSION_CODE || "1", 10),
};

// Load store version from DB if available
(async () => {
  try {
    const row = await db
      .select()
      .from(schema.storeInfo)
      .limit(1);
    if (row[0]) {
      storeVersion = {
        versionName: row[0].versionName,
        versionCode: row[0].versionCode,
      };
    }
  } catch {
    // Table might not exist yet on first boot
  }
})();

// Store app self-update check
app.get("/version", (c) => {
  return c.json(storeVersion);
});

// Download the latest app store APK
app.get("/download", async (c) => {
  // Try DB-managed store APK first
  const row = await db
    .select()
    .from(schema.storeInfo)
    .limit(1)
    .catch(() => []);

  const apkKey = row?.[0]?.apkKey || process.env.STORE_APP_APK_KEY;

  if (!apkKey) {
    return c.json({ error: "Store APK not configured" }, 404);
  }

  const url = await getDownloadUrl(apkKey);
  return c.redirect(url);
});

// Admin: upload a new store APK
app.post("/upload", adminAuth, async (c) => {
  const form = await c.req.formData();
  const apk = form.get("apk") as File;

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

  // Upload to R2
  const apkKey = `store/tick-app-store-${info.versionName}.apk`;
  await uploadFile(apkKey, apkBuffer, "application/vnd.android.package-archive");

  // Upsert store info in DB
  const existing = await db.select().from(schema.storeInfo).limit(1);
  if (existing[0]) {
    await db.update(schema.storeInfo).set({
      versionName: info.versionName,
      versionCode: info.versionCode,
      apkKey,
      apkSize: apkBuffer.length,
      updatedAt: new Date(),
    });
  } else {
    await db.insert(schema.storeInfo).values({
      id: "store",
      versionName: info.versionName,
      versionCode: info.versionCode,
      apkKey,
      apkSize: apkBuffer.length,
    });
  }

  // Update in-memory cache
  storeVersion = {
    versionName: info.versionName,
    versionCode: info.versionCode,
  };

  return c.json({
    versionName: info.versionName,
    versionCode: info.versionCode,
    apkSize: apkBuffer.length,
  }, 201);
});

export default app;
