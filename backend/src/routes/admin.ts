import { Hono } from "hono";
import { readFileSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const html = readFileSync(
  join(__dirname, "../admin/dashboard.html"),
  "utf-8"
);

const app = new Hono();

app.get("/", (c) => {
  return c.html(html);
});

export default app;
