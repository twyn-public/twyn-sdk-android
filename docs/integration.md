# Android integration guide

## 1. Add the private registry

The SDK is published to a **private** GitHub Packages Maven registry. You need a
GitHub account with access to the `twyn` org and a personal access token (classic)
with the `read:packages` scope.

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven {
            name = "TwynSdkDist"
            url = uri("https://maven.pkg.github.com/twyn-internal/twyn-sdk-dist")
            credentials {
                username = providers.gradleProperty("twynUser").orNull
                password = providers.gradleProperty("twynToken").orNull
            }
        }
    }
}
```

`gradle.properties` (git-ignored):

```properties
twynUser=your-github-user
twynToken=ghp_xxx
```

## 2. Add the dependency

```kotlin
dependencies {
    implementation("com.t4isb:t4fastid:1.0.0")
}
```

## 3. Permissions

The SDK needs camera, network and (optionally) location:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-feature android:name="android.hardware.camera.front" android:required="true" />
```

## 4. Register listeners

The listeners live on the SDK's companion object and are invoked while the SDK
activity is in the foreground:

```kotlin
T4FastID.setEnrListener(this)         // EnrollListener
T4FastID.setSDKStatusListener(this)   // SDKStatusListener
T4FastID.setEnrBackendListener(this)  // BackendTransactionListener (optional)
```

## 5. Launch the SDK

```kotlin
val intent = T4FastID.createIntent(this, sdkKey, personId, canal, env).apply {
    putExtra("language", "en")                 // "pt" | "en"
    putExtra("requestSteps", arrayOf("T4_FACE")) // e.g. T4_FACE, T4_FINGER, T4_DOCUMENT
    putExtra("iBeta", true)                    // raw liveness result, no retry dialog
    // optional: putExtra("tcn", "..."); putExtra("tot", "...")
}
startActivity(intent)
```

`env` accepts `"dev"` or `"prod"`.

## 6. Handle the result

```kotlin
override fun onEnrollFaceCompleted(result: EnrollFaceResult?) {
    // result?.livenessStatus : Boolean?
    // result?.tcn            : String?
    // result?.croppedFace    : String? (base64)
}

override fun onEnrollFaceError(error: EnrollFaceError?) {
    // error?.error_code, error?.error_message
}
```

> **Security:** the final decision (APPROVED / REJECTED / CHALLENGE) is made
> **server-side**, not from `livenessStatus`. Treat the SDK callback as the capture
> result and read the authoritative decision from your backend / the audit API.

## 7. Device integrity (optional)

The SDK ships a RASP layer that scans the device (root / Frida / hooks / camera
pipeline) and submits a report to the Sentinel server:

```kotlin
SentinelIntegrator.initAsync(
    applicationContext,
    "https://<your-sentinel-host>",
    allowCleartext = false,
)
SentinelIntegrator.scanAndSubmit(this, deepScan = false)
```

The verdict flows into the transaction (`extend.sentinel`) and the gateway applies
`SENTINEL_BLOCK` / `SENTINEL_REVIEW` accordingly.

## 8. R8 / ProGuard

Keep the SDK entry points. The SDK ships consumer rules; if you build the AAR
yourself, add:

```proguard
-keep class com.t4isb.t4fastid.** { *; }
-keepclassmembers class com.t4isb.t4fastid.** { *; }
```
