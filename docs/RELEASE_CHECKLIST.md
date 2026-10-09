# Release checklist

## Before building

- Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
- Enable Maps SDK for Android, Places API (New), and Routes API in the release Google Cloud project.
- Restrict the Maps/Places Android key to the `com.nearbyspot` package and every signing-certificate SHA-1 used for distribution.
- For production-scale Routes REST traffic, use a controlled backend and a separate server-side credential. If direct mobile REST access is retained, use a dedicated Routes-only key with the strongest supported restrictions, tight quotas, and billing alerts; an APK cannot keep that key secret.
- Keep the real key only in the untracked `secrets.properties`; never place it in CI logs or source control.
- Review Google Maps Platform quota and billing alerts for the intended rollout size.

## Verify

Run from the repository root:

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease compileDebugAndroidTestKotlin
```

On a device with Google Play services, smoke-test both coarse and precise location permission, nearby search, filters, favorites, destination autocomplete, route creation, route-stop refresh, error retry, dark theme, and opening a place in Google Maps. Use a Goa route as the long-route regression case and confirm every returned route stop has at least 100 reviews.

Release builds enable R8 code shrinking and resource shrinking. The checked-in fallback API key exists only so CI can compile; it cannot make live Google requests.

## Package and distribute

Use Play App Signing rather than committing a keystore or passwords. Configure the upload signing key outside the repository with environment variables, then create the release bundle:

```bash
export NEARBYSPOT_RELEASE_STORE_FILE=/absolute/path/to/upload-keystore.jks
export NEARBYSPOT_RELEASE_STORE_PASSWORD=your_store_password
export NEARBYSPOT_RELEASE_KEY_ALIAS=your_upload_alias
export NEARBYSPOT_RELEASE_KEY_PASSWORD=your_key_password
./gradlew bundleRelease
```

When those variables are absent, release tasks intentionally create unsigned artifacts for CI verification. Never store the values in tracked Gradle files or shell scripts.

Upload the signed `.aab` through a protected release channel, complete the Play Console data-safety and privacy disclosures, and use an internal test track before production rollout. Confirm crash-free startup and live Google API requests from the Play-signed build before widening the release.
