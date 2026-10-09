# Manual GitHub Release Guide

## 1. Push repository

Create an empty GitHub repository called `flashsale-helper` with a `main` branch. From your downloaded source:

```bash
git init
git add .
git commit -m "Initial AccessibilityService flash-sale helper"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/flashsale-helper.git
git push -u origin main
```

**Replace** `YOUR_USERNAME` with your username, or use GitHub Desktop to publish the repo. No GitHub Personal Access Token should be stored in the repository.

## 2. Test / preview release

Go to **Actions → Build APK and publish GitHub Release → Run workflow**:

- `tag`: `v0.1.0-rc1` (must be unique)
- `variant`: `debug`
- `prerelease`: true

The workflow tests and builds the app, checks that the debug APK is signed, uploads APK + `SHA256SUMS.txt` to both the workflow run and a GitHub Release. GitHub Actions' debug keystore is ephemeral; the next preview may not install over the previous preview. Uninstall the old preview if necessary (this deletes app data).

No release happens automatically on a push. Only a manual dispatch creates a Release. An existing tag/release is refused to avoid silently overwriting a downloadable binary.

## 3. Stable signing key

Create a **keystore offline** and store it somewhere durable and backed up. Example for local computer (do **not** commit this keystore):

```bash
keytool -genkeypair -v -keystore flashsale-release.jks \
  -alias flashsale -keyalg RSA -keysize 3072 -validity 10000
```

Encode its contents locally:

```bash
# Linux/macOS
base64 -w 0 flashsale-release.jks
# On macOS use: base64 < flashsale-release.jks | tr -d '\n'
```

For PowerShell:

```powershell
[Convert]::ToBase64String([System.IO.File]::ReadAllBytes("flashsale-release.jks"))
```

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**. Add:

| Secret | Content |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Entire base64-encoded keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Keystore key alias, e.g. `flashsale` |
| `RELEASE_KEY_PASSWORD` | Key password |

Then manually run a unique tag such as `v0.1.0` with `variant=release`, `prerelease=false`.

**Do not lose the original keystore or passwords.** Android upgrades require a matching signing identity. Debug and release variants will not generally upgrade over one another. Keep version codes monotonically increasing for future stable releases.

## 4. Verify your APK

```bash
sha256sum -c SHA256SUMS.txt
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose FlashSaleHelper-v0.1.0-release.apk
```

On Windows, PowerShell can calculate a hash using `Get-FileHash <file> -Algorithm SHA256`.

## 5. Workflow permissions and protection

The release workflow requests `contents: write` for its own `GITHUB_TOKEN` (GitHub Release creation). Repository Actions permissions may need to allow write access. Set branch protection on `main` and review changes to workflow files, especially those that handle signing material. The signing key is decoded only on the hosted runner, in temporary storage. Never print it into logs.

## 6. Troubleshooting

- **`Failed to find package 'tools'` during Android SDK setup:** use `android-actions/setup-android@v4` with `packages: 'platform-tools'`. The legacy SDK `tools` package was removed by Google; do not request it.
- **`sdkmanager` unavailable:** inspect the `android-actions/setup-android@v4` step; GitHub's runner image must include supported Android command-line tools.
- **Missing signing secret:** choose debug preview, or configure all four release signing secrets.
- **APK unsigned:** inspect Gradle's `release` signing configuration and the `apksigner verify` output.
- **Permission denied on release:** check repository Actions permission `contents: write`, and organization rules.
- **Release tag already exists:** choose a new tag; changing a published APK in place is intentionally disabled.
- **Android refuses sideloaded accessibility:** newer Android builds may require explicit approval under the application's system settings before the service can be enabled.
