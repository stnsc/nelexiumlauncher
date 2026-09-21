# Trying OTA updates

This branch adds a sideloaded APK updater. Install its initial APK manually once.
Open **Settings → App updates** for feed settings, download status, and installation controls.
Version 5 (`1.4-ota`) is the initial updater build.

## Hosting

Host a signed APK and the following JSON on a public HTTPS server (static hosting
is enough). Replace the URL and checksum with real values. No server credentials
belong in the feed or app. Publish the APK first and replace the feed last.

```json
{
  "versionCode": 6,
  "versionName": "1.5",
  "minSdk": 23,
  "apkUrl": "https://your-server/updates/nelexium-6.apk",
  "sha256": "REPLACE_WITH_64_CHARACTER_SHA256_OF_THE_SIGNED_APK"
}
```

Use immutable APK URLs for each version and disable long-lived caching for the
feed. HTTPS redirects are supported (including GitHub release downloads); redirects
to HTTP are rejected. APKs are limited to 256 MB, feeds to 64 KB.

## Two-build test

1. From the repository root run `bash gradlew :app:assembleDebug`. Install
   `app/build/outputs/apk/debug/app-debug.apk` on the dash manually.
2. Change `versionCode` in `app/build.gradle.kts` to `6` and `versionName` to `1.5`.
   Rebuild with the same signing key. Do not install this second build manually.
3. Calculate `sha256sum app/build/outputs/apk/debug/app-debug.apk`, upload that APK,
   and publish the JSON above using its checksum and actual HTTPS download URL.
4. On the dash open **Settings → App updates**, enter your JSON URL, and tap **Check and download now**.
5. While parked, tap **Install downloaded update**. On Android 8+, allow this app
   to install unknown apps when prompted, then return and tap Install again.
   Older Android versions may require enabling Unknown sources in system settings.
6. Confirm Android's installation dialog and reopen the launcher with Home if needed.
   Check that Updates shows version 6 and that your themes remain intact.

For an automatic test, enable the checkbox and save settings. The launcher checks
when it next has focus and internet access, then every 15 minutes while open.
An update-ready button appears after the download; installation is always manual.
Downloads can use metered connections such as a phone hotspot.

## Signing and failures

Use the same application ID and signing key for the initial install and all updates.
The updater verifies SHA-256, package name, exact version code, minimum Android
version, and matching current signing certificates before offering installation.
Android's installer performs the final APK signature verification. This first version
does not support signing-key rotation. A debug APK from another machine may have a
different debug key; release distribution should use a backed-up release keystore.
The existing project does not configure release signing: use Android Studio's
Generate Signed APK flow or your private signing configuration. Never commit keys.

Failed checks/downloads show an error and retry on the next interval or manual check.
An interrupted partial download restarts from the beginning. Completed downloads
survive process restarts and are reused after the next successful feed check and
verification. Work is not scheduled while the launcher is closed, and downloads may
be stopped if Android kills the process. No silent install, automatic rollback, or
guaranteed automatic relaunch is implemented. An offline launch still works normally.

Before relying on this on the dash, test: no internet, reconnection, a broken URL,
incorrect checksum, an APK signed with a different key, a cancelled installation,
and power/process interruption during download. Verify layout on the dash resolution.

Platform references: [install-source permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()),
[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider),
and [APK signing](https://developer.android.com/studio/publish/app-signing).
