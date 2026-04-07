import { createMiddleware } from "hono/factory";

export const adminAuth = createMiddleware(async (c, next) => {
  const apiKey = process.env.ADMIN_API_KEY;
  if (!apiKey) {
    return c.json({ error: "Server misconfigured: no admin key set" }, 500);
  }

  const provided =
    c.req.header("X-API-Key") ||
    c.req.header("Authorization")?.replace("Bearer ", "");

  if (provided !== apiKey) {
    return c.json({ error: "Unauthorized" }, 401);
  }

  await next();
});
