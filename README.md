# twyn-sdk-android â€” integration sample

> Public integration sample for the **Twyn Android SDK** (T4FastID / Twyn PAAS).
> This repository contains **no proprietary logic** â€” it shows how to integrate the
> SDK and pulls the engine as a signed binary from the private registry.

## What is here

| Path | Purpose |
|---|---|
| `sample/` | Minimal Android app that launches the SDK and handles the callbacks |
| `docs/integration.md` | Step-by-step integration guide |
| `.github/workflows/ci.yml` | Builds the sample against the released SDK binary |

## SDK artifact

| | |
|---|---|
| Coordinates | `com.t4isb:t4fastid:<version>` |
| Format | Android Archive (`.aar`) + stripped native libs |
| Registry | GitHub Packages (private) â€” `https://maven.pkg.github.com/twyn-internal/twyn-android-sdk` |
| Native ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| minSdk / targetSdk | 24 / 36 |

> The registry is **private**. You need a GitHub token with `read:packages` for the
> `twyn` org. Credentials are read from `gradle.properties` / CI secrets â€” never
> committed.

## Quick start

1. Add the registry + dependency (see `docs/integration.md`):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    google(); mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/twyn-internal/twyn-android-sdk")
      credentials {
        username = providers.gradleProperty("twynUser").get()
        password = providers.gradleProperty("twynToken").get()
      }
    }
  }
}
```

```kotlin
// app/build.gradle.kts
dependencies { implementation("com.t4isb:t4fastid:1.0.0") }
```

2. Register the listeners and launch the SDK:

```kotlin
T4FastID.setEnrListener(this)
T4FastID.setSDKStatusListener(this)

val intent = T4FastID.createIntent(this, sdkKey, personId, canal, env).apply {
  putExtra("language", "en")
  putExtra("requestSteps", arrayOf("T4_FACE"))
}
startActivity(intent)
```

3. Handle the callbacks (`EnrollListener`):

```kotlin
override fun onEnrollFaceCompleted(r: EnrollFaceResult?) {
  // r?.tcn, r?.livenessStatus, r?.croppedFace
}
override fun onEnrollFaceError(e: EnrollFaceError?) {
  // e?.error_code, e?.error_message
}
```

4. (Optional) device-integrity layer:

```kotlin
SentinelIntegrator.initAsync(applicationContext, "https://<your-sentinel-host>", allowCleartext = false)
SentinelIntegrator.scanAndSubmit(this, deepScan = false)
```

## License

The **sample code** in this repository is released under the MIT License (see `LICENSE`).
The **SDK binary** is proprietary and governed by a separate commercial license â€”
this repository grants no rights to the SDK itself.
