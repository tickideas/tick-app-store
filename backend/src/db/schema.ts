import { sqliteTable, text, integer } from "drizzle-orm/sqlite-core";

export const apps = sqliteTable("apps", {
  id: text("id").primaryKey(),
  name: text("name").notNull(),
  packageName: text("package_name").notNull().unique(),
  description: text("description").default(""),
  iconKey: text("icon_key"), // R2 object key for app icon
  createdAt: integer("created_at", { mode: "timestamp" })
    .notNull()
    .$defaultFn(() => new Date()),
  updatedAt: integer("updated_at", { mode: "timestamp" })
    .notNull()
    .$defaultFn(() => new Date()),
});

export const versions = sqliteTable("versions", {
  id: text("id").primaryKey(),
  appId: text("app_id")
    .notNull()
    .references(() => apps.id, { onDelete: "cascade" }),
  versionName: text("version_name").notNull(), // e.g. "1.2.3"
  versionCode: integer("version_code").notNull(), // e.g. 5
  apkKey: text("apk_key").notNull(), // R2 object key
  apkSize: integer("apk_size"), // bytes
  releaseNotes: text("release_notes").default(""),
  createdAt: integer("created_at", { mode: "timestamp" })
    .notNull()
    .$defaultFn(() => new Date()),
});
