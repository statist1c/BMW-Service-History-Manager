# Publishing and future app updates

Destination: https://github.com/statist1c/BMW-Service-History-Manager

The app already checks this repository. No GitHub token is embedded. Upload the project source and publish APK releases when you are ready; source is maintained in this repository; APK releases are uploaded manually.

## Preserve the signing identity

The supplied installable APK is **release signed**. A separate private signing kit contains its keystore, password and backup instructions. Keep it offline and private. **Do not upload the signing kit, keystore, password or base64 secret as a repository file or release asset.** They are intentionally absent from this project/source ZIP.

Every future APK must use this same keystore/alias, package ID `dev.mgu.service`, and a higher versionCode. Losing the key prevents updates to installations signed with it. If you choose a different key before your first public release, uninstall this preview and install the new signed build; then retain your chosen key for all subsequent releases.

Alias: `bmw-service`. Store password and key password are the same value, supplied in the private kit. The certificate is self-signed for Android application identity; it does not claim BMW endorsement.

## Included GitHub release workflow

1. Upload the contents of this project, retaining dotfiles, the Gradle wrapper JAR and `LICENSE.md` / `NOTICE.md`. Do not upload `app/build`, `.gradle`, `local.properties`, or the private signing kit.
2. In the GitHub repository, create Actions secrets:
   * `ANDROID_KEYSTORE_BASE64`: base64 of `bmw-service-release.jks` from the private kit.
   * `ANDROID_KEYSTORE_PASSWORD`: the exact password from `keystore-password.txt`.
3. Optionally run `.github/workflows/release.yml` manually from Actions, using the desired version such as `v0.2.2`. It builds the selected branch, runs core checks and Android lint, signs the APK and saves an Actions artifact containing `BMW-Service-Manager-v0.2.2.apk`, `BMW-Service-Manager-v0.2.2-source.zip` and `BMW-Service-Manager-v0.2.2-SHA256SUMS.txt` (using the requested version). It does not run on pushes or release creation, and never creates a release or uploads release assets.
4. Download the artifact and attach the APK yourself to a stable release with the matching version tag. You can also upload the supplied signed v0.2.2 APK directly without running the workflow. For later updates, use a higher stable version. The workflow derives versionName and versionCode from its version input: `major*1,000,000 + minor*1,000 + patch`. Components minor/patch must be below 1,000. Version `0.2.2` is versionCode 2002.
5. Confirm GitHub's release asset metadata includes its SHA-256 digest. The app intentionally will not offer APKs missing that digest, mismatched names, drafts, prereleases or nonstable tags. GitHub-generated source archives alone are not installable APKs.

To create the base64 secret without displaying it, encode the keystore to a local file and paste that value into the secret field. Delete that temporary file after setting the secret. Never place the password/base64 directly into a shell command or log.

## Local signed build

Set `BMW_KEYSTORE_PASSWORD` in the process environment from your private password file, then run:

```text
bash gradlew assembleRelease -PreleaseKeystore=/absolute/private/path/bmw-service-release.jks -PappVersionName=0.2.2 -PappVersionCode=2002
```

On Windows, use `gradlew.bat` and a Windows absolute path, and quote each `-P...` argument in PowerShell. The keystore argument is resolved relative to the `app` module if it is not absolute. Rename/copy `app/build/outputs/apk/release/app-release.apk` to **`BMW-Service-Manager-v0.2.2.apk`** before attaching it to the matching stable GitHub release. Always increment both versions, sign with the same key, and retain the noncommercial license and notices.

## Update behavior and limits

Daily checks are best effort under Android background scheduling. Download is explicit; install is explicit and uses Android's package installer. If Android opens the “allow installs from this source” screen, enable it, return to this app and review/install again. The verified APK is held in the app-private cache; it may be removed by Android, in which case download again.

The updater checks the latest 100 release entries without authentication, so GitHub public API rate limits apply. Repository access errors are shown in Settings. Update availability and verification are tested with local fixtures; an actual newer public release must be published before a real network update can be demonstrated.

For release data, the implementation uses [GitHub's release asset API](https://docs.github.com/en/rest/releases/assets) and [Android JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler).

Attach exactly one APK per release. Its filename can be versioned or renamed; the updater discovers the APK by its extension. Do not attach a second legacy-name copy: multiple APKs are ambiguous and skipped. Existing v0.2.0 or earlier installations still require the old fixed filename, or a manual installation of v0.2.2 to adopt this policy.
