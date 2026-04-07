import nodeApk from "node-apk";
const { Apk } = nodeApk;
type Resource = { value: any; locale?: { language?: string; country?: string } };

export interface ApkInfo {
  packageName: string;
  versionName: string;
  versionCode: number;
  appName: string | null;
  icon: Buffer | null;
}

export async function parseApk(buffer: Buffer): Promise<ApkInfo> {
  const apk = new Apk(buffer);

  try {
    const manifest = await apk.getManifestInfo();

    // Resolve app label
    let appName: string | null = null;
    const label = manifest.applicationLabel;
    if (typeof label === "string") {
      appName = label;
    } else if (typeof label === "number") {
      const resources = await apk.getResources();
      const resolved: Resource[] = resources.resolve(label);
      if (resolved.length > 0) {
        const en = resolved.find(
          (r: Resource) => r.locale && r.locale.language === "en"
        );
        appName = String((en || resolved[0]).value);
      }
    }

    // Extract icon
    let icon: Buffer | null = null;
    const iconRef = manifest.applicationIcon;
    if (iconRef) {
      try {
        const resources = await apk.getResources();
        const resolved: Resource[] = resources.resolve(iconRef);
        // Pick the highest-density PNG icon (skip xml/adaptive icons)
        const pngIcons = resolved.filter(
          (r: Resource) =>
            typeof r.value === "string" &&
            r.value.endsWith(".png")
        );
        const densityOrder = ["xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi"];
        let bestIcon = pngIcons[0];
        for (const density of densityOrder) {
          const match = pngIcons.find((r: Resource) =>
            String(r.value).includes(density)
          );
          if (match) {
            bestIcon = match;
            break;
          }
        }
        if (bestIcon) {
          icon = await apk.extract(String(bestIcon.value));
        } else {
          // Adaptive icons — try to extract the foreground drawable PNG
          const foregroundPaths = [
            "res/drawable-xxxhdpi-v4/ic_launcher_foreground.png",
            "res/drawable-xxhdpi-v4/ic_launcher_foreground.png",
            "res/drawable-xhdpi-v4/ic_launcher_foreground.png",
            "res/drawable-hdpi-v4/ic_launcher_foreground.png",
            "res/mipmap-xxxhdpi/ic_launcher_foreground.png",
            "res/mipmap-xxhdpi/ic_launcher_foreground.png",
            "res/mipmap-xhdpi/ic_launcher_foreground.png",
          ];
          for (const path of foregroundPaths) {
            try {
              icon = await apk.extract(path);
              break;
            } catch {
              // Try next path
            }
          }
        }
      } catch {
        // Icon extraction can fail — that's ok
      }
    }

    return {
      packageName: manifest.package,
      versionName: manifest.versionName,
      versionCode: manifest.versionCode,
      appName,
      icon,
    };
  } finally {
    apk.close();
  }
}
