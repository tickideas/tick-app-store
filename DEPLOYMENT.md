# Deployment Guide — Tick Ideas App Store Backend

This guide walks through deploying the backend on Dokploy with Cloudflare R2 for file storage.

---

## Step 1: Create the R2 Bucket

1. Go to [Cloudflare Dashboard](https://dash.cloudflare.com) → **R2 Object Storage** → **Create bucket**
2. Name it: `tick-app-store` (or whatever you prefer)
3. Location: choose the region closest to your server
4. Click **Create bucket**

### Get your R2 credentials

1. In the R2 overview page, click **Manage R2 API Tokens** (right sidebar)
2. Click **Create API Token**
3. Set permissions to **Object Read & Write**
4. Scope it to the `tick-app-store` bucket only
5. Click **Create API Token**
6. Save these — you'll need them:
   - **Access Key ID** (looks like a long alphanumeric string)
   - **Secret Access Key**

### Get your R2 endpoint

Your R2 endpoint is:
```
https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

You can find your **Account ID** in the Cloudflare dashboard URL or in **R2 Overview** on the right sidebar.

### (Optional) Enable public access

If you want direct download URLs instead of presigned URLs:

1. Go to your bucket → **Settings** → **Public Access**
2. Add a custom domain (e.g. `r2.yourdomain.com`) or use the R2.dev subdomain
3. Note the public URL — you'll set this as `R2_PUBLIC_URL`

> If you skip this, the backend will generate presigned URLs automatically. Both work fine.

---

## Step 2: Push to Git

The backend needs to be in a Git repository that Dokploy can access.

```bash
cd tick-app-store
git init
git add .
git commit -m "Initial commit: app store backend"
git remote add origin git@github.com:tickideas/tick-app-store.git
git push -u origin main
```

---

## Step 3: Deploy on Dokploy

### Create the service

1. Open your Dokploy dashboard
2. Click **Create Project** (or use an existing one)
3. Inside the project, click **Create Service** → **Application**
4. Give it a name: `tick-app-store`

### Connect the repository

1. In the service settings, go to **General**
2. Set **Provider** to your Git provider (GitHub, GitLab, etc.)
3. Select your `tick-app-store` repository
4. Set **Branch**: `main`
5. Set **Build Path**: `./backend` (important — the Dockerfile is in the backend directory)

### Configure the domain

1. Go to the **Domains** tab
2. Add your domain (e.g. `appstore-api.yourdomain.com`)
3. Set **Container Port**: `3000`
4. Enable HTTPS (Let's Encrypt)

### Add environment variables

Go to the **Environment** tab and add these:

```env
# Admin auth — generate a strong random key
ADMIN_API_KEY=<generate-a-strong-key>

# Cloudflare R2
R2_ENDPOINT=https://<your-account-id>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=<your-r2-access-key>
R2_SECRET_ACCESS_KEY=<your-r2-secret-key>
R2_BUCKET=tick-app-store

# Optional: if you enabled public access on R2
# R2_PUBLIC_URL=https://r2.yourdomain.com

# Store app self-update (we'll update these later when the Android app is built)
STORE_APP_VERSION_NAME=1.0.0
STORE_APP_VERSION_CODE=1
```

To generate a strong admin key:
```bash
openssl rand -hex 32
```

### Set up persistent storage

The SQLite database needs to persist across deploys.

1. Go to the **Advanced** tab → **Volumes** (or **Mounts**)
2. Add a volume mount:
   - **Volume Name**: `appstore-data`
   - **Mount Path**: `/app/data`

> This is critical. Without it, your database resets on every deploy.

### Deploy

Click **Deploy**. Watch the build logs — you should see:
```
🏪 Tick Ideas App Store running on http://localhost:3000
```

---

## Step 4: Verify the deployment

Once deployed, test from your terminal:

```bash
# Health check
curl https://appstore-api.yourdomain.com/
# Expected: {"name":"Tick Ideas App Store","status":"running"}

# List apps (should be empty)
curl https://appstore-api.yourdomain.com/api/apps
# Expected: {"apps":[]}

# Store version
curl https://appstore-api.yourdomain.com/api/store/version
# Expected: {"versionName":"1.0.0","versionCode":1}

# Test admin auth works
curl -X POST https://appstore-api.yourdomain.com/api/apps \
  -H "X-API-Key: your-admin-key" \
  -F "name=Test App" \
  -F "packageName=com.tickideas.test" \
  -F "description=A test app"
# Expected: {"id":"...","name":"Test App","packageName":"com.tickideas.test"}
```

If the test app was created successfully, clean it up:
```bash
curl -X DELETE https://appstore-api.yourdomain.com/api/apps/<app-id> \
  -H "X-API-Key: your-admin-key"
```

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `Cannot open database because the directory does not exist` | Volume not mounted. Check that `/app/data` is mounted |
| `Server misconfigured: no admin key set` | `ADMIN_API_KEY` env var is missing |
| R2 upload fails | Check `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` |
| Database resets after redeploy | Volume mount is missing or misconfigured |
| 502 Bad Gateway | Container port mismatch — make sure domain points to port `3000` |

---

## What's Next

Once the backend is live and verified, we'll build the Android app store client that talks to this API.
