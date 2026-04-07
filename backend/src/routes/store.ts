import { Hono } from "hono";
import { getDownloadUrl } from "../services/r2.js";

const app = new Hono();

// Store app self-update check
// The Android app store client calls this to check if there's a newer version of itself
app.get("/version", (c) => {
  return c.json({
    versionName: process.env.STORE_APP_VERSION_NAME || "1.0.0",
    versionCode: parseInt(process.env.STORE_APP_VERSION_CODE || "1", 10),
  });
});

// Download the latest app store APK
app.get("/download", async (c) => {
  const key = process.env.STORE_APP_APK_KEY;
  if (!key) {
    return c.json({ error: "Store APK not configured" }, 404);
  }
  const url = await getDownloadUrl(key);
  return c.redirect(url);
});

export default app;
